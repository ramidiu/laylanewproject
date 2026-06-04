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
  activeSegment: 'payout' | 'payment' = 'payout';

  payoutGroups: CountryGroup[] = [];
  payoutLoading = true;

  paymentGroups: CountryGroup[] = [];
  paymentLoading = true;

  constructor(private configService: ConfigService) {}

  ngOnInit(): void {
    this.loadPayoutTypes();
    this.loadPaymentMethods();
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
