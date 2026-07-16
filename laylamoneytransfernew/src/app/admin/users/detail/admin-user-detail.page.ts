import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ToastController, AlertController } from '@ionic/angular';
import { UserService, RiskScoreResponse } from '../../../core/services/user.service';
import { KycService } from '../../../core/services/kyc.service';
import { TransactionService } from '../../../core/services/transaction.service';
import { BeneficiaryService } from '../../../core/services/beneficiary.service';
import { UserResponse } from '../../../core/models/user.model';
import { KycDocumentResponse, KycDocumentStatus } from '../../../core/models/kyc.model';
import { TransactionResponse } from '../../../core/models/transaction.model';
import { BeneficiaryResponse, UpdateBeneficiaryRequest } from '../../../core/models/beneficiary.model';

@Component({
  selector: 'app-admin-user-detail',
  templateUrl: './admin-user-detail.page.html',
  styleUrls: ['./admin-user-detail.page.scss']
})
export class AdminUserDetailPage implements OnInit {
  user: UserResponse | null = null;
  documents: KycDocumentResponse[] = [];
  transactions: TransactionResponse[] = [];
  riskScore: RiskScoreResponse | null = null;
  riskLoading = false;
  loading = true;

  // KYC audit trail (uploads, approvals, tier changes) with actor role + IP
  kycAudit: any[] = [];
  kycAuditLoading = false;

  // Admin recipient (beneficiary) management
  beneficiaries: BeneficiaryResponse[] = [];
  benLoading = false;
  editingBen: BeneficiaryResponse | null = null;
  benSaving = false;
  benForm: UpdateBeneficiaryRequest = {};
  readonly deliveryMethods = ['BANK_DEPOSIT', 'MOBILE_WALLET', 'CASH_PICKUP'];

  constructor(
    private route: ActivatedRoute,
    private userService: UserService,
    private kycService: KycService,
    private transactionService: TransactionService,
    private beneficiaryService: BeneficiaryService,
    private toastCtrl: ToastController,
    private alertCtrl: AlertController
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadUser(id);
    }
  }

  loadUser(id: string): void {
    this.userService.getUserById(id).subscribe({
      next: (user) => {
        this.user = user;
        this.loadDocuments(id);
        this.loadKycAudit(id);
        this.loadTransactions(id);
        this.loadRiskScore(user.id);
        this.loadBeneficiaries(user.id);
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  loadRiskScore(numericId: number): void {
    this.riskLoading = true;
    this.userService.getRiskScore(numericId).subscribe({
      next: (res) => {
        this.riskScore = res;
        this.riskLoading = false;
      },
      error: () => {
        this.riskScore = null;
        this.riskLoading = false;
      }
    });
  }

  riskColor(level: string | undefined): string {
    switch ((level || '').toUpperCase()) {
      case 'LOW': return 'success';
      case 'MEDIUM': return 'warning';
      case 'HIGH': return 'danger';
      default: return 'medium';
    }
  }

  breakdownEntries(): Array<{ key: string; value: any }> {
    if (!this.riskScore?.breakdown) return [];
    return Object.entries(this.riskScore.breakdown).map(([k, v]) => ({ key: k, value: v }));
  }

  loadDocuments(userId: string): void {
    this.kycService.getDocuments(userId).subscribe({
      next: (docs) => this.documents = docs,
      error: () => this.documents = []
    });
  }

  loadKycAudit(userId: string): void {
    this.kycAuditLoading = true;
    this.kycService.getAuditLog(userId).subscribe({
      next: (logs) => { this.kycAudit = logs || []; this.kycAuditLoading = false; },
      error: () => { this.kycAudit = []; this.kycAuditLoading = false; }
    });
  }

  /** Human-readable summary of a KYC audit event's JSON details. */
  kycAuditSummary(details: string): string {
    if (!details) return '';
    try {
      const d = JSON.parse(details);
      const parts: string[] = [];
      if (d.newStatus) parts.push('Status: ' + d.newStatus);
      if (d.documentId) parts.push('Doc #' + d.documentId);
      if (d.newTier) parts.push('Tier: ' + d.newTier);
      if (d.previousTier) parts.push('(was ' + d.previousTier + ')');
      if (d.rejectionReason) parts.push('Reason: ' + d.rejectionReason);
      return parts.join(' · ');
    } catch { return details; }
  }

  loadTransactions(userId: string): void {
    this.transactionService.list({ page: 0, size: 10 }).subscribe({
      next: (res) => this.transactions = res.content,
      error: () => this.transactions = []
    });
  }

  async approveDocument(doc: KycDocumentResponse): Promise<void> {
    if (!this.user) return;
    this.kycService.reviewDocument(this.user.uuid, doc.id, { status: KycDocumentStatus.APPROVED }).subscribe({
      next: () => {
        this.showToast('Document approved', 'success');
        this.loadDocuments(this.user!.uuid);
      },
      error: () => this.showToast('Failed to approve document', 'danger')
    });
  }

  async rejectDocument(doc: KycDocumentResponse): Promise<void> {
    if (!this.user) return;
    const alert = await this.alertCtrl.create({
      header: 'Reject Document',
      inputs: [
        { name: 'reason', type: 'textarea', placeholder: 'Reason for rejection' }
      ],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Reject',
          handler: (data) => {
            this.kycService.reviewDocument(this.user!.uuid, doc.id, {
              status: KycDocumentStatus.REJECTED,
              rejectionReason: data.reason
            }).subscribe({
              next: () => {
                this.showToast('Document rejected', 'warning');
                this.loadDocuments(this.user!.uuid);
              },
              error: () => this.showToast('Failed to reject document', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async suspendUser(): Promise<void> {
    if (!this.user) return;
    this.userService.suspendUser(this.user.id.toString()).subscribe({
      next: () => {
        this.showToast('User suspended', 'warning');
        this.loadUser(this.user!.uuid);
      },
      error: () => this.showToast('Failed to suspend user', 'danger')
    });
  }

  async activateUser(): Promise<void> {
    if (!this.user) return;
    this.userService.activateUser(this.user.id.toString()).subscribe({
      next: () => {
        this.showToast('User activated', 'success');
        this.loadUser(this.user!.uuid);
      },
      error: () => this.showToast('Failed to activate user', 'danger')
    });
  }

  loadBeneficiaries(numericId: number): void {
    this.benLoading = true;
    this.beneficiaryService.listForUserAdmin(numericId).subscribe({
      next: (list) => {
        this.beneficiaries = list || [];
        this.benLoading = false;
      },
      error: () => {
        this.beneficiaries = [];
        this.benLoading = false;
      }
    });
  }

  openBenEdit(ben: BeneficiaryResponse): void {
    this.editingBen = ben;
    // Seed the form with the current values; only changed fields are sent on save.
    this.benForm = {
      fullName: ben.fullName,
      country: ben.country,
      deliveryMethod: ben.deliveryMethod,
      bankName: ben.bankName,
      accountNumber: ben.accountNumber,
      iban: ben.iban,
      swiftBic: ben.swiftBic,
      sortCode: ben.sortCode,
      mobileNumber: ben.mobileNumber,
      mobileProvider: ben.mobileProvider,
      address: ben.address,
      city: ben.city,
      relationship: ben.relationship
    };
  }

  closeBenEdit(): void {
    this.editingBen = null;
    this.benForm = {};
  }

  saveBen(): void {
    if (!this.editingBen) return;
    this.benSaving = true;
    this.beneficiaryService.updateAdmin(this.editingBen.id, this.benForm).subscribe({
      next: () => {
        this.benSaving = false;
        this.showToast('Recipient updated — synced to USI', 'success');
        this.closeBenEdit();
        if (this.user) this.loadBeneficiaries(this.user.id);
      },
      error: () => {
        this.benSaving = false;
        this.showToast('Failed to update recipient', 'danger');
      }
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
