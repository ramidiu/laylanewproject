import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { SettlementService } from '../../core/services/settlement.service';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-settlement-rates',
  templateUrl: './settlement-rates.page.html',
  styleUrls: ['./settlement-rates.page.scss']
})
export class SettlementRatesPage implements OnInit {
  activeSegment: 'global' | 'partner' = 'global';

  globalRates: any[] = [];
  globalLoading = true;
  editingRate: string | null = null;
  editRateValue = 0;

  partners: any[] = [];
  partnerRates: any[] = [];
  partnerLoading = true;
  expandedPartner: number | null = null;
  partnerRateList: any[] = [];

  newRate = { currency: '', rateToUsd: 0 };
  showAddRate = false;

  constructor(
    private settlementService: SettlementService,
    private partnerService: PartnerService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadGlobalRates();
    this.loadPartners();
    this.loadPartnerRates();
  }

  loadGlobalRates(): void {
    this.globalLoading = true;
    this.settlementService.getGlobalRates().subscribe({
      next: (res) => {
        this.globalRates = Array.isArray(res) ? res : res?.data || [];
        this.globalLoading = false;
      },
      error: () => { this.globalRates = []; this.globalLoading = false; }
    });
  }

  loadPartners(): void {
    this.partnerService.getPayoutPartners().subscribe({
      next: (res) => this.partners = Array.isArray(res) ? res : res?.data || [],
      error: () => {}
    });
  }

  loadPartnerRates(): void {
    this.partnerLoading = true;
    this.settlementService.getPartnerRates().subscribe({
      next: (res) => {
        this.partnerRates = Array.isArray(res) ? res : res?.data || [];
        this.partnerLoading = false;
      },
      error: () => { this.partnerRates = []; this.partnerLoading = false; }
    });
  }

  startEditRate(rate: any): void {
    this.editingRate = rate.currency;
    this.editRateValue = rate.rateToUsd;
  }

  saveRate(rate: any): void {
    this.settlementService.updateGlobalRate(rate.currency, { rateToUsd: this.editRateValue }).subscribe({
      next: () => {
        this.editingRate = null;
        this.showToast('Rate updated', 'success');
        this.loadGlobalRates();
      },
      error: () => this.showToast('Failed to update rate', 'danger')
    });
  }

  addGlobalRate(): void {
    if (!this.newRate.currency || !this.newRate.rateToUsd) return;
    this.settlementService.addGlobalRate(this.newRate).subscribe({
      next: () => {
        this.showToast('Rate added', 'success');
        this.showAddRate = false;
        this.newRate = { currency: '', rateToUsd: 0 };
        this.loadGlobalRates();
      },
      error: () => this.showToast('Failed to add rate', 'danger')
    });
  }

  cancelEdit(): void { this.editingRate = null; }

  viewPartnerRates(partner: any): void {
    if (this.expandedPartner === partner.id) { this.expandedPartner = null; return; }
    this.expandedPartner = partner.id;
    this.settlementService.getPartnerRate(partner.id).subscribe({
      next: (res) => this.partnerRateList = Array.isArray(res) ? res : res?.data || [],
      error: () => this.partnerRateList = []
    });
  }

  getPartnerName(partnerId: number): string {
    const p = this.partners.find(x => x.id === partnerId);
    return p ? p.name : `Partner #${partnerId}`;
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
