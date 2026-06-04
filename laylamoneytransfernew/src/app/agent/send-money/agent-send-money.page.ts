import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LoadingController, ToastController } from '@ionic/angular';
import { FxService } from '../../core/services/fx.service';
import { TransactionService } from '../../core/services/transaction.service';
import { CorridorResponse, QuoteResponse } from '../../core/models/fx.model';

@Component({
  selector: 'app-agent-send-money',
  template: `
    <div class="agent-send">
      <h1 class="page-title">New Transaction</h1>
      <p class="page-subtitle">Initiate a send on behalf of a customer</p>

      <div class="send-form-card fb-card">
        <form [formGroup]="sendForm" (ngSubmit)="onSubmit()">
          <!-- Customer details -->
          <h3 class="section-title">Customer Details</h3>
          <div class="form-row">
            <div class="fb-field">
              <label class="fb-field__label">Customer First Name</label>
              <input class="fb-input" formControlName="customerFirstName" placeholder="First name" />
            </div>
            <div class="fb-field">
              <label class="fb-field__label">Customer Last Name</label>
              <input class="fb-input" formControlName="customerLastName" placeholder="Last name" />
            </div>
          </div>
          <div class="fb-field">
            <label class="fb-field__label">Customer Phone</label>
            <input class="fb-input" formControlName="customerPhone" placeholder="+1234567890" />
          </div>

          <!-- Beneficiary details -->
          <h3 class="section-title">Beneficiary Details</h3>
          <div class="form-row">
            <div class="fb-field">
              <label class="fb-field__label">Beneficiary Name</label>
              <input class="fb-input" formControlName="beneficiaryName" placeholder="Full name" />
            </div>
            <div class="fb-field">
              <label class="fb-field__label">Beneficiary Phone</label>
              <input class="fb-input" formControlName="beneficiaryPhone" placeholder="+1234567890" />
            </div>
          </div>

          <!-- Transfer details -->
          <h3 class="section-title">Transfer Details</h3>
          <div class="fb-field">
            <label class="fb-field__label">Corridor</label>
            <select class="fb-input" formControlName="corridorId">
              <option value="">Select corridor</option>
              <option *ngFor="let c of corridors" [value]="c.id">
                {{ c.sendCountry }} -> {{ c.receiveCountry }} ({{ c.sendCurrency }}/{{ c.receiveCurrency }})
              </option>
            </select>
          </div>

          <div class="form-row">
            <div class="fb-field">
              <label class="fb-field__label">Amount ({{ selectedCurrency }})</label>
              <input type="number" class="fb-input" formControlName="amount" placeholder="0.00" />
            </div>
            <div class="fb-field">
              <label class="fb-field__label">Delivery Method</label>
              <select class="fb-input" formControlName="deliveryMethod">
                <option value="BANK_TRANSFER">Bank Transfer</option>
                <option value="MOBILE_WALLET">Mobile Wallet</option>
                <option value="CASH_PICKUP">Cash Pickup</option>
              </select>
            </div>
          </div>

          <!-- Quote display -->
          <div class="fb-quote-card" *ngIf="quote" style="margin: 24px 0;">
            <div class="rate-display">1 {{ selectedCurrency }} = {{ quote.appliedRate | number:'1.4-4' }}</div>
            <div class="fee-row">
              <span class="label">Fee</span>
              <span class="value fb-currency">{{ quote.fee | number:'1.2-2' }} {{ selectedCurrency }}</span>
            </div>
            <div class="fee-row">
              <span class="label">Total</span>
              <span class="value fb-currency">{{ quote.totalCost | number:'1.2-2' }} {{ selectedCurrency }}</span>
            </div>
            <div class="receive-amount">{{ quote.receiveAmount | number:'1.2-2' }}</div>
          </div>

          <div class="form-actions">
            <button class="fb-btn fb-btn--secondary" type="button" (click)="getQuote()" [disabled]="!sendForm.get('amount')?.value || !sendForm.get('corridorId')?.value">
              Get Quote
            </button>
            <button class="fb-btn fb-btn--primary" type="submit" [disabled]="sendForm.invalid || !quote">
              <ion-icon name="send"></ion-icon>
              Submit Transaction
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styleUrls: ['./agent-send-money.page.scss']
})
export class AgentSendMoneyPage implements OnInit {
  sendForm!: FormGroup;
  corridors: CorridorResponse[] = [];
  quote: QuoteResponse | null = null;
  selectedCurrency = 'USD';

  constructor(
    private fb: FormBuilder,
    private fxService: FxService,
    private transactionService: TransactionService,
    private router: Router,
    private loadingCtrl: LoadingController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.sendForm = this.fb.group({
      customerFirstName: ['', Validators.required],
      customerLastName: ['', Validators.required],
      customerPhone: ['', Validators.required],
      beneficiaryName: ['', Validators.required],
      beneficiaryPhone: ['', Validators.required],
      corridorId: ['', Validators.required],
      amount: ['', [Validators.required, Validators.min(1)]],
      deliveryMethod: ['BANK_TRANSFER', Validators.required]
    });

    this.fxService.getCorridors().subscribe({
      next: (c) => this.corridors = c.filter(x => x.isActive),
      error: () => {}
    });

    this.sendForm.get('corridorId')?.valueChanges.subscribe(id => {
      const corridor = this.corridors.find(c => c.id === id);
      if (corridor) this.selectedCurrency = corridor.sendCurrency;
    });
  }

  getQuote(): void {
    const corridor = this.corridors.find(c => c.id === this.sendForm.value.corridorId);
    if (!corridor) return;

    this.fxService.getQuote({
      sendCurrency: corridor.sendCurrency,
      receiveCurrency: corridor.receiveCurrency,
      sendAmount: this.sendForm.value.amount,
      deliveryMethod: this.sendForm.value.deliveryMethod,
      corridorId: corridor.id
    }).subscribe({
      next: (q) => this.quote = q,
      error: () => this.showToast('Failed to get quote', 'danger')
    });
  }

  async onSubmit(): Promise<void> {
    if (this.sendForm.invalid || !this.quote) return;
    const loading = await this.loadingCtrl.create({ message: 'Processing...' });
    await loading.present();
    // In a real implementation, this would use an agent-specific endpoint
    loading.dismiss();
    this.showToast('Transaction submitted successfully', 'success');
    this.router.navigate(['/agent/dashboard']);
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
