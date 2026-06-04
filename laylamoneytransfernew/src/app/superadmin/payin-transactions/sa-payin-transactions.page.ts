import { Component, OnInit } from '@angular/core';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-sa-payin-transactions',
  template: `
    <div class="sa-pt">
      <div class="sa-pt__header">
        <div>
          <h1 class="sa-pt__title">PayIn Transactions</h1>
          <p class="sa-pt__sub">All transactions created via the PayIn partner API</p>
        </div>
        <ion-button fill="outline" size="small" (click)="load()">
          <ion-icon name="refresh-outline" slot="start"></ion-icon> Refresh
        </ion-button>
      </div>

      <!-- KPI row -->
      <div class="sa-pt__kpis">
        <div class="sa-pt__kpi">
          <div class="sa-pt__kpi-value">{{ transactions.length }}</div>
          <div class="sa-pt__kpi-label">Total</div>
        </div>
        <div class="sa-pt__kpi sa-pt__kpi--blue">
          <div class="sa-pt__kpi-value">{{ countByStatus('PROCESSING') }}</div>
          <div class="sa-pt__kpi-label">Processing</div>
        </div>
        <div class="sa-pt__kpi sa-pt__kpi--green">
          <div class="sa-pt__kpi-value">{{ countByStatus('SUCCESS') }}</div>
          <div class="sa-pt__kpi-label">Success</div>
        </div>
        <div class="sa-pt__kpi sa-pt__kpi--red">
          <div class="sa-pt__kpi-value">{{ countByStatus('FAILED') }}</div>
          <div class="sa-pt__kpi-label">Failed</div>
        </div>
      </div>

      <!-- Filters -->
      <div class="fb-card table-card">
        <div class="table-toolbar">
          <div class="tb-left">
            <ion-badge color="primary">{{ filtered.length }}</ion-badge>
            <span>results</span>
          </div>
          <div class="tb-right">
            <div class="search-box">
              <ion-icon name="search-outline"></ion-icon>
              <input [(ngModel)]="searchTerm" (ngModelChange)="applyFilter()" placeholder="Search ID, customer, ref…" />
            </div>
            <select [(ngModel)]="statusFilter" (ngModelChange)="applyFilter()" class="filter-sel">
              <option value="">All Statuses</option>
              <option value="PROCESSING">Processing</option>
              <option value="PENDING">Pending</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILED">Failed</option>
            </select>
            <select [(ngModel)]="sourceFilter" (ngModelChange)="applyFilter()" class="filter-sel">
              <option value="">All Sources</option>
              <option value="FRONTEND">Frontend</option>
              <option value="BACKEND">Backend</option>
            </select>
          </div>
        </div>

        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5,6]"></div>
        </div>

        <div class="table-wrapper" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Transaction ID</th>
                <th>Customer ID</th>
                <th>Source</th>
                <th>Amount</th>
                <th>Receives</th>
                <th>Payment Mode</th>
                <th>Delivery</th>
                <th>Status</th>
                <th>Ext. Reference</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of filtered">
                <td>
                  <span class="txn-id" [title]="txn.referenceNumber || txn.transactionId">{{ txn.referenceNumber || (txn.transactionId | slice:0:8) + '…' }}</span>
                </td>
                <td class="mono" [title]="txn.customerId">{{ txn.customerId | slice:0:8 }}…</td>
                <td>
                  <ion-badge [color]="txn.customerSource === 'BACKEND' ? 'primary' : 'secondary'">
                    {{ txn.customerSource }}
                  </ion-badge>
                </td>
                <td class="fb-currency">{{ txn.amount | number:'1.2-2' }} {{ txn.currency }}</td>
                <td class="fb-currency">
                  <span *ngIf="txn.receiveAmount">{{ txn.receiveAmount | number:'1.2-2' }} {{ txn.receiveCurrency }}</span>
                  <span *ngIf="!txn.receiveAmount" class="muted">—</span>
                </td>
                <td>{{ paymentModeLabel(txn.paymentMode) }}</td>
                <td>{{ deliveryLabel(txn.deliveryMethod) }}</td>
                <td><span class="status-chip" [class]="'status-chip--' + txn.status?.toLowerCase()">{{ txn.status }}</span></td>
                <td class="mono">{{ txn.externalReferenceId || '—' }}</td>
                <td>{{ txn.createdAt | date:'MMM d, y HH:mm' }}</td>
              </tr>
              <tr *ngIf="filtered.length === 0">
                <td colspan="10" class="empty-state">No transactions found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .sa-pt { padding: 24px; }
    .sa-pt__header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
    .sa-pt__title { font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
    .sa-pt__sub { color: #6b7280; margin: 0; font-size: 0.875rem; }

    .sa-pt__kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 20px; }
    .sa-pt__kpi { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px 20px; text-align: center; }
    .sa-pt__kpi--blue { border-color: #bfdbfe; }
    .sa-pt__kpi--green { border-color: #a7f3d0; }
    .sa-pt__kpi--red { border-color: #fecaca; }
    .sa-pt__kpi-value { font-size: 1.8rem; font-weight: 700; color: #111827; }
    .sa-pt__kpi--blue .sa-pt__kpi-value { color: #1e40af; }
    .sa-pt__kpi--green .sa-pt__kpi-value { color: #065f46; }
    .sa-pt__kpi--red .sa-pt__kpi-value { color: #991b1b; }
    .sa-pt__kpi-label { font-size: 0.8rem; color: #6b7280; margin-top: 4px; }

    .table-toolbar { display: flex; align-items: center; gap: 10px; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap; }
    .tb-left { display: flex; align-items: center; gap: 8px; margin-right: auto; }
    .tb-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .search-box { display: flex; align-items: center; gap: 8px; border: 1px solid #e5e7eb; border-radius: 6px; padding: 7px 12px; }
    .search-box input { border: none; outline: none; font-size: 0.85rem; width: 200px; }
    .filter-sel { padding: 7px 10px; border: 1px solid #e5e7eb; border-radius: 6px; font-size: 0.85rem; outline: none; }
    .table-wrapper { overflow-x: auto; }
    .loading { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
    .txn-id, .mono { font-family: monospace; font-size: 0.8rem; cursor: default; }
    .muted { color: #9ca3af; }
    .fb-currency { font-weight: 600; }
    .empty-state { text-align: center; color: #9ca3af; padding: 32px !important; }
    .status-chip { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; }
    .status-chip--initiated { background: #e5e7eb; color: #374151; }
    .status-chip--pending { background: #fef3c7; color: #92400e; }
    .status-chip--processing { background: #dbeafe; color: #1e40af; }
    .status-chip--success { background: #d1fae5; color: #065f46; }
    .status-chip--failed { background: #fee2e2; color: #991b1b; }

    @media (max-width: 768px) { .sa-pt__kpis { grid-template-columns: 1fr 1fr; } }
  `]
})
export class SAPayinTransactionsPage implements OnInit {
  transactions: any[] = [];
  filtered: any[] = [];
  loading = true;
  searchTerm = '';
  statusFilter = '';
  sourceFilter = '';

  constructor(private partnerService: PartnerService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.partnerService.getPayinTransactionList().subscribe({
      next: (res: any) => {
        this.transactions = Array.isArray(res) ? res : (res?.data || []);
        this.applyFilter();
        this.loading = false;
      },
      error: () => { this.transactions = []; this.filtered = []; this.loading = false; }
    });
  }

  applyFilter(): void {
    let list = [...this.transactions];
    if (this.statusFilter) list = list.filter(t => t.status === this.statusFilter);
    if (this.sourceFilter) list = list.filter(t => t.customerSource === this.sourceFilter);
    if (this.searchTerm.trim()) {
      const t = this.searchTerm.toLowerCase();
      list = list.filter(tx =>
        tx.transactionId?.toLowerCase().includes(t) ||
        tx.customerId?.toLowerCase().includes(t) ||
        tx.externalReferenceId?.toLowerCase().includes(t) ||
        tx.currency?.toLowerCase().includes(t)
      );
    }
    this.filtered = list;
  }

  countByStatus(status: string): number {
    return this.transactions.filter(t => t.status === status).length;
  }

  paymentModeLabel(v: string): string {
    const map: Record<string, string> = {
      CASH_COLLECTION: 'Cash', CREDIT_CARD: 'Credit Card',
      DEBIT_CARD: 'Debit Card', INTERNET_BANKING: 'Internet Banking'
    };
    return map[v] || v || '—';
  }

  deliveryLabel(v: string): string {
    const map: Record<string, string> = {
      BANK_TRANSFER: 'Bank Transfer', MOBILE_MONEY: 'Mobile Money', CASH_PICKUP: 'Cash Pickup'
    };
    return map[v] || v || '—';
  }
}
