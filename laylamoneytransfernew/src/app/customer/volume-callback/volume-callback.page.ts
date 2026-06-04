import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-volume-callback',
  templateUrl: './volume-callback.page.html',
  styleUrls: ['./volume-callback.page.scss']
})
export class VolumeCallbackPage implements OnInit {
  paymentId: string | null = null;
  merchantPaymentId: string | null = null;
  amount: string | null = null;
  currency: string | null = null;
  status: string | null = null;

  txnRef = '';
  sendAmount = 0;
  sendCurrency = '';
  receiveAmount = 0;
  receiveCurrency = '';
  recipientName = '';
  fee = 0;
  exchangeRate = 0;

  get isSuccess(): boolean {
    return this.status?.toUpperCase() === 'COMPLETED';
  }

  get isPending(): boolean {
    const s = this.status?.toUpperCase();
    return s === 'PENDING' || s === 'PROCESSING';
  }

  constructor(private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      this.paymentId = params.get('paymentId');
      this.merchantPaymentId = params.get('merchantPaymentId');
      this.amount = params.get('amount');
      this.currency = params.get('currency');
      this.status = params.get('status');
    });

    try {
      const stored = localStorage.getItem('layla_last_txn');
      if (stored) {
        const d = JSON.parse(stored);
        this.txnRef = d.ref || '';
        this.sendAmount = d.sendAmount || 0;
        this.sendCurrency = d.sendCurrency || this.currency || 'GBP';
        this.receiveAmount = d.receiveAmount || 0;
        this.receiveCurrency = d.receiveCurrency || '';
        this.recipientName = d.recipientName || '';
        this.fee = d.fee || 0;
        this.exchangeRate = d.exchangeRate || 0;
      }
    } catch {}
  }

  goToTransactions(): void {
    this.router.navigate(['/home/transactions']);
  }

  goToHome(): void {
    this.router.navigate(['/home/dashboard']);
  }
}
