import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

/**
 * Single source of truth for transaction-status wording across the whole app.
 * Every raw value — Layla enum (UPPER_SNAKE), USI strings (lowercase, spaced), and legacy/invented
 * ones — is normalised to ONE canonical status, then shown via the i18n label. No screen should ever
 * render a raw status string; use <app-status-chip [status]="...">.
 */
@Component({
  selector: 'app-status-chip',
  template: `
    <span class="fb-status-chip" [ngClass]="chipClass">
      {{ displayStatus }}
    </span>
  `,
  styles: [`:host { display: inline-block; }`]
})
export class StatusChipComponent {
  @Input() status: string = '';

  /** raw value (any casing/vocabulary) -> canonical status key */
  private static readonly MAP: Record<string, string> = {
    // pending / created
    'created': 'PENDING', 'pending': 'PENDING',
    // compliance is disabled -> surfaced as pending (keeps prior behaviour)
    'compliance_hold': 'PENDING', 'awaiting_compliance': 'PENDING',
    // processing (funds in, provider initiated). "funds received" is an internal state that the
    // customer/admin should just see as "Processing" — there is no separate funds-received status.
    'processing': 'PROCESSING', 'initiated': 'PROCESSING',
    'funds_received': 'PROCESSING', 'funds received': 'PROCESSING',
    // sending to payout partner
    'sent_to_payout': 'SENDING', 'sent for pay': 'SENDING',
    // done — every "success/paid/completed" synonym collapses to PAID
    'paid': 'PAID', 'completed': 'PAID', 'processed': 'PAID', 'delivered': 'PAID',
    'settled': 'PAID', 'success': 'PAID', 'payment_completed': 'PAID',
    // archived = a stale, UNPAID pending order that was auto-cleaned up — NOT paid, NOT a payout failure
    'archived': 'ARCHIVED',
    // failed
    'failed': 'FAILED', 'create_failed': 'FAILED', 'confirm_failed': 'FAILED',
    // cancelled
    'cancelled': 'CANCELLED', 'canceled': 'CANCELLED', 'cancel_requested': 'CANCELLED',
    'aborted': 'CANCELLED', 'deleted': 'CANCELLED',
    // refunded
    'refunded': 'REFUNDED'
  };

  private get canonical(): string {
    const raw = (this.status || '').trim().toLowerCase();
    return StatusChipComponent.MAP[raw] || (this.status || '').trim().toUpperCase().replace(/ /g, '_');
  }

  get chipClass(): string {
    return `fb-status-chip--${this.canonical.toLowerCase().replace(/_/g, '-')}`;
  }

  get displayStatus(): string {
    const key = 'TRANSACTIONS.STATUS_' + this.canonical;
    const translated = this.translate.instant(key);
    if (translated !== key) return translated;
    // Fallback: humanise the canonical value (FUNDS_RECEIVED -> "Funds Received")
    return this.canonical.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  constructor(private translate: TranslateService) {}
}
