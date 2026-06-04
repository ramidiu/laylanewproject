import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PartnerService } from '../../core/services/partner.service';
import { SettlementService } from '../../core/services/settlement.service';

@Component({
  selector: 'app-payin-partner-dashboard',
  template: `
    <div class="payin-dash">
      <h1 class="page-title">Pay-In Dashboard</h1>
      <p class="page-subtitle">Overview of collections, commissions, and settlements</p>

      <!-- KPIs -->
      <div class="kpi-grid">
        <app-kpi-card
          value="{{ totalCollected }}"
          label="Total Collected"
          icon="cash-outline"
          variant="sky"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ commissionEarned }}"
          label="Commission Earned"
          icon="trending-up-outline"
          variant="success"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ balanceOwed }}"
          label="Balance Owed"
          icon="wallet-outline"
          variant="warning"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ pendingSettlements }}"
          label="Pending Settlements"
          icon="hourglass-outline"
        ></app-kpi-card>
      </div>

      <!-- Quick Actions -->
      <div class="quick-actions">
        <ion-button fill="solid" style="--background:#1B3571;--color:#FFFFFF;--background-hover:#122550;--background-activated:#122550;" (click)="goTo('/payin-partner/transactions')">
          <ion-icon name="swap-horizontal-outline" slot="start"></ion-icon>
          View Transactions
        </ion-button>
        <ion-button fill="outline" style="--border-color:#1B3571;--color:#1B3571;" (click)="goTo('/payin-partner/settlements')">
          <ion-icon name="wallet-outline" slot="start"></ion-icon>
          Submit Settlement
        </ion-button>
        <ion-button fill="outline" style="--border-color:#1B3571;--color:#1B3571;" (click)="goTo('/payin-partner/ledger')">
          <ion-icon name="book-outline" slot="start"></ion-icon>
          View Ledger
        </ion-button>
        <ion-button fill="solid" style="--background:#00B894;--color:#FFFFFF;--background-hover:#00a381;--background-activated:#00a381;" (click)="goTo('/payin-partner/create-customer')">
          <ion-icon name="person-add-outline" slot="start"></ion-icon>
          Create Customer
        </ion-button>
      </div>

      <!-- Recent Transactions -->
      <div class="fb-card table-card">
        <h3 class="section-title">Recent Transactions</h3>
        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>
        <div class="table-wrapper" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Transaction ID</th>
                <th>Customer</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of recentTransactions">
                <td style="font-family:monospace;font-size:.8rem">{{ txn.referenceNumber || (txn.transactionId | slice:0:8) + '…' }}</td>
                <td style="font-family:monospace;font-size:.8rem">{{ txn.customerId | slice:0:8 }}…</td>
                <td class="fb-currency">{{ txn.amount | number:'1.2-2' }} {{ txn.currency }}</td>
                <td><app-status-chip [status]="txn.status"></app-status-chip></td>
                <td>{{ txn.createdAt | date:'MMM d, y HH:mm' }}</td>
              </tr>
              <tr *ngIf="recentTransactions.length === 0">
                <td colspan="5" class="empty-state">No recent transactions</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./payin-partner-dashboard.page.scss']
})
export class PayinPartnerDashboardPage implements OnInit {
  recentTransactions: any[] = [];
  loading = true;
  totalCollected = '0.00';
  commissionEarned = '0.00';
  balanceOwed = '0.00';
  pendingSettlements = 0;

  constructor(
    private partnerService: PartnerService,
    private settlementService: SettlementService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;

    this.partnerService.getPayinTransactionList().subscribe({
      next: (res: any) => {
        const data: any[] = Array.isArray(res) ? res : (res?.data || res?.content || []);
        this.recentTransactions = data.slice(0, 5);
        const total = data.reduce((sum: number, t: any) => sum + (Number(t.amount) || 0), 0);
        this.totalCollected = total.toFixed(2);
        this.loading = false;
      },
      error: () => {
        this.recentTransactions = [];
        this.loading = false;
      }
    });

    // Code added by Naresh: use partner-scoped /my-ledger (returns {entries,balance}) instead
    // of the admin-only /payin-balances which fails for PAYIN_PARTNER role with 403.
    this.partnerService.getPayinLedger().subscribe({
      next: (res: any) => {
        const data = res?.data || res || {};
        this.commissionEarned = Number(data.totalCommission || 0).toFixed(2);
        this.balanceOwed = Number(data.balance || data.balanceOwed || 0).toFixed(2);
      },
      error: () => {}
    });

    this.settlementService.getMySettlements().subscribe({
      next: (res: any) => {
        const data: any[] = Array.isArray(res) ? res : (res?.data || res?.content || []);
        this.pendingSettlements = data.filter((s: any) => s.status === 'PENDING' || s.status === 'SUBMITTED').length;
      },
      error: () => {}
    });
  }

  goTo(route: string): void {
    this.router.navigate([route]);
  }
}
