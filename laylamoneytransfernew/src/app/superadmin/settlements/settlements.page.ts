import { Component, OnInit } from '@angular/core';
import { ToastController, AlertController } from '@ionic/angular';
import { SettlementService } from '../../core/services/settlement.service';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-settlements',
  templateUrl: './settlements.page.html',
  styleUrls: ['./settlements.page.scss']
})
export class SettlementsPage implements OnInit {
  settlements: any[] = [];
  partners: any[] = [];
  loading = true;
  showInitiateForm = false;

  payoutPartners: any[] = [];
  payinPartners: any[] = [];

  newSettlement = {
    partnerId: null as number | null,
    amount: 0,
    currency: 'USD',
    notes: '',
    direction: 'ADMIN_TO_PAYOUT'
  };

  constructor(
    private settlementService: SettlementService,
    private partnerService: PartnerService,
    private toastCtrl: ToastController,
    private alertCtrl: AlertController
  ) {}

  ngOnInit(): void {
    this.loadSettlements();
    this.loadPartners();
  }

  loadSettlements(): void {
    this.loading = true;
    this.settlementService.getAllSettlements().subscribe({
      next: (res) => {
        this.settlements = Array.isArray(res) ? res : res?.data || [];
        this.loading = false;
      },
      error: () => { this.settlements = []; this.loading = false; }
    });
  }

  loadPartners(): void {
    // Load BOTH partner lists so the dropdown can swap per direction without refetching.
    // Response shape: ApiResponse<List<Partner>> → read res.data (with safe fallbacks).
    this.partnerService.getPayoutPartners().subscribe({
      next: (res: any) => {
        this.payoutPartners = Array.isArray(res) ? res : (res?.data || []);
        this.syncPartnersForDirection();
      },
      error: () => { this.payoutPartners = []; this.syncPartnersForDirection(); }
    });
    this.partnerService.getPayinPartners().subscribe({
      next: (res: any) => {
        this.payinPartners = Array.isArray(res) ? res : (res?.data || []);
        this.syncPartnersForDirection();
      },
      error: () => { this.payinPartners = []; this.syncPartnersForDirection(); }
    });
  }

  // Called on direction change from the template — rebinds the Partner dropdown source.
  onDirectionChange(): void {
    this.newSettlement.partnerId = null;
    this.syncPartnersForDirection();
  }

  private syncPartnersForDirection(): void {
    this.partners = this.newSettlement.direction === 'PAYIN_TO_ADMIN'
        ? this.payinPartners
        : this.payoutPartners;
  }

  getStatusColor(status: string): string {
    const map: Record<string, string> = {
      PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', COMPLETED: 'success', CANCELLED: 'medium'
    };
    return map[status] || 'medium';
  }

  approveSettlement(settlement: any): void {
    this.settlementService.approveSettlement(settlement.id).subscribe({
      next: () => {
        this.showToast('Settlement approved', 'success');
        this.loadSettlements();
      },
      error: () => this.showToast('Failed to approve settlement', 'danger')
    });
  }

  async rejectSettlement(settlement: any): Promise<void> {
    const dialog = await this.alertCtrl.create({
      header: 'Reject Settlement',
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Rejection reason' }],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Reject',
          handler: (data) => {
            this.settlementService.rejectSettlement(settlement.id, data.reason).subscribe({
              next: () => { this.showToast('Settlement rejected', 'success'); this.loadSettlements(); },
              error: () => this.showToast('Failed to reject settlement', 'danger')
            });
          }
        }
      ]
    });
    await dialog.present();
  }

  initiateSettlement(): void {
    if (!this.newSettlement.partnerId || !this.newSettlement.amount) return;
    const service$ = this.newSettlement.direction === 'ADMIN_TO_PAYOUT'
      ? this.settlementService.initiateAdminToPayout(this.newSettlement)
      : this.settlementService.initiatePayinToAdmin(this.newSettlement);

    service$.subscribe({
      next: () => {
        this.showToast('Settlement initiated', 'success');
        this.showInitiateForm = false;
        this.newSettlement = { partnerId: null, amount: 0, currency: 'USD', notes: '', direction: 'ADMIN_TO_PAYOUT' };
        this.loadSettlements();
      },
      error: () => this.showToast('Failed to initiate settlement', 'danger')
    });
  }

  getPartnerName(partnerId: number): string {
    // Backend field is `partnerName`; keep `name` as legacy fallback. Search both lists
    // because settlements may reference payin OR payout partners.
    const p = [...this.payoutPartners, ...this.payinPartners].find(x => x.id === partnerId);
    return p ? (p.partnerName || p.name) : `Partner #${partnerId}`;
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
