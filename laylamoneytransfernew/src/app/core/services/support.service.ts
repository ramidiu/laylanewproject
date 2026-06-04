import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class SupportService {
  private baseUrl = `${environment.apiUrl}/support`;

  constructor(private http: HttpClient) {}

  createTicket(data: FormData): Observable<any> {
    return this.http.post(`${this.baseUrl}/tickets`, data).pipe(map((r: any) => r.data || r));
  }

  getMyTickets(): Observable<any[]> {
    return this.http.get<any>(`${this.baseUrl}/tickets`).pipe(map((r: any) => r.data || []));
  }

  getTicketDetail(ticketId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/tickets/${ticketId}`).pipe(map((r: any) => r.data || r));
  }

  replyToTicket(ticketId: number, data: FormData): Observable<any> {
    return this.http.post(`${this.baseUrl}/tickets/${ticketId}/reply`, data).pipe(map((r: any) => r.data || r));
  }
}
