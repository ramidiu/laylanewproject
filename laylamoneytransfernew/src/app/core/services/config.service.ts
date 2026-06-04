import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private txnUrl = `${environment.apiUrl}/transactions`;
  private userUrl = `${environment.apiUrl}/users`;

  constructor(private http: HttpClient) {}

  // Transfer config
  getPayoutTypes(): Observable<any> { return this.http.get(`${this.txnUrl}/config/admin/payout-types`); }
  togglePayoutType(id: number): Observable<any> { return this.http.put(`${this.txnUrl}/config/admin/payout-types/${id}/toggle`, {}); }
  togglePayoutCountry(code: string, active: boolean): Observable<any> { return this.http.put(`${this.txnUrl}/config/admin/payout-types/country/${code}/toggle`, { active }); }
  getPaymentMethods(): Observable<any> { return this.http.get(`${this.txnUrl}/config/admin/payment-methods`); }
  togglePaymentMethod(id: number): Observable<any> { return this.http.put(`${this.txnUrl}/config/admin/payment-methods/${id}/toggle`, {}); }
  togglePaymentCountry(code: string, active: boolean): Observable<any> { return this.http.put(`${this.txnUrl}/config/admin/payment-methods/country/${code}/toggle`, { active }); }
  getActiveCountries(): Observable<any> { return this.http.get(`${this.txnUrl}/config/active-countries`); }
  getActiveReceiveCountries(): Observable<any> { return this.http.get(`${this.txnUrl}/config/active-receive-countries`); }
  getPayoutTypesByCurrency(currency: string): Observable<any> { return this.http.get(`${this.txnUrl}/config/payout-types`, { params: { currency } }); }

  // Bank database
  getBankConfig(country: string): Observable<any> { return this.http.get(`${this.txnUrl}/banks/config/${country}`); }
  getBankNames(country: string): Observable<any> { return this.http.get(`${this.txnUrl}/banks/list/${country}`); }
  bankLookup(identifier: string, country: string): Observable<any> { return this.http.get(`${this.txnUrl}/banks/lookup`, { params: { identifier, country } }); }
  bankSearchIdentifier(prefix: string, country: string): Observable<any> { return this.http.get(`${this.txnUrl}/banks/search-identifier`, { params: { prefix, country } }); }
  bankSearchName(name: string, country: string): Observable<any> { return this.http.get(`${this.txnUrl}/banks/search`, { params: { name, country } }); }

  // Lookups
  getMobileServices(countryCode: string): Observable<any> { return this.http.get(`${this.txnUrl}/lookup/mobile-services`, { params: { countryCode } }); }
  getCashPoints(countryCode: string): Observable<any> { return this.http.get(`${this.txnUrl}/lookup/cash-points`, { params: { countryCode } }); }
  getRelations(): Observable<any> { return this.http.get(`${this.txnUrl}/lookup/relations`); }

  // Admin CRUD — cash collection points
  getAllCashPoints(): Observable<any> { return this.http.get(`${this.txnUrl}/lookup/admin/cash-points`); }
  addCashPoint(point: any): Observable<any> { return this.http.post(`${this.txnUrl}/lookup/admin/cash-points`, point); }
  updateCashPoint(id: number, point: any): Observable<any> { return this.http.put(`${this.txnUrl}/lookup/admin/cash-points/${id}`, point); }
  deleteCashPoint(id: number): Observable<any> { return this.http.delete(`${this.txnUrl}/lookup/admin/cash-points/${id}`); }

  // System config
  getSystemConfig(): Observable<any> { return this.http.get(`${this.userUrl}/admin/system-config`); }
  updateSystemConfig(key: string, value: string): Observable<any> { return this.http.put(`${this.userUrl}/admin/system-config/${key}`, { value }); }

  // User stats
  getUserStats(): Observable<any> { return this.http.get(`${this.userUrl}/admin/stats`); }

  // Email templates
  getTemplates(): Observable<any> { return this.http.get(`${environment.apiUrl}/admin/notifications/templates`); }
  updateTemplate(id: number, data: any): Observable<any> { return this.http.put(`${environment.apiUrl}/admin/notifications/templates/${id}`, data); }
  previewTemplate(data: any): Observable<any> { return this.http.post(`${environment.apiUrl}/admin/notifications/templates/preview`, data); }
}
