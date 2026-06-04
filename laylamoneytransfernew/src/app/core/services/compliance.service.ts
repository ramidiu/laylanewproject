import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ComplianceAlertResponse,
  ComplianceAlertDetail,
  ComplianceCaseResponse,
  AlertDispositionRequest,
  ComplianceMetrics,
  ComplianceAuditEntry
} from '../models/compliance.model';
import { PageResponse, PageRequest } from '../models/common.model';

@Injectable({
  providedIn: 'root'
})
export class ComplianceService {
  private readonly baseUrl = `${environment.apiUrl}/compliance`;

  constructor(private http: HttpClient) {}

  getAlerts(params: PageRequest & { status?: string; severity?: string; userId?: number }): Observable<PageResponse<ComplianceAlertResponse>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, value.toString());
      }
    });
    return this.http.get<any>(`${this.baseUrl}/alerts`, { params: httpParams }).pipe(
      map(res => res.data || res)
    );
  }

  getAlertDetail(id: number): Observable<ComplianceAlertDetail> {
    return this.http.get<any>(`${this.baseUrl}/alerts/${id}/details`).pipe(
      map(res => res.data || res)
    );
  }

  dispositionAlert(id: number, request: AlertDispositionRequest): Observable<ComplianceAlertResponse> {
    return this.http.post<any>(`${this.baseUrl}/alerts/${id}/disposition`, request).pipe(
      map(res => res.data || res)
    );
  }

  getCases(params: PageRequest): Observable<PageResponse<ComplianceCaseResponse>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, value.toString());
      }
    });
    return this.http.get<any>(`${this.baseUrl}/cases`, { params: httpParams }).pipe(
      map(res => res.data || res)
    );
  }

  getMetrics(): Observable<ComplianceMetrics> {
    return this.http.get<any>(`${this.baseUrl}/admin/metrics`).pipe(
      map(res => res.data || res)
    );
  }

  bulkDispositionForUser(userId: number, request: AlertDispositionRequest): Observable<{userId: number; action: string; dispositioned: number}> {
    const params = new HttpParams().set('userId', userId.toString());
    return this.http.post<any>(`${this.baseUrl}/alerts/bulk-disposition`, request, { params }).pipe(
      map(res => res.data || res)
    );
  }

  getAuditTrail(params: {
    reviewerId?: number;
    action?: string;
    startDate?: string;
    endDate?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<ComplianceAuditEntry>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, value.toString());
      }
    });
    return this.http.get<any>(`${this.baseUrl}/admin/audit`, { params: httpParams }).pipe(
      map(res => res.data || res)
    );
  }

  runRescreenNow(): Observable<{screened: number; failed: number}> {
    return this.http.post<any>(`${this.baseUrl}/admin/rescreen/run`, {}).pipe(
      map(res => res.data || res)
    );
  }

  getOpenAlertCounts(userIds: number[]): Observable<Record<number, number>> {
    if (!userIds || userIds.length === 0) {
      return new Observable<Record<number, number>>(sub => { sub.next({}); sub.complete(); });
    }
    const params = new HttpParams().set('userIds', userIds.join(','));
    return this.http.get<any>(`${this.baseUrl}/alerts/open-counts`, { params }).pipe(
      map(res => res.data || res)
    );
  }

  listCtrs(params: {
    filingStatus?: string;
    startDate?: string;
    endDate?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<CtrReport>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, value.toString());
      }
    });
    return this.http.get<any>(`${this.baseUrl}/admin/ctr`, { params: httpParams }).pipe(
      map(res => res.data || res)
    );
  }

  submitCtr(id: number, externalReference?: string): Observable<any> {
    let httpParams = new HttpParams();
    if (externalReference) httpParams = httpParams.set('externalReference', externalReference);
    return this.http.post<any>(`${this.baseUrl}/admin/ctr/${id}/submit`, null, { params: httpParams }).pipe(
      map(res => res.data || res)
    );
  }

  generateSarFromAlert(alertId: number): Observable<SarDraftResponse> {
    return this.http.post<any>(`${this.baseUrl}/admin/alerts/${alertId}/generate-sar`, {}).pipe(
      map(res => res.data || res)
    );
  }
}

export interface CtrReport {
  id: number;
  reportDate: string;
  userId: number;
  userEmail?: string;
  userName?: string;
  transactionCount: number;
  totalAmount: number;
  currency: string;
  threshold: number;
  filingStatus: 'DRAFT' | 'SUBMITTED' | 'ACKNOWLEDGED';
  filedAt?: string;
  transactionRefs?: string;
  createdAt: string;
}

export interface SarDraftResponse {
  sarReportId: number;
  filingStatus: string;
  reportContent: string;
  caseReference?: string;
}
