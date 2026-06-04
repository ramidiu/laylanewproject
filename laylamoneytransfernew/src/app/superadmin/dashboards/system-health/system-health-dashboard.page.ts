import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { catchError, of, forkJoin, map } from 'rxjs';

interface ServiceInfo {
  name: string;
  port: number;
  probeUrl: string;
  status: 'UP' | 'DOWN' | 'CHECKING';
  detail: string;
  responseTimeMs?: number;
}

@Component({
  selector: 'app-system-health-dashboard',
  templateUrl: './system-health-dashboard.page.html',
  styleUrls: ['../dashboard-common.scss']
})
export class SystemHealthDashboardPage implements OnInit {
  loading = true;
  lastChecked = '';
  private apiBase = environment.apiUrl;

  services: ServiceInfo[] = [
    { name: 'API Gateway', port: 8080, probeUrl: '/auth/check-email?email=probe@health', status: 'CHECKING', detail: 'Checking...' },
    { name: 'Auth Service', port: 8081, probeUrl: '/auth/check-email?email=probe@health', status: 'CHECKING', detail: 'Checking...' },
    { name: 'User Service', port: 8082, probeUrl: '/users?page=0&size=1', status: 'CHECKING', detail: 'Checking...' },
    { name: 'Transaction Service', port: 8083, probeUrl: '/transactions/config/active-countries', status: 'CHECKING', detail: 'Checking...' },
    { name: 'FX Service', port: 8084, probeUrl: '/fx/rates', status: 'CHECKING', detail: 'Checking...' },
    { name: 'Compliance Service', port: 8085, probeUrl: '/compliance/alerts?page=0&size=1', status: 'CHECKING', detail: 'Checking...' },
    { name: 'Notification Service', port: 8086, probeUrl: '/notifications?page=0&size=1', status: 'CHECKING', detail: 'Checking...' }
  ];

  constructor(private http: HttpClient) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.services = this.services.map(s => ({ ...s, status: 'CHECKING' as const, detail: 'Checking...' }));

    const probes = this.services.map(s => {
      const url = this.apiBase + s.probeUrl;
      const start = Date.now();
      return this.http.get(url, { observe: 'response' }).pipe(
        map(resp => ({
          up: resp.status >= 200 && resp.status < 500,
          ms: Date.now() - start
        })),
        catchError((err) => {
          const status = err?.status || 0;
          return of({
            up: status > 0 && status < 500,
            ms: Date.now() - start
          });
        })
      );
    });

    forkJoin(probes).subscribe(results => {
      this.services = this.services.map((s, i) => ({
        ...s,
        status: results[i].up ? 'UP' : 'DOWN',
        detail: results[i].up
          ? `Healthy (${results[i].ms}ms)`
          : 'Unreachable',
        responseTimeMs: results[i].ms
      }));
      this.lastChecked = new Date().toLocaleTimeString();
      this.loading = false;
    });
  }

  get upCount(): number {
    return this.services.filter(s => s.status === 'UP').length;
  }

  get downCount(): number {
    return this.services.filter(s => s.status === 'DOWN').length;
  }
}
