import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError, catchError } from 'rxjs';
import { ToastController } from '@ionic/angular';
import { Router } from '@angular/router';

@Injectable()
export class ErrorInterceptor implements HttpInterceptor {
  private readonly silentPaths = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/verify-mfa', '/auth/verify-otp', '/auth/resend-otp', '/auth/check-email', '/api/wallet', '/api/referrals', '/api/fx/rates', '/api/fx/quote', '/api/corridors', '/kyc/status', '/kyc/documents', '/api/beneficiaries'];

  constructor(
    private toastController: ToastController,
    private router: Router
  ) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // Don't show toast for 401 (handled by auth interceptor)
        // Don't show toast for auth endpoints (handled by login page)
        if (error.status === 401 || this.isSilentPath(request.url)) {
          return throwError(() => error);
        }

        let message = 'An unexpected error occurred';

        if (error.error instanceof ErrorEvent) {
          message = error.error.message;
        } else {
          switch (error.status) {
            case 0:
              message = 'Unable to connect to the server. Please check your connection.';
              break;
            case 400:
              message = error.error?.message || 'Invalid request.';
              break;
            case 403:
              message = 'You do not have permission to perform this action.';
              break;
            case 404:
              message = error.error?.message || 'Resource not found.';
              break;
            case 409:
              message = error.error?.message || 'A conflict occurred.';
              break;
            case 429:
              message = 'Too many requests. Please wait a moment.';
              break;
            case 500:
              message = 'Server error. Please try again later.';
              break;
            case 503:
              message = 'Service temporarily unavailable.';
              break;
            default:
              message = error.error?.message || `Error: ${error.statusText}`;
          }
        }

        this.showErrorToast(message);
        return throwError(() => error);
      })
    );
  }

  private isSilentPath(url: string): boolean {
    return this.silentPaths.some(path => url.includes(path));
  }

  private async showErrorToast(message: string): Promise<void> {
    try { await this.toastController.dismiss(); } catch {}
    const toast = await this.toastController.create({
      message,
      duration: 5000,
      position: 'top',
      color: 'danger',
      cssClass: 'fb-toast fb-toast-danger',
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
