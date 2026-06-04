import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular';
import { ComplianceService } from '../../core/services/compliance.service';
import {
  ComplianceAlertResponse,
  ComplianceAlertDetail,
  ComplianceMetrics,
  AlertStatus
} from '../../core/models/compliance.model';
import { PageResponse } from '../../core/models/common.model';

type StatusFilter = 'OPEN' | 'UNDER_REVIEW' | 'CLOSED_FALSE_POSITIVE' | 'CLOSED_SAR_FILED' | 'ESCALATED' | '';
type TypeFilter = '' | 'SANCTIONS' | 'PEP';

@Component({
  selector: 'app-admin-compliance',
  templateUrl: './admin-compliance.page.html',
  styleUrls: ['./admin-compliance.page.scss']
})
export class AdminCompliancePage implements OnInit {
  alerts: ComplianceAlertResponse[] = [];
  filteredAlerts: ComplianceAlertResponse[] = [];
  alertsLoading = true;

  metrics: ComplianceMetrics | null = null;

  statusFilter: StatusFilter = 'OPEN';
  typeFilter: TypeFilter = '';
  severityFilter: '' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' = '';
  sortOrder: 'createdAt,desc' | 'createdAt,asc' | 'severity,desc' = 'createdAt,desc';
  userIdFilter: number | null = null;

  page = 0;
  pageSize = 25;
  totalPages = 0;
  totalElements = 0;

  selectedAlert: ComplianceAlertDetail | null = null;
  detailLoading = false;
  dispositioning = false;
  openForSelectedUser = 0;

  constructor(
    private complianceService: ComplianceService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const userIdParam = this.route.snapshot.queryParamMap.get('userId');
    if (userIdParam) {
      const asNumber = Number(userIdParam);
      if (!isNaN(asNumber)) this.userIdFilter = asNumber;
    }
    this.loadAlerts();
    this.loadMetrics();
  }

  clearUserFilter(): void {
    this.userIdFilter = null;
    this.router.navigate([], { queryParams: { userId: null }, queryParamsHandling: 'merge', replaceUrl: true });
    this.page = 0;
    this.loadAlerts();
  }

  loadAlerts(): void {
    this.alertsLoading = true;
    this.complianceService.getAlerts({
      page: this.page,
      size: this.pageSize,
      sort: this.sortOrder,
      status: this.statusFilter || undefined,
      severity: this.severityFilter || undefined,
      userId: this.userIdFilter ?? undefined
    }).subscribe({
      next: (res: PageResponse<ComplianceAlertResponse>) => {
        this.alerts = res.content || [];
        this.totalPages = res.totalPages || 0;
        this.totalElements = res.totalElements || 0;
        this.applyLocalFilters();
        this.alertsLoading = false;
      },
      error: () => {
        this.alerts = [];
        this.filteredAlerts = [];
        this.alertsLoading = false;
      }
    });
  }

  loadMetrics(): void {
    this.complianceService.getMetrics().subscribe({
      next: (m) => (this.metrics = m),
      error: () => (this.metrics = null)
    });
  }

  applyLocalFilters(): void {
    if (!this.typeFilter) {
      this.filteredAlerts = [...this.alerts];
      return;
    }
    this.filteredAlerts = this.alerts.filter(a => {
      const details = this.parseDetails(a.details);
      return (details?.['listType'] || '').toString().toUpperCase() === this.typeFilter;
    });
  }

  setStatusFilter(status: StatusFilter): void {
    this.statusFilter = status;
    this.page = 0;
    this.loadAlerts();
  }

  setTypeFilter(type: TypeFilter): void {
    this.typeFilter = type;
    this.applyLocalFilters();
  }

  setSeverityFilter(severity: '' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'): void {
    this.severityFilter = severity;
    this.page = 0;
    this.loadAlerts();
  }

  setSortOrder(sortOrder: 'createdAt,desc' | 'createdAt,asc' | 'severity,desc'): void {
    this.sortOrder = sortOrder;
    this.page = 0;
    this.loadAlerts();
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) {
      this.page++;
      this.loadAlerts();
    }
  }

  prevPage(): void {
    if (this.page > 0) {
      this.page--;
      this.loadAlerts();
    }
  }

  openAlert(alert: ComplianceAlertResponse): void {
    this.detailLoading = true;
    this.selectedAlert = null;
    this.openForSelectedUser = 0;
    this.complianceService.getAlertDetail(alert.id).subscribe({
      next: (detail) => {
        this.selectedAlert = detail;
        this.detailLoading = false;
        if (detail.userId) {
          this.complianceService.getOpenAlertCounts([detail.userId]).subscribe({
            next: (counts) => {
              this.openForSelectedUser = Number(counts?.[detail.userId!] || 0);
            },
            error: () => { this.openForSelectedUser = 0; }
          });
        }
      },
      error: () => {
        this.detailLoading = false;
        this.showToast('Failed to load alert details', 'danger');
      }
    });
  }

  async bulkClearForCurrentUser(): Promise<void> {
    if (!this.selectedAlert?.userId) return;
    const userId = this.selectedAlert.userId;
    const count = this.openForSelectedUser;

    const dialog = await this.alertCtrl.create({
      header: 'Clear all for this customer',
      message: `This will mark all ${count} open alerts for customer #${userId} as false positive. The customer + each matched list entry will be whitelisted, and any held transactions released. A reason is required.`,
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Reason (required)' }],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Confirm bulk clear',
          handler: (data) => {
            if (!data.reason || !data.reason.trim()) {
              this.showToast('Reason is required', 'warning');
              return false;
            }
            this.submitBulkClear(userId, data.reason.trim());
            return true;
          }
        }
      ]
    });
    await dialog.present();
  }

  private submitBulkClear(userId: number, reason: string): void {
    this.dispositioning = true;
    this.complianceService.bulkDispositionForUser(userId, { action: 'FALSE_POSITIVE', reason }).subscribe({
      next: (res) => {
        this.dispositioning = false;
        this.showToast(`Cleared ${res.dispositioned} alerts for customer #${userId}`, 'success');
        this.selectedAlert = null;
        this.openForSelectedUser = 0;
        this.loadAlerts();
        this.loadMetrics();
      },
      error: (err) => {
        this.dispositioning = false;
        this.showToast(err?.error?.message || 'Bulk clear failed', 'danger');
      }
    });
  }

  closeDetail(): void {
    this.selectedAlert = null;
  }

  async disposition(action: 'FALSE_POSITIVE' | 'CONFIRMED_MATCH' | 'ESCALATE'): Promise<void> {
    if (!this.selectedAlert) return;
    const alertId = this.selectedAlert.id;
    const label = action === 'FALSE_POSITIVE' ? 'Clear as False Positive'
      : action === 'CONFIRMED_MATCH' ? 'Confirm Match'
      : 'Escalate to Case';

    const dialog = await this.alertCtrl.create({
      header: label,
      message: this.dispositionMessage(action),
      inputs: [{ name: 'reason', type: 'textarea', placeholder: 'Reason / notes (optional)' }],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Confirm',
          handler: (data) => {
            this.submitDisposition(alertId, action, data.reason || '');
          }
        }
      ]
    });
    await dialog.present();
  }

  private submitDisposition(alertId: number, action: 'FALSE_POSITIVE' | 'CONFIRMED_MATCH' | 'ESCALATE', reason: string): void {
    this.dispositioning = true;
    this.complianceService.dispositionAlert(alertId, { action, reason }).subscribe({
      next: () => {
        this.dispositioning = false;
        this.showToast(this.dispositionSuccessMessage(action), 'success');
        this.selectedAlert = null;
        this.loadAlerts();
        this.loadMetrics();
      },
      error: (err) => {
        this.dispositioning = false;
        this.showToast(err?.error?.message || 'Failed to disposition alert', 'danger');
      }
    });
  }

  private dispositionMessage(action: 'FALSE_POSITIVE' | 'CONFIRMED_MATCH' | 'ESCALATE'): string {
    if (action === 'FALSE_POSITIVE')
      return 'Mark this alert as a false positive. The customer + list entry will be added to the whitelist and any held transaction will be released.';
    if (action === 'CONFIRMED_MATCH')
      return 'Confirm this is a genuine match. The alert will be closed as SAR-filed.';
    return 'Escalate this alert to a compliance case for further investigation.';
  }

  private dispositionSuccessMessage(action: 'FALSE_POSITIVE' | 'CONFIRMED_MATCH' | 'ESCALATE'): string {
    if (action === 'FALSE_POSITIVE') return 'Alert cleared as false positive';
    if (action === 'CONFIRMED_MATCH') return 'Alert confirmed as genuine match';
    return 'Alert escalated';
  }

  severityColor(severity: string): string {
    switch ((severity || '').toUpperCase()) {
      case 'CRITICAL':
      case 'HIGH': return 'danger';
      case 'MEDIUM': return 'warning';
      case 'LOW': return 'primary';
      default: return 'medium';
    }
  }

  listTypeColor(listType: string | undefined): string {
    if (!listType) return 'medium';
    if (listType === 'SANCTIONS') return 'danger';
    if (listType === 'PEP') return 'warning';
    return 'primary';
  }

  parseDetails(raw: any): Record<string, any> | null {
    if (!raw) return null;
    if (typeof raw === 'object') return raw as any;
    try { return JSON.parse(raw); } catch { return null; }
  }

  detailScore(detail: Record<string, any> | null): string {
    if (!detail) return '';
    const score = detail['score'];
    if (typeof score === 'number') return score.toFixed(2);
    return String(score || '');
  }

  detailListType(detail: Record<string, any> | null): string {
    return detail?.['listType'] || '';
  }

  detailMatchedName(detail: Record<string, any> | null): string {
    return detail?.['matchedName'] || '';
  }

  detailScreenedName(detail: Record<string, any> | null): string {
    return detail?.['screenedName'] || '';
  }

  detailSource(detail: Record<string, any> | null): string {
    return detail?.['source'] || '';
  }

  statusLabel(status: AlertStatus | string): string {
    switch (status) {
      case AlertStatus.OPEN: return 'OPEN';
      case AlertStatus.UNDER_REVIEW: return 'UNDER REVIEW';
      case AlertStatus.ESCALATED: return 'ESCALATED';
      case AlertStatus.CLOSED_FALSE_POSITIVE: return 'FALSE POSITIVE';
      case AlertStatus.CLOSED_SAR_FILED: return 'CONFIRMED';
      case AlertStatus.CLOSED_NO_ACTION: return 'CLOSED';
      default: return String(status);
    }
  }

  parseArray(raw: string | undefined | null): string[] {
    if (!raw) return [];
    try {
      const val = JSON.parse(raw);
      if (Array.isArray(val)) return val.filter((x: any) => typeof x === 'string');
    } catch {}
    return [];
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
