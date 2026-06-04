import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AddBeneficiaryRequest,
  UpdateBeneficiaryRequest,
  BeneficiaryResponse
} from '../models/beneficiary.model';

@Injectable({
  providedIn: 'root'
})
export class BeneficiaryService {
  private readonly baseUrl = `${environment.apiUrl}/beneficiaries`;

  constructor(private http: HttpClient) {}

  add(request: AddBeneficiaryRequest): Observable<BeneficiaryResponse> {
    return this.http.post<any>(this.baseUrl, request).pipe(
      map(res => res.data || res)
    );
  }

  list(): Observable<BeneficiaryResponse[]> {
    return this.http.get<any>(this.baseUrl).pipe(
      map(res => res || [])
    );
  }

  getById(id: string): Observable<BeneficiaryResponse> {
    return this.http.get<any>(`${this.baseUrl}/${id}`).pipe(
      map(res => res.data || res)
    );
  }

  update(id: string, request: UpdateBeneficiaryRequest): Observable<BeneficiaryResponse> {
    return this.http.put<any>(`${this.baseUrl}/${id}`, request).pipe(
      map(res => res.data || res)
    );
  }

  delete(id: string): Observable<any> {
    return this.http.delete(`${this.baseUrl}/${id}`);
  }

  toggleFavourite(id: string): Observable<any> {
    return this.http.put(`${this.baseUrl}/${id}`, { isFavourite: true });
  }

  getFavourites(): Observable<BeneficiaryResponse[]> {
    return this.http.get<any>(this.baseUrl, {
      params: new HttpParams().set('favourite', 'true')
    }).pipe(
      map(res => res || [])
    );
  }
}
