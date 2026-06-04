import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface ReferralCode {
  id: number;
  code: string;
  userId: number;
  usageCount: number;
  isActive: boolean;
  createdAt: string;
}

export interface ReferralValidation {
  valid: boolean;
  rateBoostPercentage?: number;
  message?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ReferralService {
  private readonly baseUrl = `${environment.apiUrl}/referrals`;

  constructor(private http: HttpClient) {}

  /** Get the current user's referral code */
  getMyCode(): Observable<ReferralCode> {
    return this.http.get<any>(`${this.baseUrl}/my-code`).pipe(
      map(res => res?.data ?? res)
    );
  }

  /** Validate a referral code (optionally scoped to a corridor) */
  validateCode(code: string, corridorId?: number | string): Observable<ReferralValidation> {
    const params: any = {};
    if (corridorId != null) {
      params.corridorId = String(corridorId);
    }
    return this.http.get<any>(`${this.baseUrl}/validate/${code}`, { params }).pipe(
      map(res => res?.data ?? res)
    );
  }

  /** Admin: get all referral configs (global + per-corridor) */
  getAdminConfigs(): Observable<any[]> {
    return this.http.get<any>(`${this.baseUrl}/admin/configs`).pipe(
      map(res => res?.data ?? res ?? [])
    );
  }

  /** Admin: create or update a referral config */
  saveAdminConfig(payload: any): Observable<any> {
    if (payload.id) {
      return this.http.put<any>(`${this.baseUrl}/admin/configs/${payload.id}`, payload).pipe(
        map(res => res?.data ?? res)
      );
    }
    return this.http.post<any>(`${this.baseUrl}/admin/configs`, payload).pipe(
      map(res => res?.data ?? res)
    );
  }
}
