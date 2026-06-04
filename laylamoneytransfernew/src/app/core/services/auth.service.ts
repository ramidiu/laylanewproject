import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';
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

  refreshToken(): Observable<TokenResponse> {
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    return this.http.post<TokenResponse>(`${this.baseUrl}/refresh`, { refreshToken }).pipe(
      tap(response => {
        this.storeTokens(response.accessToken, response.refreshToken);
      })
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
    this.isAuthenticatedSubject.next(false);
    window.location.href = redirectTo;
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
