import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';

@Component({
  selector: 'app-mfa-setup',
  templateUrl: './mfa-setup.page.html',
  styleUrls: ['./mfa-setup.page.scss']
})
export class MfaSetupPage implements OnInit {
  step: 'setup' | 'verify' | 'done' = 'setup';
  mfaEnabled = false;
  secret = '';
  qrCodeDataUri = '';
  verifyCode = '';
  password = '';
  error = '';
  enabling = false;
  loading = true;

  constructor(
    private authService: AuthService,
    private userService: UserService,
    private router: Router,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.userService.getProfile().subscribe({
      next: (user) => {
        this.mfaEnabled = user.mfaEnabled;
        this.loading = false;
        if (!this.mfaEnabled) {
          this.loadSetup();
        }
      },
      error: () => {
        this.loading = false;
        this.error = 'Failed to load profile.';
      }
    });
  }

  loadSetup(): void {
    this.error = '';
    this.authService.setupMfa().subscribe({
      next: (res) => {
        this.secret = res.secret;
        this.qrCodeDataUri = res.qrCodeDataUri;
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to load MFA setup. Please try again.';
      }
    });
  }

  onEnable(): void {
    if (this.verifyCode.length !== 6) return;
    this.enabling = true;
    this.error = '';

    this.authService.enableMfa(this.secret, this.verifyCode).subscribe({
      next: () => {
        this.enabling = false;
        this.step = 'done';
        this.mfaEnabled = true;
      },
      error: (err) => {
        this.enabling = false;
        this.error = err.error?.message || 'Invalid code. Please try again.';
      }
    });
  }

  onDisable(): void {
    if (!this.password) return;
    this.error = '';

    this.authService.disableMfa(this.password).subscribe({
      next: async () => {
        this.mfaEnabled = false;
        const toast = await this.toastCtrl.create({
          message: 'Two-factor authentication has been disabled.',
          duration: 4000,
          position: 'top',
          color: 'success',
          cssClass: 'fb-toast fb-toast-success',
          buttons: [{ icon: 'close-outline', role: 'cancel' }]
        });
        await toast.present();
        this.router.navigate(['/home/profile']);
      },
      error: (err) => {
        this.error = err.error?.message || 'Failed to disable 2FA. Check your password.';
      }
    });
  }
}
