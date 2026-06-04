import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { AuthService } from '../../core/services/auth.service';
import { LanguageService } from '../../core/services/language.service';


@Component({
  selector: 'app-login',
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss']
})
export class LoginPage implements OnInit {
  loginForm!: FormGroup;
  mfaForm!: FormGroup;
  showMfa = false;
  mfaToken = '';
  showPassword = false;
  returnUrl = '';
  loginError = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router,
    private toastCtrl: ToastController,
    public languageService: LanguageService
  ) {}

  get isRtl(): boolean { return this.languageService.isRtl; }
  toggleLanguage(): void { this.languageService.toggleLanguage(); }

  ngOnInit(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });

    this.mfaForm = this.fb.group({
      code: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(6)]]
    });

    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '';

    // Clear error when user types
    this.loginForm.valueChanges.subscribe(() => {
      if (this.loginError) this.loginError = '';
    });
  }

  private static readonly ADMIN_ONLY_ROLES = [
    'ADMIN', 'SUPER_ADMIN', 'COMPLIANCE_OFFICER', 'TREASURY_MANAGER', 'SUPPORT', 'FINANCE'
  ];

  onLogin(): void {
    if (this.loginForm.invalid) return;
    this.loginError = '';

    this.authService.login(this.loginForm.value).subscribe({
      next: (response) => {
        if (response.emailVerified === false) {
          const email = response.email || this.loginForm.value.email;
          this.showToast('Please verify your email to continue.', 'warning');
          window.location.href = `/otp-verify?email=${encodeURIComponent(email)}&flow=login`;
        } else if (response.mfaSetupRequired) {
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
          // Block admin/staff roles from customer frontend
          const user = this.authService.getCurrentUser();
          const roles: string[] = user?.roles || [];
          const hasAdminRole = roles.some((r: string) => LoginPage.ADMIN_ONLY_ROLES.includes(r));
          if (hasAdminRole) {
            this.authService.logout();
            this.loginError = 'Staff accounts cannot login here. Please use the Admin Login at /admin-login';
            return;
          }
          this.navigateAfterLogin();
        }
      },
      error: (error) => {
        const msg = (error.error?.message || '').toLowerCase();
        if (error.error?.emailVerified === false || error.error?.code === 'EMAIL_NOT_VERIFIED'
            || msg.includes('not verified') || msg.includes('otp verification')) {
          const email = error.error?.email || this.loginForm.value.email;
          this.showToast('We sent a verification code to your email.', 'warning');
          window.location.href = `/otp-verify?email=${encodeURIComponent(email)}&flow=login`;
          return;
        }
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

  goToRegister(): void {
    window.location.href = '/register';
  }

  togglePassword(): void {
    this.showPassword = !this.showPassword;
  }

  private navigateAfterLogin(): void {
    const user = this.authService.getCurrentUser();
    const roles = user?.roles || [];
    const isOnlyCustomer = roles.includes('CUSTOMER') &&
      !roles.some((r: string) => ['ADMIN', 'SUPER_ADMIN', 'AGENT', 'PAYOUT_PARTNER', 'PAYIN_PARTNER'].includes(r));
    if (isOnlyCustomer && user?.kycTier === 'TIER_0') {
      window.location.href = '/home/kyc';
      return;
    }
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
