import { Component, OnInit } from '@angular/core';
import { AlertController, ToastController } from '@ionic/angular';
import { ComplianceService, CtrReport } from '../../core/services/compliance.service';
import { PageResponse } from '../../core/models/common.model';

type FilingStatusFilter = '' | 'DRAFT' | 'SUBMITTED' | 'ACKNOWLEDGED';

@Component({
  selector: 'app-sa-ctr-reports',
  templateUrl: './sa-ctr-reports.page.html',
  styleUrls: ['./sa-ctr-reports.page.scss']
})
export class SACtrReportsPage implements OnInit {
  reports: CtrReport[] = [];
  loading = true;

  filingStatus: FilingStatusFilter = '';
  startDate = '';
  endDate = '';

  page = 0;
  pageSize = 50;
  totalPages = 0;
  totalElements = 0;

  constructor(
    private complianceService: ComplianceService,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.complianceService.listCtrs({
      page: this.page,
      size: this.pageSize,
      filingStatus: this.filingStatus || undefined,
      startDate: this.startDate || undefined,
      endDate: this.endDate || undefined
    }).subscribe({
      next: (res: PageResponse<CtrReport>) => {
        this.reports = res.content || [];
        this.totalPages = res.totalPages || 0;
        this.totalElements = res.totalElements || 0;
        this.loading = false;
      },
      error: () => {
        this.reports = [];
        this.loading = false;
        this.toast('Failed to load CTR reports', 'danger');
      }
    });
  }

  setStatus(s: FilingStatusFilter): void {
    this.filingStatus = s;
    this.page = 0;
    this.load();
  }

  onDateChange(): void {
    this.page = 0;
    this.load();
  }

  clearDates(): void {
    this.startDate = '';
    this.endDate = '';
    this.page = 0;
    this.load();
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) {
      this.page++;
      this.load();
    }
  }

  prevPage(): void {
    if (this.page > 0) {
      this.page--;
      this.load();
    }
  }

  async submitCtr(r: CtrReport): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Mark CTR as SUBMITTED',
      message: `Customer #${r.userId} sent ${r.currency} ${r.totalAmount} across ${r.transactionCount} transactions on ${r.reportDate}. Enter the regulator reference for this filing.`,
      inputs: [
        { name: 'externalReference', type: 'text', placeholder: 'FinCEN / regulator reference (optional)' }
      ],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Submit',
          handler: (data) => {
            this.complianceService.submitCtr(r.id, data?.externalReference?.trim() || undefined).subscribe({
              next: () => {
                this.toast(`CTR #${r.id} marked as SUBMITTED`, 'success');
                this.load();
              },
              error: () => this.toast('Failed to submit CTR', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  parseRefs(json: string | undefined): string[] {
    if (!json) return [];
    try {
      const parsed = JSON.parse(json);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  statusColor(status: string): string {
    switch (status) {
      case 'DRAFT': return 'warning';
      case 'SUBMITTED': return 'primary';
      case 'ACKNOWLEDGED': return 'success';
      default: return 'medium';
    }
  }

  exportCsv(): void {
    if (this.reports.length === 0) {
      this.toast('Nothing to export', 'warning');
      return;
    }
    const headers = [
      'CTR ID', 'Report Date', 'Customer ID', 'Customer Name', 'Customer Email',
      'Txn Count', 'Total Amount', 'Currency', 'Threshold', 'Filing Status', 'Filed At', 'Refs'
    ];
    const rows = this.reports.map(r => [
      r.id, r.reportDate, r.userId, r.userName || '', r.userEmail || '',
      r.transactionCount, r.totalAmount, r.currency, r.threshold,
      r.filingStatus, r.filedAt || '',
      this.parseRefs(r.transactionRefs).join(' | ')
    ]);
    const csv = [headers, ...rows]
      .map(row => row.map(cell => this.csvEscape(cell)).join(','))
      .join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ctr-reports-${new Date().toISOString().split('T')[0]}.csv`;
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

  private async toast(message: string, color: string): Promise<void> {
    const t = await this.toastCtrl.create({
      message, duration: 3500, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await t.present();
  }
}
