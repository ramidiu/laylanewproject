import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';
import { AlertController, ToastController } from '@ionic/angular';
import { forkJoin } from 'rxjs';
import { UserService, RiskScoreResponse } from '../../core/services/user.service';
import { AddressDetail } from '../../core/services/address.service';
import { KycService } from '../../core/services/kyc.service';
import { TransactionService } from '../../core/services/transaction.service';
import { UserResponse } from '../../core/models/user.model';
import { KycDocumentResponse, KycDocumentStatus, KycStatusResponse } from '../../core/models/kyc.model';
import { TransactionResponse } from '../../core/models/transaction.model';
import { PageResponse } from '../../core/models/common.model';

@Component({
  selector: 'app-sa-user-profile',
  templateUrl: './sa-user-profile.page.html',
  styleUrls: ['./sa-user-profile.page.scss']
})
export class SAUserProfilePage implements OnInit, OnDestroy {
  uuid = '';
  user: UserResponse | null = null;
  kycStatus: KycStatusResponse | null = null;
  documents: KycDocumentResponse[] = [];

  /** Docs shown in the UI: for PARTIAL customers, hide imported placeholders (no real upload);
   *  for everyone else show all documents normally. */
  get displayDocuments(): KycDocumentResponse[] {
    const isPartial = (this.kycStatus?.overallStatus || '').toUpperCase() === 'PARTIAL';
    return isPartial ? this.documents.filter(d => (d as any).realUpload === true) : this.documents;
  }
  transactions: TransactionResponse[] = [];
  totalTransactions = 0;
  txPage = 0;
  txLoading = false;
  readonly TX_PAGE_SIZE = 20;

  loading = true;
  saving = false;
  activeTab: 'details' | 'kyc' | 'transactions' = 'details';

  // Admin document upload
  uploadSection: 'identity' | 'address' = 'identity';
  uploadDocType = 'PASSPORT';
  uploadDocNumber = '';
  uploadIssueDate = '';
  uploadExpiryDate = '';
  uploadFrontFile: File | null = null;
  uploadBackFile: File | null = null;
  uploading = false;
  uploadAddressSubType = '';
  uploadDocumentDate = '';

  get isAddressProof(): boolean { return this.uploadSection === 'address'; }
  get isDrivingLicence(): boolean { return this.uploadDocType === 'DRIVING_LICENCE'; }

  switchSection(section: 'identity' | 'address'): void {
    this.uploadSection = section;
    this.uploadDocType = section === 'address' ? 'PROOF_OF_ADDRESS' : 'PASSPORT';
    this.uploadFrontFile = null;
    this.uploadBackFile = null;
    this.uploadDocNumber = '';
    this.uploadIssueDate = '';
    this.uploadExpiryDate = '';
    this.uploadAddressSubType = '';
    this.uploadDocumentDate = '';
  }

  onDocTypeChange(): void {
    if (this.uploadDocType !== 'DRIVING_LICENCE') {
      this.uploadBackFile = null;
    }
  }

  readonly DOC_TYPES = [
    { value: 'PASSPORT',         label: 'Passport' },
    { value: 'DRIVING_LICENCE',  label: 'Driving Licence' },
    { value: 'NATIONAL_ID',      label: 'National ID' },
    { value: 'PROOF_OF_ADDRESS', label: 'Proof of Address' },
    { value: 'SOURCE_OF_FUNDS',  label: 'Source of Funds' },
  ];

  readonly ADDRESS_PROOF_TYPES = [
    'Utility Bill (within 3 months)',
    'Bank Statement (within 3 months)',
    'Council Tax Bill',
    'Government Letter (within 3 months)',
    'Lease/Rental Agreement',
    'Credit Card Statement (within 3 months)',
  ];

  riskScore: RiskScoreResponse | null = null;
  riskLoading = false;
  riskOverrideLevel: 'LOW' | 'MEDIUM' | 'HIGH' | '' = '';
  riskOverriding = false;

  editForm!: FormGroup;

  private blobUrls: string[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private fb: FormBuilder,
    private userService: UserService,
    private kycService: KycService,
    private transactionService: TransactionService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.uuid = this.route.snapshot.paramMap.get('uuid') || '';
    if (!this.uuid) {
      this.router.navigate(['/superadmin/users']);
      return;
    }
    this.loadAll();
  }

  loadAll(): void {
    this.loading = true;
    forkJoin({
      user: this.userService.getUserById(this.uuid),
      kycStatus: this.kycService.getStatus(this.uuid),
      documents: this.kycService.getDocuments(this.uuid)
    }).subscribe({
      next: ({ user, kycStatus, documents }) => {
        this.user = user;
        this.kycStatus = kycStatus;
        this.documents = documents || [];
        this.initForm(user);
        this.loadTransactions(user.id);
        this.loadRiskScore(user.id);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.showToast('Failed to load user profile.', 'danger');
      }
    });
  }

  loadRiskScore(numericId: number): void {
    if (!numericId) return;
    this.riskLoading = true;
    this.userService.getRiskScore(numericId).subscribe({
      next: (res) => {
        this.riskScore = res;
        this.riskOverrideLevel = (res?.riskScore as any) || '';
        this.riskLoading = false;
      },
      error: () => {
        this.riskScore = null;
        this.riskLoading = false;
      }
    });
  }

  saveRiskOverride(): void {
    if (!this.user || !this.riskOverrideLevel || this.riskOverriding) return;
    this.riskOverriding = true;
    this.userService.overrideRiskScore(this.user.id, this.riskOverrideLevel).subscribe({
      next: () => {
        this.riskOverriding = false;
        this.showToast(`Risk score overridden to ${this.riskOverrideLevel}.`, 'success');
        this.loadRiskScore(this.user!.id);
      },
      error: (err) => {
        this.riskOverriding = false;
        this.showToast(err.error?.message || 'Failed to override risk score.', 'danger');
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

  loadTransactions(userId: number): void {
    if (!userId) return;
    this.txPage = 0;
    this.txLoading = true;
    this.transactionService.list({ page: 0, size: this.TX_PAGE_SIZE, filterUserId: userId }).subscribe({
      next: (res: PageResponse<TransactionResponse>) => {
        this.transactions = res.content || [];
        this.totalTransactions = res.totalElements || 0;
        this.txLoading = false;
      },
      error: () => { this.transactions = []; this.txLoading = false; }
    });
  }

  loadMoreTransactions(): void {
    if (!this.user?.id || this.txLoading) return;
    this.txPage++;
    this.txLoading = true;
    this.transactionService.list({ page: this.txPage, size: this.TX_PAGE_SIZE, filterUserId: this.user.id }).subscribe({
      next: (res: PageResponse<TransactionResponse>) => {
        this.transactions = [...this.transactions, ...(res.content || [])];
        this.txLoading = false;
      },
      error: () => { this.txPage--; this.txLoading = false; }
    });
  }

  get hasMoreTransactions(): boolean {
    return this.transactions.length < this.totalTransactions;
  }

  initForm(user: UserResponse): void {
    this.editForm = this.fb.group({
      firstName: [user.firstName || ''],
      lastName: [user.lastName || ''],
      phone: [user.phone || ''],
      dateOfBirth: [user.dateOfBirth || ''],
      country: [user.country || ''],
      addressLine1: [user.addressLine1 || ''],
      addressLine2: [user.addressLine2 || ''],
      city: [user.city || ''],
      postcode: [user.postcode || ''],
      preferredLanguage: [user.preferredLanguage || '']
    });
  }

  /** Fill the address fields from a Smarty autocomplete selection. */
  onAddressSelected(detail: AddressDetail): void {
    if (!this.editForm) return;
    this.editForm.patchValue({
      addressLine1: detail.street || this.editForm.get('addressLine1')?.value || '',
      addressLine2: detail.address2 || this.editForm.get('addressLine2')?.value || '',
      city: detail.city || '',
      postcode: detail.postcode || ''
    });
    this.editForm.markAsDirty();
  }

  onSaveProfile(): void {
    if (!this.editForm.dirty || this.saving) return;
    this.saving = true;
    this.userService.updateUserAdmin(this.uuid, this.editForm.value).subscribe({
      next: (updated) => {
        this.user = updated;
        this.saving = false;
        this.editForm.markAsPristine();
        this.showToast('Profile updated successfully.', 'success');
      },
      error: (err) => {
        this.saving = false;
        this.showToast(err.error?.message || 'Failed to update profile.', 'danger');
      }
    });
  }

  async approveDoc(doc: KycDocumentResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Approve Document',
      message: `Approve ${this.formatDocType(doc.documentType)}?`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Approve',
          handler: () => {
            this.kycService.reviewDocument(this.uuid, doc.id, { status: KycDocumentStatus.APPROVED }).subscribe({
              next: () => {
                this.showToast('Document approved.', 'success');
                this.kycService.getDocuments(this.uuid).subscribe(docs => this.documents = docs || []);
                this.kycService.getStatus(this.uuid).subscribe(s => this.kycStatus = s);
              },
              error: (err) => this.showToast(err.error?.message || 'Failed to approve document.', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  async rejectDoc(doc: KycDocumentResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Reject Document',
      message: 'Please provide a rejection reason:',
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Enter reason...' }],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Reject',
          cssClass: 'alert-button-danger',
          handler: (data) => {
            if (!data.reason?.trim()) return false;
            this.kycService.reviewDocument(this.uuid, doc.id, {
              status: KycDocumentStatus.REJECTED,
              rejectionReason: data.reason.trim()
            }).subscribe({
              next: () => {
                this.showToast('Document rejected.', 'warning');
                this.kycService.getDocuments(this.uuid).subscribe(docs => this.documents = docs || []);
                this.kycService.getStatus(this.uuid).subscribe(s => this.kycStatus = s);
              },
              error: (err) => this.showToast(err.error?.message || 'Failed to reject document.', 'danger')
            });
            return true;
          }
        }
      ]
    });
    await alert.present();
  }

  async suspendUser(): Promise<void> {
    if (!this.user) return;
    const alert = await this.alertCtrl.create({
      header: 'Suspend User',
      message: `Suspend ${this.user.firstName} ${this.user.lastName}?`,
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Reason (optional)' }],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Suspend',
          cssClass: 'alert-button-danger',
          handler: (data) => {
            this.userService.suspendUser(this.user!.id.toString()).subscribe({
              next: (u) => { this.user = u; this.showToast('User suspended.', 'warning'); },
              error: () => this.showToast('Failed to suspend user.', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  activateUser(): void {
    this.userService.activateUser(this.user!.id.toString()).subscribe({
      next: (u) => { this.user = u; this.showToast('User activated.', 'success'); },
      error: () => this.showToast('Failed to activate user.', 'danger')
    });
  }

  getKycStatusClass(status: string | undefined): string {
    const s = (status || '').toUpperCase();
    if (s === 'PENDING' || s === 'PENDING_REVIEW') return 'warning';
    if (s === 'VERIFIED' || s === 'APPROVED') return 'success';
    if (s === 'REJECTED') return 'danger';
    return 'medium';
  }

  getDocStatusClass(status: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'PENDING') return 'warning';
    if (s === 'APPROVED') return 'success';
    if (s === 'REJECTED') return 'danger';
    return 'medium';
  }

  formatDocType(type: string): string {
    return (type || '').replace(/_/g, ' ');
  }

  openDoc(doc: KycDocumentResponse): void {
    this.kycService.getDocumentFileBlob(this.uuid, doc.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        this.blobUrls.push(url);
        window.open(url, '_blank');
      },
      error: () => this.showToast('Failed to load document preview.', 'danger')
    });
  }

  ngOnDestroy(): void {
    for (const url of this.blobUrls) {
      try { URL.revokeObjectURL(url); } catch {}
    }
    this.blobUrls = [];
  }

  // ── Admin Document Upload ──────────────────────────────────────

onFrontFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.uploadFrontFile = file;
  }

  onBackFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.uploadBackFile = file;
  }

  clearFront(): void { this.uploadFrontFile = null; }
  clearBack(): void  { this.uploadBackFile  = null; }

  async onAdminUpload(): Promise<void> {
    if (!this.uuid || !this.uploadFrontFile || this.uploading) return;
    this.uploading = true;

    const docNumber = this.isAddressProof
      ? (this.uploadAddressSubType || undefined)
      : (this.uploadDocNumber || undefined);
    const issueDate = this.isAddressProof
      ? (this.uploadDocumentDate || undefined)
      : (this.uploadIssueDate || undefined);
    const expiryDate = this.isAddressProof ? undefined : (this.uploadExpiryDate || undefined);

    try {
      await this.kycService.uploadDocument(
        this.uuid,
        this.uploadDocType,
        this.uploadFrontFile,
        docNumber,
        issueDate,
        expiryDate
      ).toPromise();

      if (this.uploadBackFile) {
        await this.kycService.uploadDocument(
          this.uuid,
          this.uploadDocType,
          this.uploadBackFile,
          docNumber ? docNumber + ' (back)' : undefined,
          undefined,
          undefined
        ).toPromise();
      }

      this.uploading = false;
      this.resetUploadForm();
      this.showToast('Document(s) uploaded successfully.', 'success');
      this.kycService.getDocuments(this.uuid).subscribe(docs => this.documents = docs || []);
      this.kycService.getStatus(this.uuid).subscribe(s => this.kycStatus = s);
    } catch (err: any) {
      this.uploading = false;
      this.showToast(err?.error?.message || 'Upload failed. Please try again.', 'danger');
    }
  }

  private resetUploadForm(): void {
    this.uploadSection = 'identity';
    this.uploadDocType = 'PASSPORT';
    this.uploadDocNumber = '';
    this.uploadIssueDate = '';
    this.uploadExpiryDate = '';
    this.uploadAddressSubType = '';
    this.uploadDocumentDate = '';
    this.uploadFrontFile = null;
    this.uploadBackFile = null;
  }

  goBack(): void {
    this.router.navigate(['/superadmin/users']);
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
