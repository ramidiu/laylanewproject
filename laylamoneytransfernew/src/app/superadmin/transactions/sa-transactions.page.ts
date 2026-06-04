import { Component, OnInit } from '@angular/core';
import { AlertController, ToastController } from '@ionic/angular';
import { TransactionService } from '../../core/services/transaction.service';
import { PartnerService } from '../../core/services/partner.service';
import { TransactionResponse } from '../../core/models/transaction.model';
import { PageResponse } from '../../core/models/common.model';

@Component({
  selector: 'app-sa-transactions',
  templateUrl: './sa-transactions.page.html',
  styleUrls: ['./sa-transactions.page.scss']
})
export class SATransactionsPage implements OnInit {
  transactions: TransactionResponse[] = [];
  loading = true;
  searchQuery = '';
  statusFilter = '';
  fromDate = '';
  toDate = '';
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  pageSize = 20;

  stats: any = {};
  statsLoading = true;

  statuses = ['CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED','ARCHIVED'];

  // PayIn tab
  activeTab: 'REGULAR' | 'PAYIN' = 'REGULAR';
  payinTransactions: any[] = [];
  payinFiltered: any[] = [];
  payinLoading = false;
  payinSearchTerm = '';
  payinStatusFilter = '';
  payinSourceFilter = '';

  constructor(
    private transactionService: TransactionService,
    private partnerService: PartnerService,
    private toastCtrl: ToastController,
    private alertCtrl: AlertController
  ) {}

  ngOnInit(): void {
    this.loadTransactions();
    this.loadStats();
    this.loadPayinTransactions();
  }

  loadPayinTransactions(): void {
    this.payinLoading = true;
    this.partnerService.getPayinTransactionList().subscribe({
      next: (res: any) => {
        this.payinTransactions = Array.isArray(res) ? res : (res?.data || []);
        this.applyPayinFilter();
        this.payinLoading = false;
      },
      error: () => { this.payinTransactions = []; this.payinFiltered = []; this.payinLoading = false; }
    });
  }

  applyPayinFilter(): void {
    let list = [...this.payinTransactions];
    if (this.payinStatusFilter) list = list.filter(t => t.status === this.payinStatusFilter);
    if (this.payinSourceFilter) list = list.filter(t => t.customerSource === this.payinSourceFilter);
    if (this.payinSearchTerm.trim()) {
      const q = this.payinSearchTerm.toLowerCase();
      list = list.filter(t =>
        t.transactionId?.toLowerCase().includes(q) ||
        t.customerId?.toLowerCase().includes(q) ||
        t.externalReferenceId?.toLowerCase().includes(q) ||
        t.currency?.toLowerCase().includes(q)
      );
    }
    this.payinFiltered = list;
  }

  payinPaymentLabel(v: string): string {
    const m: Record<string, string> = { CASH_COLLECTION: 'Cash', CREDIT_CARD: 'Credit Card', DEBIT_CARD: 'Debit Card', INTERNET_BANKING: 'Internet Banking' };
    return m[v] || v || '—';
  }

  payinDeliveryLabel(v: string): string {
    const m: Record<string, string> = { BANK_TRANSFER: 'Bank Transfer', MOBILE_MONEY: 'Mobile Money', CASH_PICKUP: 'Cash Pickup' };
    return m[v] || v || '—';
  }

  loadStats(): void {
    this.statsLoading = true;
    this.transactionService.getTransactionStats().subscribe({
      next: (res: any) => {
        // Backend returns totalTransactions/completedCount/etc. Template reads
        // stats.total/completed/etc. Normalize so the template stays simple.
        const raw = res?.data || res || {};
        const byStatus = raw.byStatus || {};
        this.stats = {
          total: raw.totalTransactions ?? 0,
          completed: raw.completedCount ?? ((byStatus.PAID || 0) + (byStatus.COMPLETED || 0)),
          processing: raw.processingCount ?? (byStatus.PROCESSING || 0),
          pending: raw.pendingCount ?? (byStatus.PENDING || 0),
          failed: raw.failedCount ?? (byStatus.FAILED || 0),
          cancelled: raw.cancelledCount ?? (byStatus.CANCELLED || 0)
        };
        this.statsLoading = false;
      },
      error: () => {
        this.stats = {};
        this.statsLoading = false;
      }
    });
  }

  loadTransactions(): void {
    this.loading = true;
    this.transactionService.getAllTransactions(this.currentPage, this.pageSize, {
      status: this.statusFilter || undefined,
      search: this.searchQuery || undefined,
      startDate: this.fromDate || undefined,
      endDate: this.toDate || undefined
    }).subscribe({
      next: (res: any) => {
        const data = res?.data || res;
        this.transactions = data?.content || data || [];
        this.totalPages = data?.totalPages || 0;
        this.totalElements = data?.totalElements || 0;
        this.loading = false;
      },
      error: () => {
        this.transactions = [];
        this.loading = false;
      }
    });
  }

  onSearch(event: any): void {
    this.searchQuery = event.target?.value || '';
    this.currentPage = 0;
    this.loadTransactions();
  }

  onStatusFilter(status: string): void {
    this.statusFilter = status;
    this.currentPage = 0;
    this.loadTransactions();
  }

  onDateChange(): void {
    this.currentPage = 0;
    this.loadTransactions();
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadTransactions();
  }

  async updateStatus(txn: TransactionResponse, newStatus: string): Promise<void> {
    this.transactionService.updateStatus(txn.id, newStatus).subscribe({
      next: () => {
        this.showToast(`Transaction updated to ${newStatus}`, 'success');
        this.loadTransactions();
        this.loadStats();
      },
      error: () => this.showToast('Failed to update status', 'danger')
    });
  }

  async markFundsReceived(txn: TransactionResponse): Promise<void> {
    this.transactionService.markFundsReceived(txn.id as any).subscribe({
      next: () => {
        this.showToast('Funds received confirmed', 'success');
        this.loadTransactions();
        this.loadStats();
      },
      error: () => this.showToast('Failed to mark funds received', 'danger')
    });
  }

  async markPaid(txn: TransactionResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Mark Paid',
      message: `Mark ${txn.referenceNumber} as PAID and complete it? (Admin fallback — no payout partner is assigned to this corridor.)`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Yes, Mark Paid',
          handler: () => {
            this.transactionService.adminMarkPaid(txn.id as any).subscribe({
              next: () => { this.showToast('Transaction marked PAID and completed', 'success'); this.loadTransactions(); this.loadStats(); },
              error: (err: any) => this.showToast(err?.error?.message || 'Failed to mark paid', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async viewBeneficiary(txn: any): Promise<void> {
    const dash = (v: any) => (v !== null && v !== undefined && String(v).trim() !== '' ? v : '—');
    const alert = await this.alertCtrl.create({
      header: 'Beneficiary Details',
      subHeader: txn.beneficiaryName || '',
      message: `<div style="text-align:left;line-height:1.7">
        <b>Reference:</b> ${dash(txn.referenceNumber)}<br>
        <b>Telephone:</b> ${dash(txn.beneficiaryPhone)}<br>
        <b>Country:</b> ${dash(txn.beneficiaryCountry)}<br>
        <b>City:</b> ${dash(txn.beneficiaryCity)}<br>
        <b>Bank Name:</b> ${dash(txn.beneficiaryBankName)}<br>
        <b>Account Number:</b> ${dash(txn.beneficiaryAccountNumber)}<br>
        <b>Branch:</b> ${dash(txn.beneficiaryBranch)}<br>
        <b>Swift Code:</b> ${dash(txn.beneficiarySwift)}
      </div>`,
      buttons: ['Close']
    });
    await alert.present();
  }

  async releaseCompliance(txn: TransactionResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Release from Compliance Hold',
      message: `Release transaction ${txn.referenceNumber} back to PENDING? The user can then proceed with pay-in.`,
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Reason (optional)' }],
      buttons: [
        { text: 'No', role: 'cancel' },
        {
          text: 'Yes, Release',
          handler: (data) => {
            this.transactionService.adminReleaseCompliance(txn.id as any, data.reason || 'Compliance cleared by admin').subscribe({
              next: () => {
                this.showToast('Transaction released from compliance hold', 'success');
                this.loadTransactions();
                this.loadStats();
              },
              error: () => this.showToast('Failed to release transaction', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async releaseAllCompliance(): Promise<void> {
    const held = this.transactions.filter(t => t.status === 'COMPLIANCE_HOLD');
    if (held.length === 0) {
      this.showToast('No transactions on compliance hold', 'warning');
      return;
    }
    const alert = await this.alertCtrl.create({
      header: 'Release All Compliance Holds',
      message: `Release ${held.length} transaction(s) currently on COMPLIANCE_HOLD back to PENDING?`,
      buttons: [
        { text: 'No', role: 'cancel' },
        {
          text: `Yes, Release ${held.length}`,
          handler: () => {
            let done = 0; let failed = 0;
            const tick = () => {
              if (done + failed === held.length) {
                this.showToast(`Released ${done} of ${held.length}` + (failed ? `, ${failed} failed` : ''), failed ? 'warning' : 'success');
                this.loadTransactions();
                this.loadStats();
              }
            };
            held.forEach(t => {
              this.transactionService.adminReleaseCompliance(t.id as any, 'Bulk release by admin').subscribe({
                next: () => { done++; tick(); },
                error: () => { failed++; tick(); }
              });
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async adminCancel(txn: TransactionResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Cancel Transaction',
      message: `Cancel transaction ${txn.referenceNumber}?`,
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Reason for cancellation' }],
      buttons: [
        { text: 'No', role: 'cancel' },
        {
          text: 'Yes, Cancel',
          handler: (data) => {
            this.transactionService.adminCancel(txn.id as any, data.reason).subscribe({
              next: () => {
                this.showToast('Transaction cancelled', 'success');
                this.loadTransactions();
                this.loadStats();
              },
              error: () => this.showToast('Failed to cancel transaction', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async adminRefund(txn: TransactionResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Refund Transaction',
      message: `Refund transaction ${txn.referenceNumber}?`,
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Reason for refund' }],
      buttons: [
        { text: 'No', role: 'cancel' },
        {
          text: 'Yes, Refund',
          handler: (data) => {
            this.transactionService.adminRefund(txn.id as any, data.reason).subscribe({
              next: () => {
                this.showToast('Transaction refunded', 'success');
                this.loadTransactions();
                this.loadStats();
              },
              error: () => this.showToast('Failed to refund transaction', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async adminArchive(txn: TransactionResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Archive Transaction',
      message: `Archive transaction ${txn.referenceNumber}? This cannot be undone.`,
      buttons: [
        { text: 'No', role: 'cancel' },
        {
          text: 'Yes, Archive',
          handler: () => {
            this.transactionService.adminArchive(txn.id as any).subscribe({
              next: () => {
                this.showToast('Transaction archived', 'success');
                this.loadTransactions();
                this.loadStats();
              },
              error: () => this.showToast('Failed to archive transaction', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'PAID': case 'COMPLETED': case 'FUNDS_RECEIVED': return 'success';
      case 'PROCESSING': case 'SENT_TO_PAYOUT': return 'primary';
      case 'PENDING': case 'CREATED': return 'warning';
      case 'FAILED': return 'danger';
      case 'CANCELLED': case 'REFUNDED': return 'medium';
      case 'COMPLIANCE_HOLD': return 'tertiary';
      default: return 'medium';
    }
  }

  getStatusLabel(status: string): string {
    if (status === 'PAID' || status === 'COMPLETED') return 'Paid';
    return status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  exportTransactions(): void {
    this.showToast('Export functionality - integrate with CSV export endpoint', 'primary');
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
