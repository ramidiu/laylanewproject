import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ToastController } from '@ionic/angular';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-wallet-management',
  templateUrl: './wallet-management.page.html',
  styleUrls: ['./wallet-management.page.scss']
})
export class WalletManagementPage implements OnInit {
  private readonly baseUrl = `${environment.apiUrl}/wallet/admin`;

  wallets: any[] = [];
  filteredWallets: any[] = [];
  walletsLoading = true;
  searchTerm = '';

  // Detail / Ledger view
  selectedWallet: any = null;
  ledgerEntries: any[] = [];
  ledgerLoading = false;

  // Credit / Debit modal
  showModal = false;
  modalMode: 'CREDIT' | 'DEBIT' = 'CREDIT';
  modalUserId: number | null = null;
  modalAmount: number | null = null;
  modalDescription = '';
  modalSubmitting = false;

  constructor(
    private http: HttpClient,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadWallets();
  }

  loadWallets(): void {
    this.walletsLoading = true;
    this.http.get<any>(`${this.baseUrl}/all`).subscribe({
      next: (res) => {
        this.wallets = res?.data ?? res ?? [];
        this.applyFilter();
        this.walletsLoading = false;
      },
      error: () => {
        this.wallets = [];
        this.filteredWallets = [];
        this.walletsLoading = false;
      }
    });
  }

  applyFilter(): void {
    const term = this.searchTerm.toLowerCase().trim();
    if (!term) {
      this.filteredWallets = this.wallets;
      return;
    }
    this.filteredWallets = this.wallets.filter(w =>
      (w.walletNumber || '').toLowerCase().includes(term) ||
      (w.userEmail || '').toLowerCase().includes(term) ||
      (w.userName || '').toLowerCase().includes(term) ||
      String(w.userId).includes(term)
    );
  }

  viewLedger(wallet: any): void {
    this.selectedWallet = wallet;
    this.ledgerEntries = [];
    this.ledgerLoading = true;
    this.http.get<any>(`${this.baseUrl}/${wallet.userId}/transactions`).subscribe({
      next: (res) => {
        this.ledgerEntries = res?.data ?? res ?? [];
        this.ledgerLoading = false;
      },
      error: () => {
        this.ledgerEntries = [];
        this.ledgerLoading = false;
      }
    });
  }

  closeLedger(): void {
    this.selectedWallet = null;
    this.ledgerEntries = [];
  }

  openModal(mode: 'CREDIT' | 'DEBIT', wallet: any): void {
    this.modalMode = mode;
    this.modalUserId = wallet.userId;
    this.modalAmount = null;
    this.modalDescription = '';
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.modalUserId = null;
  }

  submitModal(): void {
    if (!this.modalAmount || !this.modalDescription || !this.modalUserId) return;
    this.modalSubmitting = true;

    const endpoint = this.modalMode === 'CREDIT' ? 'credit' : 'debit';
    this.http.post<any>(`${this.baseUrl}/${this.modalUserId}/${endpoint}`, {
      amount: this.modalAmount,
      description: this.modalDescription
    }).subscribe({
      next: () => {
        this.showToast(`${this.modalMode} applied successfully`, 'success');
        this.modalSubmitting = false;
        this.closeModal();
        this.loadWallets();
        if (this.selectedWallet && this.selectedWallet.userId === this.modalUserId) {
          this.viewLedger(this.selectedWallet);
        }
      },
      error: (err) => {
        const msg = err?.error?.message || `Failed to apply ${this.modalMode.toLowerCase()}`;
        this.showToast(msg, 'danger');
        this.modalSubmitting = false;
      }
    });
  }

  getStatusColor(status: string): string {
    switch (status?.toUpperCase()) {
      case 'ACTIVE': return 'success';
      case 'FROZEN': return 'warning';
      case 'CLOSED': return 'danger';
      default: return 'medium';
    }
  }

  getEntryTypeColor(type: string): string {
    if (type === 'CREDIT') return 'success';
    if (type === 'DEBIT') return 'danger';
    return 'medium';
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message,
      duration: 4000,
      position: 'top',
      color,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
