import { Component, OnInit } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-executive-dashboard',
  templateUrl: './executive-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class ExecutiveDashboardPage implements OnInit {
  loading = true;
  totalVolume = 0; totalRevenue = 0; totalUsers = 0; totalTxns = 0;
  openAlerts = 0; recentTxns: any[] = [];

  volumeChart: ChartConfiguration<'line'> = {
    type: 'line',
    data: { labels: [], datasets: [{ label: 'Daily Volume', data: [], borderColor: '#1B3571', backgroundColor: 'rgba(27,53,113,0.08)', fill: true, tension: 0.3 }] },
    options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
  };

  statusChart: ChartConfiguration<'doughnut'> = {
    type: 'doughnut',
    data: { labels: ['Completed', 'Pending', 'Processing', 'On Hold', 'Failed'], datasets: [{ data: [0, 0, 0, 0, 0], backgroundColor: ['#059669', '#f59e0b', '#3b82f6', '#ef4444', '#6b7280'] }] },
    options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }
  };

  constructor(private ds: DashboardService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.ds.getExecutiveDashboard().subscribe({
      next: (d) => {
        if (d.txnStats) {
          this.totalVolume = d.txnStats.totalVolume || d.txnStats.paidVolume || 0;
          this.totalRevenue = d.txnStats.totalRevenue || d.txnStats.paidRevenue || 0;
          this.totalTxns = d.txnStats.totalTransactions || 0;

          // Prefer the keyed byStatus map; fall back to the flat *Count fields on older backends.
          const byStatus: any = d.txnStats.byStatus || {
            PAID: d.txnStats.completedCount || 0,
            COMPLETED: 0,
            PENDING: d.txnStats.pendingCount || 0,
            PROCESSING: d.txnStats.processingCount || 0,
            COMPLIANCE_HOLD: 0,
            FAILED: d.txnStats.failedCount || 0,
            CANCELLED: d.txnStats.cancelledCount || 0
          };
          // Assign a NEW data reference so Angular's input diffing picks it up and
          // the chart redraws. In-place array mutation was silently no-op'ing.
          this.statusChart = {
            ...this.statusChart,
            data: {
              labels: ['Completed', 'Pending', 'Processing', 'On Hold', 'Failed'],
              datasets: [{
                data: [
                  (byStatus.PAID || 0) + (byStatus.COMPLETED || 0),
                  byStatus.PENDING || 0,
                  byStatus.PROCESSING || 0,
                  byStatus.COMPLIANCE_HOLD || 0,
                  (byStatus.FAILED || 0) + (byStatus.CANCELLED || 0)
                ],
                backgroundColor: ['#059669', '#f59e0b', '#3b82f6', '#ef4444', '#6b7280']
              }]
            }
          };
        }
        this.totalUsers = d.userStats?.totalUsers || 0;
        this.openAlerts = d.alertStats?.totalAlerts || 0;
        this.recentTxns = d.recent || [];
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
