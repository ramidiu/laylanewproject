import { Component, OnInit } from '@angular/core';
import { ModalController, ToastController } from '@ionic/angular';
import { SettlementService } from '../../core/services/settlement.service';

@Component({
  selector: 'app-payin-partner-settlements',
  template: `
    <div class="payin-settlements">
      <div class="page-header">
        <div>
          <h1 class="page-title">Settlements</h1>
          <p class="page-subtitle">Manage pay-in settlements to admin</p>
        </div>
        <ion-button fill="solid" color="primary" (click)="openSubmitModal()">
          <ion-icon name="add-circle-outline" slot="start"></ion-icon>
          Submit Settlement
        </ion-button>
      </div>

      <div class="fb-card table-card">
        <div class="table-toolbar">
          <div class="table-toolbar__count">
            <ion-badge color="primary">{{ settlements.length }}</ion-badge>
            <span>settlements</span>
          </div>
          <ion-button fill="clear" size="small" (click)="loadData()">
            <ion-icon name="refresh-outline" slot="start"></ion-icon>
            Refresh
          </ion-button>
        </div>

        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>

        <div class="table-wrapper" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Amount</th>
                <th>Currency</th>
                <th>Status</th>
                <th>Reference</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let s of settlements">
                <td>{{ s.id }}</td>
                <td class="fb-currency">{{ s.amount | number:'1.2-2' }}</td>
                <td>{{ s.currency }}</td>
                <td>
                  <span class="settlement-status"
                    [class.settlement-status--pending]="s.status === 'PENDING'"
                    [class.settlement-status--approved]="s.status === 'APPROVED'"
                    [class.settlement-status--rejected]="s.status === 'REJECTED'"
                    [class.settlement-status--completed]="s.status === 'COMPLETED'">
                    {{ s.status === 'PENDING' ? 'Awaiting admin' : s.status }}
                  </span>
                </td>
                <td>{{ s.reference || '-' }}</td>
                <td>{{ s.createdAt | date:'MMM d, y HH:mm' }}</td>
              </tr>
              <tr *ngIf="settlements.length === 0">
                <td colspan="6" class="empty-state">No settlements found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Submit Settlement Modal -->
      <div class="modal-backdrop" *ngIf="showModal" (click)="closeModal()">
        <div class="modal-content" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <h2 class="modal-title">Submit Settlement</h2>
            <button class="modal-close" (click)="closeModal()">
              <ion-icon name="close"></ion-icon>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">Amount</label>
              <input
                type="number"
                class="form-input"
                [(ngModel)]="settlementForm.amount"
                placeholder="Enter settlement amount"
                min="0"
                step="0.01"
              />
            </div>
            <div class="form-group">
              <label class="form-label">Currency</label>
              <select class="form-input" [(ngModel)]="settlementForm.currency">
                <option value="USD">USD</option>
                <option value="GBP">GBP</option>
                <option value="EUR">EUR</option>
                <option value="NGN">NGN</option>
                <option value="KES">KES</option>
                <option value="GHS">GHS</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Payment Reference</label>
              <input
                type="text"
                class="form-input"
                [(ngModel)]="settlementForm.reference"
                placeholder="Bank transfer reference"
              />
            </div>
            <div class="form-group">
              <label class="form-label">Notes (optional)</label>
              <textarea
                class="form-input form-textarea"
                [(ngModel)]="settlementForm.notes"
                placeholder="Additional notes"
                rows="3"
              ></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <ion-button fill="outline" color="medium" (click)="closeModal()">Cancel</ion-button>
            <ion-button
              fill="solid"
              color="primary"
              (click)="submitSettlement()"
              [disabled]="submitting || !settlementForm.amount || !settlementForm.reference"
            >
              <ion-spinner *ngIf="submitting" name="crescent" slot="start"></ion-spinner>
              {{ submitting ? 'Submitting...' : 'Submit' }}
            </ion-button>
          </div>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./payin-partner-settlements.page.scss']
})
export class PayinPartnerSettlementsPage implements OnInit {
  settlements: any[] = [];
  loading = true;
  showModal = false;
  submitting = false;

  settlementForm = {
    amount: null as number | null,
    currency: 'USD',
    reference: '',
    notes: ''
  };

  constructor(
    private settlementService: SettlementService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.settlementService.getMySettlements().subscribe({
      next: (res: any) => {
        this.settlements = Array.isArray(res) ? res : (res.content || []);
        this.loading = false;
      },
      error: () => {
        this.settlements = [];
        this.loading = false;
      }
    });
  }

  openSubmitModal(): void {
    this.settlementForm = { amount: null, currency: 'USD', reference: '', notes: '' };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
  }

  submitSettlement(): void {
    if (!this.settlementForm.amount || !this.settlementForm.reference) return;

    this.submitting = true;
    this.settlementService.initiatePayinToAdmin({
      amount: this.settlementForm.amount,
      currency: this.settlementForm.currency,
      reference: this.settlementForm.reference,
      notes: this.settlementForm.notes
    }).subscribe({
      next: () => {
        this.showToast('Settlement submitted successfully', 'success');
        this.submitting = false;
        this.closeModal();
        this.loadData();
      },
      error: () => {
        this.showToast('Failed to submit settlement', 'danger');
        this.submitting = false;
      }
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
