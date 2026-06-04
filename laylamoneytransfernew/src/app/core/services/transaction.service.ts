import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateTransactionRequest,
  TransactionResponse,
  StatusHistoryResponse,
  TransactionFilterParams
} from '../models/transaction.model';
import { PageResponse } from '../models/common.model';

@Injectable({
  providedIn: 'root'
})
export class TransactionService {
  private readonly baseUrl = `${environment.apiUrl}/transactions`;

  constructor(private http: HttpClient) {}

  create(request: CreateTransactionRequest): Observable<TransactionResponse> {
    return this.http.post<any>(this.baseUrl, request).pipe(
      map(res => res.data || res)
    );
  }

  getById(id: string): Observable<TransactionResponse> {
    return this.http.get<any>(`${this.baseUrl}/${id}`).pipe(
      map(res => res.data || res)
    );
  }

  list(params: TransactionFilterParams & { filterUserId?: number }): Observable<PageResponse<TransactionResponse>> {
    let httpParams = new HttpParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, value.toString());
      }
    });
    return this.http.get<any>(this.baseUrl, { params: httpParams }).pipe(
      map(res => res.data || res)
    );
  }

  cancel(id: string, reason?: string): Observable<any> {
    return this.http.put(`${this.baseUrl}/${id}/cancel`, { reason });
  }

  getHistory(id: string): Observable<StatusHistoryResponse[]> {
    return this.http.get<any>(`${this.baseUrl}/${id}/history`).pipe(
      map(res => res.data || res)
    );
  }

  updateStatus(id: string, status: string, reason?: string): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/${id}/status`, { status, reason });
  }

  refund(id: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/${id}/refund`, {});
  }

  // Admin actions
  getAllTransactions(page = 0, size = 20, filters?: { status?: string; search?: string; startDate?: string; endDate?: string }): Observable<any> {
    let params: Record<string, string> = { page: page.toString(), size: size.toString() };
    if (filters?.status) params['status'] = filters.status;
    if (filters?.search) params['search'] = filters.search;
    if (filters?.startDate) params['startDate'] = filters.startDate;
    if (filters?.endDate) params['endDate'] = filters.endDate;
    return this.http.get(`${environment.apiUrl}/transactions/admin/all`, { params });
  }

  getTransactionStats(): Observable<any> {
    return this.http.get(`${environment.apiUrl}/transactions/admin/stats`);
  }

  markFundsReceived(id: number, paymentReference?: string): Observable<any> {
    return this.http.put(`${environment.apiUrl}/transactions/admin/${id}/funds-received`, { paymentReference });
  }

  adminCancel(id: number, reason?: string): Observable<any> {
    return this.http.put(`${environment.apiUrl}/transactions/admin/${id}/cancel`, { reason });
  }

  adminReleaseCompliance(id: number, reason?: string): Observable<any> {
    return this.http.put(`${environment.apiUrl}/transactions/admin/${id}/release-from-compliance`, { reason });
  }

  adminMarkPaid(id: number): Observable<any> {
    return this.http.put(`${environment.apiUrl}/transactions/admin/${id}/mark-paid`, {});
  }

  adminRefund(id: number, reason?: string): Observable<any> {
    return this.http.put(`${environment.apiUrl}/transactions/admin/${id}/refund`, { reason });
  }

  adminArchive(id: number): Observable<any> {
    return this.http.put(`${environment.apiUrl}/transactions/admin/${id}/archive`, {});
  }

  downloadReceipt(id: string | number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/receipt.pdf`, {
      responseType: 'blob'
    });
  }

  getRecent(limit: number = 5): Observable<TransactionResponse[]> {
    return this.http.get<any>(this.baseUrl, {
      params: new HttpParams()
        .set('page', '0')
        .set('size', limit.toString())
        .set('sortBy', 'createdAt')
        .set('sortDir', 'desc')
    }).pipe(
      map(res => {
        const data = res.data || res;
        return data.content || [];
      })
    );
  }
}
