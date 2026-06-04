import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-payin-partners',
  templateUrl: './payin-partners.page.html',
  styleUrls: ['./payin-partners.page.scss']
})
export class PayinPartnersPage implements OnInit {
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
        console.log('[PayinPartners] API response:', res);
        this.partners = Array.isArray(res) ? res : res?.data || [];
        console.log('[PayinPartners] Parsed partners:', this.partners);
        this.loading = false;
      },
      error: (err) => {
        console.error('[PayinPartners] API error:', err);
        this.partners = [];
        this.loading = false;
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
        this.newPartner = { partnerName: '', contactEmail: '', contactPhone: '', password: '' };
        this.loadPartners();
      },
      error: () => this.showToast('Failed to create partner', 'danger')
    });
  }

  toggleActive(partner: any): void {
    this.partnerService.updatePayinPartner(partner.id, { isActive: !partner.isActive }).subscribe({
      next: () => {
        this.showToast('Partner toggled', 'success');
        this.loadPartners();
      },
      error: () => this.showToast('Failed to toggle partner', 'danger')
    });
  }

  loginAsPartner(partner: any): void {
    sessionStorage.setItem('fb_admin_return', '/superadmin/payin-partners');
    sessionStorage.setItem('fb_admin_partner_id', partner.id.toString());
    sessionStorage.setItem('fb_admin_partner_name', partner.partnerName || 'Partner');
    window.location.href = '/payin-partner';
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
