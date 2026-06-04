import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { TransactionService } from '../../../core/services/transaction.service';
import { TransactionResponse, StatusHistoryResponse } from '../../../core/models/transaction.model';

@Component({
  selector: 'app-partner-transaction-detail',
  template: `
    <div class="partner-txn-detail" *ngIf="transaction">
      <ion-button fill="clear" routerLink="/partner/dashboard" color="primary">
        <ion-icon name="arrow-back" slot="start"></ion-icon>
        Back to Dashboard
      </ion-button>

      <div class="fb-card">
        <div class="detail-header">
          <div>
            <h2>{{ transaction.referenceNumber }}</h2>
            <app-status-chip [status]="transaction.status"></app-status-chip>
          </div>
        </div>

        <div class="detail-grid">
          <div class="detail-item">
            <span class="label">Beneficiary</span>
            <span class="value">{{ transaction.beneficiaryName }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Payout Amount</span>
            <span class="value fb-currency">{{ transaction.receiveAmount | number:'1.2-2' }} {{ transaction.receiveCurrency }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Delivery Method</span>
            <span class="value">{{ transaction.deliveryMethod }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Created</span>
            <span class="value">{{ transaction.createdAt | date:'MMM d, y HH:mm' }}</span>
          </div>
        </div>

        <div class="detail-actions" *ngIf="transaction.status === 'SENT_TO_PAYOUT'">
          <button class="fb-btn fb-btn--primary" (click)="markAsPaid()">
            <ion-icon name="checkmark-circle"></ion-icon>
            Mark as Paid
          </button>
        </div>
      </div>

      <!-- Timeline -->
      <div class="fb-card" *ngIf="history.length > 0">
        <h3 class="section-title">Status History</h3>
        <div class="fb-timeline">
          <div *ngFor="let entry of history; let i = index"
            class="fb-timeline-item"
            [ngClass]="{ 'completed': i < history.length - 1, 'current': i === history.length - 1 }">
            <div class="fb-timeline-item-dot"></div>
            <div>
              <div class="timeline-status">{{ entry.toStatus }}</div>
              <div class="timeline-time">{{ entry.createdAt | date:'MMM d, y HH:mm' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./partner-transaction-detail.page.scss']
})
export class PartnerTransactionDetailPage implements OnInit {
  transaction: TransactionResponse | null = null;
  history: StatusHistoryResponse[] = [];

  constructor(
    private route: ActivatedRoute,
    private transactionService: TransactionService,
    private http: HttpClient,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.transactionService.getById(id).subscribe(txn => this.transaction = txn);
      this.transactionService.getHistory(id).subscribe(h => this.history = h);
    }
  }

  markAsPaid(): void {
    if (!this.transaction) return;
    this.http.post(`${environment.apiUrl}/transactions/${this.transaction.id}/status`, { status: 'PAID' }).subscribe({
      next: () => {
        this.showToast('Marked as paid', 'success');
        this.transaction!.status = 'PAID' as any;
      },
      error: () => this.showToast('Failed', 'danger')
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
