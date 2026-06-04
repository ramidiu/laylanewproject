import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-trust-callback',
  templateUrl: './trust-callback.page.html',
  styleUrls: ['./trust-callback.page.scss']
})
export class TrustCallbackPage implements OnInit {

  isSuccess = false;
  isLoading = true;

  errorCode: string | null = null;
  orderReference: string | null = null;
  transactionReference: string | null = null;
  settleStatus: string | null = null;

  txnRef = '';
  sendAmount = 0;
  sendCurrency = '';
  receiveAmount = 0;
  receiveCurrency = '';
  recipientName = '';
  fee = 0;
  exchangeRate = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    // Read stored transaction data (written by send-money page before redirect)
    try {
      const stored = localStorage.getItem('layla_last_txn');
      if (stored) {
        const d = JSON.parse(stored);
        this.txnRef        = d.ref           || '';
        this.sendAmount    = d.sendAmount    || 0;
        this.sendCurrency  = d.sendCurrency  || 'GBP';
        this.receiveAmount = d.receiveAmount || 0;
        this.receiveCurrency = d.receiveCurrency || '';
        this.recipientName = d.recipientName || '';
        this.fee           = d.fee           || 0;
        this.exchangeRate  = d.exchangeRate  || 0;
      }
    } catch {}

    this.route.queryParamMap.subscribe(params => {
      // Trust Payments uses 'errorcode' (not 'code') in redirect params
      this.errorCode           = params.get('errorcode');
      this.orderReference      = params.get('orderreference');
      this.transactionReference = params.get('transactionreference');
      this.settleStatus        = params.get('settlestatus');

      // Build payload for backend confirm call
      const payload: Record<string, string> = { referenceNumber: this.txnRef };
      params.keys.forEach(key => {
        payload[key] = params.get(key) || '';
      });

      this.confirmWithBackend(payload);
    });
  }

  private confirmWithBackend(payload: Record<string, string>): void {
    this.isLoading = true;
    this.http.post<{ success: boolean; referenceNumber: string }>(
      `${environment.apiUrl}/trust-payment/confirm`, payload
    ).subscribe({
      next: (res) => {
        this.isSuccess = res.success;
        this.isLoading = false;
        if (this.isSuccess) {
          // Clear stored txn so a back-navigation doesn't re-show success
          localStorage.removeItem('layla_last_txn');
        }
      },
      error: () => {
        // Fallback: use errorcode directly if backend is unreachable
        this.isSuccess = this.errorCode === '0';
        this.isLoading = false;
      }
    });
  }

  goToTransactions(): void {
    this.router.navigate(['/home/transactions']);
  }

  goToHome(): void {
    this.router.navigate(['/home/dashboard']);
  }

  tryAgain(): void {
    this.router.navigate(['/home/send']);
  }
}
