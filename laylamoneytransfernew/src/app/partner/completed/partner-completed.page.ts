import { Component, OnInit } from '@angular/core';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-partner-completed',
  template: `
    <div class="partner-completed">
      <h1 class="page-title">Completed Transactions</h1>
      <p class="page-subtitle">Paid, cancelled, and refunded transactions</p>

      <div class="fb-card table-card">
        <div class="table-toolbar">
          <div class="table-toolbar__count">
            <ion-badge color="success">{{ transactions.length }}</ion-badge>
            <span>completed transactions</span>
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
                <th>Reference</th>
                <th>Beneficiary</th>
                <th>Payout Amount</th>
                <th>Delivery</th>
                <th>Status</th>
                <th>Completed</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let txn of transactions">
                <td class="fb-currency">{{ txn.referenceNumber }}</td>
                <td>{{ txn.beneficiaryName }}</td>
                <td class="fb-currency">{{ txn.receiveAmount | number:'1.2-2' }} {{ txn.receiveCurrency }}</td>
                <td>{{ txn.deliveryMethod }}</td>
                <td><app-status-chip [status]="txn.status"></app-status-chip></td>
                <td>{{ txn.updatedAt | date:'MMM d, y HH:mm' }}</td>
              </tr>
              <tr *ngIf="transactions.length === 0">
                <td colspan="6" class="empty-state">No completed transactions yet</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./partner-completed.page.scss']
})
export class PartnerCompletedPage implements OnInit {
  transactions: any[] = [];
  loading = true;

  constructor(private partnerService: PartnerService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.partnerService.getMyCompleted().subscribe({
      next: (res: any) => {
        this.transactions = (() => { const d = res?.data || res; return Array.isArray(d) ? d : (d?.content || []); })();
        this.loading = false;
      },
      error: () => {
        this.transactions = [];
        this.loading = false;
      }
    });
  }
}
