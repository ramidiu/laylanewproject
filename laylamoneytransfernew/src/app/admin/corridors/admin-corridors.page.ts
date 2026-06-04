import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { FxService } from '../../core/services/fx.service';
import { ReferralService } from '../../core/services/referral.service';
import { CorridorResponse } from '../../core/models/fx.model';

@Component({
  selector: 'app-admin-corridors',
  templateUrl: './admin-corridors.page.html',
  styleUrls: ['./admin-corridors.page.scss']
})
export class AdminCorridorsPage implements OnInit {
  corridors: CorridorResponse[] = [];
  loading = true;
  editingFees: any = null;
  editBaseFee = 0;
  editFeePercent = 0;
  // map of corridorId -> active BANK_DEPOSIT fee record
  corridorFeesMap: Map<number, any> = new Map();

  // Referral config
  editingReferral: any = null;
  editRateBoost = 0;
  editReferralCredit = 0;
  // map of corridorId -> referral config record (null = using global default)
  referralConfigMap: Map<number, any> = new Map();
  globalReferralConfig: any = null;

  constructor(
    private fxService: FxService,
    private referralService: ReferralService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadCorridors();
    this.loadReferralConfigs();
  }

  loadCorridors(): void {
    this.loading = true;
    this.fxService.getCorridors().subscribe({
      next: (corridors) => {
        this.corridors = corridors.filter(c => c.isActive);
        this.loading = false;
        this.loadAllFees(this.corridors);
      },
      error: () => {
        this.corridors = [];
        this.loading = false;
      }
    });
  }

  loadReferralConfigs(): void {
    this.referralService.getAdminConfigs().subscribe({
      next: (configs: any[]) => {
        this.referralConfigMap.clear();
        configs.forEach(cfg => {
          if (cfg.corridorId == null) {
            this.globalReferralConfig = cfg;
          } else {
            this.referralConfigMap.set(cfg.corridorId, cfg);
          }
        });
      },
      error: () => {}
    });
  }

  loadAllFees(corridors: any[]): void {
    corridors.forEach(corridor => {
      this.fxService.getCorridorFeesById(corridor.id).subscribe({
        next: (fees: any[]) => {
          const bankFee = fees.find((f: any) => f.deliveryMethod === 'BANK_DEPOSIT' && f.isActive !== false)
            || fees[0] || null;
          if (bankFee) this.corridorFeesMap.set(corridor.id, bankFee);
        },
        error: () => {}
      });
    });
  }

  getFeeDisplay(corridor: any): string {
    const fee = this.corridorFeesMap.get(corridor.id);
    if (!fee) return '0.00';
    if (fee.feeType === 'FLAT') return (fee.flatFee || 0).toFixed(2);
    if (fee.feeType === 'PERCENTAGE') return '—';
    return '—';
  }

  getFeePctDisplay(corridor: any): string {
    const fee = this.corridorFeesMap.get(corridor.id);
    if (!fee) return '0';
    if (fee.feeType === 'PERCENTAGE') return (fee.percentageFee || 0).toFixed(2);
    if (fee.feeType === 'FLAT') return '0';
    return '0';
  }

  toggleCorridor(corridor: any): void {
    this.fxService.toggleCorridor(String(corridor.id), !corridor.isActive).subscribe({
      next: () => {
        corridor.isActive = !corridor.isActive;
        this.showToast(`Corridor ${corridor.isActive ? 'enabled' : 'disabled'}`, 'success');
      },
      error: () => this.showToast('Failed to update corridor', 'danger')
    });
  }

  startEditFees(corridor: any): void {
    this.editingFees = corridor.id;
    const fee = this.corridorFeesMap.get(corridor.id);
    this.editBaseFee = fee ? (fee.flatFee || 0) : 0;
    this.editFeePercent = fee ? (fee.percentageFee || 0) : 0;
  }

  saveFees(corridor: any): void {
    const existing = this.corridorFeesMap.get(corridor.id);
    const payload = {
      deliveryMethod: 'BANK_DEPOSIT',
      feeType: this.editFeePercent > 0 ? 'PERCENTAGE' : 'FLAT',
      flatFee: this.editFeePercent > 0 ? 0 : this.editBaseFee,
      percentageFee: this.editFeePercent,
      currency: corridor.sendCurrency || 'GBP'
    };

    const save$ = existing
      ? this.fxService.updateCorridorFee(corridor.id, existing.id, payload)
      : this.fxService.createCorridorFee(corridor.id, payload);

    save$.subscribe({
      next: (saved: any) => {
        this.corridorFeesMap.set(corridor.id, saved?.data || saved);
        this.editingFees = null;
        this.showToast('Fees updated successfully', 'success');
      },
      error: () => this.showToast('Failed to update fees', 'danger')
    });
  }

  cancelEdit(): void {
    this.editingFees = null;
  }

  // ── Referral Config ──────────────────────────────────────────────────────

  getReferralConfigId(corridorId: number): number | null {
    return this.referralConfigMap.get(corridorId)?.id ?? null;
  }

  getReferralBoost(corridorId: number): string {
    const cfg = this.referralConfigMap.get(corridorId) ?? this.globalReferralConfig;
    return cfg ? Number(cfg.rateBoostPercentage).toFixed(2) : '0.50';
  }

  getReferralCredit(corridorId: number): string {
    const cfg = this.referralConfigMap.get(corridorId) ?? this.globalReferralConfig;
    return cfg ? Number(cfg.referrerCreditAmount).toFixed(2) : '5.00';
  }

  getReferralCurrency(corridorId: number): string {
    const cfg = this.referralConfigMap.get(corridorId) ?? this.globalReferralConfig;
    return cfg?.creditCurrency ?? 'GBP';
  }

  startEditReferral(corridor: any): void {
    this.editingReferral = corridor.id;
    const cfg = this.referralConfigMap.get(corridor.id);
    this.editRateBoost = cfg ? Number(cfg.rateBoostPercentage) : Number(this.globalReferralConfig?.rateBoostPercentage ?? 0.5);
    this.editReferralCredit = cfg ? Number(cfg.referrerCreditAmount) : Number(this.globalReferralConfig?.referrerCreditAmount ?? 5);
  }

  saveReferral(corridor: any): void {
    const existing = this.referralConfigMap.get(corridor.id);
    const payload = {
      id: existing?.id ?? null,
      corridorId: corridor.id,
      rateBoostPercentage: this.editRateBoost,
      referrerCreditAmount: this.editReferralCredit,
      creditCurrency: corridor.sendCurrency || 'GBP',
      isActive: true
    };

    this.referralService.saveAdminConfig(payload).subscribe({
      next: (saved: any) => {
        this.referralConfigMap.set(corridor.id, saved?.data || saved);
        this.editingReferral = null;
        this.showToast('Referral config saved', 'success');
      },
      error: () => this.showToast('Failed to save referral config', 'danger')
    });
  }

  cancelReferralEdit(): void {
    this.editingReferral = null;
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
