import { Component, OnInit } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-support-dashboard',
  templateUrl: './support-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class SupportDashboardPage implements OnInit {
  loading = true;
  totalTickets = 0;
  openTickets = 0;
  resolvedTickets = 0;
  latestOpen: any[] = [];

  statusChart: ChartConfiguration<'doughnut'> = {
    type: 'doughnut',
    data: {
      labels: ['Open', 'Resolved', 'Other'],
      datasets: [{
        data: [0, 0, 0],
        backgroundColor: ['#f59e0b', '#059669', '#6b7280']
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
    this.ds.getSupportTickets().subscribe({
      next: (tickets) => {
        const all = Array.isArray(tickets) ? tickets : [];
        this.totalTickets = all.length;
        this.openTickets = all.filter((t: any) => t.status === 'OPEN' || t.status === 'NEW').length;
        this.resolvedTickets = all.filter((t: any) => t.status === 'RESOLVED' || t.status === 'CLOSED').length;
        const other = this.totalTickets - this.openTickets - this.resolvedTickets;

        this.statusChart.data!.datasets![0].data = [this.openTickets, this.resolvedTickets, other];

        this.latestOpen = all
          .filter((t: any) => t.status === 'OPEN' || t.status === 'NEW')
          .slice(0, 10);

        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
