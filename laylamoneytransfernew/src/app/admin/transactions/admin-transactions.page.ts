import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular';
import { TransactionService } from '../../core/services/transaction.service';
import { TransactionResponse } from '../../core/models/transaction.model';

@Component({
  selector: 'app-admin-transactions',
  templateUrl: './admin-transactions.page.html',
  styleUrls: ['./admin-transactions.page.scss']
})
export class AdminTransactionsPage implements OnInit {
  transactions: TransactionResponse[] = [];
  loading = true;
  searchQuery = '';
  // Default to the actionable PROCESSING view; "All Status" shows everything.
  statusFilter = 'PROCESSING';
  fromDate = '';
  toDate = '';
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  pageSize = 20;

  stats: any = {};
  statsLoading = true;

  statuses = ['CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED','ARCHIVED'];

  constructor(
    private transactionService: TransactionService,
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
  }

  loadStats(): void {
    this.statsLoading = true;
    this.transactionService.getTransactionStats().subscribe({
      next: (res: any) => {
        this.stats = res.data || res;
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

  async adminRelease(txn: TransactionResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Release from Compliance',
      message: `Release transaction ${txn.referenceNumber} from compliance hold back to PENDING?`,
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Reason / note (optional)' }],
      buttons: [
        { text: 'No', role: 'cancel' },
        {
          text: 'Yes, Release',
          handler: (data) => {
            this.transactionService.adminReleaseCompliance(txn.id as any, data.reason).subscribe({
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

  exportTransactions(): void {
    this.showToast('Export functionality - integrate with CSV export endpoint', 'primary');
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
