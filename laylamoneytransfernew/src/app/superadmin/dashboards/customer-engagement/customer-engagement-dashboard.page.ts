import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-customer-engagement-dashboard',
  templateUrl: './customer-engagement-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class CustomerEngagementDashboardPage implements OnInit {
  loading = true;
  totalUsers = 0;
  totalTransactions = 0;
  avgTxnsPerUser = '0';
  referralCodes = 'Coming soon';

  constructor(private ds: DashboardService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    forkJoin({
      userStats: this.ds.getUserStats(),
      txnStats: this.ds.getTransactionStats()
    }).subscribe({
      next: (d) => {
        this.totalUsers = d.userStats?.totalUsers || 0;
        this.totalTransactions = d.txnStats?.totalTransactions || 0;
        this.avgTxnsPerUser = this.totalUsers > 0
          ? (this.totalTransactions / this.totalUsers).toFixed(1)
          : '0';
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
