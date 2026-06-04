import { Component, OnInit } from '@angular/core';
import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { TransactionService } from '../../core/services/transaction.service';
import { BeneficiaryService } from '../../core/services/beneficiary.service';
import { FxService } from '../../core/services/fx.service';
import { UserService } from '../../core/services/user.service';
import { WalletService, WalletInfo } from '../../core/services/wallet.service';
import { ReferralService, ReferralCode } from '../../core/services/referral.service';
// Code added by Naresh: Veriff module removed — import deleted
import { TransactionResponse } from '../../core/models/transaction.model';
import { BeneficiaryResponse } from '../../core/models/beneficiary.model';
import { FxRateResponse } from '../../core/models/fx.model';
import { UserResponse } from '../../core/models/user.model';

export interface MonthlyData {
  month: string;
  amount: number;
  count: number;
}

@Component({
  selector: 'app-home',
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
  providers: [DatePipe, DecimalPipe, TitleCasePipe]
})
export class HomePage implements OnInit {
  greeting = '';
  fullName = '';
  userProfile: UserResponse | null = null;

  recentTransactions: TransactionResponse[] = [];
  allTransactions: TransactionResponse[] = [];
  beneficiaries: BeneficiaryResponse[] = [];
  rates: FxRateResponse[] = [];

  stats = { totalSent: 0, count: 0, pending: 0, recipients: 0 };
  monthlyData: MonthlyData[] = [];
  maxMonthlyAmount = 1;

  selectedTransaction: TransactionResponse | null = null;
  showReceiptModal = false;
  copiedRef = '';

  // Wallet
  wallet: WalletInfo | null = null;

  // Referral
  referralCode: ReferralCode | null = null;

  showReferralModal = false;
  referralCopied = false;

  loading = true;

  constructor(
    private authService: AuthService,
    private transactionService: TransactionService,
    private beneficiaryService: BeneficiaryService,
    private fxService: FxService,
    private userService: UserService,
    private walletService: WalletService,
    private referralService: ReferralService,
    private router: Router,
    private datePipe: DatePipe,
    private decimalPipe: DecimalPipe,
    private titleCasePipe: TitleCasePipe
  ) {}

  ngOnInit(): void {
    this.greeting = this.getGreeting();
    const jwt = this.authService.getCurrentUser();
    this.fullName = jwt?.email?.split('@')[0] || 'User';
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.loading = true;

    forkJoin({
      profile: this.userService.getProfile().pipe(catchError(() => of(null))),
      transactions: this.transactionService.getRecent(100).pipe(catchError(() => of([]))),
      beneficiaries: this.beneficiaryService.list().pipe(catchError(() => of([]))),
      rates: this.fxService.getRates().pipe(catchError(() => of([]))),
      wallet: this.walletService.getWallet().pipe(catchError(() => of(null))),
      referralCode: this.referralService.getMyCode().pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ profile, transactions, beneficiaries, rates, wallet, referralCode }) => {
        if (profile) {
          this.userProfile = profile;
          this.fullName = `${profile.firstName} ${profile.lastName}`.trim();
        }
        this.allTransactions = transactions;
        this.recentTransactions = transactions.slice(0, 5);
        this.beneficiaries = beneficiaries;
        this.rates = rates;
        this.wallet = wallet;
        this.referralCode = referralCode;
        this.computeStats();
        this.computeAnalytics();
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  private computeStats(): void {
    const completedStatuses = ['COMPLETED', 'PAID', 'DELIVERED'];
    const pendingStatuses = ['PENDING', 'PROCESSING', 'CREATED', 'FUNDS_RECEIVED', 'SENT_TO_PAYOUT'];
    const completed = this.allTransactions.filter(t => completedStatuses.includes(t.status));
    const pending = this.allTransactions.filter(t => pendingStatuses.includes(t.status));
    this.stats = {
      totalSent: completed.reduce((sum, t) => sum + (t.sendAmount || 0), 0),
      count: this.allTransactions.length,
      pending: pending.length,
      recipients: this.beneficiaries.length
    };
  }

  private computeAnalytics(): void {
    const now = new Date();
    const months: MonthlyData[] = [];
    for (let i = 5; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      months.push({ month: d.toLocaleString('default', { month: 'short' }), amount: 0, count: 0 });
    }
    this.allTransactions.forEach(txn => {
      if (!txn.createdAt) return;
      const txnDate = new Date(txn.createdAt);
      const monthsAgo = (now.getFullYear() - txnDate.getFullYear()) * 12 + (now.getMonth() - txnDate.getMonth());
      if (monthsAgo >= 0 && monthsAgo <= 5) {
        const idx = 5 - monthsAgo;
        months[idx].amount += txn.sendAmount || 0;
        months[idx].count++;
      }
    });
    this.monthlyData = months;
    this.maxMonthlyAmount = Math.max(...months.map(m => m.amount), 1);
  }

  getBarHeight(amount: number): string {
    const pct = Math.round((amount / this.maxMonthlyAmount) * 100);
    return `${Math.max(pct, amount > 0 ? 4 : 0)}%`;
  }

  handleRefresh(event: any): void {
    this.loadDashboard();
    setTimeout(() => event.target.complete(), 1500);
  }

  goToSend(beneficiaryId?: string): void {
    const extras = beneficiaryId ? { queryParams: { beneficiaryId } } : {};
    this.router.navigate(['/home/send'], extras);
  }

  goToTransaction(id: string): void {
    this.router.navigate(['/home/transactions', id]);
  }

  openReceipt(txn: TransactionResponse, event: Event): void {
    event.stopPropagation();
    this.selectedTransaction = txn;
    this.showReceiptModal = true;
  }

  closeReceipt(): void {
    this.showReceiptModal = false;
    setTimeout(() => (this.selectedTransaction = null), 300);
  }

  downloadReceipt(): void {
    if (!this.selectedTransaction) return;
    const t = this.selectedTransaction;

    const fmt = (v: number) => this.decimalPipe.transform(v, '1.2-2') || '0.00';
    const fmtRate = (v: number) => this.decimalPipe.transform(v, '1.2-4') || '0.0000';
    const date = this.datePipe.transform(t.createdAt, 'EEEE, MMMM d, y · h:mm a') || '';
    const delivery = this.titleCasePipe.transform(t.deliveryMethod) || t.deliveryMethod;

    const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Receipt - ${t.referenceNumber}</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #1f2937; padding: 40px; max-width: 600px; margin: 0 auto; }
  .brand { text-align: center; font-size: 20px; font-weight: 700; color: #1B3571; margin-bottom: 24px; }
  .ref-box { background: #f8f9fb; border-radius: 12px; text-align: center; padding: 20px; margin-bottom: 24px; }
  .ref-box .ref { font-size: 18px; font-weight: 700; color: #1B3571; font-family: monospace; }
  .ref-box .date { font-size: 13px; color: #6b7280; margin-top: 4px; }
  .ref-box .status { display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; text-transform: uppercase; margin-bottom: 8px; background: #fef3c7; color: #92400e; }
  .transfer { display: flex; align-items: center; justify-content: space-between; padding: 20px 0; border-bottom: 1px solid #e5e7eb; }
  .transfer .side { text-align: center; flex: 1; }
  .transfer .label { font-size: 11px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.05em; }
  .transfer .amount { font-size: 24px; font-weight: 700; color: #1B3571; font-family: monospace; }
  .transfer .receive { color: #059669; }
  .transfer .cur { font-size: 13px; color: #6b7280; margin-left: 4px; }
  .transfer .arrow { color: #9ca3af; font-size: 20px; }
  .section-title { font-size: 15px; font-weight: 700; color: #1B3571; padding: 16px 0 12px; border-bottom: 1px solid #e5e7eb; }
  .row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f3f4f6; font-size: 14px; }
  .row .rl { color: #6b7280; }
  .row .rv { font-weight: 500; text-align: right; }
  .row.total { border-top: 2px solid #e5e7eb; border-bottom: none; margin-top: 4px; padding-top: 12px; }
  .row.total .rl { font-weight: 700; color: #1B3571; }
  .row.total .rv { font-weight: 700; color: #059669; font-size: 15px; }
  .note { display: flex; align-items: center; gap: 10px; background: #ecfdf5; border-radius: 8px; padding: 14px; margin-top: 24px; font-size: 13px; color: #065f46; }
  @media print { body { padding: 20px; } }
</style></head><body>
<div class="brand">Layla Money Transfer — Transaction Receipt</div>
<div class="ref-box">
  <div class="status">${t.status}</div>
  <div class="ref">${t.referenceNumber}</div>
  <div class="date">${date}</div>
</div>
<div class="transfer">
  <div class="side"><div class="label">You Sent</div><div class="amount">${fmt(t.sendAmount)}<span class="cur">${t.sendCurrency}</span></div></div>
  <div class="arrow">&rarr;</div>
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
  <span>&#x2705; This transfer was processed securely by Layla Money Transfer. Keep this receipt for your records.</span>
</div>
</body></html>`;

    // Primary: download the backend-rendered branded PDF receipt (the Layla receipt design).
    this.transactionService.downloadReceipt(t.id!).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `receipt-${t.referenceNumber || t.id}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => {
        // Fallback: client-side print view if the backend PDF can't be fetched.
        const w = window.open('', '_blank');
        if (w) {
          w.document.write(html);
          w.document.close();
          setTimeout(() => w.print(), 300);
        }
      }
    });
  }

  async copyReference(ref: string, event: Event): Promise<void> {
    event.stopPropagation();
    try {
      await navigator.clipboard.writeText(ref);
      this.copiedRef = ref;
      setTimeout(() => (this.copiedRef = ''), 2000);
    } catch { /* fallback silently */ }
  }

  inviteFriend(): void {
    this.showReferralModal = true;
  }

  closeReferralModal(): void {
    this.showReferralModal = false;
    this.referralCopied = false;
  }

  async copyReferralCode(): Promise<void> {
    if (!this.referralCode) return;
    try {
      await navigator.clipboard.writeText(this.referralCode.code);
      this.referralCopied = true;
      setTimeout(() => (this.referralCopied = false), 2000);
    } catch { /* fallback silently */ }
  }

  shareViaWhatsApp(): void {
    const code = this.referralCode?.code || '';
    const msg = encodeURIComponent(
      `Join Layla Money Transfer — send money internationally with great rates!${code ? ` Use my referral code *${code}* when you sign up and get a rate boost on your first transfer.` : ''} Download: https://layla.money`
    );
    const url = `https://web.whatsapp.com/send?text=${msg}`;
    // Open in new window with specific dimensions to avoid popup blocker
    const win = window.open(url, 'whatsapp_share', 'width=800,height=600,scrollbars=yes');
    if (!win) {
      // Fallback: direct navigation
      window.location.href = url;
    }
  }

  goToWallet(): void {
    this.router.navigate(['/home/wallet']);
  }

  getInitials(name: string): string {
    if (!name) return '??';
    return name.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2);
  }

  private getGreeting(): string {
    const h = new Date().getHours();
    if (h < 12) return 'HOME.GREETING_MORNING';
    if (h < 17) return 'HOME.GREETING_AFTERNOON';
    return 'HOME.GREETING_EVENING';
  }
}
