import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { ComplianceService } from '../../core/services/compliance.service';
import { ComplianceAuditEntry } from '../../core/models/compliance.model';
import { PageResponse } from '../../core/models/common.model';

type ActionFilter = '' | 'CLOSED_FALSE_POSITIVE' | 'CLOSED_SAR_FILED' | 'CLOSED_NO_ACTION' | 'ESCALATED';
type TypeFilter = '' | 'SANCTIONS' | 'PEP';

@Component({
  selector: 'app-sa-compliance-audit',
  templateUrl: './sa-compliance-audit.page.html',
  styleUrls: ['./sa-compliance-audit.page.scss']
})
export class SAComplianceAuditPage implements OnInit {
  entries: ComplianceAuditEntry[] = [];
  filteredEntries: ComplianceAuditEntry[] = [];
  loading = true;

  actionFilter: ActionFilter = '';
  typeFilter: TypeFilter = '';
  startDate = '';
  endDate = '';

  page = 0;
  pageSize = 50;
  totalPages = 0;
  totalElements = 0;

  constructor(
    private complianceService: ComplianceService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadAudit();
  }

  loadAudit(): void {
    this.loading = true;
    this.complianceService.getAuditTrail({
      page: this.page,
      size: this.pageSize,
      action: this.actionFilter || undefined,
      startDate: this.startDate || undefined,
      endDate: this.endDate || undefined
    }).subscribe({
      next: (res: PageResponse<ComplianceAuditEntry>) => {
        this.entries = res.content || [];
        this.totalPages = res.totalPages || 0;
        this.totalElements = res.totalElements || 0;
        this.applyTypeFilter();
        this.loading = false;
      },
      error: () => {
        this.entries = [];
        this.filteredEntries = [];
        this.loading = false;
      }
    });
  }

  applyTypeFilter(): void {
    if (!this.typeFilter) {
      this.filteredEntries = [...this.entries];
      return;
    }
    this.filteredEntries = this.entries.filter(e =>
      (e.listType || '').toUpperCase() === this.typeFilter
    );
  }

  setAction(action: ActionFilter): void {
    this.actionFilter = action;
    this.page = 0;
    this.loadAudit();
  }

  setType(type: TypeFilter): void {
    this.typeFilter = type;
    this.applyTypeFilter();
  }

  onDateChange(): void {
    this.page = 0;
    this.loadAudit();
  }

  clearDates(): void {
    this.startDate = '';
    this.endDate = '';
    this.page = 0;
    this.loadAudit();
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) {
      this.page++;
      this.loadAudit();
    }
  }

  prevPage(): void {
    if (this.page > 0) {
      this.page--;
      this.loadAudit();
    }
  }

  actionLabel(action: string): string {
    switch (action) {
      case 'CLOSED_FALSE_POSITIVE': return 'False Positive';
      case 'CLOSED_SAR_FILED': return 'Confirmed (SAR)';
      case 'CLOSED_NO_ACTION': return 'No Action';
      case 'ESCALATED': return 'Escalated';
      default: return action;
    }
  }

  actionColor(action: string): string {
    switch (action) {
      case 'CLOSED_FALSE_POSITIVE': return 'success';
      case 'CLOSED_SAR_FILED': return 'danger';
      case 'CLOSED_NO_ACTION': return 'medium';
      case 'ESCALATED': return 'warning';
      default: return 'medium';
    }
  }

  listTypeColor(t: string | undefined): string {
    if ((t || '').toUpperCase() === 'SANCTIONS') return 'danger';
    if ((t || '').toUpperCase() === 'PEP') return 'warning';
    return 'medium';
  }

  exportCsv(): void {
    if (this.filteredEntries.length === 0) {
      this.showToast('Nothing to export', 'warning');
      return;
    }
    const headers = [
      'Alert ID', 'Closed At', 'Action',
      'Customer ID', 'Customer Name', 'Customer Email',
      'Severity', 'List Type', 'Matched Name', 'Source',
      'Reviewer ID', 'Reviewer Name', 'Reason', 'Description'
    ];
    const rows = this.filteredEntries.map(e => [
      e.alertId,
      e.resolvedAt || '',
      e.status,
      e.customerId,
      e.customerName || '',
      e.customerEmail || '',
      e.severity,
      e.listType || '',
      e.matchedName || '',
      e.source || '',
      e.reviewerId || '',
      e.reviewerName || '',
      (e.reason || '').replace(/\r?\n/g, ' '),
      (e.description || '').replace(/\r?\n/g, ' ')
    ]);

    const csv = [headers, ...rows]
      .map(row => row.map(cell => this.csvEscape(cell)).join(','))
      .join('\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const date = new Date().toISOString().split('T')[0];
    a.download = `compliance-audit-${date}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private csvEscape(value: any): string {
    const s = value === null || value === undefined ? '' : String(value);
    if (s.includes(',') || s.includes('"') || s.includes('\n')) {
      return '"' + s.replace(/"/g, '""') + '"';
    }
    return s;
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
