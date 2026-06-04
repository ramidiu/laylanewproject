import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AdminSupportService {
  private baseUrl = `${environment.apiUrl}/support/admin`;

  constructor(private http: HttpClient) {}

  getAllTickets(status?: string, priority?: string): Observable<any[]> {
    let params: any = {};
    if (status) params.status = status;
    if (priority) params.priority = priority;
    return this.http.get<any>(`${this.baseUrl}/tickets`, { params }).pipe(map((r: any) => r.data || []));
  }

  getTicketDetail(ticketId: number): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/tickets/${ticketId}`).pipe(map((r: any) => r.data || r));
  }

  replyToTicket(ticketId: number, data: FormData): Observable<any> {
    return this.http.post(`${this.baseUrl}/tickets/${ticketId}/reply`, data).pipe(map((r: any) => r.data || r));
  }

  updateStatus(ticketId: number, status: string): Observable<any> {
    return this.http.put(`${this.baseUrl}/tickets/${ticketId}/status`, { status }).pipe(map((r: any) => r.data || r));
  }
}
