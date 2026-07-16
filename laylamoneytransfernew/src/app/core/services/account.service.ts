import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

/**
 * Account lifecycle operations (Google Play Account Deletion policy).
 * The JWT is attached automatically by the auth interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly baseUrl = `${environment.apiUrl}/account`;

  constructor(private http: HttpClient) {}

  /**
   * Request deletion of the authenticated user's account. The server resolves
   * the account from the JWT — the supplied userId is informational only.
   */
  requestDeletion(payload: { userId?: string; reason?: string }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/delete-request`, payload).pipe(
      map(res => res?.data ?? res)
    );
  }
}
