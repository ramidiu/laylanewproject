import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  KycDocumentResponse,
  KycStatusResponse,
  KycReviewRequest
} from '../models/kyc.model';

@Injectable({
  providedIn: 'root'
})
export class KycService {
  private readonly baseUrl = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) {}

  uploadDocument(userId: string, type: string, file: File, documentNumber?: string, issueDate?: string, expiryDate?: string): Observable<KycDocumentResponse> {
    if (!userId) return of(null as any);
    const formData = new FormData();
    formData.append('file', file);
    formData.append('type', type);
    if (documentNumber) formData.append('documentNumber', documentNumber);
    if (issueDate) formData.append('issueDate', issueDate);
    if (expiryDate) formData.append('expiryDate', expiryDate);
    return this.http.post<any>(`${this.baseUrl}/${userId}/kyc/documents`, formData).pipe(
      map(res => res.data || res)
    );
  }

  getDocuments(userId: string): Observable<KycDocumentResponse[]> {
    if (!userId) return of([]);
    return this.http.get<any>(`${this.baseUrl}/${userId}/kyc/documents`).pipe(
      map(res => res || [])
    );
  }

  getStatus(userId: string): Observable<KycStatusResponse> {
    if (!userId) return of(null as any);
    return this.http.get<any>(`${this.baseUrl}/${userId}/kyc/status`).pipe(
      map(res => res.data || res)
    );
  }

  reviewDocument(userId: string, docId: string, request: KycReviewRequest): Observable<any> {
    return this.http.put(`${this.baseUrl}/${userId}/kyc/documents/${docId}`, request);
  }

  getDocumentFileBlob(userId: string, docId: string | number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${userId}/kyc/documents/${docId}/file`, {
      responseType: 'blob'
    });
  }
}
