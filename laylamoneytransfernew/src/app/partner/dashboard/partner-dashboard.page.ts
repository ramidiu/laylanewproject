import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';
import { SettlementService } from '../../core/services/settlement.service';

@Component({
  selector: 'app-partner-dashboard',
  template: `
    <div class="partner-dash">
      <h1 class="page-title">Payout Dashboard</h1>
      <p class="page-subtitle">Manage assigned payouts and settlements</p>

      <!-- KPIs -->
      <div class="kpi-grid">
        <app-kpi-card
          value="{{ awaitingCount }}"
          label="Awaiting Payout"
          icon="hourglass-outline"
          variant="warning"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ completedCount }}"
          label="Completed"
          icon="checkmark-circle-outline"
          variant="success"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ pendingSettlements }}"
          label="Pending Settlements"
          icon="wallet-outline"
          variant="sky"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ ledgerBalance }}"
          label="Ledger Balance"
          icon="cash-outline"
        ></app-kpi-card>
      </div>

      <!-- Quick Actions -->
      <div class="quick-actions">
        <ion-button fill="solid" style="--background:#1B3571;--color:#FFFFFF;--background-hover:#122550;--background-activated:#122550;" (click)="goTo('/partner/transactions')">
          <ion-icon name="time-outline" slot="start"></ion-icon>
          Pending Payouts
        </ion-button>
        <ion-button fill="outline" style="--border-color:#1B3571;--color:#1B3571;" (click)="goTo('/partner/completed')">
          <ion-icon name="checkmark-circle-outline" slot="start"></ion-icon>
          View Completed
        </ion-button>
        <ion-button fill="outline" style="--border-color:#1B3571;--color:#1B3571;" (click)="goTo('/partner/ledger')">
          <ion-icon name="book-outline" slot="start"></ion-icon>
          View Ledger
        </ion-button>
      </div>

      <!-- Recent Transactions -->
      <div class="fb-card table-card">
        <h3 class="section-title">Recent Pending Payouts</h3>
        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>
        <div class="table-wrapper" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Reference</th>
                <th>Beneficiary</th>
                <th>Amount</th>
                <th>Delivery</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of recentTransactions">
                <td class="fb-currency">{{ txn.referenceNumber }}</td>
                <td>{{ txn.beneficiaryName }}</td>
                <td class="fb-currency">{{ txn.receiveAmount | number:'1.2-2' }} {{ txn.receiveCurrency }}</td>
                <td>{{ txn.deliveryMethod }}</td>
                <td><app-status-chip [status]="txn.status"></app-status-chip></td>
                <td>
                  <button
                    *ngIf="txn.status === 'SENT_TO_PAYOUT' || txn.status === 'PROCESSING' || txn.status === 'FUNDS_RECEIVED'"
                    class="pill-btn pill-btn--success"
                    (click)="markAsPaid(txn)"
                  >
                    <ion-icon name="checkmark-circle-outline"></ion-icon>
                    Mark Paid
                  </button>
                </td>
              </tr>
              <tr *ngIf="recentTransactions.length === 0">
                <td colspan="6" class="empty-state">No pending payouts</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./partner-dashboard.page.scss']
})
export class PartnerDashboardPage implements OnInit {
  recentTransactions: any[] = [];
  loading = true;
  awaitingCount = 0;
  completedCount = 0;
  pendingSettlements = 0;
  ledgerBalance = '0.00';

  constructor(
    private partnerService: PartnerService,
    private settlementService: SettlementService,
    private router: Router,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;

    this.partnerService.getMyTransactions().subscribe({
      next: (res: any) => {
        // Backend returns ApiResponse<{success, data}> — prior code read res.content which never existed.
        const data: any[] = Array.isArray(res) ? res : (res?.data || res?.content || []);
        this.recentTransactions = data.slice(0, 5);
        this.awaitingCount = data.length;
        this.loading = false;
      },
      error: () => {
        this.recentTransactions = [];
        this.loading = false;
      }
    });

    this.partnerService.getMyCompleted().subscribe({
      next: (res: any) => {
        const data: any[] = Array.isArray(res) ? res : (res?.data || res?.content || []);
        this.completedCount = data.length;
      },
      error: () => {}
    });

    this.partnerService.getMyLedger().subscribe({
      next: (res: any) => {
        const entries = (() => { const d = res?.data || res; return Array.isArray(d) ? d : (d?.content || []); })();
        if (entries.length > 0) {
          this.ledgerBalance = entries[entries.length - 1]?.balance?.toFixed(2) || '0.00';
        }
      },
      error: () => {}
    });

    this.settlementService.getMySettlements().subscribe({
      next: (res: any) => {
        const data = (() => { const d = res?.data || res; return Array.isArray(d) ? d : (d?.content || []); })();
        this.pendingSettlements = data.filter((s: any) => s.status === 'PENDING' || s.status === 'SUBMITTED').length;
      },
      error: () => {}
    });
  }

  markAsPaid(txn: any): void {
    this.partnerService.markPaid(txn.id).subscribe({
      next: () => {
        this.showToast('Transaction marked as paid', 'success');
        this.loadData();
      },
      error: () => this.showToast('Failed to update status', 'danger')
    });
  }

  goTo(route: string): void {
    this.router.navigate([route]);
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
