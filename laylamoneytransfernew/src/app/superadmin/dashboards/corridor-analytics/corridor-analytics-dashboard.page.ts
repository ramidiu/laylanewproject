import { Component, OnInit } from '@angular/core';
import { DashboardService } from '../../../core/services/dashboard.service';

@Component({
  selector: 'app-corridor-analytics-dashboard',
  templateUrl: './corridor-analytics-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class CorridorAnalyticsDashboardPage implements OnInit {
  loading = true;
  corridors: any[] = [];
  activeCorridors = 0;
  totalCorridors = 0;

  constructor(private ds: DashboardService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.ds.getCorridors().subscribe({
      next: (data) => {
        this.corridors = Array.isArray(data) ? data : [];
        this.totalCorridors = this.corridors.length;
        this.activeCorridors = this.corridors.filter((c: any) => c.isActive || c.active).length;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }
}
