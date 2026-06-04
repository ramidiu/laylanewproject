import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';
import { SettlementService } from '../../core/services/settlement.service';

@Component({
  selector: 'app-payin-partner-ledger',
  template: `
    <div class="payin-ledger">
      <h1 class="page-title">Ledger</h1>
      <p class="page-subtitle">Your pay-in ledger entries and running balance</p>

      <!-- You Owe Admin Card -->
      <div class="owe-card" *ngIf="!loading">
        <div class="owe-card__content">
          <div class="owe-card__left">
            <span class="owe-card__label">You Owe Admin</span>
            <span class="owe-card__amount">{{ currentBalance | number:'1.2-2' }} USD</span>
            <span class="owe-card__sub">Collected: {{ totalCredits | number:'1.2-2' }} USD &nbsp;|&nbsp; Settled: {{ totalDebits | number:'1.2-2' }} USD</span>
          </div>
          <div class="owe-card__right">
            <button class="btn btn--warning" (click)="showPayAdminForm = true" *ngIf="!showPayAdminForm && currentBalance > 0">Pay Admin</button>
          </div>
        </div>
      </div>

      <!-- Pay Admin Form -->
      <div class="pay-admin-form-card" *ngIf="showPayAdminForm">
        <div class="pay-admin-form-card__header">
          <h3>Pay Admin</h3>
          <span class="outstanding-label">Outstanding: <strong>{{ currentBalance | number:'1.2-2' }} USD</strong></span>
        </div>
        <div class="pay-admin-form-card__body">
          <div class="form-group">
            <label class="form-label">Amount</label>
            <div class="amount-input-row">
              <input type="number" class="form-input" [(ngModel)]="payAmount" placeholder="0.00" min="0" step="0.01" />
              <button class="btn btn--outline btn--sm" (click)="payAmount = currentBalance">Full</button>
              <button class="btn btn--outline btn--sm" (click)="payAmount = Math.round(currentBalance * 50) / 100">Half</button>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">Payment Reference</label>
            <input type="text" class="form-input" [(ngModel)]="payReference" placeholder="Bank transfer reference" />
          </div>
          <div class="form-summary" *ngIf="payAmount">
            <div class="form-summary__row">
              <span>Outstanding Before</span>
              <span class="mono-value">{{ currentBalance | number:'1.2-2' }} USD</span>
            </div>
            <div class="form-summary__row">
              <span>Amount</span>
              <span class="mono-value">{{ payAmount | number:'1.2-2' }} USD</span>
            </div>
            <div class="form-summary__row">
              <span>Outstanding After</span>
              <span class="mono-value">{{ (currentBalance - payAmount) | number:'1.2-2' }} USD</span>
            </div>
          </div>
          <div class="form-actions">
            <button class="btn btn--outline" (click)="closePayForm()">Cancel</button>
            <button class="btn btn--warning" (click)="submitPayAdmin()" [disabled]="paySubmitting || !payAmount || !payReference">
              {{ paySubmitting ? 'Processing...' : 'Pay ' + (payAmount | number:'1.2-2') + ' USD' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Summary -->
      <div class="ledger-summary" *ngIf="!loading">
        <div class="summary-item summary-item--credit">
          <span class="summary-label">Total Credits</span>
          <span class="summary-value">{{ totalCredits | number:'1.2-2' }}</span>
        </div>
        <div class="summary-item summary-item--debit">
          <span class="summary-label">Total Debits</span>
          <span class="summary-value">{{ totalDebits | number:'1.2-2' }}</span>
        </div>
        <div class="summary-item summary-item--balance">
          <span class="summary-label">Current Balance</span>
          <span class="summary-value">{{ currentBalance | number:'1.2-2' }}</span>
        </div>
      </div>

      <div class="fb-card table-card">
        <div class="table-toolbar">
          <h3 class="section-title">Ledger Entries</h3>
          <ion-button fill="clear" size="small" (click)="loadData()">
            <ion-icon name="refresh-outline" slot="start"></ion-icon>
            Refresh
          </ion-button>
        </div>

        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>

        <div class="table-wrapper" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Type</th>
                <th>Description</th>
                <th>Amount</th>
                <th>Currency</th>
                <th>Balance</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let entry of ledgerEntries">
                <td>{{ entry.createdAt | date:'MMM d, y HH:mm' }}</td>
                <td>
                  <span class="entry-type" [ngClass]="{
                    'entry-type--credit': entry.entryType === 'CREDIT',
                    'entry-type--debit': entry.entryType === 'DEBIT'
                  }">
                    {{ entry.entryType }}
                  </span>
                </td>
                <td>{{ entry.description }}</td>
                <td class="fb-currency" [ngClass]="{
                  'amount--credit': entry.entryType === 'CREDIT',
                  'amount--debit': entry.entryType === 'DEBIT'
                }">
                  {{ entry.entryType === 'CREDIT' ? '+' : '-' }}{{ (entry.localAmount ?? entry.amount ?? 0) | number:'1.2-2' }}
                </td>
                <td>{{ entry.localCurrency || entry.currency || '' }}</td>
                <td class="fb-currency">{{ (entry.balanceUsd ?? entry.balance ?? 0) | number:'1.2-2' }}</td>
              </tr>
              <tr *ngIf="ledgerEntries.length === 0">
                <td colspan="6" class="empty-state">No ledger entries found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./payin-partner-ledger.page.scss']
})
export class PayinPartnerLedgerPage implements OnInit {
  ledgerEntries: any[] = [];
  loading = true;
  totalCredits = 0;
  totalDebits = 0;
  currentBalance = 0;

  // Pay Admin form
  showPayAdminForm = false;
  payAmount: number | null = null;
  payReference = '';
  paySubmitting = false;

  // Expose Math for template
  Math = Math;

  constructor(
    private partnerService: PartnerService,
    private settlementService: SettlementService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.partnerService.getPayinLedger().subscribe({
      next: (res: any) => {
        // Backend shape: ApiResponse<{entries, balance}>, i.e. res.data.entries for the array.
        const data = res?.data ?? res ?? {};
        this.ledgerEntries = Array.isArray(data) ? data : (data.entries || data.content || []);
        this.calculateSummary();
        this.loading = false;
      },
      error: () => {
        this.ledgerEntries = [];
        this.loading = false;
      }
    });
  }

  closePayForm(): void {
    this.showPayAdminForm = false;
    this.payAmount = null;
    this.payReference = '';
  }

  submitPayAdmin(): void {
    if (!this.payAmount || !this.payReference) return;
    this.paySubmitting = true;
    this.settlementService.initiatePayinToAdmin({
      amount: this.payAmount,
      reference: this.payReference
    }).subscribe({
      next: () => {
        this.showToast('Payment submitted successfully', 'success');
        this.paySubmitting = false;
        this.closePayForm();
        this.loadData();
      },
      error: () => {
        this.showToast('Failed to submit payment', 'danger');
        this.paySubmitting = false;
      }
    });
  }

  private calculateSummary(): void {
    // Backend field names: localAmount / balanceUsd (fall back to amount / balance for compat).
    const amt = (e: any) => Number(e.localAmount ?? e.amount ?? 0);
    const bal = (e: any) => Number(e.balanceUsd ?? e.balance ?? 0);
    this.totalCredits = this.ledgerEntries
      .filter((e: any) => e.entryType === 'CREDIT')
      .reduce((sum: number, e: any) => sum + amt(e), 0);
    this.totalDebits = this.ledgerEntries
      .filter((e: any) => e.entryType === 'DEBIT')
      .reduce((sum: number, e: any) => sum + amt(e), 0);
    if (this.ledgerEntries.length > 0) {
      this.currentBalance = bal(this.ledgerEntries[this.ledgerEntries.length - 1]);
    }
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
