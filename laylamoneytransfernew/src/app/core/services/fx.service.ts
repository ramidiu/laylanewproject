import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  QuoteRequest,
  QuoteResponse,
  FxRateResponse,
  CorridorResponse,
  DeliveryMethodResponse
} from '../models/fx.model';

@Injectable({
  providedIn: 'root'
})
export class FxService {
  private readonly fxUrl = `${environment.apiUrl}/fx`;
  private readonly corridorUrl = `${environment.apiUrl}/corridors`;

  constructor(private http: HttpClient) {}

  getQuote(request: QuoteRequest): Observable<QuoteResponse> {
    return this.http.post<any>(`${this.fxUrl}/quote`, request).pipe(
      map(res => res.data || res)
    );
  }

  getRates(): Observable<FxRateResponse[]> {
    return this.http.get<any>(`${this.fxUrl}/rates`).pipe(
      map(res => res || [])
    );
  }

  getRateForPair(base: string, target: string): Observable<FxRateResponse> {
    return this.http.get<any>(`${this.fxUrl}/rates/${base}/${target}`).pipe(
      map(res => res.data || res)
    );
  }

  getCorridors(): Observable<CorridorResponse[]> {
    return this.http.get<any>(this.corridorUrl).pipe(
      map(res => res?.data || res || [])
    );
  }

  getDeliveryMethods(corridorId: string): Observable<DeliveryMethodResponse[]> {
    return this.http.get<any>(`${this.corridorUrl}/${corridorId}/delivery-methods`).pipe(
      map(res => res || [])
    );
  }

  toggleCorridor(corridorId: string, isActive: boolean): Observable<CorridorResponse> {
    return this.http.put<any>(`${environment.apiUrl}/admin/corridors/${corridorId}/toggle`, { isActive }).pipe(
      map(res => res.data || res)
    );
  }

  updateCorridorFees(corridorId: string, baseFee: number, feePercent: number): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/admin/corridors/${corridorId}/fees`, {
      deliveryMethod: 'BANK_DEPOSIT',
      feeType: 'FLAT',
      flatFee: baseFee,
      percentageFee: feePercent,
      currency: 'GBP'
    }).pipe(map(res => res.data || res));
  }

  updateMargin(marginId: number, marginPercentage: number): Observable<any> {
    return this.http.put<any>(`${environment.apiUrl}/fx/margins/${marginId}`, { marginPercentage });
  }

  getMargins(): Observable<any> {
    return this.http.get<any>(`${environment.apiUrl}/fx/margins`).pipe(map(res => res.data || res));
  }

  // ── Corridor Fees ───────────────────────────────────────────

  getCorridorFees(): Observable<any> {
    return this.http.get<any>(`${this.fxUrl}/corridor-fees`).pipe(map(res => res.data || res));
  }

  getCorridorFeesById(corridorId: number): Observable<any> {
    return this.http.get<any>(`${this.fxUrl}/corridor-fees/corridor/${corridorId}`).pipe(map(res => res.data || res));
  }

  createCorridorFee(corridorId: number, data: any): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/admin/corridors/${corridorId}/fees`, data);
  }

  updateCorridorFee(corridorId: number, feeId: number, data: any): Observable<any> {
    return this.http.put<any>(`${environment.apiUrl}/admin/corridors/${corridorId}/fees/${feeId}`, data);
  }

  deleteCorridorFee(feeId: number): Observable<any> {
    return this.http.delete<any>(`${environment.apiUrl}/admin/corridors/fees/${feeId}`);
  }

  // ── Manual Rate Override ───────────────────────────────────────────────────

  getRateMode(): Observable<{ manualMode: boolean }> {
    return this.http.get<any>(`${this.fxUrl}/rate-mode`).pipe(map(res => res.data || res));
  }

  setRateMode(enabled: boolean): Observable<any> {
    return this.http.put<any>(`${this.fxUrl}/rate-mode?enabled=${enabled}`, {});
  }

  getManualRates(): Observable<Record<string, number>> {
    return this.http.get<any>(`${this.fxUrl}/manual-rates`).pipe(map(res => res.data || res || {}));
  }

  setManualRate(base: string, target: string, rate: number): Observable<any> {
    return this.http.put<any>(`${this.fxUrl}/manual-rates/${base}/${target}?rate=${rate}`, {});
  }

  clearManualRate(base: string, target: string): Observable<any> {
    return this.http.delete<any>(`${this.fxUrl}/manual-rates/${base}/${target}`);
  }
}
