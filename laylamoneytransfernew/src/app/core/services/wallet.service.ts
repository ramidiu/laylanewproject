import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface WalletInfo {
  id: number;
  userId: number;
  balance: number;
  currency: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class WalletService {
  private readonly baseUrl = `${environment.apiUrl}/wallet`;

  constructor(private http: HttpClient) {}

  getWallet(): Observable<WalletInfo> {
    return this.http.get<any>(this.baseUrl).pipe(
      map(res => res?.data ?? res)
    );
  }

  topUp(amount: number, currency: string): Observable<WalletInfo> {
    return this.http.post<any>(`${this.baseUrl}/topup`, { amount, currency }).pipe(
      map(res => res?.data ?? res)
    );
  }

  getTransactions(): Observable<any[]> {
    return this.http.get<any>(`${this.baseUrl}/transactions`).pipe(
      map(res => res?.data ?? res ?? [])
    );
  }
}
