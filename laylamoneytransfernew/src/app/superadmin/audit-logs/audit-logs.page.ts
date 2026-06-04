import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-audit-logs',
  templateUrl: './audit-logs.page.html',
  styleUrls: ['./audit-logs.page.scss']
})
export class AuditLogsPage implements OnInit {
  logs: any[] = [];
  loading = true;

  actionFilter = '';
  fromDate = '';
  toDate = '';
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  pageSize = 25;

  actionTypes = [
    'LOGIN', 'LOGIN_SUCCESS', 'LOGIN_FAILED', 'ADMIN_LOGIN_SUCCESS', 'ADMIN_LOGIN_FAILED',
    'KYC_UPDATE', 'DOCUMENT_UPLOADED', 'STATUS_CHANGED', 'TIER_UPGRADED', 'MANUAL_OVERRIDE', 'SCREENING_RUN',
    'TRANSACTION_STATUS_CHANGE',
    'CONFIG_CHANGE',
    'USER_CREATE', 'USER_UPDATE',
    'TRANSACTION_CREATE', 'TRANSACTION_UPDATE',
    'SETTLEMENT_CREATE', 'SETTLEMENT_APPROVE', 'SETTLEMENT_REJECT'
  ];

  private baseUrl = `${environment.apiUrl}/auth/admin/audit-logs`;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadLogs();
  }

  loadLogs(): void {
    this.loading = true;
    const params: any = { page: this.currentPage, size: this.pageSize };
    if (this.actionFilter) params.action = this.actionFilter;
    if (this.fromDate) params.startDate = this.fromDate;
    if (this.toDate) params.endDate = this.toDate;

    this.http.get<any>(this.baseUrl, { params }).subscribe({
      next: (res) => {
        if (res?.content) {
          this.logs = res.content;
          this.totalPages = res.totalPages;
          this.totalElements = res.totalElements;
        } else if (Array.isArray(res)) {
          this.logs = res;
          this.totalPages = 1;
          this.totalElements = res.length;
        } else {
          const data = res?.data;
          this.logs = data?.content || (Array.isArray(data) ? data : []);
          this.totalPages = data?.totalPages || 1;
          this.totalElements = data?.totalElements || this.logs.length;
        }
        this.loading = false;
      },
      error: () => { this.logs = []; this.loading = false; }
    });
  }

  onActionFilter(action: string): void {
    this.actionFilter = action;
    this.currentPage = 0;
    this.loadLogs();
  }

  onDateChange(): void {
    this.currentPage = 0;
    this.loadLogs();
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadLogs();
  }

  getActionColor(action: string): string {
    if (!action) return 'medium';
    if (action === 'TRANSACTION_STATUS_CHANGE') return 'tertiary';
    if (action === 'CONFIG_CHANGE') return 'warning';
    if (action?.includes('DELETE') || action?.includes('REJECT') || action?.includes('FAIL')) return 'danger';
    if (action?.includes('CREATE') || action?.includes('APPROVE') || action === 'TIER_UPGRADED') return 'success';
    if (action?.includes('UPDATE') || action?.includes('OVERRIDE') || action?.includes('SCREENING')) return 'warning';
    if (action?.includes('LOGIN') || action?.includes('LOGOUT')) return 'primary';
    if (action?.includes('KYC') || action?.includes('DOCUMENT') || action?.includes('STATUS')) return 'secondary';
    return 'medium';
  }
}
