import { Component, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-partner-perf-dashboard',
  templateUrl: './partner-perf-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class PartnerPerfDashboardPage implements OnInit {
  loading = true;
  payoutPartners: any[] = [];
  payinPartners: any[] = [];
  payoutCount = 0;
  payinCount = 0;
  activeCount = 0;
  totalVolume = 0;

  constructor(private ds: DashboardService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    forkJoin({
      payout: this.ds.getPayoutPartners(),
      payin: this.ds.getPayinPartners()
    }).subscribe({
      next: (d) => {
        this.payoutPartners = Array.isArray(d.payout) ? d.payout : [];
        this.payinPartners = Array.isArray(d.payin) ? d.payin : [];
        this.payoutCount = this.payoutPartners.length;
        this.payinCount = this.payinPartners.length;

        const all = [...this.payoutPartners, ...this.payinPartners];
        this.activeCount = all.filter((p: any) => p.active || p.status === 'ACTIVE').length;
        this.totalVolume = all.reduce((sum: number, p: any) => sum + (p.totalVolume || 0), 0);

        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
