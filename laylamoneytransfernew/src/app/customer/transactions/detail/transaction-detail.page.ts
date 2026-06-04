import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ToastController } from '@ionic/angular';
import { TransactionService } from '../../../core/services/transaction.service';
import { UserService } from '../../../core/services/user.service';
import { UserResponse } from '../../../core/models/user.model';
import { TransactionResponse, StatusHistoryResponse } from '../../../core/models/transaction.model';
import { BeneficiaryService } from '../../../core/services/beneficiary.service';
import { BeneficiaryResponse } from '../../../core/models/beneficiary.model';
import { generateStripeStyleInvoice } from './invoice-generator';

@Component({
  selector: 'app-transaction-detail',
  templateUrl: './transaction-detail.page.html',
  styleUrls: ['./transaction-detail.page.scss'],
  providers: [DatePipe, DecimalPipe, TitleCasePipe]
})
export class TransactionDetailPage implements OnInit {
  transaction: TransactionResponse | null = null;
  beneficiary: BeneficiaryResponse | null = null;
  history: StatusHistoryResponse[] = [];
  loading = true;
  downloading = false;
  // Code added by Naresh: independent flag so the Invoice button shows its own loading state.
  downloadingInvoice = false;
  // Inline HTML receipt (works inside the mobile React-Native WebView, where PDF/print don't).
  showReceiptModal = false;
  receiptHtml: SafeHtml | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private transactionService: TransactionService,
    private userService: UserService,
    private beneficiaryService: BeneficiaryService,
    private datePipe: DatePipe,
    private decimalPipe: DecimalPipe,
    private titleCasePipe: TitleCasePipe,
    private toastCtrl: ToastController,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/home/transactions']);
      return;
    }
    this.loadTransaction(id);
  }

  private loadTransaction(id: string): void {
    this.transactionService.getById(id).subscribe({
      next: (txn) => {
        this.transaction = txn;
        this.loading = false;
        this.loadHistory(id);
        this.loadBeneficiary(txn.beneficiaryId);
      },
      error: () => {
        this.loading = false;
        this.router.navigate(['/home/transactions']);
      }
    });
  }

  /** Load full beneficiary record so the customer can see account / swift / bank details. */
  private loadBeneficiary(beneficiaryId: any): void {
    if (beneficiaryId === null || beneficiaryId === undefined || beneficiaryId === '') return;
    this.beneficiaryService.getById(String(beneficiaryId)).subscribe({
      next: (b) => (this.beneficiary = b),
      error: () => (this.beneficiary = null)
    });
  }

  private loadHistory(id: string): void {
    this.transactionService.getHistory(id).subscribe({
      next: (h) => this.history = h || [],
      error: () => {}
    });
  }

  goBack(): void {
    this.router.navigate(['/home/transactions']);
  }

  downloadReceipt(): void {
    if (!this.transaction || this.downloading) return;
    this.downloading = true;
    this.transactionService.downloadReceipt(this.transaction.id!).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `receipt-${this.transaction?.referenceNumber || this.transaction?.id}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.downloading = false;
      },
      error: async () => {
        this.downloading = false;
        const toast = await this.toastCtrl.create({
          message: 'Failed to download receipt. Falling back to print view.',
          duration: 3000, position: 'top', color: 'warning',
          buttons: [{ icon: 'close-outline', role: 'cancel' }]
        });
        await toast.present();
        this.printFallback();
      }
    });
  }

  // Code added by Naresh: Stripe-style invoice download. Fetches the current user
  // profile for Billed-To details, then renders a client-side PDF using jsPDF.
  // No backend change; no impact on the existing Download Receipt flow.
  downloadInvoice(): void {
    if (!this.transaction || this.downloadingInvoice) return;
    this.downloadingInvoice = true;

    const txn = this.transaction;
    this.userService.getProfile().subscribe({
      next: (profile: UserResponse) => this.renderInvoice(txn, profile),
      // Missing profile is not fatal — N/A will fill the Billed-To block.
      error: () => this.renderInvoice(txn, null)
    });
  }

  private renderInvoice(txn: TransactionResponse, profile: UserResponse | null): void {
    generateStripeStyleInvoice(txn, profile)
      .then(() => {
        this.downloadingInvoice = false;
      })
      .catch(async (err) => {
        this.downloadingInvoice = false;
        const toast = await this.toastCtrl.create({
          message: `Failed to generate invoice: ${err?.message || 'unknown error'}`,
          duration: 3000, position: 'top', color: 'danger',
          buttons: [{ icon: 'close-outline', role: 'cancel' }]
        });
        await toast.present();
      });
  }

  /** Show the receipt inline (HTML) — renders inside the mobile WebView where PDF/print fail. */
  viewReceipt(): void {
    const inner = this.buildReceiptInnerHtml();
    if (!inner) return;
    this.receiptHtml = this.sanitizer.bypassSecurityTrustHtml(inner);
    this.showReceiptModal = true;
  }

  closeReceipt(): void {
    this.showReceiptModal = false;
  }

  private printFallback(): void {
    const inner = this.buildReceiptInnerHtml();
    if (!inner) return;
    const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>Receipt - ${this.transaction?.referenceNumber}</title></head><body style="max-width:600px;margin:0 auto;padding:40px;">${inner}</body></html>`;
    const w = window.open('', '_blank');
    if (w) { w.document.write(html); w.document.close(); setTimeout(() => w.print(), 300); }
  }

  /** Builds the receipt as a self-contained HTML string (scoped <style> + content). */
  private buildReceiptInnerHtml(): string {
    if (!this.transaction) return '';
    const t = this.transaction;

    const fmt = (v: number) => this.decimalPipe.transform(v, '1.2-2') || '0.00';
    const fmtRate = (v: number) => this.decimalPipe.transform(v, '1.2-4') || '0.0000';
    const date = this.datePipe.transform(t.createdAt, 'EEEE, MMMM d, y · h:mm a') || '';
    const delivery = this.titleCasePipe.transform(t.deliveryMethod) || t.deliveryMethod;

    const html = `
<style>
  .ly-receipt { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #1f2937; max-width: 600px; margin: 0 auto; }
  .ly-receipt * { box-sizing: border-box; }
  .ly-receipt .brand { text-align: center; font-size: 18px; font-weight: 700; color: #1B3571; margin-bottom: 20px; }
  .ly-receipt .ref-box { background: #f8f9fb; border-radius: 12px; text-align: center; padding: 18px; margin-bottom: 20px; }
  .ly-receipt .ref-box .ref { font-size: 17px; font-weight: 700; color: #1B3571; font-family: monospace; }
  .ly-receipt .ref-box .date { font-size: 12px; color: #6b7280; margin-top: 4px; }
  .ly-receipt .ref-box .status { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: 600; text-transform: uppercase; margin-bottom: 8px; background: #fef3c7; color: #92400e; }
  .ly-receipt .transfer { display: flex; align-items: center; justify-content: space-between; padding: 16px 0; border-bottom: 1px solid #e5e7eb; }
  .ly-receipt .transfer .side { text-align: center; flex: 1; }
  .ly-receipt .transfer .label { font-size: 11px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.05em; }
  .ly-receipt .transfer .amount { font-size: 22px; font-weight: 700; color: #1B3571; font-family: monospace; }
  .ly-receipt .transfer .receive { color: #059669; }
  .ly-receipt .transfer .cur { font-size: 12px; color: #6b7280; margin-left: 4px; }
  .ly-receipt .transfer .arrow { color: #9ca3af; font-size: 20px; }
  .ly-receipt .section-title { font-size: 14px; font-weight: 700; color: #1B3571; padding: 16px 0 12px; border-bottom: 1px solid #e5e7eb; }
  .ly-receipt .row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f3f4f6; font-size: 14px; }
  .ly-receipt .row .rl { color: #6b7280; }
  .ly-receipt .row .rv { font-weight: 500; text-align: right; }
  .ly-receipt .row.total { border-top: 2px solid #e5e7eb; border-bottom: none; margin-top: 4px; padding-top: 12px; }
  .ly-receipt .row.total .rl { font-weight: 700; color: #1B3571; }
  .ly-receipt .row.total .rv { font-weight: 700; color: #059669; font-size: 15px; }
  .ly-receipt .note { display: flex; align-items: center; gap: 10px; background: #ecfdf5; border-radius: 8px; padding: 14px; margin-top: 20px; font-size: 13px; color: #065f46; }
  .ly-receipt .note svg { min-width: 18px; }
</style>
<div class="ly-receipt">
<div class="brand">Layla Money Transfer — Transaction Receipt</div>
<div class="ref-box">
  <div class="status">${t.status}</div>
  <div class="ref">${t.referenceNumber}</div>
  <div class="date">${date}</div>
</div>
<div class="transfer">
  <div class="side"><div class="label">You Sent</div><div class="amount">${fmt(t.sendAmount)}<span class="cur">${t.sendCurrency}</span></div></div>
  <div class="arrow">→</div>
  <div class="side"><div class="label">Recipient Gets</div><div class="amount receive">${fmt(t.receiveAmount)}<span class="cur">${t.receiveCurrency}</span></div></div>
</div>
<div class="section-title">Transfer Details</div>
<div class="row"><span class="rl">Recipient</span><span class="rv">${t.beneficiaryName}</span></div>
<div class="row"><span class="rl">Exchange Rate</span><span class="rv" style="font-family:monospace">1 ${t.sendCurrency} = ${fmtRate(t.appliedRate)} ${t.receiveCurrency}</span></div>
<div class="row"><span class="rl">Transfer Fee</span><span class="rv" style="font-family:monospace">${fmt(t.feeAmount)} ${t.sendCurrency}</span></div>
${t.walletAmountUsed ? `<div class="row" style="color:#059669"><span class="rl">Wallet Used</span><span class="rv" style="font-family:monospace">-${fmt(t.walletAmountUsed)} ${t.sendCurrency}</span></div>` : ''}
<div class="row total"><span class="rl">Total Debited</span><span class="rv" style="font-family:monospace">${fmt(t.totalDebitAmount - (t.walletAmountUsed || 0))} ${t.sendCurrency}</span></div>
<div class="row"><span class="rl">Delivery Method</span><span class="rv">${delivery}</span></div>
${t.walletAmountUsed ? `<div class="row"><span class="rl">Wallet Used</span><span class="rv" style="font-family:monospace">${fmt(t.walletAmountUsed)} ${t.sendCurrency}</span></div>` : ''}
${t.referralCodeUsed ? `<div class="row"><span class="rl">Referral Code</span><span class="rv">${t.referralCodeUsed}</span></div>` : ''}
<div class="note">
  <svg width="18" height="18" fill="none" viewBox="0 0 24 24" stroke="#059669" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/></svg>
  <span>This transfer was processed securely by Layla Money Transfer. Keep this receipt for your records.</span>
</div>
</div>`;

    return html;
  }
}
