import { Component, OnInit } from '@angular/core';

interface Commission {
  id: string;
  transactionRef: string;
  amount: number;
  currency: string;
  rate: number;
  date: string;
  status: string;
}

@Component({
  selector: 'app-agent-commissions',
  template: `
    <div class="agent-commissions">
      <h1 class="page-title">Commissions</h1>
      <p class="page-subtitle">Track your earnings</p>

      <!-- Summary KPIs -->
      <div class="kpi-grid">
        <app-kpi-card
          value="$1,250.00"
          label="Total Earnings (This Month)"
          icon="cash"
          variant="success"
        ></app-kpi-card>
        <app-kpi-card
          value="$450.00"
          label="Pending Payout"
          icon="time"
          variant="warning"
        ></app-kpi-card>
        <app-kpi-card
          value="156"
          label="Transactions (This Month)"
          icon="receipt"
          variant="sky"
        ></app-kpi-card>
      </div>

      <!-- Date range filter -->
      <div class="filters-bar">
        <select class="fb-input filter-select" [(ngModel)]="periodFilter" (change)="loadCommissions()">
          <option value="today">Today</option>
          <option value="week">This Week</option>
          <option value="month">This Month</option>
          <option value="quarter">This Quarter</option>
        </select>
      </div>

      <!-- Commissions table -->
      <div class="fb-card table-card">
        <div class="table-wrapper">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Transaction Ref</th>
                <th>Commission Amount</th>
                <th>Commission Rate</th>
                <th>Date</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let comm of commissions">
                <td class="fb-currency">{{ comm.transactionRef }}</td>
                <td class="fb-currency commission-amount">{{ comm.amount | number:'1.2-2' }} {{ comm.currency }}</td>
                <td>{{ comm.rate }}%</td>
                <td>{{ comm.date | date:'MMM d, y' }}</td>
                <td><app-status-chip [status]="comm.status"></app-status-chip></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div *ngIf="commissions.length === 0" class="empty-state">
          <ion-icon name="cash-outline"></ion-icon>
          <p>No commissions for this period</p>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./agent-commissions.page.scss']
})
export class AgentCommissionsPage implements OnInit {
  commissions: Commission[] = [];
  periodFilter = 'month';

  ngOnInit(): void {
    this.loadCommissions();
  }

  loadCommissions(): void {
    // Placeholder data - would be loaded from an agent commission API endpoint
    this.commissions = [
      { id: '1', transactionRef: 'FB-2026-001234', amount: 15.00, currency: 'USD', rate: 1.5, date: '2026-03-29T10:00:00', status: 'PAID' },
      { id: '2', transactionRef: 'FB-2026-001235', amount: 22.50, currency: 'USD', rate: 1.5, date: '2026-03-29T11:30:00', status: 'PAID' },
      { id: '3', transactionRef: 'FB-2026-001236', amount: 8.75, currency: 'USD', rate: 1.5, date: '2026-03-28T14:20:00', status: 'PENDING' },
      { id: '4', transactionRef: 'FB-2026-001237', amount: 30.00, currency: 'USD', rate: 1.5, date: '2026-03-28T09:15:00', status: 'PAID' },
      { id: '5', transactionRef: 'FB-2026-001238', amount: 12.00, currency: 'USD', rate: 1.5, date: '2026-03-27T16:45:00', status: 'PAID' }
    ];
  }
}
