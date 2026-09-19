import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
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

  // Number of PAID USI transactions available to export (shown as a count on the USI CSV button).
  usiPaidCount = 0;

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
    private alertCtrl: AlertController,
    private router: Router
  ) {}

  /** Open the receipt viewer (print + download PDF) for a transaction. */
  openReceipt(txn: TransactionResponse): void {
    if (txn?.id != null) this.router.navigate(['/receipt', txn.id]);
  }

  ngOnInit(): void {
    this.loadTransactions();
    this.loadStats();
    this.loadPayinTransactions();
    this.loadUsiPaidCount();
  }

  /** Fetch how many PAID USI transactions exist — used for the USI CSV button count. */
  loadUsiPaidCount(): void {
    this.transactionService.getUsiCsvCount().subscribe({
      next: (count) => this.usiPaidCount = count || 0,
      error: () => this.usiPaidCount = 0
    });
  }

  /**
   * Download the USI CSV — only PAID transactions paid out via USI, in the exact column
   * format USI requires. Respects the page's From/To date filters when set.
   */
  downloadUsiCsv(): void {
    if (this.usiPaidCount === 0) {
      this.showToast('No paid transactions to export yet. The file will download with headers only.', 'warning');
    } else {
      this.showToast('Preparing CSV…', 'primary');
    }
    this.transactionService.downloadUsiCsv({
      startDate: this.fromDate || undefined,
      endDate: this.toDate || undefined
    }).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `paid-transactions-${new Date().toISOString().slice(0, 10)}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.showToast('Paid transactions CSV downloaded', 'success');
      },
      error: () => this.showToast('Failed to download CSV', 'danger')
    });
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

  /** Human-readable collection type (how the customer paid in) for regular transactions. */
  collectionLabel(txn: any): string {
    switch (txn?.paymentMethodType) {
      case 'CARD': return 'Trust Payments';
      case 'OPEN_BANKING':
      case 'BANK_TRANSFER': return 'Bank';
      case 'AGENT_CASH': return 'Cash';
      case 'WALLET': return 'Wallet';
      default: return txn?.paymentMethodType || '—';
    }
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
    if (this.activeTab === 'PAYIN') {
      this.exportPayinCsv();
      return;
    }
    // Fetch EVERY row matching the current status + date-range + search filters (not just
    // the visible page), then build the CSV client-side.
    this.showToast('Preparing export…', 'primary');
    const size = this.totalElements && this.totalElements > 0 ? this.totalElements : 10000;
    this.transactionService.getAllTransactions(0, size, {
      status: this.statusFilter || undefined,
      search: this.searchQuery || undefined,
      startDate: this.fromDate || undefined,
      endDate: this.toDate || undefined
    }).subscribe({
      next: (res: any) => {
        const data = res?.data || res;
        const rows: TransactionResponse[] = data?.content || data || [];
        if (!rows.length) { this.showToast('No transactions match the current filters', 'warning'); return; }
        const headers = ['Transaction ID', 'Reference', 'Payout Reference', 'Status',
          'Created At', 'Last Updated', 'Sender Name', 'Sender ID',
          'Beneficiary Name', 'Beneficiary ID', 'Corridor',
          'Send Amount', 'Send Currency', 'Receive Amount', 'Receive Currency',
          'Exchange Rate', 'Applied Rate', 'Fee Amount', 'Total Debit',
          'Delivery Method', 'Payment Method', 'Risk Score'];
        const lines = rows.map(t => [
          t.id, t.referenceNumber, t.payoutReference, t.status,
          this.csvDate(t.createdAt), this.csvDate(t.updatedAt), t.senderName, t.senderId,
          t.beneficiaryName, t.beneficiaryId,
          (t.sendCurrency && t.receiveCurrency) ? `${t.sendCurrency}->${t.receiveCurrency}` : '',
          t.sendAmount, t.sendCurrency, t.receiveAmount, t.receiveCurrency,
          t.exchangeRate, t.appliedRate, t.feeAmount, t.totalDebitAmount,
          t.deliveryMethod, t.paymentMethodType, t.riskScore
        ].map(v => this.csvCell(v)).join(','));
        const csv = [headers.join(','), ...lines].join('\r\n');
        this.downloadCsv(csv, this.exportFileName('transactions'));
        this.showToast(`Exported ${rows.length} transaction(s)`, 'success');
      },
      error: () => this.showToast('Export failed. Please try again.', 'danger')
    });
  }

  private exportPayinCsv(): void {
    const rows = this.payinFiltered || [];
    if (!rows.length) { this.showToast('No pay-in transactions to export', 'warning'); return; }
    const headers = ['Transaction ID', 'Reference', 'Customer ID', 'External Ref', 'Amount',
      'Currency', 'Receive Amount', 'Receive Currency', 'Delivery Method', 'Payment Mode',
      'Status', 'Source', 'Created At'];
    const lines = rows.map(t => [
      t.transactionId, t.referenceNumber, t.customerId, t.externalReferenceId, t.amount,
      t.currency, t.receiveAmount, t.receiveCurrency, t.deliveryMethod, t.paymentMode,
      t.status, t.customerSource, this.csvDate(t.createdAt)
    ].map(v => this.csvCell(v)).join(','));
    const csv = [headers.join(','), ...lines].join('\r\n');
    this.downloadCsv(csv, this.exportFileName('payin-transactions'));
    this.showToast(`Exported ${rows.length} pay-in transaction(s)`, 'success');
  }

  /** Normalise a timestamp to ISO 8601 (UTC) for client reconciliation; blank if unparseable. */
  private csvDate(v: any): string {
    if (!v) return '';
    const d = new Date(v);
    return isNaN(d.getTime()) ? String(v) : d.toISOString();
  }

  /** Escape a value for CSV: wrap in quotes if it contains a comma, quote or newline. */
  private csvCell(v: any): string {
    if (v === null || v === undefined) return '';
    const s = String(v);
    return /[",\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }

  /** Filename that records the active filters (status + selected date range). */
  private exportFileName(prefix: string): string {
    const parts = [prefix];
    if (this.activeTab === 'REGULAR') {
      if (this.statusFilter) parts.push(this.statusFilter);
      if (this.fromDate) parts.push('from-' + this.fromDate);
      if (this.toDate) parts.push('to-' + this.toDate);
    }
    return parts.join('_') + '.csv';
  }

  private downloadCsv(csv: string, filename: string): void {
    // Prepend a UTF-8 BOM so Excel opens it with correct encoding.
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
