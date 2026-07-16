import { Component, Input, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { FxService } from '../../../core/services/fx.service';

/**
 * Reusable editor for per-payment-type exchange rates (rate slabs).
 * Pick a corridor + payment type (Bank / Cash / Mobile / … or "All payment types"),
 * then set either a single all-amounts band (a flat rate like 1 GBP = 103 INR) or
 * several amount bands. Used identically in admin and superadmin panels.
 */
@Component({
  selector: 'app-rate-slabs-editor',
  templateUrl: './rate-slabs-editor.component.html',
  styleUrls: ['./rate-slabs-editor.component.scss']
})
export class RateSlabsEditorComponent implements OnInit {
  /** Optional preloaded corridors; if not provided the component loads them itself. */
  @Input() corridors: any[] | null = null;

  slabCorridorId: number | null = null;
  deliveryMethod = '';               // '' = corridor-wide (all payment types)
  rateSlabs: any[] = [];
  loadingCorridors = false;
  savingSlabs = false;

  readonly paymentTypes: { value: string; label: string }[] = [
    { value: '', label: 'All payment types' },
    { value: 'BANK_DEPOSIT', label: 'Bank Deposit' },
    { value: 'CASH_PICKUP', label: 'Cash Pickup' },
    { value: 'MOBILE_WALLET', label: 'Mobile Wallet' },
    { value: 'HOME_DELIVERY', label: 'Home Delivery' },
    { value: 'UPI', label: 'UPI' },
    { value: 'AIRTIME_TOPUP', label: 'Airtime Top-up' }
  ];

  constructor(private fxService: FxService, private toastCtrl: ToastController) {}

  ngOnInit(): void {
    if (this.corridors && this.corridors.length) return;
    this.loadingCorridors = true;
    this.fxService.getCorridors().subscribe({
      next: (c: any[]) => { this.corridors = c || []; this.loadingCorridors = false; },
      error: () => { this.corridors = []; this.loadingCorridors = false; }
    });
  }

  private slabCorridor(): any { return (this.corridors || []).find(c => c.id === this.slabCorridorId); }
  slabSendCurrency(): string { return this.slabCorridor()?.sendCurrency || ''; }
  slabReceiveCurrency(): string { return this.slabCorridor()?.receiveCurrency || ''; }

  /** Reload when either the corridor or the payment type changes. */
  onScopeChange(): void { this.loadRateSlabs(); }

  loadRateSlabs(): void {
    this.rateSlabs = [];
    if (!this.slabCorridorId) return;
    this.fxService.getRateSlabs(this.slabCorridorId, this.deliveryMethod || undefined).subscribe({
      next: (slabs) => {
        this.rateSlabs = (slabs || []).map((s: any) => ({
          minAmount: s.minAmount, maxAmount: s.maxAmount, rate: s.rate,
          isActive: s.isActive !== false
        }));
      },
      error: () => { this.rateSlabs = []; }
    });
  }

  addSlabRow(): void {
    const last = this.rateSlabs[this.rateSlabs.length - 1];
    const nextMin = last && last.maxAmount != null ? Number(last.maxAmount) + 1 : (this.rateSlabs.length === 0 ? 0 : null);
    this.rateSlabs.push({ minAmount: nextMin, maxAmount: null, rate: null, isActive: true });
  }

  removeSlabRow(i: number): void { this.rateSlabs.splice(i, 1); }

  saveRateSlabs(): void {
    if (!this.slabCorridorId) { this.showToast('Select a corridor first.', 'danger'); return; }
    for (const s of this.rateSlabs) {
      if (s.minAmount == null || s.rate == null || Number(s.rate) <= 0) {
        this.showToast('Each band needs a Min amount and a positive Rate.', 'danger');
        return;
      }
    }
    this.savingSlabs = true;
    const payload = this.rateSlabs.map(s => ({
      minAmount: Number(s.minAmount),
      maxAmount: s.maxAmount === null || s.maxAmount === '' || s.maxAmount === undefined ? null : Number(s.maxAmount),
      rate: Number(s.rate),
      isActive: s.isActive !== false
    }));
    this.fxService.saveRateSlabs(this.slabCorridorId, payload, this.deliveryMethod || undefined).subscribe({
      next: () => { this.savingSlabs = false; this.showToast('Rate saved', 'success'); this.loadRateSlabs(); },
      error: (e: any) => { this.savingSlabs = false; this.showToast(e?.error?.message || 'Failed to save rate', 'danger'); }
    });
  }

  currentPtLabel(): string {
    return this.paymentTypes.find(p => p.value === this.deliveryMethod)?.label || 'All payment types';
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 3500, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
