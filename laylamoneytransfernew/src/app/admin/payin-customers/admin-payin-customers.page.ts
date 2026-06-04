import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-admin-payin-customers',
  template: `
    <div class="sa-pc">
      <div class="sa-pc__header">
        <div>
          <h1 class="sa-pc__title">Pay-In Customers</h1>
          <p class="sa-pc__sub">Manage pay-in customers and enable/disable UK frontend users for transactions</p>
        </div>
        <ion-button fill="outline" size="small" (click)="load()">
          <ion-icon name="refresh-outline" slot="start"></ion-icon> Refresh
        </ion-button>
      </div>

      <div class="sa-pc__legend">
        <span class="src-badge src-badge--backend">BACKEND</span> Created via partner portal &nbsp;|&nbsp;
        <span class="src-badge src-badge--frontend">FRONTEND</span> Created by partner (customer-facing) &nbsp;|&nbsp;
        <span class="src-badge src-badge--user">UK USER</span> Registered via Layla app — toggle to enable/disable pay-in access
      </div>

      <div class="fb-card table-card">
        <div class="sa-pc__toolbar">
          <div class="sa-pc__counts">
            <ion-badge color="primary">{{ customers.length }}</ion-badge> total &nbsp;
            <ion-badge color="warning">{{ ukUsersCount }}</ion-badge> UK users &nbsp;
            <ion-badge color="success">{{ ukEnabledCount }}</ion-badge> enabled
          </div>
          <div class="search-box">
            <ion-icon name="search-outline"></ion-icon>
            <input [(ngModel)]="searchTerm" (ngModelChange)="applyFilter()" placeholder="Search name, email, ID…" />
          </div>
          <select [(ngModel)]="sourceFilter" (ngModelChange)="applyFilter()" class="filter-sel">
            <option value="">All Sources</option>
            <option value="BACKEND">Backend</option>
            <option value="FRONTEND">Frontend</option>
            <option value="FRONTEND_USER">UK Users</option>
          </select>
        </div>

        <div *ngIf="loading" class="loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5,6]"></div>
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
                <th>Pay-In Access</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let c of filtered" [class.sa-pc__row--disabled]="c.createdSource === 'FRONTEND_USER' && !c.payinEnabled">
                <td class="mono" [title]="c.customerId">{{ c.customerId | slice:0:8 }}…</td>
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
                  <span class="src-badge"
                    [class.src-badge--backend]="c.createdSource === 'BACKEND'"
                    [class.src-badge--frontend]="c.createdSource === 'FRONTEND'"
                    [class.src-badge--user]="c.createdSource === 'FRONTEND_USER'">
                    {{ c.createdSource === 'FRONTEND_USER' ? 'UK USER' : c.createdSource }}
                  </span>
                </td>
                <td>
                  <button *ngIf="c.createdSource === 'FRONTEND_USER'"
                    class="toggle-btn"
                    [class.toggle-btn--on]="c.payinEnabled"
                    [class.toggle-btn--off]="!c.payinEnabled"
                    [disabled]="toggling[c.userId]"
                    (click)="togglePayin(c)">
                    <ion-icon [name]="c.payinEnabled ? 'toggle' : 'toggle-outline'"></ion-icon>
                    {{ c.payinEnabled ? 'ON' : 'OFF' }}
                  </button>
                  <span *ngIf="c.createdSource !== 'FRONTEND_USER'" class="muted">—</span>
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
    .sa-pc { padding: 24px; }
    .sa-pc__header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; }
    .sa-pc__title { font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
    .sa-pc__sub { color: #6b7280; margin: 0; font-size: 0.875rem; }
    .sa-pc__legend { font-size: 0.78rem; color: #6b7280; margin-bottom: 16px; display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
    .sa-pc__toolbar { display: flex; align-items: center; gap: 12px; padding: 12px 16px; border-bottom: 1px solid #e5e7eb; flex-wrap: wrap; }
    .sa-pc__counts { display: flex; align-items: center; gap: 8px; flex: 1; flex-wrap: wrap; }
    .sa-pc__row--disabled td { opacity: 0.55; }
    .search-box { display: flex; align-items: center; gap: 8px; border: 1px solid #e5e7eb; border-radius: 6px; padding: 7px 12px; }
    .search-box ion-icon { color: #9ca3af; }
    .search-box input { border: none; outline: none; font-size: 0.85rem; width: 200px; }
    .filter-sel { padding: 7px 10px; border: 1px solid #e5e7eb; border-radius: 6px; font-size: 0.85rem; outline: none; }
    .table-wrapper { overflow-x: auto; }
    .loading { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
    .empty-state { text-align: center; color: #9ca3af; padding: 32px !important; }
    .mono { font-family: monospace; font-size: 0.8rem; cursor: default; }
    .muted { color: #d1d5db; }
    .src-badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: .03em; }
    .src-badge--backend { background: #dbeafe; color: #1e40af; }
    .src-badge--frontend { background: #d1fae5; color: #065f46; }
    .src-badge--user { background: #fef3c7; color: #92400e; }
    .toggle-btn { display: inline-flex; align-items: center; gap: 5px; padding: 5px 12px; border-radius: 20px; font-size: 0.78rem; font-weight: 700; cursor: pointer; border: none; transition: all .15s; }
    .toggle-btn--on { background: #d1fae5; color: #065f46; }
    .toggle-btn--on:hover:not(:disabled) { background: #a7f3d0; }
    .toggle-btn--off { background: #fee2e2; color: #991b1b; }
    .toggle-btn--off:hover:not(:disabled) { background: #fecaca; }
    .toggle-btn:disabled { opacity: 0.6; cursor: not-allowed; }
    .toggle-btn ion-icon { font-size: 1rem; }
  `]
})
export class AdminPayinCustomersPage implements OnInit {
  customers: any[] = [];
  filtered: any[] = [];
  loading = true;
  searchTerm = '';
  sourceFilter = '';
  toggling: Record<number, boolean> = {};

  constructor(
    private partnerService: PartnerService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.partnerService.getPayinCustomers().subscribe({
      next: (res: any) => {
        this.customers = Array.isArray(res) ? res : (res?.data || []);
        this.applyFilter();
        this.loading = false;
      },
      error: () => { this.customers = []; this.filtered = []; this.loading = false; }
    });
  }

  get ukUsersCount(): number {
    return this.customers.filter(c => c.createdSource === 'FRONTEND_USER').length;
  }

  get ukEnabledCount(): number {
    return this.customers.filter(c => c.createdSource === 'FRONTEND_USER' && c.payinEnabled).length;
  }

  applyFilter(): void {
    let list = [...this.customers];
    if (this.sourceFilter) list = list.filter(c => c.createdSource === this.sourceFilter);
    if (this.searchTerm.trim()) {
      const t = this.searchTerm.toLowerCase();
      list = list.filter(c =>
        (c.firstName + ' ' + c.lastName).toLowerCase().includes(t) ||
        c.email?.toLowerCase().includes(t) ||
        c.customerId?.toLowerCase().includes(t)
      );
    }
    this.filtered = list;
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
          `Pay-in access ${customer.payinEnabled ? 'enabled' : 'disabled'} for ${customer.firstName}`,
          customer.payinEnabled ? 'success' : 'medium'
        );
      },
      error: () => {
        this.toggling[userId] = false;
        this.showToast('Failed to update pay-in access', 'danger');
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
