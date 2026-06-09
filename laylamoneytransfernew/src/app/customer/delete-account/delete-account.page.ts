import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AlertController } from '@ionic/angular';
import { AccountService } from '../../core/services/account.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-delete-account',
  templateUrl: './delete-account.page.html',
  styleUrls: ['./delete-account.page.scss']
})
export class DeleteAccountPage {
  confirmed = false;
  submitting = false;
  reason = '';

  constructor(
    private accountService: AccountService,
    private authService: AuthService,
    private toast: ToastService,
    private alertCtrl: AlertController,
    public router: Router
  ) {}

  cancel(): void {
    this.router.navigate(['/home/profile']);
  }

  async onDeletePressed(): Promise<void> {
    if (!this.confirmed || this.submitting) { return; }

    const alert = await this.alertCtrl.create({
      header: 'Delete your account?',
      message:
        'Deleting your account will remove your profile and disable access to the app.\n\n' +
        'Certain transaction records and identity verification documents may be retained for the ' +
        'legally required retention period under Anti-Money Laundering (AML), Know Your Customer (KYC), ' +
        'tax, and financial regulations.\n\nThis action cannot be undone.',
      cssClass: 'delete-account-alert',
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Delete Account',
          role: 'destructive',
          cssClass: 'alert-danger',
          handler: () => { this.submit(); }
        }
      ]
    });
    await alert.present();
  }

  private submit(): void {
    this.submitting = true;
    const user = this.authService.getCurrentUser();
    this.accountService.requestDeletion({ userId: user?.sub, reason: this.reason?.trim() || undefined })
      .subscribe({
        next: () => {
          this.submitting = false;
          // Clear everything and prevent further access.
          try {
            localStorage.clear();
            sessionStorage.clear();
          } catch {}
          this.toast.success('Your account deletion request has been received. You have been signed out.', 6000);
          // authService.logout removes tokens and hard-navigates to /login.
          this.authService.logout('/login');
        },
        error: (err) => {
          this.submitting = false;
          const msg = err?.error?.message || 'Could not submit your deletion request. Please try again or contact support.';
          this.toast.error(msg, 6000);
        }
      });
  }
}
