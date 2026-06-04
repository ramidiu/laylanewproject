import { Component, OnInit } from '@angular/core';
import { ToastController, AlertController } from '@ionic/angular';
import { SettlementService } from '../../core/services/settlement.service';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-admin-settlements',
  templateUrl: './admin-settlements.page.html',
  styleUrls: ['./admin-settlements.page.scss']
})
export class AdminSettlementsPage implements OnInit {
  settlements: any[] = [];
  partners: any[] = [];
  loading = true;
  showInitiateForm = false;

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
    this.partnerService.getPayoutPartners().subscribe({
      next: (res) => this.partners = Array.isArray(res) ? res : res?.data || [],
      error: () => {}
    });
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
    const p = this.partners.find(x => x.id === partnerId);
    return p ? p.name : `Partner #${partnerId}`;
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
