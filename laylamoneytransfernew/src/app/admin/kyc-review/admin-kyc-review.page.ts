import { Component, OnDestroy, OnInit } from '@angular/core';
import { AlertController, ToastController } from '@ionic/angular';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { UserService } from '../../core/services/user.service';
import { KycService } from '../../core/services/kyc.service';
import { ComplianceService } from '../../core/services/compliance.service';
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
  openComplianceAlerts?: number;
}

@Component({
  selector: 'app-admin-kyc-review',
  templateUrl: './admin-kyc-review.page.html',
  styleUrls: ['./admin-kyc-review.page.scss']
})
export class AdminKycReviewPage implements OnInit, OnDestroy {
  entries: KycUserEntry[] = [];
  filteredEntries: KycUserEntry[] = [];
  loading = true;
  private blobUrls: string[] = [];
  activeFilter: 'ALL' | 'PARTIAL' | 'PENDING' | 'VERIFIED' | 'REJECTED' = 'ALL';

  stats = { partial: 0, pending: 0, verified: 0, rejected: 0 };

  private apiUrl = environment.apiUrl;

  constructor(
    private userService: UserService,
    private kycService: KycService,
    private complianceService: ComplianceService,
    private http: HttpClient,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.userService.listUsers({ page: 0, size: 200, sort: 'recent' }).subscribe({
      next: (res) => {
        const users = res.content || [];
        const kycUsers = users.filter(u => !u.roles?.length || !u.roles.some((r: string) => ['ADMIN', 'SUPER_ADMIN'].includes(r)));

        if (kycUsers.length === 0) {
          this.entries = [];
          this.loading = false;
          this.applyFilter();
          return;
        }

        const statusCalls = kycUsers.map(u => this.kycService.getStatus(u.uuid));
        forkJoin(statusCalls).subscribe({
          next: (statuses) => {
            this.entries = kycUsers.map((user, i) => ({
              user,
              kycStatus: statuses[i],
              documents: [],
              expanded: false,
              loadingDocs: false,
              openComplianceAlerts: 0
            })).filter(e => e.kycStatus?.overallStatus && e.kycStatus.overallStatus !== 'NOT_SUBMITTED');

            this.computeStats();
            this.applyFilter();
            this.loading = false;
            this.loadComplianceCounts();
          },
          error: () => {
            // Fallback: show all non-admin users
            this.entries = kycUsers.map(user => ({
              user,
              documents: [],
              expanded: false,
              loadingDocs: false
            }));
            this.computeStats();
            this.applyFilter();
            this.loading = false;
          }
        });
      },
      error: () => {
        this.entries = [];
        this.loading = false;
      }
    });
  }

  computeStats(): void {
    this.stats = { partial: 0, pending: 0, verified: 0, rejected: 0 };
    for (const entry of this.entries) {
      const status = entry.kycStatus?.overallStatus?.toUpperCase() || '';
      if (status === 'PARTIAL') {
        this.stats.partial++;
      } else if (status === 'PENDING' || status === 'PENDING_REVIEW') {
        this.stats.pending++;
      } else if (status === 'VERIFIED' || status === 'APPROVED') {
        this.stats.verified++;
      } else if (status === 'REJECTED') {
        this.stats.rejected++;
      }
    }
  }

  applyFilter(): void {
    if (this.activeFilter === 'ALL') {
      this.filteredEntries = [...this.entries];
    } else {
      this.filteredEntries = this.entries.filter(e => {
        const status = e.kycStatus?.overallStatus?.toUpperCase() || '';
        if (this.activeFilter === 'PARTIAL') return status === 'PARTIAL';
        if (this.activeFilter === 'PENDING') return status === 'PENDING' || status === 'PENDING_REVIEW';
        if (this.activeFilter === 'VERIFIED') return status === 'VERIFIED' || status === 'APPROVED';
        if (this.activeFilter === 'REJECTED') return status === 'REJECTED';
        return true;
      });
    }
  }

  setFilter(filter: 'ALL' | 'PARTIAL' | 'PENDING' | 'VERIFIED' | 'REJECTED'): void {
    this.activeFilter = filter;
    this.applyFilter();
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
        // Hide legacy/imported placeholder docs (no real file — "no_image.png" / empty path).
        entry.documents = (docs || []).filter(d => {
          const p = (d.filePath || '').toLowerCase();
          return p && !p.includes('no_image');
        });
        entry.loadingDocs = false;
        for (const doc of entry.documents) {
          if (doc.fileUrl) {
            this.kycService.getDocumentFileBlob(entry.user.uuid, doc.id).subscribe({
              next: (blob) => {
                const url = URL.createObjectURL(blob);
                this.blobUrls.push(url);
                (doc as any).previewUrl = url;
                (doc as any).previewMime = blob.type || '';
                (doc as any).isImage = (blob.type || '').startsWith('image/');
                (doc as any).isPdf = (blob.type || '') === 'application/pdf';
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

  getStatusClass(status: string | undefined): string {
    const s = (status || '').toUpperCase();
    if (s === 'PENDING' || s === 'PENDING_REVIEW') return 'warning';
    if (s === 'VERIFIED' || s === 'APPROVED') return 'success';
    if (s === 'REJECTED') return 'danger';
    return 'medium';
  }

  getStatusLabel(status: string | undefined): string {
    const s = (status || '').toUpperCase();
    if (s === 'PENDING_REVIEW') return 'PENDING';
    if (s === 'NOT_SUBMITTED') return 'NOT SUBMITTED';
    return s || 'UNKNOWN';
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
    this.router.navigate(['/admin/compliance'], {
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
        this.computeStats();
        this.applyFilter();
      }
    });
  }

  openPreview(doc: any): void {
    if (doc.previewUrl) {
      window.open(doc.previewUrl, '_blank');
    }
  }

  ngOnDestroy(): void {
    for (const url of this.blobUrls) {
      try { URL.revokeObjectURL(url); } catch {}
    }
    this.blobUrls = [];
  }
}
