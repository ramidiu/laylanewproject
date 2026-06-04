import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of, catchError, map } from 'rxjs';
import { ChartConfiguration } from 'chart.js';
import { DashboardService } from '../../../core/services/dashboard.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-user-mgmt-dashboard',
  templateUrl: './user-mgmt-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class UserMgmtDashboardPage implements OnInit {
  loading = true;
  totalUsers = 0;
  kycPending = 0;
  activeUsers = 0;
  suspendedUsers = 0;

  tierChart: ChartConfiguration<'doughnut'> = {
    type: 'doughnut',
    data: {
      labels: ['TIER_0', 'TIER_1', 'TIER_2', 'TIER_3'],
      datasets: [{
        data: [0, 0, 0, 0],
        backgroundColor: ['#6b7280', '#3b82f6', '#8b5cf6', '#059669']
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom' } }
    }
  };

  private api = environment.apiUrl;

  constructor(private ds: DashboardService, private http: HttpClient) {}

  ngOnInit(): void { this.load(); }

  private tierCount(tier: string) {
    return this.http.get<any>(`${this.api}/users`, { params: { page: '0', size: '1', kycTier: tier } })
      .pipe(map(r => (r.data || r)?.totalElements || 0), catchError(() => of(0)));
  }

  load(): void {
    this.loading = true;
    forkJoin({
      userStats: this.ds.getUserStats(),
      kycPending: this.ds.getKycPending(),
      tier0: this.tierCount('TIER_0'),
      tier1: this.tierCount('TIER_1'),
      tier2: this.tierCount('TIER_2'),
      tier3: this.tierCount('TIER_3')
    }).subscribe({
      next: (d) => {
        this.totalUsers = d.userStats?.totalUsers || 0;
        this.kycPending = d.kycPending?.pending || 0;
        this.activeUsers = this.totalUsers - this.kycPending;
        this.suspendedUsers = 0;

        this.tierChart.data!.datasets![0].data = [d.tier0, d.tier1, d.tier2, d.tier3];

        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
