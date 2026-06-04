import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-payin-partner-customers',
  template: `
    <div class="payin-customers">
      <div class="pc-header">
        <div>
          <h1 class="page-title">Customers</h1>
          <p class="page-subtitle">Pay-in customers and UK-registered frontend users</p>
        </div>
        <ion-button fill="outline" size="small" (click)="loadData()">
          <ion-icon name="refresh-outline" slot="start"></ion-icon>
          Refresh
        </ion-button>
      </div>

      <!-- Legend -->
      <div class="pc-legend">
        <span class="pc-badge pc-badge--backend">BACKEND</span> Created by partner portal &nbsp;|&nbsp;
        <span class="pc-badge pc-badge--frontend">FRONTEND</span> Created by partner (customer-facing) &nbsp;|&nbsp;
        <span class="pc-badge pc-badge--user">UK USER</span> Registered via Layla app — toggle to enable
      </div>

      <div class="fb-card table-card">
        <div class="pc-toolbar">
          <div class="pc-count">
            <ion-badge color="primary">{{ customers.length }}</ion-badge>
            <span>customers</span>
          </div>
          <div class="pc-search">
            <ion-icon name="search-outline"></ion-icon>
            <input [(ngModel)]="searchTerm" (ngModelChange)="applyFilter()" placeholder="Search name, email…" />
          </div>
        </div>

        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>

        <div class="table-wrapper" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Customer ID</th>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Nationality</th>
                <th>Verified</th>
                <th>Source</th>
                <th>Payin Access</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let c of filtered" [class.pc-row--disabled]="c.createdSource === 'FRONTEND_USER' && !c.payinEnabled">
                <td class="pc-id">{{ c.customerId | slice:0:8 }}…</td>
                <td>{{ c.firstName }} {{ c.lastName }}</td>
                <td>{{ c.email }}</td>
                <td>{{ c.phone || '—' }}</td>
                <td>{{ c.nationality || '—' }}</td>
                <td>
                  <ion-badge [color]="c.isVerified ? 'success' : 'medium'">
                    {{ c.isVerified ? 'Verified' : 'Unverified' }}
                  </ion-badge>
                </td>
                <td>
                  <span class="pc-badge"
                    [class.pc-badge--backend]="c.createdSource === 'BACKEND'"
                    [class.pc-badge--frontend]="c.createdSource === 'FRONTEND'"
                    [class.pc-badge--user]="c.createdSource === 'FRONTEND_USER'">
                    {{ c.createdSource === 'FRONTEND_USER' ? 'UK USER' : c.createdSource }}
                  </span>
                </td>
                <td>
                  <!-- Toggle only shown for frontend UK users -->
                  <button *ngIf="c.createdSource === 'FRONTEND_USER'"
                    class="pc-toggle"
                    [class.pc-toggle--on]="c.payinEnabled"
                    [class.pc-toggle--off]="!c.payinEnabled"
                    [disabled]="toggling[c.userId]"
                    (click)="togglePayin(c)">
                    <ion-icon [name]="c.payinEnabled ? 'toggle' : 'toggle-outline'"></ion-icon>
                    {{ c.payinEnabled ? 'ON' : 'OFF' }}
                  </button>
                  <span *ngIf="c.createdSource !== 'FRONTEND_USER'" class="pc-na">—</span>
                </td>
                <td>{{ c.createdAt | date:'MMM d, y' }}</td>
              </tr>
              <tr *ngIf="filtered.length === 0">
                <td colspan="9" class="empty-state">No customers found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .payin-customers { padding: 24px; }
    .pc-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; }
    .page-title { font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
    .page-subtitle { color: #6b7280; margin: 0; font-size: 0.875rem; }
    .pc-legend { font-size: 0.78rem; color: #6b7280; margin-bottom: 16px; display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
    .pc-toolbar { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap; }
    .pc-count { display: flex; align-items: center; gap: 8px; flex: 1; }
    .pc-search { display: flex; align-items: center; gap: 8px; border: 1px solid #e5e7eb; border-radius: 6px; padding: 7px 12px; }
    .pc-search ion-icon { color: #9ca3af; }
    .pc-search input { border: none; outline: none; font-size: 0.85rem; width: 200px; }
    .table-wrapper { overflow-x: auto; }
    .loading { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
    .empty-state { text-align: center; color: #9ca3af; padding: 32px !important; }
    .pc-id { font-family: monospace; font-size: 0.8rem; }
    .pc-row--disabled td { opacity: 0.55; }
    .pc-badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: .03em; }
    .pc-badge--backend { background: #dbeafe; color: #1e40af; }
    .pc-badge--frontend { background: #d1fae5; color: #065f46; }
    .pc-badge--user { background: #fef3c7; color: #92400e; }
    .pc-toggle { display: inline-flex; align-items: center; gap: 5px; padding: 5px 12px; border-radius: 20px; font-size: 0.78rem; font-weight: 700; cursor: pointer; border: none; transition: all .15s; }
    .pc-toggle--on { background: #d1fae5; color: #065f46; }
    .pc-toggle--on:hover:not(:disabled) { background: #a7f3d0; }
    .pc-toggle--off { background: #fee2e2; color: #991b1b; }
    .pc-toggle--off:hover:not(:disabled) { background: #fecaca; }
    .pc-toggle:disabled { opacity: 0.6; cursor: not-allowed; }
    .pc-toggle ion-icon { font-size: 1rem; }
    .pc-na { color: #d1d5db; }
  `]
})
export class PayinPartnerCustomersPage implements OnInit {
  customers: any[] = [];
  filtered: any[] = [];
  loading = true;
  searchTerm = '';
  toggling: Record<number, boolean> = {};

  constructor(
    private partnerService: PartnerService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.partnerService.getPayinCustomers().subscribe({
      next: (res: any) => {
        this.customers = Array.isArray(res) ? res : (res?.data || []);
        this.applyFilter();
        this.loading = false;
      },
      error: () => {
        this.customers = [];
        this.filtered = [];
        this.loading = false;
      }
    });
  }

  applyFilter(): void {
    const term = this.searchTerm.toLowerCase().trim();
    this.filtered = term
      ? this.customers.filter(c =>
          (c.firstName + ' ' + c.lastName).toLowerCase().includes(term) ||
          c.email?.toLowerCase().includes(term) ||
          c.customerId?.toLowerCase().includes(term))
      : [...this.customers];
  }

  togglePayin(customer: any): void {
    const userId = customer.userId;
    if (!userId || this.toggling[userId]) return;
    this.toggling[userId] = true;
    this.partnerService.toggleFrontendCustomerPayin(userId).subscribe({
      next: (res: any) => {
        customer.payinEnabled = res.payinEnabled ?? !customer.payinEnabled;
        this.toggling[userId] = false;
        this.showToast(
          `Payin access ${customer.payinEnabled ? 'enabled' : 'disabled'} for ${customer.firstName}`,
          customer.payinEnabled ? 'success' : 'medium'
        );
      },
      error: () => {
        this.toggling[userId] = false;
        this.showToast('Failed to update payin access', 'danger');
      }
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 3000, position: 'top', color,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
