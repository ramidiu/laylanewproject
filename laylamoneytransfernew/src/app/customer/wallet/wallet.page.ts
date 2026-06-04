import { Component, OnInit } from '@angular/core';
import { WalletService, WalletInfo } from '../../core/services/wallet.service';

@Component({
  selector: 'app-wallet',
  templateUrl: './wallet.page.html',
  styleUrls: ['./wallet.page.scss']
})
export class WalletPage implements OnInit {
  wallet: WalletInfo | null = null;
  transactions: any[] = [];
  loading = true;

  totalCredit = 0;
  totalDebit = 0;

  constructor(private walletService: WalletService) {}

  ngOnInit(): void {
    this.loadWallet();
  }

  loadWallet(): void {
    this.loading = true;
    this.walletService.getWallet().subscribe({
      next: (w) => {
        this.wallet = w;
        this.loadTransactions();
      },
      error: () => {
        this.wallet = null;
        this.loading = false;
      }
    });
  }

  loadTransactions(): void {
    this.walletService.getTransactions().subscribe({
      next: (txns) => {
        this.transactions = txns;
        this.computeTotals();
        this.loading = false;
      },
      error: () => {
        this.transactions = [];
        this.loading = false;
      }
    });
  }

  private computeTotals(): void {
    this.totalCredit = 0;
    this.totalDebit = 0;
    for (const txn of this.transactions) {
      const amount = Math.abs(txn.amount || 0);
      if (this.isCredit(txn)) {
        this.totalCredit += amount;
      } else {
        this.totalDebit += amount;
      }
    }
  }

  isCredit(txn: any): boolean {
    const type = (txn.type || '').toUpperCase();
    return type.includes('CREDIT') || type.includes('TOPUP') || type.includes('TOP_UP')
        || type.includes('REFUND') || type.includes('REWARD') || type.includes('REFERRAL')
        || (txn.amount > 0);
  }

  handleRefresh(event: any): void {
    this.loadWallet();
    setTimeout(() => event.target.complete(), 1500);
  }
}
