import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { ConfigService } from '../../core/services/config.service';

interface CountryGroup {
  countryName: string;
  currency: string;
  countryCode: string;
  items: any[];
}

@Component({
  selector: 'app-transfer-config',
  templateUrl: './transfer-config.page.html',
  styleUrls: ['./transfer-config.page.scss']
})
export class TransferConfigPage implements OnInit {
  activeSegment: 'payout' | 'payment' | 'points' = 'payout';

  payoutGroups: CountryGroup[] = [];
  payoutLoading = true;

  paymentGroups: CountryGroup[] = [];
  paymentLoading = true;

  // Cash collection points (admin manage)
  cashPoints: any[] = [];
  cashPointsLoading = true;
  newPoint: any = { countryCode: '', countryName: '', pointName: '', address: '', city: '', contactNumber: '', isActive: true };
  savingPoint = false;

  constructor(
    private configService: ConfigService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadPayoutTypes();
    this.loadPaymentMethods();
    this.loadCashPoints();
  }

  // --- Cash collection points ---

  loadCashPoints(): void {
    this.cashPointsLoading = true;
    this.configService.getAllCashPoints().subscribe({
      next: (res) => {
        this.cashPoints = (Array.isArray(res) ? res : res?.data || [])
          .sort((a: any, b: any) => (a.countryCode || '').localeCompare(b.countryCode || ''));
        this.cashPointsLoading = false;
      },
      error: () => { this.cashPoints = []; this.cashPointsLoading = false; }
    });
  }

  addCashPoint(): void {
    const p = this.newPoint;
    if (!p.countryCode?.trim() || !p.pointName?.trim()) {
      this.showToast('Country code and point name are required', 'warning');
      return;
    }
    this.savingPoint = true;
    this.configService.addCashPoint({
      countryCode: p.countryCode.trim().toUpperCase(),
      countryName: p.countryName?.trim() || '',
      pointName: p.pointName.trim(),
      address: p.address?.trim() || '',
      city: p.city?.trim() || '',
      contactNumber: p.contactNumber?.trim() || '',
      isActive: true
    }).subscribe({
      next: () => {
        this.showToast('Collection point added', 'success');
        this.newPoint = { countryCode: '', countryName: '', pointName: '', address: '', city: '', contactNumber: '', isActive: true };
        this.savingPoint = false;
        this.loadCashPoints();
      },
      error: () => { this.savingPoint = false; this.showToast('Failed to add collection point', 'danger'); }
    });
  }

  toggleCashPoint(point: any): void {
    this.configService.updateCashPoint(point.id, { isActive: !point.isActive }).subscribe({
      next: () => { this.showToast('Collection point updated', 'success'); this.loadCashPoints(); },
      error: () => this.showToast('Failed to update', 'danger')
    });
  }

  deleteCashPoint(point: any): void {
    this.configService.deleteCashPoint(point.id).subscribe({
      next: () => { this.showToast('Collection point deleted', 'success'); this.loadCashPoints(); },
      error: () => this.showToast('Failed to delete', 'danger')
    });
  }

  loadPayoutTypes(): void {
    this.payoutLoading = true;
    this.configService.getPayoutTypes().subscribe({
      next: (res) => {
        const items = Array.isArray(res) ? res : res?.data || [];
        this.payoutGroups = this.groupByCountry(items);
        this.payoutLoading = false;
      },
      error: () => { this.payoutGroups = []; this.payoutLoading = false; }
    });
  }

  loadPaymentMethods(): void {
    this.paymentLoading = true;
    this.configService.getPaymentMethods().subscribe({
      next: (res) => {
        const items = Array.isArray(res) ? res : res?.data || [];
        this.paymentGroups = this.groupByCountry(items);
        this.paymentLoading = false;
      },
      error: () => { this.paymentGroups = []; this.paymentLoading = false; }
    });
  }

  // --- Toggles ---

  togglePayoutType(item: any): void {
    this.configService.togglePayoutType(item.id).subscribe({
      next: () => { this.showToast('Payout type toggled', 'success'); this.loadPayoutTypes(); },
      error: () => this.showToast('Failed to toggle payout type', 'danger')
    });
  }

  togglePaymentMethod(item: any): void {
    this.configService.togglePaymentMethod(item.id).subscribe({
      next: () => { this.showToast('Payment method toggled', 'success'); this.loadPaymentMethods(); },
      error: () => this.showToast('Failed to toggle payment method', 'danger')
    });
  }

  togglePayoutCountry(group: CountryGroup): void {
    const active = !this.isGroupActive(group);
    this.configService.togglePayoutCountry(group.countryCode, active).subscribe({
      next: () => { this.showToast(`${group.countryName} payouts ${active ? 'enabled' : 'disabled'}`, 'success'); this.loadPayoutTypes(); },
      error: () => this.showToast('Failed to toggle country', 'danger')
    });
  }

  togglePaymentCountry(group: CountryGroup): void {
    const active = !this.isGroupActive(group);
    this.configService.togglePaymentCountry(group.countryCode, active).subscribe({
      next: () => { this.showToast(`${group.countryName} payments ${active ? 'enabled' : 'disabled'}`, 'success'); this.loadPaymentMethods(); },
      error: () => this.showToast('Failed to toggle country', 'danger')
    });
  }

  // --- Helpers ---

  isGroupActive(group: CountryGroup): boolean {
    return group.items.some(item => item.isActive);
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
    // Sort: active countries first, then alphabetical
    return Object.values(groups).sort((a, b) => {
      const aActive = a.items.some(i => i.isActive) ? 0 : 1;
      const bActive = b.items.some(i => i.isActive) ? 0 : 1;
      if (aActive !== bActive) return aActive - bActive;
      return a.countryName.localeCompare(b.countryName);
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
