import { Component, OnInit } from '@angular/core';
import { ToastController, AlertController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-partner-transactions',
  template: `
    <div class="partner-transactions">
      <div class="pt-header">
        <div>
          <h1 class="page-title">Pending Payouts</h1>
          <p class="page-subtitle">Transactions awaiting payout confirmation</p>
        </div>
        <ion-button fill="outline" size="small" (click)="loadAll()">
          <ion-icon name="refresh-outline" slot="start"></ion-icon>
          Refresh
        </ion-button>
      </div>

      <!-- Tabs -->
      <div class="pt-tabs">
        <button class="pt-tab" [class.pt-tab--active]="activeTab === 'payout'" (click)="activeTab = 'payout'">
          <ion-icon name="arrow-redo-outline"></ion-icon>
          Payout Transactions
          <span class="pt-tab-count">{{ payoutTxns.length }}</span>
        </button>
        <button class="pt-tab" [class.pt-tab--active]="activeTab === 'payin'" (click)="activeTab = 'payin'">
          <ion-icon name="arrow-undo-outline"></ion-icon>
          Pay-In Transactions
          <span class="pt-tab-count pt-tab-count--orange">{{ payinTxns.length }}</span>
        </button>
      </div>

      <!-- ===== PAYOUT TAB ===== -->
      <div *ngIf="activeTab === 'payout'" class="fb-card table-card">
        <div *ngIf="loadingPayout" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>
        <div class="table-wrapper" *ngIf="!loadingPayout">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Reference</th>
                <th>Beneficiary</th>
                <th>Payout Amount</th>
                <th>Delivery Method</th>
                <th>Status</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of payoutTxns">
                <td class="fb-currency">{{ txn.referenceNumber }}</td>
                <td>{{ txn.beneficiaryName }}</td>
                <td class="fb-currency">{{ txn.receiveAmount | number:'1.2-2' }} {{ txn.receiveCurrency }}</td>
                <td>{{ txn.deliveryMethod }}</td>
                <td><app-status-chip [status]="txn.status"></app-status-chip></td>
                <td>{{ txn.createdAt | date:'MMM d, y HH:mm' }}</td>
                <td>
                  <button class="action-btn action-btn--info" (click)="viewBeneficiary(txn)">
                    <ion-icon name="eye-outline"></ion-icon>
                    Details
                  </button>
                  <button class="action-btn action-btn--success" (click)="markPayoutPaid(txn)" [disabled]="txn._marking">
                    <ion-icon name="checkmark-circle-outline"></ion-icon>
                    Mark Paid
                  </button>
                </td>
              </tr>
              <tr *ngIf="payoutTxns.length === 0">
                <td colspan="7" class="empty-state">
                  <ion-icon name="checkmark-circle-outline" class="empty-icon"></ion-icon>
                  <p>All caught up! No pending payouts.</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ===== PAY-IN TAB ===== -->
      <div *ngIf="activeTab === 'payin'" class="fb-card table-card">
        <div class="pt-payin-info">
          <ion-icon name="information-circle-outline"></ion-icon>
          These are Pay-In transactions with status <strong>PROCESSING</strong> awaiting payout. Mark them as Paid once funds are disbursed.
        </div>
        <div *ngIf="loadingPayin" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>
        <div class="table-wrapper" *ngIf="!loadingPayin">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Transaction ID</th>
                <th>Customer</th>
                <th>Source</th>
                <th>Beneficiary</th>
                <th>Bank</th>
                <th>Amount</th>
                <th>Receives</th>
                <th>Delivery</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of payinTxns">
                <td class="mono" [title]="txn.referenceNumber || txn.transactionId">{{ txn.referenceNumber || (txn.transactionId | slice:0:8) + '…' }}</td>
                <td class="mono" [title]="txn.customerId">{{ txn.customerId | slice:0:8 }}…</td>
                <td>
                  <span class="src-badge" [class.src-badge--backend]="txn.customerSource === 'BACKEND'"
                        [class.src-badge--frontend]="txn.customerSource === 'FRONTEND'"
                        [class.src-badge--user]="txn.customerSource === 'FRONTEND_USER'">
                    {{ txn.customerSource === 'FRONTEND_USER' ? 'UK USER' : txn.customerSource }}
                  </span>
                </td>
                <td>{{ txn.beneficiaryName || '—' }}</td>
                <td>{{ txn.beneficiaryBank || '—' }}</td>
                <td class="fb-currency">{{ txn.amount | number:'1.2-2' }} {{ txn.currency }}</td>
                <td class="fb-currency">
                  <span *ngIf="txn.receiveAmount">{{ txn.receiveAmount | number:'1.2-2' }} {{ txn.receiveCurrency }}</span>
                  <span *ngIf="!txn.receiveAmount" class="muted">—</span>
                </td>
                <td>{{ txn.deliveryMethod || '—' }}</td>
                <td>{{ txn.createdAt | date:'MMM d, y HH:mm' }}</td>
                <td>
                  <button class="action-btn action-btn--info" (click)="viewBeneficiary(txn)">
                    <ion-icon name="eye-outline"></ion-icon>
                    Details
                  </button>
                  <button class="action-btn action-btn--success" (click)="markPayinPaid(txn)" [disabled]="txn._marking">
                    <ion-icon name="checkmark-circle-outline"></ion-icon>
                    {{ txn._marking ? 'Saving…' : 'Mark Paid' }}
                  </button>
                </td>
              </tr>
              <tr *ngIf="payinTxns.length === 0">
                <td colspan="10" class="empty-state">
                  <ion-icon name="checkmark-circle-outline" class="empty-icon"></ion-icon>
                  <p>No Pay-In transactions awaiting payout.</p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .partner-transactions { padding: 24px; }
    .pt-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
    .page-title { font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
    .page-subtitle { color: #6b7280; margin: 0; font-size: 0.875rem; }

    .pt-tabs { display: flex; gap: 4px; margin-bottom: 16px; border-bottom: 2px solid #e5e7eb; }
    .pt-tab { display: flex; align-items: center; gap: 8px; padding: 10px 20px; background: none; border: none; border-bottom: 3px solid transparent; margin-bottom: -2px; cursor: pointer; font-size: 0.9rem; font-weight: 500; color: #6b7280; transition: all .15s; }
    .pt-tab:hover { color: #1B3A6B; }
    .pt-tab--active { color: #1B3A6B; border-bottom-color: #1B3A6B; }
    .pt-tab-count { background: #e5e7eb; color: #374151; border-radius: 12px; padding: 1px 8px; font-size: 0.75rem; font-weight: 700; }
    .pt-tab-count--orange { background: #fef3c7; color: #92400e; }

    .pt-payin-info { display: flex; align-items: flex-start; gap: 8px; padding: 10px 16px; background: #eff6ff; border-bottom: 1px solid #bfdbfe; font-size: 0.85rem; color: #1e40af; }
    .pt-payin-info ion-icon { font-size: 1.1rem; flex-shrink: 0; margin-top: 1px; }

    .table-wrapper { overflow-x: auto; }
    .loading { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
    .empty-state { text-align: center; color: #9ca3af; padding: 32px !important; }
    .empty-icon { font-size: 2rem; display: block; margin: 0 auto 8px; }
    .mono { font-family: monospace; font-size: 0.8rem; cursor: default; }
    .muted { color: #9ca3af; }
    .fb-currency { font-weight: 600; }

    .src-badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; }
    .src-badge--backend { background: #dbeafe; color: #1e40af; }
    .src-badge--frontend { background: #d1fae5; color: #065f46; }
    .src-badge--user { background: #fef3c7; color: #92400e; }

    .action-btn { display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px; border-radius: 6px; border: none; font-size: 0.8rem; font-weight: 600; cursor: pointer; transition: all .15s; }
    .action-btn--success { background: #d1fae5; color: #065f46; }
    .action-btn--success:hover:not(:disabled) { background: #a7f3d0; }
    .action-btn--info { background: #e0e7ff; color: #1B3A6B; margin-right: 6px; }
    .action-btn--info:hover { background: #c7d2fe; }
    .action-btn:disabled { opacity: 0.55; cursor: not-allowed; }
  `]
})
export class PartnerTransactionsPage implements OnInit {
  payoutTxns: any[] = [];
  payinTxns: any[] = [];
  loadingPayout = true;
  loadingPayin = true;
  activeTab: 'payout' | 'payin' = 'payout';

  constructor(
    private partnerService: PartnerService,
    private toastCtrl: ToastController,
    private alertCtrl: AlertController
  ) {}

  async viewBeneficiary(txn: any): Promise<void> {
    const dash = (v: any) => (v !== null && v !== undefined && String(v).trim() !== '' ? v : '—');
    const phone   = txn.beneficiaryPhone || txn.beneficiaryMobileNumber;
    const country = txn.beneficiaryCountry;
    const city    = txn.beneficiaryCity || txn.beneficiaryBranchCity;
    const bank    = txn.beneficiaryBankName || txn.beneficiaryBank;
    const account = txn.beneficiaryAccountNumber || txn.beneficiaryAccount;
    const branch  = txn.beneficiaryBranch || txn.beneficiarySortCode;
    const swift   = txn.beneficiarySwift || txn.beneficiarySwiftBic;
    const iban    = txn.beneficiaryIban;
    const provider = txn.beneficiaryProvider || txn.beneficiaryMobileProvider;
    const address = txn.beneficiaryAddress;
    const alert = await this.alertCtrl.create({
      header: 'Beneficiary Details',
      subHeader: txn.beneficiaryName || '',
      cssClass: 'fb-beneficiary-alert',
      message: `<div style="text-align:left;line-height:1.7">
        <b>Reference:</b> ${dash(txn.referenceNumber)}<br>
        <b>Telephone:</b> ${dash(phone)}<br>
        <b>Country:</b> ${dash(country)}<br>
        <b>City:</b> ${dash(city)}<br>
        <b>Address:</b> ${dash(address)}<br>
        <b>Bank Name:</b> ${dash(bank)}<br>
        <b>Account Number:</b> ${dash(account)}<br>
        <b>Branch:</b> ${dash(branch)}<br>
        <b>Swift Code:</b> ${dash(swift)}<br>
        <b>IBAN:</b> ${dash(iban)}<br>
        <b>Mobile Provider:</b> ${dash(provider)}
      </div>`,
      buttons: ['Close']
    });
    await alert.present();
  }

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loadPayoutTransactions();
    this.loadPayinTransactions();
  }

  loadPayoutTransactions(): void {
    this.loadingPayout = true;
    this.partnerService.getMyTransactions().subscribe({
      next: (res: any) => {
        const data = res?.data || res;
        this.payoutTxns = Array.isArray(data) ? data : (data?.content || []);
        this.loadingPayout = false;
      },
      error: () => {
        this.payoutTxns = [];
        this.loadingPayout = false;
      }
    });
  }

  loadPayinTransactions(): void {
    this.loadingPayin = true;
    this.partnerService.getPayinProcessingTransactions().subscribe({
      next: (res: any) => {
        this.payinTxns = Array.isArray(res) ? res : (res?.data || []);
        this.loadingPayin = false;
      },
      error: () => {
        this.payinTxns = [];
        this.loadingPayin = false;
      }
    });
  }

  markPayoutPaid(txn: any): void {
    txn._marking = true;
    this.partnerService.markPaid(txn.id).subscribe({
      next: () => {
        this.showToast('Transaction marked as paid', 'success');
        this.loadPayoutTransactions();
      },
      error: () => {
        txn._marking = false;
        this.showToast('Failed to update status', 'danger');
      }
    });
  }

  markPayinPaid(txn: any): void {
    txn._marking = true;
    this.partnerService.markPayinTransactionPaid(txn.transactionId).subscribe({
      next: () => {
        this.showToast(`Pay-In transaction marked as paid`, 'success');
        this.payinTxns = this.payinTxns.filter(t => t.transactionId !== txn.transactionId);
      },
      error: (err: any) => {
        txn._marking = false;
        const msg = err?.error?.message || 'Failed to update status';
        this.showToast(msg, 'danger');
      }
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 4000, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
