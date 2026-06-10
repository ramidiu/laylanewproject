import { Component, OnDestroy, OnInit, SecurityContext } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AlertController } from '@ionic/angular';
import { UserService, RiskScoreResponse } from '../../core/services/user.service';
import { KycService } from '../../core/services/kyc.service';
import { ComplianceService } from '../../core/services/compliance.service';
import { PartnerService } from '../../core/services/partner.service';
import { UserResponse } from '../../core/models/user.model';
import { KycDocumentResponse, KycDocumentStatus, KycStatusResponse } from '../../core/models/kyc.model';
import { environment } from '../../../environments/environment';
import { forkJoin } from 'rxjs';

interface KycUserEntry {
  user: UserResponse;
  kycStatus?: KycStatusResponse;
  documents: KycDocumentResponse[];
  expanded: boolean;
  loadingDocs: boolean;
  riskScore?: RiskScoreResponse | null;
  riskLoading?: boolean;
  openComplianceAlerts?: number;
}

@Component({
  selector: 'app-sa-kyc-review',
  templateUrl: './sa-kyc-review.page.html',
  styleUrls: ['./sa-kyc-review.page.scss']
})
export class SAKycReviewPage implements OnInit, OnDestroy {
  entries: KycUserEntry[] = [];
  filteredEntries: KycUserEntry[] = [];
  loading = true;
  private blobUrls: string[] = [];
  activeFilter: 'PARTIAL' | 'PENDING' = 'PENDING';
  searchQuery = '';

  stats = { pending: 0, verified: 0, rejected: 0 };
  partialCount = 0;

  currentPage = 0;
  pageSize = 20;
  totalPages = 0;
  totalElements = 0;

  // PayIn customers tab
  activeMainTab: 'USERS' | 'PAYIN' = 'USERS';
  payinCustomers: any[] = [];
  payinFilteredCustomers: any[] = [];
  payinSearchQuery = '';
  payinLoading = true;
  selectedPayinCustomer: any = null;
  payinCustomerDocs: any[] = [];
  payinDocsLoading = false;

  constructor(
    private userService: UserService,
    private kycService: KycService,
    private complianceService: ComplianceService,
    private partnerService: PartnerService,
    private alertCtrl: AlertController,
    private router: Router,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.loadStats();
    this.loadUsers();
    this.loadPayinCustomers();
  }

  loadStats(): void {
    this.userService.listUsers({ page: 0, size: 1, kycStatus: 'PENDING' }).subscribe({
      next: (res) => { this.stats.pending = res.totalElements || 0; }
    });
    this.userService.listUsers({ page: 0, size: 1, kycStatus: 'PARTIAL' }).subscribe({
      next: (res) => { this.partialCount = res.totalElements || 0; }
    });
  }

  loadPayinCustomers(): void {
    this.payinLoading = true;
    this.partnerService.getPayinCustomers().subscribe({
      next: (res: any) => {
        const list = Array.isArray(res) ? res : (res?.data || []);
        this.payinCustomers = list.sort((a: any, b: any) =>
          new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
        );
        this.payinFilteredCustomers = [...this.payinCustomers];
        this.payinLoading = false;
      },
      error: () => { this.payinCustomers = []; this.payinFilteredCustomers = []; this.payinLoading = false; }
    });
  }

  onPayinSearch(query: string): void {
    this.payinSearchQuery = query;
    const q = query.toLowerCase().trim();
    if (!q) {
      this.payinFilteredCustomers = [...this.payinCustomers];
    } else {
      this.payinFilteredCustomers = this.payinCustomers.filter(c =>
        (c.firstName + ' ' + c.lastName).toLowerCase().includes(q) ||
        (c.email || '').toLowerCase().includes(q) ||
        (c.customerId || '').toLowerCase().includes(q)
      );
    }
  }

  selectPayinCustomer(c: any): void {
    this.selectedPayinCustomer = c;
    this.payinCustomerDocs = [];
    this.payinDocsLoading = true;
    this.partnerService.getPayinCustomerDocuments(c.customerId).subscribe({
      next: (res: any) => {
        this.payinCustomerDocs = Array.isArray(res) ? res : (res?.data || []);
        this.payinDocsLoading = false;
      },
      error: () => { this.payinCustomerDocs = []; this.payinDocsLoading = false; }
    });
  }

  togglePayinCustomer(c: any): void {
    c._expanded = !c._expanded;
    if (c._expanded && !c._docs && !c._docsLoading) {
      c._docsLoading = true;
      this.partnerService.getPayinCustomerDocuments(c.customerId).subscribe({
        next: (res: any) => {
          c._docs = Array.isArray(res) ? res : (res?.data || []);
          c._docsLoading = false;
        },
        error: () => { c._docs = []; c._docsLoading = false; }
      });
    }
  }

  getPayinDocUrl(docId: number): string {
    return `${environment.apiUrl}/payin/customer/document/${docId}/file`;
  }

  loadUsers(): void {
    this.loading = true;
    this.userService.listUsers({
      page: this.currentPage,
      size: this.pageSize,
      kycStatus: this.activeFilter,
      sort: 'recent'
    }).subscribe({
      next: (res) => {
        this.totalPages = res.totalPages || 0;
        this.totalElements = res.totalElements || 0;
        const users = res.content || [];
        const EXCLUDED_ROLES = ['ADMIN', 'SUPER_ADMIN', 'PAYIN_PARTNER', 'PAYOUT_PARTNER', 'COMPLIANCE_OFFICER', 'TREASURY_MANAGER', 'SUPPORT', 'FINANCE', 'AGENT'];
        const kycUsers = users.filter(u => !u.roles?.length || !u.roles.some((r: string) => EXCLUDED_ROLES.includes(r)));

        if (kycUsers.length === 0) {
          this.entries = [];
          this.loading = false;
          this.applyFilter();
          return;
        }

        const sortedUsers = kycUsers.sort((a, b) =>
          new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
        );
        const statusCalls = sortedUsers.map(u => this.kycService.getStatus(u.uuid));
        forkJoin(statusCalls).subscribe({
          next: (statuses) => {
            this.entries = sortedUsers.map((user, i) => ({
              user,
              kycStatus: statuses[i],
              documents: [],
              expanded: false,
              loadingDocs: false,
              riskScore: null,
              riskLoading: true,
              openComplianceAlerts: 0
            }));

            this.applyFilter();
            this.loading = false;
            this.loadRiskScores();
            this.loadComplianceCounts();
          },
          error: () => {
            this.entries = sortedUsers.map(user => ({
              user,
              documents: [],
              expanded: false,
              loadingDocs: false,
              riskScore: null,
              riskLoading: true,
              openComplianceAlerts: 0
            }));
            this.applyFilter();
            this.loading = false;
            this.loadRiskScores();
            this.loadComplianceCounts();
          }
        });
      },
      error: () => {
        this.entries = [];
        this.loading = false;
      }
    });
  }

  goToPage(page: number): void {
    if (page < 0 || (this.totalPages && page >= this.totalPages)) return;
    this.currentPage = page;
    this.loadUsers();
  }

  private loadComplianceCounts(): void {
    const userIds = this.entries
      .map(e => e.user?.id)
      .filter((id): id is number => typeof id === 'number' && id > 0);
    if (userIds.length === 0) return;
    this.complianceService.getOpenAlertCounts(userIds).subscribe({
      next: (counts) => {
        for (const entry of this.entries) {
          entry.openComplianceAlerts = Number(counts?.[entry.user.id] || 0);
        }
      },
      error: () => {}
    });
  }

  openComplianceForUser(entry: KycUserEntry): void {
    if (!entry.user?.id) return;
    this.router.navigate(['/superadmin/compliance'], {
      queryParams: { userId: entry.user.id }
    });
  }

  private async showComplianceBlockDialog(entry: KycUserEntry): Promise<void> {
    const count = entry.openComplianceAlerts || 0;
    const dialog = await this.alertCtrl.create({
      header: 'Compliance review required',
      message: `This customer has ${count} open compliance alert${count === 1 ? '' : 's'}. Clear them first in the compliance queue before approving any KYC documents.`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Open compliance queue',
          handler: () => this.openComplianceForUser(entry)
        }
      ]
    });
    await dialog.present();
  }

  private loadRiskScores(): void {
    for (const entry of this.entries) {
      if (!entry.user?.id) {
        entry.riskLoading = false;
        continue;
      }
      this.userService.getRiskScore(entry.user.id).subscribe({
        next: (res) => {
          entry.riskScore = res;
          entry.riskLoading = false;
        },
        error: () => {
          entry.riskScore = null;
          entry.riskLoading = false;
        }
      });
    }
  }

  riskColor(level: string | undefined): string {
    switch ((level || '').toUpperCase()) {
      case 'LOW': return 'success';
      case 'MEDIUM': return 'warning';
      case 'HIGH': return 'danger';
      default: return 'medium';
    }
  }

  computeStats(): void {
    this.stats = { pending: 0, verified: 0, rejected: 0 };
    this.partialCount = 0;
    for (const entry of this.entries) {
      const status = entry.kycStatus?.overallStatus?.toUpperCase() || 'NOT_SUBMITTED';
      if (status === 'PARTIAL') {
        this.partialCount++;
        continue;
      }
      if (status === 'PENDING' || status === 'PENDING_REVIEW') {
        this.stats.pending++;
      } else if (status === 'VERIFIED' || status === 'APPROVED') {
        this.stats.verified++;
      } else if (status === 'REJECTED') {
        this.stats.rejected++;
      }
    }
  }

  viewProfile(entry: KycUserEntry): void {
    this.router.navigate(['/superadmin/users', entry.user.uuid]);
  }

  applyFilter(): void {
    const q = this.searchQuery.toLowerCase().trim();
    let result = this.entries.filter(e => {
      const status = e.kycStatus?.overallStatus?.toUpperCase() || 'NOT_SUBMITTED';
      // PARTIAL = imported-only (no real submission); PENDING = a real document awaiting review.
      if (this.activeFilter === 'PARTIAL') return status === 'PARTIAL';
      if (this.activeFilter === 'PENDING') return status === 'PENDING' || status === 'PENDING_REVIEW';
      return true;
    });

    if (q) {
      result = result.filter(e => {
        const fullName = ((e.user.firstName || '') + ' ' + (e.user.lastName || '')).toLowerCase();
        const email = (e.user.email || '').toLowerCase();
        const id = String(e.user.id || '');
        return fullName.includes(q) || email.includes(q) || id.includes(q);
      });
    }
    this.filteredEntries = result;
  }

  onSearch(query: string): void {
    this.searchQuery = query;
    this.applyFilter();
  }

  setFilter(filter: 'PARTIAL' | 'PENDING'): void {
    this.activeFilter = filter;
    this.currentPage = 0;
    this.loadUsers();
  }

  toggleExpand(entry: KycUserEntry): void {
    entry.expanded = !entry.expanded;
    if (entry.expanded && entry.documents.length === 0) {
      this.loadDocuments(entry);
    }
  }

  loadDocuments(entry: KycUserEntry): void {
    entry.loadingDocs = true;
    this.kycService.getDocuments(entry.user.uuid).subscribe({
      next: (docs) => {
        // Hide only the imported/placeholder docs (no real file). Keep every real upload —
        // realUpload===false OR a "no_image" path = placeholder; anything else stays visible.
        entry.documents = (docs || []).filter(d => {
          const p = (d.filePath || '').toLowerCase();
          return (d as any).realUpload !== false && !p.includes('no_image');
        });
        entry.loadingDocs = false;
        for (const doc of entry.documents) {
          if (doc.fileUrl) {
            this.kycService.getDocumentFileBlob(entry.user.uuid, doc.id).subscribe({
              next: (blob) => {
                const rawUrl = URL.createObjectURL(blob);
                this.blobUrls.push(rawUrl);
                const mime = blob.type || '';
                (doc as any).isImage = mime.startsWith('image/');
                (doc as any).isPdf = mime === 'application/pdf';
                (doc as any).rawBlobUrl = rawUrl;
                (doc as any).previewUrl = (doc as any).isPdf
                    ? this.sanitizer.bypassSecurityTrustResourceUrl(rawUrl)
                    : this.sanitizer.bypassSecurityTrustUrl(rawUrl);
                (doc as any).previewMime = mime;
                console.log('[KYC] loaded preview for doc', doc.id, 'mime:', mime, 'isImage:', (doc as any).isImage);
              },
              error: () => {
                (doc as any).previewUrl = null;
                (doc as any).previewMime = '';
                (doc as any).isImage = false;
                (doc as any).isPdf = false;
              }
            });
          }
        }
      },
      error: () => {
        entry.documents = [];
        entry.loadingDocs = false;
      }
    });
  }

  isPendingVerification(entry: KycUserEntry): boolean {
    // "Profile Changed — Awaiting Re-verification" applies ONLY when a verified user
    // edits their profile AFTER KYC approval. A newly-registered customer who just
    // got TIER_2 approved isn't a profile change — their updated_at is before/at the
    // last verifiedAt timestamp.
    const tier = (entry.user.kycTier || '').toString().toUpperCase();
    const isVerified = !!tier && tier !== 'TIER_0' && tier !== 'NONE';
    if (!isVerified) return false;
    if (entry.user.status !== 'PENDING_VERIFICATION') return false;

    // Compare user.updatedAt vs the most-recent doc reviewedAt — if the user's row
    // was touched AFTER the latest KYC approval, the profile was actually changed.
    const userUpdated = entry.user.updatedAt ? Date.parse(entry.user.updatedAt) : 0;
    const lastReviewed = (entry.documents || [])
      .map(d => d.reviewedAt ? Date.parse(d.reviewedAt) : 0)
      .reduce((a, b) => Math.max(a, b), 0);

    if (!userUpdated || !lastReviewed) return false;     // no comparable timestamps → not a change
    // Add a 5-second tolerance so the auto-set TIER_2 right after doc approval
    // doesn't accidentally trigger the badge.
    return userUpdated > lastReviewed + 5_000;
  }

  getStatusClass(status: string | undefined, entry?: KycUserEntry): string {
    if (entry && this.isPendingVerification(entry)) return 'warning';
    const s = (status || '').toUpperCase();
    if (s === 'PENDING' || s === 'PENDING_REVIEW') return 'warning';
    if (s === 'VERIFIED' || s === 'APPROVED') return 'success';
    if (s === 'REJECTED') return 'danger';
    return 'medium';
  }

  getStatusLabel(status: string | undefined, entry?: KycUserEntry): string {
    if (entry && this.isPendingVerification(entry)) return 'PROFILE CHANGED';
    const s = (status || '').toUpperCase();
    if (s === 'PENDING_REVIEW') return 'PENDING';
    if (s === 'NOT_SUBMITTED') return 'NOT SUBMITTED';
    return s || 'UNKNOWN';
  }

  async reVerifyUser(entry: KycUserEntry): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Re-verify User',
      message: `Approve profile changes for ${entry.user.firstName} ${entry.user.lastName}? This will restore their account and allow transactions.`,
      inputs: [
        {
          name: 'tier',
          type: 'radio',
          label: 'TIER_1 (Basic)',
          value: 'TIER_1'
        },
        {
          name: 'tier',
          type: 'radio',
          label: 'TIER_2 (Standard)',
          value: 'TIER_2',
          checked: true
        },
        {
          name: 'tier',
          type: 'radio',
          label: 'TIER_3 (Premium)',
          value: 'TIER_3'
        }
      ],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Approve',
          handler: (tier) => {
            this.userService.updateUserAdmin(entry.user.uuid, { status: 'ACTIVE', kycTier: tier || 'TIER_2' }).subscribe({
              next: () => {
                entry.user.status = 'ACTIVE' as any;
                entry.user.kycTier = (tier || 'TIER_2') as any;
                this.loadStats();
                this.applyFilter();
              },
              error: () => {}
            });
          }
        }
      ]
    });
    await alert.present();
  }

  getDocStatusClass(status: KycDocumentStatus | string): string {
    const s = (status || '').toUpperCase();
    if (s === 'PENDING') return 'warning';
    if (s === 'APPROVED') return 'success';
    if (s === 'REJECTED') return 'danger';
    return 'medium';
  }

  formatDocType(type: string): string {
    return (type || '').replace(/_/g, ' ');
  }

  approveDoc(entry: KycUserEntry, doc: KycDocumentResponse): void {
    const openAlerts = entry.openComplianceAlerts || 0;
    if (openAlerts > 0) {
      this.showComplianceBlockDialog(entry);
      return;
    }
    this.kycService.reviewDocument(entry.user.uuid, doc.id, {
      status: KycDocumentStatus.APPROVED
    }).subscribe({
      next: () => {
        this.loadDocuments(entry);
        this.refreshEntryStatus(entry);
      },
      error: () => {}
    });
  }

  async rejectDoc(entry: KycUserEntry, doc: KycDocumentResponse): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Reject Document',
      message: 'Please provide a reason for rejection:',
      inputs: [
        {
          name: 'reason',
          type: 'textarea',
          placeholder: 'Enter rejection reason...'
        }
      ],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Reject',
          cssClass: 'alert-button-danger',
          handler: (data) => {
            if (!data.reason || !data.reason.trim()) return false;
            this.kycService.reviewDocument(entry.user.uuid, doc.id, {
              status: KycDocumentStatus.REJECTED,
              rejectionReason: data.reason.trim()
            }).subscribe({
              next: () => {
                this.loadDocuments(entry);
                this.refreshEntryStatus(entry);
              },
              error: () => {}
            });
            return true;
          }
        }
      ]
    });
    await alert.present();
  }

  refreshEntryStatus(entry: KycUserEntry): void {
    this.kycService.getStatus(entry.user.uuid).subscribe({
      next: (status) => {
        entry.kycStatus = status;
        this.loadStats();
        this.applyFilter();
      }
    });
  }

  openPreview(doc: any): void {
    // Open the RAW blob URL (a plain string) in a new tab. doc.previewUrl is a sanitized
    // SafeUrl object for the [src] binding — passing it to window.open navigates to a
    // bogus URL (lands on the home page), so use the raw blob URL instead.
    if (doc.rawBlobUrl) {
      window.open(doc.rawBlobUrl, '_blank');
    }
  }

  ngOnDestroy(): void {
    for (const url of this.blobUrls) {
      try { URL.revokeObjectURL(url); } catch {}
    }
    this.blobUrls = [];
  }
}
