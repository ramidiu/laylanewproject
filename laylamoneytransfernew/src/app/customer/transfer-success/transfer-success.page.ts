import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-transfer-success',
  templateUrl: './transfer-success.page.html',
  styleUrls: ['./transfer-success.page.scss']
})
export class TransferSuccessPage implements OnInit {
  referenceNumber = '';
  sendAmount = '';
  sendCurrency = '';
  receiveAmount = '';
  receiveCurrency = '';
  recipientName = '';
  fee = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const params = this.route.snapshot.queryParams;
    this.referenceNumber = params['ref'] || '';
    this.sendAmount = params['sendAmount'] || '';
    this.sendCurrency = params['sendCurrency'] || '';
    this.receiveAmount = params['receiveAmount'] || '';
    this.receiveCurrency = params['receiveCurrency'] || '';
    this.recipientName = params['recipient'] || '';
    this.fee = params['fee'] || '';

    if (!this.referenceNumber) {
      this.router.navigate(['/home/dashboard']);
    }
  }

  goToTransactions(): void {
    this.router.navigate(['/home/transactions']);
  }

  sendAnother(): void {
    this.router.navigate(['/home/send']);
  }

  goToDashboard(): void {
    this.router.navigate(['/home/dashboard']);
  }
}
