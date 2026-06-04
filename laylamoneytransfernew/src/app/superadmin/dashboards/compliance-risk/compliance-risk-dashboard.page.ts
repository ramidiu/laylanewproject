import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ChartConfiguration } from 'chart.js';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-compliance-risk-dashboard',
  templateUrl: './compliance-risk-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class ComplianceRiskDashboardPage implements OnInit {
  loading = true;
  openAlertCount = 0;
  ctrDrafts = 0;
  sanctionsEntries = 0;
  screeningHitRate = 'N/A';
  openAlerts: any[] = [];
  latestAlerts: any[] = [];

  severityChart: ChartConfiguration<'doughnut'> = {
    type: 'doughnut',
    data: {
      labels: ['HIGH', 'MEDIUM', 'LOW'],
      datasets: [{
        data: [0, 0, 0],
        backgroundColor: ['#dc2626', '#f59e0b', '#3b82f6']
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
      alerts: this.ds.getOpenAlerts(),
      ctr: this.ds.getCtrStats(),
      ingest: this.ds.getIngestStatus()
    }).subscribe({
      next: (d) => {
        this.openAlerts = d.alerts || [];
        this.openAlertCount = this.openAlerts.length;
        this.latestAlerts = this.openAlerts.slice(0, 10);

        const high = this.openAlerts.filter((a: any) => a.severity === 'HIGH').length;
        const medium = this.openAlerts.filter((a: any) => a.severity === 'MEDIUM').length;
        const low = this.openAlerts.filter((a: any) => a.severity === 'LOW').length;
        this.severityChart.data!.datasets![0].data = [high, medium, low];

        this.ctrDrafts = d.ctr?.totalCtrs || 0;
        this.sanctionsEntries = d.ingest?.totalEntries || d.ingest?.count || 0;

        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
