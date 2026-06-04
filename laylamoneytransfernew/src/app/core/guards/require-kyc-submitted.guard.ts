import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { firstValueFrom, retry } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { KycService } from '../services/kyc.service';

@Injectable({
  providedIn: 'root'
})
export class RequireKycSubmittedGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private kycService: KycService,
    private router: Router
  ) {}

  async canActivate(): Promise<boolean> {
    const user = this.authService.getCurrentUser();
    if (!user) return false;

    const roles: string[] = user.roles || [];
    const isOnlyCustomer = roles.includes('CUSTOMER') &&
      !roles.some(r => ['ADMIN', 'SUPER_ADMIN', 'AGENT', 'PAYOUT_PARTNER', 'PAYIN_PARTNER'].includes(r));
    if (!isOnlyCustomer) return true;

    // Code added by Naresh: only redirect when the backend EXPLICITLY returns
    // overallStatus === 'NOT_SUBMITTED'. Transient failures (401 during token
    // refresh, tab-freeze network drops, aborted requests, null payloads) used
    // to be treated as "not submitted" and kicked the user to /home/kyc on
    // every tab return — that false redirect is what this change removes.
    // Backend still enforces KYC on any protected action, so allowing
    // navigation on a transient error is safe.
    try {
      const status: any = await firstValueFrom(
        this.kycService.getStatus(user.sub).pipe(retry(1))
      );
      // PARTIAL = legacy/imported customer with no real submission — treat like NOT_SUBMITTED:
      // they are email/mobile-verified and can log in, but must complete document + address KYC.
      if (status?.overallStatus === 'NOT_SUBMITTED' || status?.overallStatus === 'PARTIAL') {
        this.router.navigate(['/home/kyc'], { queryParams: { fromRegistration: 'true' } });
        return false;
      }
      return true;
    } catch {
      console.warn('[RequireKycSubmittedGuard] KYC status check failed; allowing navigation (backend remains source of truth)');
      return true;
    }
  }
}
