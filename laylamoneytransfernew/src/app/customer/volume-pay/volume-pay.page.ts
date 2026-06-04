import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

declare global {
  interface Window { Volume: any; }
}

@Component({
  selector: 'app-volume-pay',
  templateUrl: './volume-pay.page.html',
  styleUrls: ['./volume-pay.page.scss']
})
export class VolumePayPage implements OnInit, OnDestroy {
  transactionId: string = '';
  amount: number = 0;
  currency: string = 'GBP';
  recipientName: string = '';
  loading = true;
  sdkReady = false;
  merchantPaymentId: string = '';

  private scriptEl: HTMLScriptElement | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.transactionId = params['transactionId'] || '';
      this.amount = Number(params['amount'] || 0);
      this.currency = params['currency'] || 'GBP';
      this.recipientName = params['recipientName'] || '';
      this.merchantPaymentId = this.generateMerchantPaymentId();
      this.initVolume();
    });
  }

  ngOnDestroy(): void {
    if (this.scriptEl && this.scriptEl.parentNode) {
      this.scriptEl.parentNode.removeChild(this.scriptEl);
    }
  }

  private initVolume(): void {
    this.http.post<any>(`${environment.apiUrl}/volume/payment-intent`, {
      transactionId: this.transactionId,
      merchantPaymentId: this.merchantPaymentId,
      amount: this.amount,
      currency: this.currency
    }).subscribe({
      next: (res) => {
        this.loading = false;
        this.loadSdk(res.jsUrl, res.applicationId, res.environment);
      },
      error: (err) => {
        this.loading = false;
        this.showToast(err?.error?.message || 'Failed to initialise payment. Please try again.', 'danger');
      }
    });
  }

  private loadSdk(jsUrl: string, applicationId: string, environment: string): void {
    if (window.Volume) {
      this.setupVolume(applicationId, environment);
      return;
    }
    this.scriptEl = document.createElement('script');
    this.scriptEl.src = jsUrl || 'https://js.volumepay.io';
    this.scriptEl.onload = () => this.setupVolume(applicationId, environment);
    this.scriptEl.onerror = () => this.showToast('Could not load payment SDK.', 'danger');
    document.head.appendChild(this.scriptEl);
  }

  private setupVolume(applicationId: string, env: string): void {
    const volume = new window.Volume({
      environment: env || 'SANDBOX',
      applicationId: applicationId,
      agentType: 'WEB_BROWSER',
      isWebView: false,
      eventConsumer: (event: any) => console.log('Volume event:', event),
      errorConsumer: (error: any) => {
        console.error('Volume error:', error);
        this.showToast('Payment error. Please try again.', 'danger');
      }
    });

    volume.createPayment({
      amount: this.amount,
      merchantPaymentId: this.merchantPaymentId,
      paymentReference: this.transactionId,
      agentType: 'WEB_BROWSER'
    });

    volume.injectComponent('volume-element-container');
    this.sdkReady = true;
  }

  cancelPayment(): void {
    this.router.navigate(['/home/send']);
  }

  private generateMerchantPaymentId(): string {
    let id = '';
    for (let i = 0; i < 18; i++) {
      id += Math.floor(Math.random() * 10).toString();
    }
    return id;
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 4000, position: 'top', color,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
