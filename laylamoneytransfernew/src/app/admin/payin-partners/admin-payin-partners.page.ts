import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-admin-payin-partners',
  template: `
    <div class="pp-page">
      <div class="pp-header">
        <div>
          <h1 class="pp-title">Pay-In Partners</h1>
          <p class="pp-sub">Manage pay-in collection partners</p>
        </div>
        <button class="fb-btn fb-btn--primary" (click)="showAddForm = !showAddForm">
          <ion-icon name="add-outline"></ion-icon>
          Add Partner
        </button>
      </div>

      <!-- Add Form -->
      <div class="fb-card pp-add-card" *ngIf="showAddForm">
        <h3 class="pp-section-title">New Pay-In Partner</h3>
        <div class="pp-form-grid">
          <div class="fb-field">
            <label class="fb-field__label">Partner Name</label>
            <input class="fb-input" [(ngModel)]="newPartner.partnerName" placeholder="Partner company name" />
          </div>
          <div class="fb-field">
            <label class="fb-field__label">Email</label>
            <input class="fb-input" type="email" [(ngModel)]="newPartner.contactEmail" placeholder="partner@company.com" />
          </div>
          <div class="fb-field">
            <label class="fb-field__label">Phone</label>
            <input class="fb-input" [(ngModel)]="newPartner.contactPhone" placeholder="+1234567890" />
          </div>
          <div class="fb-field">
            <label class="fb-field__label">Password</label>
            <input class="fb-input" type="password" [(ngModel)]="newPartner.password" placeholder="Login password (min 8 chars)" />
          </div>
        </div>
        <div class="pp-form-actions">
          <button class="fb-btn fb-btn--primary"
            [disabled]="!newPartner.partnerName || !newPartner.contactEmail || !newPartner.password || newPartner.password.length < 8"
            (click)="addPartner()">Create Partner</button>
          <button class="fb-btn fb-btn--secondary" (click)="showAddForm = false; resetForm()">Cancel</button>
        </div>
      </div>

      <!-- Table -->
      <div class="fb-card pp-table-card">
        <div *ngIf="loading" class="pp-loading">
          <div class="fb-skeleton fb-skeleton--text" *ngFor="let i of [1,2,3,4,5]"></div>
        </div>

        <div class="pp-table-wrap" *ngIf="!loading">
          <table class="fb-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Phone</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let partner of partners">
                <td class="pp-name">{{ partner.partnerName }}</td>
                <td>{{ partner.contactEmail }}</td>
                <td>{{ partner.contactPhone || '—' }}</td>
                <td>
                  <span class="pp-status" [class.pp-status--active]="partner.isActive" [class.pp-status--inactive]="!partner.isActive">
                    {{ partner.isActive ? 'Active' : 'Inactive' }}
                  </span>
                </td>
                <td>
                  <div class="pp-actions">
                    <button class="pp-btn" [class.pp-btn--warn]="partner.isActive" [class.pp-btn--success]="!partner.isActive" (click)="toggleActive(partner)">
                      <ion-icon [name]="partner.isActive ? 'pause-circle-outline' : 'play-circle-outline'"></ion-icon>
                      {{ partner.isActive ? 'Pause' : 'Activate' }}
                    </button>
                    <button class="pp-btn pp-btn--login" (click)="loginAsPartner(partner)">
                      <ion-icon name="log-in-outline"></ion-icon>
                      Login as Partner
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div *ngIf="!loading && partners.length === 0" class="pp-empty">
          <ion-icon name="cash-outline"></ion-icon>
          <p>No pay-in partners configured yet</p>
          <button class="fb-btn fb-btn--primary" (click)="showAddForm = true">Add your first partner</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pp-page { padding: 24px; }
    .pp-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
    .pp-title { font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
    .pp-sub { color: #6b7280; margin: 0; font-size: 0.875rem; }
    .pp-add-card { margin-bottom: 20px; padding: 20px; }
    .pp-section-title { font-size: 1rem; font-weight: 600; margin: 0 0 16px; color: #111827; }
    .pp-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
    .pp-form-actions { display: flex; gap: 12px; }
    .pp-table-card { padding: 0; overflow: hidden; }
    .pp-loading { padding: 16px; display: flex; flex-direction: column; gap: 8px; }
    .pp-table-wrap { overflow-x: auto; }
    .pp-name { font-weight: 600; color: #111827; }
    .pp-status { display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 0.75rem; font-weight: 600; }
    .pp-status--active { background: #d1fae5; color: #065f46; }
    .pp-status--inactive { background: #fee2e2; color: #991b1b; }
    .pp-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .pp-btn { display: flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 6px; font-size: 0.8rem; font-weight: 500; cursor: pointer; border: 1px solid transparent; }
    .pp-btn--warn { background: #fef3c7; color: #92400e; border-color: #fde68a; }
    .pp-btn--warn:hover { background: #fde68a; }
    .pp-btn--success { background: #d1fae5; color: #065f46; border-color: #a7f3d0; }
    .pp-btn--success:hover { background: #a7f3d0; }
    .pp-btn--login { background: #eff6ff; color: #1e40af; border-color: #bfdbfe; }
    .pp-btn--login:hover { background: #dbeafe; }
    .pp-empty { text-align: center; padding: 48px 24px; color: #9ca3af; }
    .pp-empty ion-icon { font-size: 3rem; display: block; margin: 0 auto 12px; color: #d1d5db; }
    .pp-empty p { margin: 0 0 16px; font-size: 0.95rem; }
    @media (max-width: 640px) { .pp-form-grid { grid-template-columns: 1fr; } }
  `]
})
export class AdminPayinPartnersPage implements OnInit {
  partners: any[] = [];
  loading = true;
  showAddForm = false;

  newPartner = { partnerName: '', contactEmail: '', contactPhone: '', password: '' };

  constructor(
    private partnerService: PartnerService,
    private toastCtrl: ToastController,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadPartners();
  }

  loadPartners(): void {
    this.loading = true;
    this.partnerService.getPayinPartners().subscribe({
      next: (res) => {
        this.partners = Array.isArray(res) ? res : res?.data || [];
        this.loading = false;
      },
      error: () => {
        this.partners = [];
        this.loading = false;
        this.showToast('Failed to load partners', 'danger');
      }
    });
  }

  addPartner(): void {
    if (!this.newPartner.partnerName || !this.newPartner.contactEmail) return;
    if (!this.newPartner.password || this.newPartner.password.length < 8) {
      this.showToast('Password is required (minimum 8 characters)', 'danger');
      return;
    }
    this.partnerService.createPayinPartner(this.newPartner).subscribe({
      next: () => {
        this.showToast('Pay-in partner created successfully', 'success');
        this.showAddForm = false;
        this.resetForm();
        this.loadPartners();
      },
      error: (err) => this.showToast(err?.error?.message || 'Failed to create partner', 'danger')
    });
  }

  toggleActive(partner: any): void {
    this.partnerService.updatePayinPartner(partner.id, { isActive: !partner.isActive }).subscribe({
      next: () => {
        this.showToast(`Partner ${partner.isActive ? 'paused' : 'activated'}`, 'success');
        this.loadPartners();
      },
      error: () => this.showToast('Failed to update partner', 'danger')
    });
  }

  loginAsPartner(partner: any): void {
    sessionStorage.setItem('fb_admin_return', '/admin/payin-partners');
    sessionStorage.setItem('fb_admin_partner_id', partner.id.toString());
    sessionStorage.setItem('fb_admin_partner_name', partner.partnerName || 'Partner');
    window.location.href = '/payin-partner';
  }

  resetForm(): void {
    this.newPartner = { partnerName: '', contactEmail: '', contactPhone: '', password: '' };
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 4000, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
