import { Component, OnInit } from '@angular/core';
import { ConfigService } from '../../core/services/config.service';

interface CountryGroup {
  countryName: string;
  currency: string;
  countryCode: string;
  items: any[];
}

@Component({
  selector: 'app-admin-transfer-config',
  templateUrl: './admin-transfer-config.page.html',
  styleUrls: ['./admin-transfer-config.page.scss']
})
export class AdminTransferConfigPage implements OnInit {
  activeSegment: 'payout' | 'payment' | 'limits' = 'payout';

  payoutGroups: CountryGroup[] = [];
  payoutLoading = true;

  paymentGroups: CountryGroup[] = [];
  paymentLoading = true;

  // Transaction send-amount limits (system_config: transaction.min_send_amount / max_send_amount)
  private readonly MIN_KEY = 'transaction.min_send_amount';
  private readonly MAX_KEY = 'transaction.max_send_amount';
  minSend: number | null = null;
  maxSend: number | null = null;
  limitsCurrency = 'GBP';
  limitsLoading = true;
  limitsSaving = false;
  limitsMsg = '';
  limitsMsgType: 'success' | 'error' | '' = '';

  constructor(private configService: ConfigService) {}

  ngOnInit(): void {
    this.loadPayoutTypes();
    this.loadPaymentMethods();
    this.loadLimits();
  }

  loadLimits(): void {
    this.limitsLoading = true;
    this.limitsMsg = '';
    this.configService.getSystemConfig().subscribe({
      next: (res) => {
        const all = Array.isArray(res) ? res : res?.data || [];
        const min = all.find((c: any) => c.configKey === this.MIN_KEY);
        const max = all.find((c: any) => c.configKey === this.MAX_KEY);
        this.minSend = min?.configValue != null ? Number(min.configValue) : null;
        this.maxSend = max?.configValue != null ? Number(max.configValue) : null;
        this.limitsLoading = false;
      },
      error: () => { this.limitsLoading = false; this.showLimitsMsg('Could not load limits.', 'error'); }
    });
  }

  saveLimits(): void {
    this.limitsMsg = '';
    if (this.minSend == null || isNaN(this.minSend) || this.minSend < 0) {
      this.showLimitsMsg('Enter a valid minimum amount.', 'error'); return;
    }
    if (this.maxSend == null || isNaN(this.maxSend) || this.maxSend <= 0) {
      this.showLimitsMsg('Enter a valid maximum amount.', 'error'); return;
    }
    if (this.minSend >= this.maxSend) {
      this.showLimitsMsg('Minimum must be less than maximum.', 'error'); return;
    }
    this.limitsSaving = true;
    // Save min, then max, then reload to pick up fresh versions.
    this.configService.updateSystemConfig(this.MIN_KEY, String(this.minSend)).subscribe({
      next: () => {
        this.configService.updateSystemConfig(this.MAX_KEY, String(this.maxSend)).subscribe({
          next: () => {
            this.limitsSaving = false;
            this.showLimitsMsg('Send-amount limits updated.', 'success');
            this.loadLimits();
          },
          error: (e) => { this.limitsSaving = false; this.showLimitsMsg(this.errText(e, 'Failed to save maximum.'), 'error'); }
        });
      },
      error: (e) => { this.limitsSaving = false; this.showLimitsMsg(this.errText(e, 'Failed to save minimum.'), 'error'); }
    });
  }

  private errText(e: any, fallback: string): string {
    return e?.error?.message || e?.message || fallback;
  }

  private showLimitsMsg(msg: string, type: 'success' | 'error'): void {
    this.limitsMsg = msg;
    this.limitsMsgType = type;
  }

  loadPayoutTypes(): void {
    this.payoutLoading = true;
    this.configService.getPayoutTypes().subscribe({
      next: (res) => {
        const all = Array.isArray(res) ? res : res?.data || [];
        const active = all.filter((i: any) => i.isActive);
        this.payoutGroups = this.groupByCountry(active);
        this.payoutLoading = false;
      },
      error: () => { this.payoutGroups = []; this.payoutLoading = false; }
    });
  }

  loadPaymentMethods(): void {
    this.paymentLoading = true;
    this.configService.getPaymentMethods().subscribe({
      next: (res) => {
        const all = Array.isArray(res) ? res : res?.data || [];
        const active = all.filter((i: any) => i.isActive);
        this.paymentGroups = this.groupByCountry(active);
        this.paymentLoading = false;
      },
      error: () => { this.paymentGroups = []; this.paymentLoading = false; }
    });
  }

  formatPayoutType(type: string): string {
    switch (type) {
      case 'BANK_TRANSFER': return 'Bank Transfer';
      case 'MOBILE_MONEY': return 'Mobile Money';
      case 'CASH_COLLECTION': return 'Cash Collection';
      default: return type?.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()) || '';
    }
  }

  formatPaymentMethod(method: string): string {
    switch (method) {
      case 'BANK_TRANSFER': return 'Bank Transfer';
      case 'CREDIT_DEBIT_CARD': return 'Credit / Debit Card';
      case 'PAY_WITH_BANK': return 'Pay With Bank';
      case 'WALLET': return 'Wallet';
      default: return method?.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()) || '';
    }
  }

  getPayoutIcon(type: string): string {
    switch (type) {
      case 'BANK_TRANSFER': return 'business-outline';
      case 'MOBILE_MONEY': return 'phone-portrait-outline';
      case 'CASH_COLLECTION': return 'cash-outline';
      default: return 'card-outline';
    }
  }

  getPaymentIcon(method: string): string {
    switch (method) {
      case 'BANK_TRANSFER': return 'business-outline';
      case 'CREDIT_DEBIT_CARD': return 'card-outline';
      case 'PAY_WITH_BANK': return 'wallet-outline';
      case 'WALLET': return 'phone-portrait-outline';
      default: return 'card-outline';
    }
  }

  private groupByCountry(items: any[]): CountryGroup[] {
    const groups: Record<string, CountryGroup> = {};
    for (const item of items) {
      const key = item.countryCode;
      if (!groups[key]) {
        groups[key] = {
          countryName: item.countryName,
          currency: item.currency,
          countryCode: item.countryCode,
          items: []
        };
      }
      groups[key].items.push(item);
    }
    return Object.values(groups).sort((a, b) => a.countryName.localeCompare(b.countryName));
  }
}
