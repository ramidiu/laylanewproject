import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserResponse, UpdateProfileRequest } from '../models/user.model';
import { PageResponse, PageRequest } from '../models/common.model';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly baseUrl = `${environment.apiUrl}/users`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  getProfile(): Observable<UserResponse> {
    const user = this.authService.getCurrentUser();
    const uuid = user?.sub || '';
    return this.http.get<any>(`${this.baseUrl}/${uuid}`).pipe(
      map(res => res.data || res)
    );
  }

  updateProfile(request: UpdateProfileRequest): Observable<UserResponse> {
    const user = this.authService.getCurrentUser();
    const uuid = user?.sub || '';
    return this.http.put<any>(`${this.baseUrl}/${uuid}`, request).pipe(
      map(res => res.data || res)
    );
  }

  listUsers(params: PageRequest & { search?: string; status?: string; kycTier?: string; kycStatus?: string }): Observable<PageResponse<UserResponse>> {
    let httpParams = new HttpParams();
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page.toString());
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size.toString());
    if (params.sort) httpParams = httpParams.set('sort', params.sort);
    if (params.search) httpParams = httpParams.set('search', params.search);
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.kycTier) httpParams = httpParams.set('kycTier', params.kycTier);
    if (params.kycStatus) httpParams = httpParams.set('kycStatus', params.kycStatus);

    return this.http.get<any>(this.baseUrl, { params: httpParams }).pipe(
      map(res => res.data || res)
    );
  }

  getUserById(uuid: string): Observable<UserResponse> {
    return this.http.get<any>(`${this.baseUrl}/${uuid}`).pipe(
      map(res => res.data || res)
    );
  }

  updateUserAdmin(uuid: string, request: any): Observable<UserResponse> {
    return this.http.put<any>(`${this.baseUrl}/${uuid}`, request).pipe(
      map(res => res.data || res)
    );
  }

  suspendUser(id: string): Observable<any> {
    return this.http.put(`${this.baseUrl}/${id}/suspend`, {});
  }

  activateUser(id: string): Observable<any> {
    return this.http.put(`${this.baseUrl}/${id}/activate`, {});
  }

  getRiskScore(numericUserId: number | string): Observable<RiskScoreResponse> {
    return this.http.get<any>(`${this.baseUrl}/${numericUserId}/risk-score`).pipe(
      map(res => res.data || res)
    );
  }

  overrideRiskScore(numericUserId: number | string, riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'): Observable<any> {
    return this.http.put<any>(`${this.baseUrl}/${numericUserId}/risk-override`, { riskLevel }).pipe(
      map(res => res.data || res)
    );
  }
}

export interface RiskScoreResponse {
  riskScore: 'LOW' | 'MEDIUM' | 'HIGH' | string;
  riskPoints: number;
  overridden: boolean;
  breakdown: Record<string, any>;
}
