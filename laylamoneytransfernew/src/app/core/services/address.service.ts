import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AddressSuggestion {
  addressId: string;
  addressText: string;
  entries: number;
}

export interface AddressDetail {
  street: string;
  city: string;
  postcode: string;
  state: string;
  fullAddress: string;
  address2: string;
}

@Injectable({ providedIn: 'root' })
export class AddressService {
  private readonly baseUrl = `${environment.apiUrl}/address`;

  constructor(private http: HttpClient) {}

  lookup(query: string, country = 'GB', selected = ''): Observable<AddressSuggestion[]> {
    let params = new HttpParams().set('query', query).set('country', country);
    if (selected) params = params.set('selected', selected);
    return this.http.get<any>(this.baseUrl + '/lookup', { params }).pipe(
      map(res => {
        const candidates: any[] = res?.candidates || [];
        return candidates.map((c: any) => ({
          addressId: c.address_id || '',
          addressText: c.address_text || [c.street, c.locality, c.postal_code].filter(Boolean).join(', '),
          entries: c.entries || 1
        }));
      })
    );
  }

  retrieve(addressId: string, country = 'GB'): Observable<AddressDetail> {
    const params = new HttpParams().set('addressId', addressId).set('country', country);
    return this.http.get<AddressDetail>(this.baseUrl + '/retrieve', { params });
  }
}
