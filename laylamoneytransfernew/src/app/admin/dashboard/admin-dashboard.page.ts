import { Component, OnInit } from '@angular/core';
import { TransactionService } from '../../core/services/transaction.service';
import { ComplianceService } from '../../core/services/compliance.service';
import { UserService } from '../../core/services/user.service';
import { ComplianceAlertResponse } from '../../core/models/compliance.model';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.page.html',
  styleUrls: ['./admin-dashboard.page.scss']
})
export class AdminDashboardPage implements OnInit {
  kpis = {
    totalTransactions: '0',
    revenue: '$0',
    activeUsers: '0',
    pendingCompliance: '0'
  };

  recentAlerts: ComplianceAlertResponse[] = [];
  loading = true;

  constructor(
    private transactionService: TransactionService,
    private complianceService: ComplianceService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading = true;

    // Load transactions for KPIs
    this.transactionService.list({ page: 0, size: 1 }).subscribe({
      next: (res) => {
        this.kpis.totalTransactions = res.totalElements.toLocaleString();
      },
      error: () => {}
    });

    // Load users count
    this.userService.listUsers({ page: 0, size: 1 }).subscribe({
      next: (res) => {
        this.kpis.activeUsers = res.totalElements.toLocaleString();
      },
      error: () => {}
    });

    // Load compliance alerts
    this.complianceService.getAlerts({ page: 0, size: 5, status: 'OPEN' }).subscribe({
      next: (res) => {
        this.recentAlerts = res.content;
        this.kpis.pendingCompliance = res.totalElements.toString();
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  getSeverityColor(severity: string): string {
    const colors: Record<string, string> = {
      CRITICAL: 'danger',
      HIGH: 'danger',
      MEDIUM: 'warning',
      LOW: 'primary'
    };
    return colors[severity] || 'medium';
  }
}
