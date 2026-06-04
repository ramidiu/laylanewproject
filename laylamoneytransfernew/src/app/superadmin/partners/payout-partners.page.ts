import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';

@Component({
  selector: 'app-payout-partners',
  templateUrl: './payout-partners.page.html',
  styleUrls: ['./payout-partners.page.scss']
})
export class PayoutPartnersPage implements OnInit {
  partners: any[] = [];
  loading = true;
  showAddForm = false;

  newPartner = { partnerName: '', contactEmail: '', contactPhone: '', password: '' };

  expandedPartner: number | null = null;
  partnerCountries: any[] = [];
  countriesLoading = false;

  newCountry = { countryCode: '', countryName: '' };

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
    this.partnerService.getPayoutPartners().subscribe({
      next: (res) => {
        this.partners = Array.isArray(res) ? res : res?.data || [];
        this.loading = false;
      },
      error: () => { this.partners = []; this.loading = false; }
    });
  }

  addPartner(): void {
    if (!this.newPartner.partnerName || !this.newPartner.contactEmail) return;
    if (!this.newPartner.password || this.newPartner.password.length < 8) {
      this.showToast('Password is required (minimum 8 characters)', 'danger');
      return;
    }
    this.partnerService.createPayoutPartner(this.newPartner).subscribe({
      next: () => {
        this.showToast('Partner created successfully', 'success');
        this.showAddForm = false;
        this.newPartner = { partnerName: '', contactEmail: '', contactPhone: '', password: '' };
        this.loadPartners();
      },
      error: () => this.showToast('Failed to create partner', 'danger')
    });
  }

  toggleActive(partner: any): void {
    this.partnerService.togglePayoutPartner(partner.id).subscribe({
      next: () => {
        this.showToast('Partner toggled', 'success');
        this.loadPartners();
      },
      error: () => this.showToast('Failed to toggle partner', 'danger')
    });
  }

  viewCountries(partner: any): void {
    if (this.expandedPartner === partner.id) {
      this.expandedPartner = null;
      return;
    }
    this.expandedPartner = partner.id;
    this.countriesLoading = true;
    this.partnerService.getPartnerCountries(partner.id).subscribe({
      next: (res) => {
        this.partnerCountries = Array.isArray(res) ? res : res?.data || [];
        this.countriesLoading = false;
      },
      error: () => { this.partnerCountries = []; this.countriesLoading = false; }
    });
  }

  assignCountry(): void {
    if (!this.expandedPartner || !this.newCountry.countryCode) return;
    this.partnerService.assignCountry(this.expandedPartner, this.newCountry).subscribe({
      next: () => {
        this.showToast('Country assigned', 'success');
        this.newCountry = { countryCode: '', countryName: '' };
        this.viewCountries({ id: this.expandedPartner });
      },
      error: () => this.showToast('Failed to assign country', 'danger')
    });
  }

  removeCountry(countryId: number): void {
    if (!this.expandedPartner) return;
    this.partnerService.removeCountry(this.expandedPartner, countryId).subscribe({
      next: () => {
        this.showToast('Country removed', 'success');
        this.viewCountries({ id: this.expandedPartner });
      },
      error: () => this.showToast('Failed to remove country', 'danger')
    });
  }

  loginAsPartner(partner: any): void {
    sessionStorage.setItem('fb_admin_return', '/superadmin/partners');
    sessionStorage.setItem('fb_admin_partner_id', partner.id.toString());
    sessionStorage.setItem('fb_admin_partner_name', partner.partnerName || 'Partner');
    window.location.href = '/partner';
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
