import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';

@Injectable({ providedIn: 'root' })
export class SettlementService {
  private baseUrl = `${environment.apiUrl}/transactions`;

  constructor(private http: HttpClient) {}

  // Settlements
  getAllSettlements(): Observable<any> { return this.http.get(`${this.baseUrl}/settlements/all`); }
  getPendingSettlements(): Observable<any> { return this.http.get(`${this.baseUrl}/settlements/pending`); }
  getMySettlements(): Observable<any> { return this.http.get(`${this.baseUrl}/settlements/my`); }
  initiateAdminToPayout(data: any): Observable<any> { return this.http.post(`${this.baseUrl}/settlements/admin-to-payout`, data); }
  initiatePayinToAdmin(data: any): Observable<any> { return this.http.post(`${this.baseUrl}/settlements/payin-to-admin`, data); }
  approveSettlement(id: number): Observable<any> { return this.http.put(`${this.baseUrl}/settlements/${id}/approve`, {}); }
  rejectSettlement(id: number, reason: string): Observable<any> { return this.http.put(`${this.baseUrl}/settlements/${id}/reject`, { reason }); }

  // Settlement rates
  getGlobalRates(): Observable<any> { return this.http.get(`${this.baseUrl}/settlement-rates`); }
  updateGlobalRate(currency: string, data: any): Observable<any> { return this.http.put(`${this.baseUrl}/settlement-rates/${currency}`, data); }
  addGlobalRate(data: any): Observable<any> { return this.http.post(`${this.baseUrl}/settlement-rates`, data); }
  getPartnerRates(): Observable<any> { return this.http.get(`${this.baseUrl}/settlement-rates/partners`); }
  getPartnerRate(partnerId: number): Observable<any> { return this.http.get(`${this.baseUrl}/settlement-rates/partner/${partnerId}`); }
  setPartnerRate(partnerId: number, currency: string, data: any): Observable<any> { return this.http.put(`${this.baseUrl}/settlement-rates/partner/${partnerId}/${currency}`, data); }
  removePartnerRate(partnerId: number, currency: string): Observable<any> { return this.http.delete(`${this.baseUrl}/settlement-rates/partner/${partnerId}/${currency}`); }

  // Platform ledger
  getPlatformLedger(): Observable<any> { return this.http.get(`${this.baseUrl}/admin/platform-ledger`); }
  getTransactionLedger(txnId: number): Observable<any> { return this.http.get(`${this.baseUrl}/admin/ledger/${txnId}`); }
  getPartnerBalances(): Observable<any> { return this.http.get(`${this.baseUrl}/admin/partner-balances`); }
}
