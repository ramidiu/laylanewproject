import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ChartConfiguration } from 'chart.js';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-finance-dashboard',
  templateUrl: './finance-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class FinanceDashboardPage implements OnInit {
  loading = true;
  totalPaidVolume = 0;
  feeRevenue = 0;
  fxMarginRevenue = 0;
  partnerFloatTotal = 0;
  partnerBalances: any[] = [];

  revenueChart: ChartConfiguration<'bar'> = {
    type: 'bar',
    data: {
      labels: ['Fees', 'FX Margin'],
      datasets: [{
        label: 'Revenue (GBP)',
        data: [0, 0],
        backgroundColor: ['#1B3571', '#7c3aed']
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: { y: { beginAtZero: true } }
    }
  };

  constructor(private ds: DashboardService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    forkJoin({
      stats: this.ds.getTransactionStats(),
      balances: this.ds.getPartnerBalances()
    }).subscribe({
      next: (d) => {
        // Backend `/admin/stats` field names: totalVolume, totalRevenue, totalFxMargin.
        // (totalFees/paidVolume are compat aliases now; keep fallbacks for resilience.)
        this.totalPaidVolume = Number(d.stats?.totalVolume ?? d.stats?.paidVolume ?? 0);
        this.feeRevenue      = Number(d.stats?.totalRevenue ?? d.stats?.totalFees ?? 0);
        this.fxMarginRevenue = Number(d.stats?.totalFxMargin ?? d.stats?.totalMargin ?? d.stats?.fxMargin ?? 0);

        // Assign a NEW chart config so ng2-charts detects the change (in-place
        // mutation doesn't trigger redraw).
        this.revenueChart = {
          ...this.revenueChart,
          data: {
            labels: ['Fees', 'FX Margin'],
            datasets: [{
              label: 'Revenue (GBP)',
              data: [this.feeRevenue, this.fxMarginRevenue],
              backgroundColor: ['#1B3571', '#7c3aed']
            }]
          }
        };

        // Partner balances endpoint returns [{partnerId, partnerName, balance}, ...]
        const balArr = Array.isArray(d.balances) ? d.balances : (d.balances?.data || []);
        this.partnerBalances = balArr;
        this.partnerFloatTotal = balArr.reduce((sum: number, b: any) => sum + Number(b.balance ?? b.amount ?? 0), 0);

        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
