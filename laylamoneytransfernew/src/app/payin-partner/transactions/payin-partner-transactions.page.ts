import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-payin-partner-transactions',
  template: `
    <div class="payin-transactions">
      <h1 class="page-title">Transactions</h1>
      <p class="page-subtitle">All PayIn transactions submitted through your channel</p>

      <!-- Search + filter toolbar -->
      <div class="fb-card table-card">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <ion-badge color="primary">{{ filtered.length }}</ion-badge>
            <span>transactions</span>
          </div>
          <div class="toolbar-right">
            <div class="search-box">
              <ion-icon name="search-outline"></ion-icon>
              <input [(ngModel)]="searchTerm" (ngModelChange)="applyFilter()" placeholder="Search ID, customer, currency…" />
            </div>
            <select [(ngModel)]="statusFilter" (ngModelChange)="applyFilter()" class="filter-select">
              <option value="">All Statuses</option>
              <option value="PROCESSING">Processing</option>
              <option value="PENDING">Pending</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILED">Failed</option>
              <option value="PAID">Paid</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
            <ion-button fill="clear" size="small" (click)="loadData()">
              <ion-icon name="refresh-outline" slot="start"></ion-icon>
              Refresh
            </ion-button>
          </div>
        </div>

        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>

        <div class="table-wrapper" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Transaction ID</th>
                <th>Customer ID</th>
                <th>Amount</th>
                <th>Receives</th>
                <th>Payment Mode</th>
                <th>Delivery</th>
                <th>Status</th>
                <th>Ext. Reference</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of filtered">
                <td class="txn-id">{{ txn.referenceNumber || (txn.transactionId | slice:0:8) + '…' }}</td>
                <td class="mono">{{ txn.customerId | slice:0:8 }}…</td>
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
                <td colspan="9" class="empty-state">No transactions found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .payin-transactions { padding: 24px; }
    .page-title { font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
    .page-subtitle { color: #6b7280; margin: 0 0 20px; }
    .table-toolbar { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap; }
    .toolbar-left { display: flex; align-items: center; gap: 8px; margin-right: auto; }
    .toolbar-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .search-box { display: flex; align-items: center; gap: 8px; border: 1px solid #e5e7eb; border-radius: 6px; padding: 7px 12px; }
    .search-box input { border: none; outline: none; font-size: 0.85rem; width: 200px; }
    .filter-select { padding: 7px 10px; border: 1px solid #e5e7eb; border-radius: 6px; font-size: 0.85rem; outline: none; }
    .table-wrapper { overflow-x: auto; }
    .loading { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
    .txn-id, .mono { font-family: monospace; font-size: 0.8rem; }
    .muted { color: #9ca3af; }
    .fb-currency { font-weight: 600; }
    .empty-state { text-align: center; color: #9ca3af; padding: 32px !important; }
    .status-chip { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: .03em; }
    .status-chip--initiated { background: #e5e7eb; color: #374151; }
    .status-chip--pending { background: #fef3c7; color: #92400e; }
    .status-chip--processing { background: #dbeafe; color: #1e40af; }
    .status-chip--success { background: #d1fae5; color: #065f46; }
    .status-chip--failed { background: #fee2e2; color: #991b1b; }
  `]
})
export class PayinPartnerTransactionsPage implements OnInit {
  transactions: any[] = [];
  filtered: any[] = [];
  loading = true;
  searchTerm = '';
  // Default to the actionable PROCESSING view; "All Statuses" shows everything.
  statusFilter = 'PROCESSING';

  constructor(private partnerService: PartnerService, private toastCtrl: ToastController) {}

  ngOnInit(): void { this.loadData(); }

  loadData(): void {
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
    if (this.searchTerm.trim()) {
      const t = this.searchTerm.toLowerCase();
      list = list.filter(tx =>
        tx.transactionId?.toLowerCase().includes(t) ||
        tx.customerId?.toLowerCase().includes(t) ||
        tx.currency?.toLowerCase().includes(t) ||
        tx.externalReferenceId?.toLowerCase().includes(t)
      );
    }
    this.filtered = list;
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
      BANK_TRANSFER: 'Bank', MOBILE_MONEY: 'Mobile Money', CASH_PICKUP: 'Cash Pickup'
    };
    return map[v] || v || '—';
  }
}
