import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-admin-login',
  templateUrl: './admin-login.page.html',
  styleUrls: ['./admin-login.page.scss']
})
export class AdminLoginPage implements OnInit {
  loginForm!: FormGroup;
  mfaForm!: FormGroup;
  showMfa = false;
  mfaToken = '';
  showPassword = false;
  returnUrl = '';
  loginError = '';

  private static readonly STAFF_ROLES = [
    'ADMIN', 'SUPER_ADMIN', 'COMPLIANCE_OFFICER', 'TREASURY_MANAGER',
    'SUPPORT', 'FINANCE', 'PAYOUT_PARTNER', 'PAYIN_PARTNER'
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private route: ActivatedRoute,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });

    this.mfaForm = this.fb.group({
      code: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(6)]]
    });

    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '';

    this.loginForm.valueChanges.subscribe(() => {
      if (this.loginError) this.loginError = '';
    });
  }

  onLogin(): void {
    if (this.loginForm.invalid) return;
    this.loginError = '';

    this.authService.adminLogin(this.loginForm.value).subscribe({
      next: (response) => {
        if (response.mfaSetupRequired) {
          sessionStorage.setItem('fb_mfa_setup', JSON.stringify({
            email: this.loginForm.value.email,
            password: this.loginForm.value.password,
            secret: response.mfaSetupSecret || '',
            qr: response.mfaSetupQrCodeUri || ''
          }));
          window.location.href = '/admin-mfa-setup';
        } else if (response.mfaRequired) {
          this.showMfa = true;
          this.mfaToken = response.mfaToken!;
        } else {
          const user = this.authService.getCurrentUser();
          const roles: string[] = user?.roles || [];
          const hasStaffRole = roles.some((r: string) => AdminLoginPage.STAFF_ROLES.includes(r));
          if (!hasStaffRole) {
            this.authService.logout();
            this.loginError = 'This login is for staff accounts only. Use the customer login instead.';
            return;
          }
          this.navigateAfterLogin();
        }
      },
      error: (error) => {
        this.loginError = error.error?.message || 'Invalid email or password. Please try again.';
      }
    });
  }

  onVerifyMfa(): void {
    if (this.mfaForm.invalid) return;

    this.authService.verifyMfa({
      mfaToken: this.mfaToken,
      totpCode: this.mfaForm.value.code
    }).subscribe({
      next: () => {
        this.navigateAfterLogin();
      },
      error: (error) => {
        this.showToast(error.error?.message || 'Invalid verification code.', 'danger');
      }
    });
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  private navigateAfterLogin(): void {
    const url = this.returnUrl || this.authService.getHomeRoute();
    window.location.href = url;
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      duration: 4000,
      position: 'top',
      color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
