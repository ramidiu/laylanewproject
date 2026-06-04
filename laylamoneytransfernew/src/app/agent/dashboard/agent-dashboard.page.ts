import { Component, OnInit } from '@angular/core';
import { TransactionService } from '../../core/services/transaction.service';
import { TransactionResponse } from '../../core/models/transaction.model';

@Component({
  selector: 'app-agent-dashboard',
  template: `
    <div class="agent-dash">
      <h1 class="page-title">Agent Dashboard</h1>
      <p class="page-subtitle">Your daily performance overview</p>

      <!-- KPIs -->
      <div class="kpi-grid">
        <app-kpi-card
          value="$5,000.00"
          label="Float Balance"
          icon="wallet"
          variant="sky"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ todayCount }}"
          label="Today's Transactions"
          icon="swap-horizontal"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ todayVolume }}"
          label="Today's Volume"
          icon="cash"
          variant="success"
        ></app-kpi-card>
        <app-kpi-card
          value="{{ commissionToday }}"
          label="Commission Today"
          icon="trending-up"
          variant="warning"
        ></app-kpi-card>
      </div>

      <!-- Recent transactions -->
      <div class="fb-card table-card">
        <h3 class="section-title">Recent Transactions</h3>
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
                <th>Status</th>
                <th>Time</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of recentTransactions">
                <td class="fb-currency">{{ txn.referenceNumber }}</td>
                <td>{{ txn.beneficiaryName }}</td>
                <td class="fb-currency">{{ txn.sendAmount | number:'1.2-2' }} {{ txn.sendCurrency }}</td>
                <td><app-status-chip [status]="txn.status"></app-status-chip></td>
                <td>{{ txn.createdAt | date:'HH:mm' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Quick Action -->
      <div class="quick-send-cta">
        <button class="fb-btn fb-btn--primary" routerLink="/agent/send">
          <ion-icon name="send"></ion-icon>
          New Transaction
        </button>
      </div>
    </div>
  `,
  styleUrls: ['./agent-dashboard.page.scss']
})
export class AgentDashboardPage implements OnInit {
  recentTransactions: TransactionResponse[] = [];
  loading = true;
  todayCount = '0';
  todayVolume = '$0.00';
  commissionToday = '$0.00';

  constructor(private transactionService: TransactionService) {}

  ngOnInit(): void {
    this.transactionService.getRecent(10).subscribe({
      next: (txns) => {
        this.recentTransactions = txns;
        this.todayCount = txns.length.toString();
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }
}
