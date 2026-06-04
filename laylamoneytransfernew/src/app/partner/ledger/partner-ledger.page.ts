import { Component, OnInit } from '@angular/core';
import { AlertController, ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';
import { SettlementService } from '../../core/services/settlement.service';

@Component({
  selector: 'app-partner-ledger',
  template: `
    <div class="partner-ledger">
      <h1 class="page-title">Partner Ledger</h1>
      <p class="page-subtitle">Your transaction and settlement ledger entries</p>

      <!-- Admin Owes You Card -->
      <div class="owes-card" *ngIf="!loading">
        <div class="owes-card__left">
          <span class="owes-card__label">Admin Owes You</span>
          <span class="owes-card__amount">{{ currentBalance | number:'1.2-2' }} USD</span>
          <span class="owes-card__sub">Paid out: {{ totalDebits | number:'1.2-2' }} USD &nbsp;|&nbsp; Settled: {{ totalCredits | number:'1.2-2' }} USD</span>
        </div>
      </div>

      <!-- Summary -->
      <div class="ledger-summary" *ngIf="!loading">
        <div class="summary-item summary-item--credit">
          <span class="summary-label">Total Credits</span>
          <span class="summary-value">{{ totalCredits | number:'1.2-2' }} USD</span>
        </div>
        <div class="summary-item summary-item--debit">
          <span class="summary-label">Total Debits</span>
          <span class="summary-value">{{ totalDebits | number:'1.2-2' }} USD</span>
        </div>
        <div class="summary-item summary-item--balance">
          <span class="summary-label">Current Balance</span>
          <span class="summary-value">{{ currentBalance | number:'1.2-2' }} USD</span>
        </div>
      </div>

      <!-- Pending Settlements -->
      <div class="pending-section" *ngIf="!settlementsLoading && pendingSettlements.length > 0">
        <div class="pending-section__header">
          <h3 class="pending-section__title">Pending Settlements</h3>
          <span class="pending-section__subtitle">Settlements awaiting your approval</span>
        </div>
        <div class="pending-list">
          <div class="pending-item" *ngFor="let s of pendingSettlements">
            <div class="pending-item__info">
              <span class="pending-item__amount">{{ s.amount | number:'1.2-2' }} {{ s.currency || 'USD' }}</span>
              <span class="pending-item__ref" *ngIf="s.reference">Ref: {{ s.reference }}</span>
              <span class="pending-item__date">{{ s.createdAt | date:'MMM d, y HH:mm' }}</span>
            </div>
            <div class="pending-item__status">
              <span class="status-badge status-badge--pending">PENDING</span>
            </div>
            <div class="pending-item__actions">
              <button class="btn btn--success btn--sm" (click)="approveSettlement(s.id)">Approve</button>
              <button class="btn btn--danger btn--sm" (click)="rejectSettlement(s.id)">Reject</button>
            </div>
          </div>
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
                <th>Local Amount</th>
                <th>USD Amount</th>
                <th>Balance (USD)</th>
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
                  {{ entry.entryType === 'CREDIT' ? '+' : '-' }}{{ entry.localAmount | number:'1.2-2' }} {{ entry.localCurrency }}
                </td>
                <td class="fb-currency">{{ entry.usdAmount | number:'1.2-2' }} USD</td>
                <td class="fb-currency">{{ entry.balanceUsd | number:'1.2-2' }} USD</td>
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
  styleUrls: ['./partner-ledger.page.scss']
})
export class PartnerLedgerPage implements OnInit {
  ledgerEntries: any[] = [];
  loading = true;
  totalCredits = 0;
  totalDebits = 0;
  currentBalance = 0;

  pendingSettlements: any[] = [];
  settlementsLoading = true;

  constructor(
    private partnerService: PartnerService,
    private settlementService: SettlementService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadData();
    this.loadPendingSettlements();
  }

  loadData(): void {
    this.loading = true;
    this.partnerService.getMyLedger().subscribe({
      next: (res: any) => {
        const data = res?.data || res;
        this.ledgerEntries = data?.entries || (Array.isArray(data) ? data : []);
        if (data?.balance !== undefined) {
          this.currentBalance = data.balance;
        }
        this.calculateSummary();
        this.loading = false;
      },
      error: () => {
        this.ledgerEntries = [];
        this.loading = false;
      }
    });
  }

  loadPendingSettlements(): void {
    this.settlementsLoading = true;
    this.settlementService.getPendingSettlements().subscribe({
      next: (res: any) => {
        const all = Array.isArray(res) ? res : res?.data || [];
        this.pendingSettlements = all.filter((s: any) => s.settlementType === 'ADMIN_TO_PAYOUT');
        this.settlementsLoading = false;
      },
      error: () => {
        this.pendingSettlements = [];
        this.settlementsLoading = false;
      }
    });
  }

  approveSettlement(id: number): void {
    this.settlementService.approveSettlement(id).subscribe({
      next: () => {
        this.showToast('Settlement approved', 'success');
        this.loadPendingSettlements();
        this.loadData();
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

  private calculateSummary(): void {
    this.totalCredits = this.ledgerEntries
      .filter((e: any) => e.entryType === 'CREDIT')
      .reduce((sum: number, e: any) => sum + (e.usdAmount || 0), 0);
    this.totalDebits = this.ledgerEntries
      .filter((e: any) => e.entryType === 'DEBIT')
      .reduce((sum: number, e: any) => sum + (e.usdAmount || 0), 0);
    if (this.ledgerEntries.length > 0) {
      this.currentBalance = this.ledgerEntries[this.ledgerEntries.length - 1].balanceUsd || 0;
    }
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
