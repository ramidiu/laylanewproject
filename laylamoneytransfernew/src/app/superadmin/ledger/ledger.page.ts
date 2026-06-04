import { Component, OnInit } from '@angular/core';
import { AlertController, ToastController } from '@ionic/angular';
import { SettlementService } from '../../core/services/settlement.service';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-ledger',
  templateUrl: './ledger.page.html',
  styleUrls: ['./ledger.page.scss']
})
export class LedgerPage implements OnInit {
  activeSegment: 'platform' | 'balances' = 'platform';

  platformEntries: any[] = [];
  platformLoading = true;

  partnerBalances: any[] = [];
  balancesLoading = true;

  // Settlement features
  payinBalances: any[] = [];
  payinBalancesLoading = true;
  pendingSettlements: any[] = [];
  pendingSettlementsLoading = true;

  // Pay partner form
  showPayForm = false;
  payFormPartner: any = null;
  payFormAmount: number | null = null;
  payFormReference = '';
  payFormSubmitting = false;

  // Receive payment form
  showReceiveForm = false;
  receiveFormPartner: any = null;
  receiveFormAmount: number | null = null;
  receiveFormReference = '';
  receiveFormSubmitting = false;

  constructor(
    private settlementService: SettlementService,
    private partnerService: PartnerService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadPlatformLedger();
    this.loadPartnerBalances();
    this.loadPayinBalances();
    this.loadPendingSettlements();
  }

  loadPlatformLedger(): void {
    this.platformLoading = true;
    this.settlementService.getPlatformLedger().subscribe({
      next: (res) => {
        const data = res?.data || res;
        this.platformEntries = data?.entries || (Array.isArray(data) ? data : []);
        this.platformLoading = false;
      },
      error: () => { this.platformEntries = []; this.platformLoading = false; }
    });
  }

  loadPartnerBalances(): void {
    this.balancesLoading = true;
    this.settlementService.getPartnerBalances().subscribe({
      next: (res) => {
        this.partnerBalances = Array.isArray(res) ? res : res?.data || [];
        this.balancesLoading = false;
      },
      error: () => { this.partnerBalances = []; this.balancesLoading = false; }
    });
  }

  loadPayinBalances(): void {
    this.payinBalancesLoading = true;
    this.partnerService.getPayinBalances().subscribe({
      next: (res) => {
        this.payinBalances = Array.isArray(res) ? res : res?.data || [];
        this.payinBalancesLoading = false;
      },
      error: () => { this.payinBalances = []; this.payinBalancesLoading = false; }
    });
  }

  loadPendingSettlements(): void {
    this.pendingSettlementsLoading = true;
    this.settlementService.getPendingSettlements().subscribe({
      next: (res) => {
        this.pendingSettlements = Array.isArray(res) ? res : res?.data || [];
        this.pendingSettlementsLoading = false;
      },
      error: () => { this.pendingSettlements = []; this.pendingSettlementsLoading = false; }
    });
  }

  getEntryTypeColor(type: string): string {
    if (type === 'CREDIT' || type === 'INFLOW') return 'success';
    if (type === 'DEBIT' || type === 'OUTFLOW') return 'danger';
    return 'medium';
  }

  getTotalCredits(): number {
    return this.platformEntries
      .filter(e => e.entryType === 'CREDIT')
      .reduce((sum, e) => sum + (e.usdAmount || 0), 0);
  }

  getTotalDebits(): number {
    return this.platformEntries
      .filter(e => e.entryType === 'DEBIT')
      .reduce((sum, e) => sum + (e.usdAmount || 0), 0);
  }

  getNetBalance(): number {
    return this.getTotalCredits() - this.getTotalDebits();
  }

  // --- Outstanding Payables ---
  getTotalOutstandingPayables(): number {
    return this.partnerBalances.reduce((sum, p) => sum + (p.balance || 0), 0);
  }

  // --- Outstanding Receivables ---
  getTotalOutstandingReceivables(): number {
    return this.payinBalances.reduce((sum, p) => sum + (p.balance || 0), 0);
  }

  // --- Pay / Prefund Form ---
  openPayForm(partner: any): void {
    this.payFormPartner = partner;
    this.payFormAmount = null;
    this.payFormReference = '';
    this.showPayForm = true;
    this.showReceiveForm = false;
  }

  closePayForm(): void {
    this.showPayForm = false;
    this.payFormPartner = null;
  }

  setPayFullAmount(): void {
    if (this.payFormPartner) {
      this.payFormAmount = Math.abs(this.payFormPartner.balance || 0);
    }
  }

  setPayHalfAmount(): void {
    if (this.payFormPartner) {
      this.payFormAmount = Math.round(Math.abs(this.payFormPartner.balance || 0) * 50) / 100;
    }
  }

  getPayOutstandingAfter(): number {
    if (!this.payFormPartner) return 0;
    return (this.payFormPartner.balance || 0) - (this.payFormAmount || 0);
  }

  submitPayForm(): void {
    if (!this.payFormAmount || !this.payFormReference || !this.payFormPartner) return;
    this.payFormSubmitting = true;
    this.settlementService.initiateAdminToPayout({
      partnerId: this.payFormPartner.partnerId,
      amount: this.payFormAmount,
      reference: this.payFormReference
    }).subscribe({
      next: () => {
        this.showToast('Settlement initiated successfully', 'success');
        this.payFormSubmitting = false;
        this.closePayForm();
        this.loadPartnerBalances();
        this.loadPendingSettlements();
      },
      error: () => {
        this.showToast('Failed to initiate settlement', 'danger');
        this.payFormSubmitting = false;
      }
    });
  }

  // --- Receive Payment Form ---
  openReceiveForm(partner: any): void {
    this.receiveFormPartner = partner;
    this.receiveFormAmount = null;
    this.receiveFormReference = '';
    this.showReceiveForm = true;
    this.showPayForm = false;
  }

  closeReceiveForm(): void {
    this.showReceiveForm = false;
    this.receiveFormPartner = null;
  }

  setReceiveFullAmount(): void {
    if (this.receiveFormPartner) {
      this.receiveFormAmount = Math.abs(this.receiveFormPartner.balance || 0);
    }
  }

  setReceiveHalfAmount(): void {
    if (this.receiveFormPartner) {
      this.receiveFormAmount = Math.round(Math.abs(this.receiveFormPartner.balance || 0) * 50) / 100;
    }
  }

  getReceiveOutstandingAfter(): number {
    if (!this.receiveFormPartner) return 0;
    return (this.receiveFormPartner.balance || 0) - (this.receiveFormAmount || 0);
  }

  submitReceiveForm(): void {
    if (!this.receiveFormAmount || !this.receiveFormReference || !this.receiveFormPartner) return;
    this.receiveFormSubmitting = true;
    this.settlementService.initiatePayinToAdmin({
      partnerId: this.receiveFormPartner.partnerId,
      amount: this.receiveFormAmount,
      reference: this.receiveFormReference
    }).subscribe({
      next: () => {
        this.showToast('Payment received successfully', 'success');
        this.receiveFormSubmitting = false;
        this.closeReceiveForm();
        this.loadPayinBalances();
        this.loadPendingSettlements();
      },
      error: () => {
        this.showToast('Failed to record payment', 'danger');
        this.receiveFormSubmitting = false;
      }
    });
  }

  // --- Pending Settlements Actions ---
  approveSettlement(id: number): void {
    this.settlementService.approveSettlement(id).subscribe({
      next: () => {
        this.showToast('Settlement approved', 'success');
        this.loadPendingSettlements();
        this.loadPartnerBalances();
        this.loadPayinBalances();
      },
      error: () => this.showToast('Failed to approve settlement', 'danger')
    });
  }

  async rejectSettlement(id: number): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Reject Settlement',
      message: 'Please provide a reason for rejection:',
      inputs: [{ name: 'reason', type: 'text', placeholder: 'Reason for rejection' }],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Reject',
          handler: (data) => {
            if (!data.reason) return false;
            this.settlementService.rejectSettlement(id, data.reason).subscribe({
              next: () => {
                this.showToast('Settlement rejected', 'warning');
                this.loadPendingSettlements();
              },
              error: () => this.showToast('Failed to reject settlement', 'danger')
            });
            return true;
          }
        }
      ]
    });
    await alert.present();
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
