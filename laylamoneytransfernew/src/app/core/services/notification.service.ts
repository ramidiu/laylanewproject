import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject, interval, map, switchMap, startWith } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  InAppNotificationResponse,
  AdminSendNotificationRequest,
  AdminSendNotificationResponse,
  NotificationPreferences
} from '../models/notification.model';
import { PageResponse } from '../models/common.model';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly baseUrl = `${environment.apiUrl}/notifications`;
  private readonly adminBaseUrl = `${environment.apiUrl}/admin/notifications`;
  private unreadCountSubject = new BehaviorSubject<number>(0);
  unreadCount$ = this.unreadCountSubject.asObservable();

  constructor(private http: HttpClient) {}

  getNotifications(page: number = 0, size: number = 20): Observable<PageResponse<InAppNotificationResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<any>(this.baseUrl, { params }).pipe(
      map(res => res.data || res)
    );
  }

  markAsRead(id: string | number): Observable<any> {
    return this.http.put(`${this.baseUrl}/${id}/read`, {});
  }

  markAllAsRead(): Observable<any> {
    return this.http.put(`${this.baseUrl}/read-all`, {});
  }

  getUnreadCount(): Observable<number> {
    return this.http.get<any>(`${this.baseUrl}/unread-count`).pipe(
      map(res => (typeof res === 'number') ? res : (res ?? 0))
    );
  }

  startPolling(intervalMs: number = 30000): void {
    interval(intervalMs).pipe(
      startWith(0),
      switchMap(() => this.getUnreadCount())
    ).subscribe(count => {
      this.unreadCountSubject.next(count);
    });
  }

  refreshUnreadCount(): void {
    this.getUnreadCount().subscribe(count => this.unreadCountSubject.next(count));
  }

  sendAdminNotification(request: AdminSendNotificationRequest): Observable<AdminSendNotificationResponse> {
    return this.http.post<any>(`${this.adminBaseUrl}/send`, request).pipe(
      map(res => res.data || res)
    );
  }

  getPreferences(): Observable<NotificationPreferences> {
    return this.http.get<any>(`${this.baseUrl}/preferences`).pipe(
      map(res => res.data || res)
    );
  }

  updatePreferences(prefs: Partial<NotificationPreferences>): Observable<NotificationPreferences> {
    return this.http.put<any>(`${this.baseUrl}/preferences`, prefs).pipe(
      map(res => res.data || res)
    );
  }

  getNotificationLog(page: number = 0, size: number = 20): Observable<PageResponse<any>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<any>(`${this.adminBaseUrl}/log`, { params }).pipe(
      map(res => res.data || res)
    );
  }

  getRecentInAppNotifications(page: number = 0, size: number = 20): Observable<PageResponse<any>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<any>(`${this.adminBaseUrl}/in-app`, { params }).pipe(
      map(res => res.data || res)
    );
  }
}
