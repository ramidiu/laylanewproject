import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of, tap, map, catchError, finalize, shareReplay } from 'rxjs';
import { Preferences } from '@capacitor/preferences';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  MfaVerifyRequest,
  TokenResponse,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  JwtPayload,
  OtpVerifyRequest,
  OtpVerifyResponse
} from '../models/auth.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly baseUrl = `${environment.apiUrl}/auth`;
  private readonly ACCESS_TOKEN_KEY = 'fb_access_token';
  private readonly REFRESH_TOKEN_KEY = 'fb_refresh_token';

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasValidToken());
  isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, request).pipe(
      tap(response => {
        if (!response.mfaRequired && response.accessToken) {
          this.storeTokens(response.accessToken, response.refreshToken!);
        }
      })
    );
  }

  adminLogin(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/admin/login`, request).pipe(
      tap(response => {
        if (!response.mfaRequired && response.accessToken) {
          this.storeTokens(response.accessToken, response.refreshToken!);
        }
      })
    );
  }

  checkEmail(email: string): Observable<{ exists: boolean }> {
    return this.http.get<{ exists: boolean }>(`${this.baseUrl}/check-email`, { params: { email } });
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.baseUrl}/register`, request);
  }

  requestDemoAccess(request: {
    fullName: string;
    country: string;
    email: string;
    phone: string;
    role: string;
  }): Observable<{ email: string; role: string; loginUrl: string; expiresAt: string; message: string }> {
    return this.http.post<any>(`${this.baseUrl}/demo-access`, request);
  }

  verifyOtp(request: OtpVerifyRequest): Observable<OtpVerifyResponse> {
    return this.http.post<OtpVerifyResponse>(`${this.baseUrl}/verify-otp`, request).pipe(
      tap(response => {
        if (response.accessToken) {
          this.storeTokens(response.accessToken, response.refreshToken);
        }
      })
    );
  }

  resendOtp(email: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/resend-otp`, { email });
  }

  verifyMfa(request: MfaVerifyRequest): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.baseUrl}/verify-mfa`, request).pipe(
      tap(response => {
        this.storeTokens(response.accessToken, response.refreshToken);
      })
    );
  }

  setupMfa(): Observable<any> {
    return this.http.post(`${this.baseUrl}/mfa/setup`, {});
  }

  enableMfa(secret: string, code: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/mfa/enable`, { secret, code });
  }

  disableMfa(password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/mfa/disable`, { password });
  }

  // Single-flight refresh: every caller (route guard, HTTP interceptor, concurrent
  // 401s) shares ONE in-flight /refresh call. This is essential because the backend
  // ROTATES refresh tokens (revokes the old one on use) — firing two refreshes with
  // the same token would revoke it and log the user out. shareReplay + finalize make
  // all callers await the same result, then reset so the next expiry can refresh again.
  private refreshInFlight$: Observable<TokenResponse> | null = null;

  refreshToken(): Observable<TokenResponse> {
    if (this.refreshInFlight$) return this.refreshInFlight$;
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    this.refreshInFlight$ = this.http.post<TokenResponse>(`${this.baseUrl}/refresh`, { refreshToken }).pipe(
      tap(response => this.storeTokens(response.accessToken, response.refreshToken)),
      shareReplay(1),
      finalize(() => { this.refreshInFlight$ = null; })
    );
    return this.refreshInFlight$;
  }

  /**
   * Resolve whether the session is usable, refreshing SILENTLY when the access token
   * has expired but a refresh token still exists. Used by the route guard so a user
   * with a valid refresh token is NOT bounced to the login/home screen just because
   * their short-lived access token lapsed (e.g. after unlocking the app lock).
   */
  ensureAuthenticated(): Observable<boolean> {
    if (this.hasValidToken()) return of(true);
    if (!localStorage.getItem(this.REFRESH_TOKEN_KEY)) return of(false);
    return this.refreshToken().pipe(
      map(() => true),
      catchError(() => of(false))
    );
  }

  changePassword(currentPassword: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/change-password`, { currentPassword, newPassword });
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/forgot-password`, { email } as ForgotPasswordRequest);
  }

  resetPassword(token: string, newPassword: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/reset-password`, { resetToken: token, newPassword });
  }

  logout(redirectTo: string = '/login'): void {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    void this.clearDurable();
    this.isAuthenticatedSubject.next(false);
    window.location.href = redirectTo;
  }

  // ── Durable token storage (Capacitor Preferences) ────────────────────────────
  // On iOS/Android the WebView can EVICT localStorage under storage pressure, which
  // silently logs the user out. We mirror the tokens into native Preferences (which
  // survives eviction and app restarts) and, at app startup, rehydrate localStorage
  // from Preferences when it's been wiped. localStorage stays the synchronous source
  // of truth so the interceptor/guards keep working unchanged. On web, Preferences
  // falls back to its own storage — harmless.

  /** Mirror tokens into native storage. Fire-and-forget; never blocks the login flow. */
  private async persistDurable(accessToken: string, refreshToken: string): Promise<void> {
    try {
      await Preferences.set({ key: this.ACCESS_TOKEN_KEY, value: accessToken });
      await Preferences.set({ key: this.REFRESH_TOKEN_KEY, value: refreshToken });
    } catch { /* native storage unavailable — localStorage still holds them */ }
  }

  /** Remove tokens from native storage on logout. */
  private async clearDurable(): Promise<void> {
    try {
      await Preferences.remove({ key: this.ACCESS_TOKEN_KEY });
      await Preferences.remove({ key: this.REFRESH_TOKEN_KEY });
    } catch { /* ignore */ }
  }

  /**
   * Called once at app startup (APP_INITIALIZER). If the WebView evicted localStorage
   * but native Preferences still has the tokens, restore them so the session survives.
   */
  async restoreFromDurableStore(): Promise<void> {
    try {
      if (localStorage.getItem(this.ACCESS_TOKEN_KEY) && localStorage.getItem(this.REFRESH_TOKEN_KEY)) return;
      const [a, r] = await Promise.all([
        Preferences.get({ key: this.ACCESS_TOKEN_KEY }),
        Preferences.get({ key: this.REFRESH_TOKEN_KEY }),
      ]);
      if (a.value) localStorage.setItem(this.ACCESS_TOKEN_KEY, a.value);
      if (r.value) localStorage.setItem(this.REFRESH_TOKEN_KEY, r.value);
      if (a.value || r.value) this.isAuthenticatedSubject.next(this.hasValidToken());
    } catch { /* ignore — first launch or web */ }
  }

  /** Roles that log in via the staff page (/admin-login) rather than the customer page (/login). */
  private static readonly STAFF_ROLES = [
    'ADMIN', 'SUPER_ADMIN', 'COMPLIANCE_OFFICER', 'TREASURY_MANAGER',
    'SUPPORT', 'FINANCE', 'PAYOUT_PARTNER', 'PAYIN_PARTNER', 'AGENT'
  ];

  /**
   * Log out because the session expired / was rejected, sending the user to the CORRECT login
   * page for who they are: staff (admin/superadmin/partner/agent) → /admin-login, customers →
   * /login. Reads the login route BEFORE logout() clears the token.
   */
  sessionExpiredLogout(): void {
    const loginRoute = this.loginRouteForContext();
    this.logout(loginRoute);
  }

  /** Pick the right login page from the (possibly expired) token's roles, falling back to the URL area. */
  private loginRouteForContext(): string {
    const roles = this.getUserRoles();   // decodes the token even if expired
    if (roles.some(r => AuthService.STAFF_ROLES.includes(r))) return '/admin-login';
    const path = (typeof window !== 'undefined' && window.location?.pathname) || '';
    if (/^\/(admin|superadmin|partner|payin-partner|payout-partner|agent)(\/|$)/.test(path)) return '/admin-login';
    return '/login';
  }

  isAuthenticated(): boolean {
    return this.hasValidToken();
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  getCurrentUser(): JwtPayload | null {
    const token = this.getAccessToken();
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload));
    } catch {
      return null;
    }
  }

  getUserRoles(): string[] {
    const user = this.getCurrentUser();
    return user?.roles || [];
  }

  hasRole(role: string): boolean {
    return this.getUserRoles().includes(role);
  }

  hasPermission(permission: string): boolean {
    const user = this.getCurrentUser();
    return user?.permissions?.includes(permission) || false;
  }

  getHomeRoute(): string {
    const roles = this.getUserRoles();
    if (roles.includes('SUPER_ADMIN')) return '/superadmin';
    if (roles.includes('ADMIN')) return '/superadmin';
    if (roles.includes('PAYIN_PARTNER')) return '/payin-partner';
    if (roles.includes('PAYOUT_PARTNER')) return '/partner';
    if (roles.includes('AGENT')) return '/agent';
    // TIER_0 pure customers go to KYC (not admins who also have CUSTOMER role)
    const user = this.getCurrentUser();
    const isOnlyCustomer = roles.includes('CUSTOMER') &&
      !roles.some(r => ['ADMIN', 'SUPER_ADMIN', 'AGENT', 'PAYOUT_PARTNER', 'PAYIN_PARTNER'].includes(r));
    if (isOnlyCustomer && user?.kycTier === 'TIER_0') {
      return '/home/kyc';
    }
    return '/home';
  }

  private storeTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    void this.persistDurable(accessToken, refreshToken);   // mirror to native storage
    this.isAuthenticatedSubject.next(true);
  }

  private hasValidToken(): boolean {
    const token = this.getAccessToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }
}
