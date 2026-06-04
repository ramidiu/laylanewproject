import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError, BehaviorSubject, filter, take, switchMap, catchError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { DeviceFingerprintService } from '../services/device-fingerprint.service';

/**
 * Outbound-HTTP interceptor for all authenticated requests.
 *
 * Responsibilities (in order per request):
 *   1. Attach the FingerprintJS visitor id as `X-Visitor-Id` — on every
 *      request regardless of auth state. The backend uses this for fraud
 *      scoring in `TransactionService.computeAndStoreRiskScore`.
 *   2. Skip auth header injection for public endpoints listed in
 *      `excludedPaths` (login, register, forgot/reset password, quotes, etc.).
 *   3. Attach `Authorization: Bearer <accessToken>` from `AuthService` for
 *      every other request.
 *   4. On a 401 response, attempt a single refresh-token flow and retry the
 *      failed request with the new token. Concurrent 401s are queued via
 *      `refreshTokenSubject` so the refresh only fires once.
 *   5. If refresh fails, force a logout and bubble the error to the caller.
 *
 * ## Gotchas
 * - `change-password` is excluded from the 401 retry: a 401 there means the
 *   current password was wrong, not that the token expired.
 * - The visitor id is read synchronously from in-memory cache in
 *   `DeviceFingerprintService`. If the fingerprint computation hasn't
 *   completed yet (cold start), the header is simply omitted — the service
 *   pre-warms on app bootstrap to minimise this window.
 * - Do not add tokens to cross-origin requests that aren't to our own API —
 *   current excluded-paths list is conservative on purpose.
 */
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  private refreshTokenSubject = new BehaviorSubject<string | null>(null);

  private readonly excludedPaths = ['/auth/login', '/auth/register', '/auth/forgot-password', '/auth/reset-password', '/auth/check-email', '/fx/quote', '/fx/rates'];
  private readonly excludedExactPaths = ['/api/corridors', '/api/fx/corridors'];

  constructor(
    private authService: AuthService,
    private fingerprintService: DeviceFingerprintService
  ) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    request = this.addVisitorId(request);

    if (this.isExcluded(request.url)) {
      return next.handle(request);
    }

    const token = this.authService.getAccessToken();
    if (token) {
      request = this.addToken(request, token);
    }

    return next.handle(request).pipe(
      catchError(error => {
        if (error instanceof HttpErrorResponse && error.status === 401 && !request.url.includes('/auth/change-password')) {
          return this.handle401Error(request, next);
        }
        return throwError(() => error);
      })
    );
  }

  private addToken(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private addVisitorId(request: HttpRequest<any>): HttpRequest<any> {
    const headers: Record<string, string> = {};
    const visitorId = this.fingerprintService.getVisitorIdSync();
    if (visitorId) headers['X-Visitor-Id'] = visitorId;
    const partnerId = sessionStorage.getItem('fb_admin_partner_id');
    if (partnerId) headers['X-Partner-Id'] = partnerId;
    if (Object.keys(headers).length === 0) return request;
    return request.clone({ setHeaders: headers });
  }

  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      return this.authService.refreshToken().pipe(
        switchMap(tokenResponse => {
          this.isRefreshing = false;
          this.refreshTokenSubject.next(tokenResponse.accessToken);
          return next.handle(this.addToken(request, tokenResponse.accessToken));
        }),
        catchError(error => {
          this.isRefreshing = false;
          this.authService.logout();
          return throwError(() => error);
        })
      );
    }

    return this.refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap(token => next.handle(this.addToken(request, token!)))
    );
  }

  private isExcluded(url: string): boolean {
    if (this.excludedPaths.some(path => url.includes(path))) return true;
    // Exact-match public corridor endpoints (don't exclude /corridors/payin-partners etc.)
    const urlPath = new URL(url, 'http://localhost').pathname;
    return this.excludedExactPaths.some(path =>
      urlPath === path || urlPath.match(new RegExp(`^${path.replace('*', '[^/]+')}(/delivery-methods|/limits)?$`))
    );
  }
}
