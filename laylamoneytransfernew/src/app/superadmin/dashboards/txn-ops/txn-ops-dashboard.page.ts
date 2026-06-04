import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ChartConfiguration } from 'chart.js';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-txn-ops-dashboard',
  templateUrl: './txn-ops-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class TxnOpsDashboardPage implements OnInit {
  loading = true;
  createdToday = 0;
  completed = 0;
  failed = 0;
  onHold = 0;
  processing = 0;
  recentFailed: any[] = [];

  statusChart: ChartConfiguration<'doughnut'> = {
    type: 'doughnut',
    data: {
      labels: ['Completed', 'Processing', 'On Hold', 'Failed', 'Pending'],
      datasets: [{
        data: [0, 0, 0, 0, 0],
        backgroundColor: ['#059669', '#3b82f6', '#f59e0b', '#dc2626', '#6b7280']
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom' } }
    }
  };

  constructor(private ds: DashboardService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    forkJoin({
      stats: this.ds.getTransactionStats(),
      recent: this.ds.getRecentTransactions(20)
    }).subscribe({
      next: (d) => {
        const s = d.stats?.byStatus || {};
        this.completed = (s.PAID || 0) + (s.COMPLETED || 0);
        this.failed = (s.FAILED || 0) + (s.CANCELLED || 0);
        this.onHold = s.COMPLIANCE_HOLD || 0;
        this.processing = s.PROCESSING || 0;
        this.createdToday = d.stats?.totalTransactions || 0;

        this.statusChart.data!.datasets![0].data = [
          this.completed, this.processing, this.onHold, this.failed, s.PENDING || 0
        ];

        this.recentFailed = (d.recent || []).filter(
          (t: any) => ['FAILED', 'CANCELLED', 'COMPLIANCE_HOLD'].includes(t.status)
        );

        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
