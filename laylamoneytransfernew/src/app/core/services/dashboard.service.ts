import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, forkJoin, map, catchError } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private api = environment.apiUrl;
  constructor(private http: HttpClient) {}

  // ApiResponseUnwrapInterceptor already strips {success,data} → res IS the inner payload.
  private get<T>(url: string): Observable<T> {
    return this.http.get<any>(url).pipe(catchError(() => of(null as any)));
  }

  // ---------- Executive ----------
  getTransactionStats(): Observable<any> { return this.get(`${this.api}/transactions/admin/stats`); }
  getRecentTransactions(limit = 10): Observable<any> {
    return this.http.get<any>(`${this.api}/transactions`, { params: { page: '0', size: limit.toString(), sortBy: 'createdAt', sortDir: 'desc' } })
      .pipe(map(r => r?.content || []), catchError(() => of([])));
  }

  // ---------- User Management ----------
  getUserStats(): Observable<any> {
    return this.http.get<any>(`${this.api}/users`, { params: { page: '0', size: '1' } })
      .pipe(map(r => ({ totalUsers: r?.totalElements || 0 })), catchError(() => of({ totalUsers: 0 })));
  }
  getKycPending(): Observable<any> {
    return this.http.get<any>(`${this.api}/users`, { params: { page: '0', size: '1', kycStatus: 'PENDING' } })
      .pipe(map(r => ({ pending: r?.totalElements || 0 })), catchError(() => of({ pending: 0 })));
  }

  // ---------- Compliance ----------
  getAlertStats(): Observable<any> {
    return this.http.get<any>(`${this.api}/compliance/alerts`, { params: { page: '0', size: '1' } })
      .pipe(map(r => ({ totalAlerts: r?.totalElements || 0 })), catchError(() => of({ totalAlerts: 0 })));
  }
  getOpenAlerts(): Observable<any> {
    return this.http.get<any>(`${this.api}/compliance/alerts`, { params: { page: '0', size: '100', status: 'OPEN' } })
      .pipe(map(r => r?.content || []), catchError(() => of([])));
  }
  getCtrStats(): Observable<any> {
    return this.http.get<any>(`${this.api}/compliance/admin/ctr`, { params: { page: '0', size: '1' } })
      .pipe(map(r => ({ totalCtrs: r?.totalElements || 0 })), catchError(() => of({ totalCtrs: 0 })));
  }
  getIngestStatus(): Observable<any> {
    return this.get(`${this.api}/compliance/admin/ingest/status`);
  }

  // ---------- Finance ----------
  getPaidVolume(): Observable<any> { return this.getTransactionStats(); }
  getPartnerBalances(): Observable<any> {
    // Correct path: /api/transactions/corridors/payin-balances (no /payin-partners segment)
    return this.get(`${this.api}/transactions/corridors/payin-balances`);
  }

  // ---------- Partners ----------
  getPayoutPartners(): Observable<any> {
    return this.http.get<any>(`${this.api}/transactions/partners`)
      .pipe(map(r => Array.isArray(r) ? r : r || []), catchError(() => of([])));
  }
  getPayinPartners(): Observable<any> {
    return this.http.get<any>(`${this.api}/transactions/corridors/payin-partners`)
      .pipe(map(r => Array.isArray(r) ? r : r || []), catchError(() => of([])));
  }

  // ---------- Corridors ----------
  getCorridors(): Observable<any> {
    return this.http.get<any>(`${this.api}/corridors`)
      .pipe(map(r => Array.isArray(r) ? r : r || []), catchError(() => of([])));
  }

  // ---------- Support ----------
  getSupportTickets(status?: string): Observable<any> {
    const params: any = { page: '0', size: '100' };
    if (status) params.status = status;
    return this.http.get<any>(`${this.api}/support/admin/tickets`, { params })
      .pipe(map(r => r?.content || r || []), catchError(() => of([])));
  }

  // ---------- Notifications ----------
  getNotificationStats(): Observable<any> {
    return this.http.get<any>(`${this.api}/admin/notifications/stats`)
      .pipe(catchError(() => of(null)));
  }

  // ---------- System ----------
  checkServiceHealth(): Observable<{ up: boolean }> {
    return this.http.get<any>(`${this.api.replace('/api', '')}/actuator/health`)
      .pipe(
        map(r => ({ up: r?.status === 'UP' })),
        catchError(() => of({ up: false }))
      );
  }

  // ---------- Aggregated ----------
  getExecutiveDashboard(): Observable<any> {
    return forkJoin({
      txnStats: this.getTransactionStats(),
      userStats: this.getUserStats(),
      alertStats: this.getAlertStats(),
      recent: this.getRecentTransactions(10)
    });
  }
}
