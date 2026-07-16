import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-status-chip',
  template: `
    <span class="fb-status-chip" [ngClass]="chipClass">
      {{ displayStatus }}
    </span>
  `,
  styles: [`
    :host { display: inline-block; }
  `]
})
export class StatusChipComponent {
  @Input() status: string = '';

  constructor(private translate: TranslateService) {}

  /** Compliance is disabled → mask COMPLIANCE_HOLD as PENDING. */
  private get effectiveStatus(): string {
    if ((this.status || '').toUpperCase() === 'COMPLIANCE_HOLD') return 'PENDING';
    return this.status;
  }

  get chipClass(): string {
    const normalized = this.effectiveStatus.toLowerCase().replace(/_/g, '-');
    return `fb-status-chip--${normalized}`;
  }

  get displayStatus(): string {
    const status = this.effectiveStatus;
    const key = 'TRANSACTIONS.STATUS_' + status.replace(/ /g, '_').toUpperCase();
    const translated = this.translate.instant(key);
    return translated !== key ? translated : status.replace(/_/g, ' ');
  }
}
