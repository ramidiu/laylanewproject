import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { AuthService } from '../services/auth.service';
import { KycService } from '../services/kyc.service';
import { UserService } from '../services/user.service';
import { firstValueFrom, retry } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class KycGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private kycService: KycService,
    private userService: UserService,
    private router: Router,
    private toastCtrl: ToastController
  ) {}

  async canActivate(): Promise<boolean> {
    const user = this.authService.getCurrentUser();
    if (!user) return false;

    // Only enforce for pure customers
    const roles = user.roles || [];
    const isOnlyCustomer = roles.includes('CUSTOMER') &&
      !roles.some((r: string) => ['ADMIN', 'SUPER_ADMIN', 'AGENT', 'PAYOUT_PARTNER', 'PAYIN_PARTNER'].includes(r));
    if (!isOnlyCustomer) return true;

    // Code added by Naresh: profile fetch — definitive signals only.
    // On transient failure (null profile, network error, token-refresh race)
    // we intentionally fall through to the kyc status check and, if that also
    // fails, allow navigation. Backend still enforces KYC on protected actions.
    let profile: any = null;
    try {
      profile = await firstValueFrom(this.userService.getProfile().pipe(retry(1)));
    } catch {
      console.warn('[KycGuard] profile fetch failed — deferring to KYC status check');
    }

    if (profile) {
      // Explicit PENDING_VERIFICATION signal — admin is re-verifying the user.
      if (profile.status === 'PENDING_VERIFICATION') {
        const toast = await this.toastCtrl.create({
          message: 'Your profile changes are under review. You cannot make transactions until admin re-verifies your account.',
          duration: 6000, position: 'top', color: 'warning',
          cssClass: 'fb-toast fb-toast-warning',
          buttons: [{ icon: 'close-outline', role: 'cancel' }]
        });
        await toast.present();
        this.router.navigate(['/home/profile']);
        return false;
      }
      // Explicit verified tier — allow, UNLESS the verified documents have expired.
      // An approved document whose expiry date has passed must be treated exactly
      // like the pending-review case: block before the Send Money page and send the
      // user to re-upload (the renewed docs then go to pending review).
      if (profile.kycTier && profile.kycTier !== 'TIER_0') {
        if (await this.hasExpiredDocuments(user.sub)) {
          const toast = await this.toastCtrl.create({
            message: 'Your verified documents have expired. Please upload renewed documents for admin verification.',
            duration: 6000, position: 'top', color: 'warning',
            cssClass: 'fb-toast fb-toast-warning',
            buttons: [{ icon: 'close-outline', role: 'cancel' }]
          });
          await toast.present();
          this.router.navigate(['/home/kyc']);
          return false;
        }
        return true;
      }
    }

    // Code added by Naresh: fallback to KYC document status. Only block when
    // the backend EXPLICITLY returns a non-verified state. On any transient
    // failure (null, timeout, 401 during refresh, aborted request) we allow
    // navigation — the false-redirect after a tab freeze came from this path.
    let overallStatus: string | null = null;
    try {
      const status: any = await firstValueFrom(
        this.kycService.getStatus(user.sub).pipe(retry(1))
      );
      overallStatus = status?.overallStatus ?? null;
    } catch {
      console.warn('[KycGuard] KYC status fetch failed — allowing navigation (backend remains source of truth)');
      return true;
    }

    // If we never got a definitive profile AND the status endpoint returned
    // nothing useful, allow navigation rather than guess.
    if (!profile && !overallStatus) {
      console.warn('[KycGuard] No definitive KYC signal; allowing navigation');
      return true;
    }

    // At this point the backend explicitly said the customer is not verified
    // (profile present with TIER_0, OR overallStatus in NOT_SUBMITTED/PENDING/REJECTED).
    let message = 'Please complete your identity verification before making a transaction.';
    let color: 'danger' | 'warning' = 'danger';
    if (overallStatus === 'PENDING') {
      message = 'Your documents are awaiting verification. You will be able to make transactions once your identity is verified.';
      color = 'warning';
    } else if (overallStatus === 'REJECTED') {
      message = 'Your identity verification was rejected. Please re-submit your documents.';
      color = 'danger';
    }

    try { await this.toastCtrl.dismiss(); } catch {}
    const toast = await this.toastCtrl.create({
      message,
      duration: 6000,
      position: 'top',
      color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
    this.router.navigate(['/home/kyc']);
    return false;
  }

  // Mirrors the backend transaction gate (validateKycDocumentsVerified): a document
  // that was APPROVED but whose expiry date has now passed counts as expired. On any
  // fetch failure we return false (don't block here) — the backend still enforces KYC
  // when the transaction is actually created.
  private async hasExpiredDocuments(userSub: string): Promise<boolean> {
    try {
      const docs: any[] = await firstValueFrom(this.kycService.getDocuments(userSub).pipe(retry(1)));
      if (!Array.isArray(docs) || docs.length === 0) return false;
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      return docs.some(d => {
        const status = (d?.status || '').toString().toUpperCase();
        if (status !== 'APPROVED' || !d?.expiryDate) return false;
        const expiry = new Date(d.expiryDate);
        return !isNaN(expiry.getTime()) && expiry < today;
      });
    } catch {
      console.warn('[KycGuard] expired-documents check failed — deferring to backend enforcement');
      return false;
    }
  }
}
