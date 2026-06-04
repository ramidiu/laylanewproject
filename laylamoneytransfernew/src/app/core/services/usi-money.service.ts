import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface UsiAdminTransaction {
  referenceNumber: string;
  senderId: number;
  senderName: string | null;
  senderEmail: string | null;
  beneficiaryId: number | null;
  beneficiaryName: string | null;
  destinationCountry: string | null;
  sendAmount: number;
  sendCurrency: string;
  receiveAmount: number;
  receiveCurrency: string;
  deliveryMethod: string | null;
  laylaStatus: string | null;
  createdAt: string | null;
  usiTransSessionId: string | null;
  usiReferenceNumber: string | null;
  usiPaymentToken: string | null;
  usiStatus: string | null;
  usiPaymentStatus: string | null;
  usiErrorMessage: string | null;
  usiUpdatedAt: string | null;
  nextAction: 'CREATE' | 'CONFIRM' | 'CHECK_STATUS' | 'DONE';
}

@Injectable({ providedIn: 'root' })
export class UsiMoneyService {
  private base = `${environment.apiUrl}/payout/usi`;

  constructor(private http: HttpClient) {}

  listTransactions(status: string = 'all', limit: number = 200): Observable<UsiAdminTransaction[]> {
    const params = new HttpParams().set('status', status).set('limit', String(limit));
    return this.http.get<UsiAdminTransaction[]>(`${this.base}/transactions`, { params });
  }

  createOnUsi(referenceNumber: string): Observable<any> {
    return this.http.post(`${this.base}/transaction/${referenceNumber}/create`, {});
  }

  confirmOnUsi(referenceNumber: string): Observable<any> {
    return this.http.post(`${this.base}/transaction/${referenceNumber}/confirm`, {});
  }

  checkStatus(referenceNumber: string): Observable<any> {
    return this.http.get(`${this.base}/transaction/${referenceNumber}/status`);
  }

  createRemitter(userId: number): Observable<any> {
    return this.http.post(`${this.base}/remitter/${userId}`, {});
  }

  createBeneficiary(beneficiaryId: number): Observable<any> {
    return this.http.post(`${this.base}/beneficiary/${beneficiaryId}`, {});
  }

  bulkCreate(refs: string[]): Observable<any[]> {
    return this.http.post<any[]>(`${this.base}/transaction/bulk-create`, refs);
  }

  bulkConfirm(refs: string[]): Observable<any[]> {
    return this.http.post<any[]>(`${this.base}/transaction/bulk-confirm`, refs);
  }

  bulkStatus(refs: string[]): Observable<any[]> {
    return this.http.post<any[]>(`${this.base}/transaction/bulk-status`, refs);
  }

  getCollectionPoints(countryName: string): Observable<UsiCollectionPoint[]> {
    return this.http.get<UsiCollectionPoint[]>(`${this.base}/collection-points`, {
      params: { country: countryName }
    });
  }
}

export interface UsiCollectionPoint {
  collectionId: string;
  name: string;
  bank: string;
  deliveryBank: string;
  address: string;
  city: string;
  state: string;
  countryId: string;
  code: string;
  telephone: string;
  email: string;
  contactPerson: string;
}
