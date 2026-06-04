import { Component, OnInit } from '@angular/core';
import { SettlementService } from '../../core/services/settlement.service';

@Component({
  selector: 'app-partner-settlements',
  template: `
    <div class="partner-settlements">
      <h1 class="page-title">Settlements</h1>
      <p class="page-subtitle">View your settlement history and status</p>

      <div class="fb-card table-card">
        <div class="table-toolbar">
          <div class="table-toolbar__count">
            <ion-badge color="primary">{{ settlements.length }}</ion-badge>
            <span>total settlements</span>
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
                <th>Type</th>
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
                <td>{{ s.settlementType || s.type || '-' }}</td>
                <td class="fb-currency">{{ s.amount | number:'1.2-2' }}</td>
                <td>{{ s.currency }}</td>
                <td><app-status-chip [status]="s.status"></app-status-chip></td>
                <td>{{ s.reference || '-' }}</td>
                <td>{{ s.createdAt | date:'MMM d, y HH:mm' }}</td>
              </tr>
              <tr *ngIf="settlements.length === 0">
                <td colspan="7" class="empty-state">No settlements found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./partner-settlements.page.scss']
})
export class PartnerSettlementsPage implements OnInit {
  settlements: any[] = [];
  loading = true;

  constructor(private settlementService: SettlementService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.settlementService.getMySettlements().subscribe({
      next: (res: any) => {
        this.settlements = (() => { const d = res?.data || res; return Array.isArray(d) ? d : (d?.content || []); })();
        this.loading = false;
      },
      error: () => {
        this.settlements = [];
        this.loading = false;
      }
    });
  }
}
