import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { environment } from '../../../environments/environment';

interface SetupContext {
  email: string;
  password: string;
  secret: string;
  qr: string;
}

@Component({
  selector: 'app-admin-mfa-setup',
  templateUrl: './admin-mfa-setup.page.html',
  styleUrls: ['./admin-mfa-setup.page.scss']
})
export class AdminMfaSetupPage implements OnInit {
  context: SetupContext | null = null;
  code = '';
  submitting = false;
  errorMessage = '';

  constructor(
    private http: HttpClient,
    private router: Router,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    const stored = sessionStorage.getItem('fb_mfa_setup');
    if (!stored) {
      this.router.navigate(['/login']);
      return;
    }
    try {
      this.context = JSON.parse(stored);
    } catch {
      sessionStorage.removeItem('fb_mfa_setup');
      this.router.navigate(['/login']);
    }
  }

  complete(): void {
    if (!this.context || !this.code || this.code.length !== 6 || this.submitting) return;
    this.submitting = true;
    this.errorMessage = '';

    this.http.post<any>(`${environment.apiUrl}/auth/admin/mfa-setup-complete`, {
      email: this.context.email,
      password: this.context.password,
      secret: this.context.secret,
      code: this.code
    }).subscribe({
      next: (resp) => {
        this.submitting = false;
        // Persist the real access token the same way auth.service does
        if (resp.accessToken) {
          localStorage.setItem('accessToken', resp.accessToken);
        }
        if (resp.refreshToken) {
          localStorage.setItem('refreshToken', resp.refreshToken);
        }
        sessionStorage.removeItem('fb_mfa_setup');
        this.toast('MFA enabled successfully. Welcome!', 'success');
        window.location.href = '/admin';
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err?.error?.message || 'Failed to verify code. Try again.';
      }
    });
  }

  cancel(): void {
    sessionStorage.removeItem('fb_mfa_setup');
    this.router.navigate(['/login']);
  }

  private async toast(message: string, color: string): Promise<void> {
    const t = await this.toastCtrl.create({
      message, duration: 3000, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`
    });
    await t.present();
  }
}
