import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { debounceTime, takeUntil } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-fx-calculator',
  templateUrl: './fx-calculator.component.html',
  styleUrls: ['./fx-calculator.component.scss']
})
export class FxCalculatorComponent implements OnInit, OnDestroy {
  corridors: any[] = [];
  sendCountries: { code: string; name: string; currency: string; flag: string }[] = [];
  receiveCountries: { code: string; name: string; currency: string; flag: string }[] = [];

  selectedSendCountry: { code: string; name: string; currency: string; flag: string } | null = null;
  selectedReceiveCountry: { code: string; name: string; currency: string; flag: string } | null = null;

  sendAmount: number = 100;
  receiveAmount: number = 0;
  exchangeRate: number = 0;
  fee: number = 0;
  totalCost: number = 0;
  loading: boolean = false;
  selectedCorridor: any = null;
  deliveryMethod: string = '';

  private amountSubject = new Subject<number>();
  private destroy$ = new Subject<void>();

  // Comprehensive country info map — used for display names and flags.
  // Falls back to corridor data if a code is not found here.
  private countryInfo: Record<string, { name: string; flag: string }> = {
    'GBR': { name: 'United Kingdom', flag: '\u{1F1EC}\u{1F1E7}' },
    'AUS': { name: 'Australia', flag: '\u{1F1E6}\u{1F1FA}' },
    'DEU': { name: 'Germany', flag: '\u{1F1E9}\u{1F1EA}' },
    'FRA': { name: 'France', flag: '\u{1F1EB}\u{1F1F7}' },
    'IND': { name: 'India', flag: '\u{1F1EE}\u{1F1F3}' },
    'PAK': { name: 'Pakistan', flag: '\u{1F1F5}\u{1F1F0}' },
    'NGA': { name: 'Nigeria', flag: '\u{1F1F3}\u{1F1EC}' },
    'GHA': { name: 'Ghana', flag: '\u{1F1EC}\u{1F1ED}' },
    'PHL': { name: 'Philippines', flag: '\u{1F1F5}\u{1F1ED}' },
    'NPL': { name: 'Nepal', flag: '\u{1F1F3}\u{1F1F5}' },
    'KEN': { name: 'Kenya', flag: '\u{1F1F0}\u{1F1EA}' },
    'ZAF': { name: 'South Africa', flag: '\u{1F1FF}\u{1F1E6}' },
    'BGD': { name: 'Bangladesh', flag: '\u{1F1E7}\u{1F1E9}' },
    'LKA': { name: 'Sri Lanka', flag: '\u{1F1F1}\u{1F1F0}' },
    'USA': { name: 'United States', flag: '\u{1F1FA}\u{1F1F8}' },
    'ARE': { name: 'UAE', flag: '\u{1F1E6}\u{1F1EA}' },
    'CAN': { name: 'Canada', flag: '\u{1F1E8}\u{1F1E6}' },
    'SGP': { name: 'Singapore', flag: '\u{1F1F8}\u{1F1EC}' },
    'MYS': { name: 'Malaysia', flag: '\u{1F1F2}\u{1F1FE}' },
    'JPN': { name: 'Japan', flag: '\u{1F1EF}\u{1F1F5}' },
    'CHN': { name: 'China', flag: '\u{1F1E8}\u{1F1F3}' },
    'BRA': { name: 'Brazil', flag: '\u{1F1E7}\u{1F1F7}' },
    'MEX': { name: 'Mexico', flag: '\u{1F1F2}\u{1F1FD}' },
    'TZA': { name: 'Tanzania', flag: '\u{1F1F9}\u{1F1FF}' },
    'UGA': { name: 'Uganda', flag: '\u{1F1FA}\u{1F1EC}' },
    'ZMB': { name: 'Zambia', flag: '\u{1F1FF}\u{1F1F2}' },
    'ZWE': { name: 'Zimbabwe', flag: '\u{1F1FF}\u{1F1FC}' },
    'EGY': { name: 'Egypt', flag: '\u{1F1EA}\u{1F1EC}' },
    'MAR': { name: 'Morocco', flag: '\u{1F1F2}\u{1F1E6}' },
    'ETH': { name: 'Ethiopia', flag: '\u{1F1EA}\u{1F1F9}' },
    'RWA': { name: 'Rwanda', flag: '\u{1F1F7}\u{1F1FC}' },
    'SEN': { name: 'Senegal', flag: '\u{1F1F8}\u{1F1F3}' },
    'CMR': { name: 'Cameroon', flag: '\u{1F1E8}\u{1F1F2}' },
    'THA': { name: 'Thailand', flag: '\u{1F1F9}\u{1F1ED}' },
    'VNM': { name: 'Vietnam', flag: '\u{1F1FB}\u{1F1F3}' },
    'IDN': { name: 'Indonesia', flag: '\u{1F1EE}\u{1F1E9}' },
    'NZL': { name: 'New Zealand', flag: '\u{1F1F3}\u{1F1FF}' },
    'IRL': { name: 'Ireland', flag: '\u{1F1EE}\u{1F1EA}' },
    'ESP': { name: 'Spain', flag: '\u{1F1EA}\u{1F1F8}' },
    'ITA': { name: 'Italy', flag: '\u{1F1EE}\u{1F1F9}' },
    'NLD': { name: 'Netherlands', flag: '\u{1F1F3}\u{1F1F1}' },
    'BEL': { name: 'Belgium', flag: '\u{1F1E7}\u{1F1EA}' },
    'CHE': { name: 'Switzerland', flag: '\u{1F1E8}\u{1F1ED}' },
    'SWE': { name: 'Sweden', flag: '\u{1F1F8}\u{1F1EA}' },
    'NOR': { name: 'Norway', flag: '\u{1F1F3}\u{1F1F4}' },
    'DNK': { name: 'Denmark', flag: '\u{1F1E9}\u{1F1F0}' },
    'POL': { name: 'Poland', flag: '\u{1F1F5}\u{1F1F1}' },
    'TUR': { name: 'Turkey', flag: '\u{1F1F9}\u{1F1F7}' },
    'SAU': { name: 'Saudi Arabia', flag: '\u{1F1F8}\u{1F1E6}' },
    'QAT': { name: 'Qatar', flag: '\u{1F1F6}\u{1F1E6}' },
    'KWT': { name: 'Kuwait', flag: '\u{1F1F0}\u{1F1FC}' },
    'BHR': { name: 'Bahrain', flag: '\u{1F1E7}\u{1F1ED}' },
    'OMN': { name: 'Oman', flag: '\u{1F1F4}\u{1F1F2}' },
    'JOR': { name: 'Jordan', flag: '\u{1F1EF}\u{1F1F4}' },
    'MMR': { name: 'Myanmar', flag: '\u{1F1F2}\u{1F1F2}' },
    'COL': { name: 'Colombia', flag: '\u{1F1E8}\u{1F1F4}' },
    'PER': { name: 'Peru', flag: '\u{1F1F5}\u{1F1EA}' },
    'CHL': { name: 'Chile', flag: '\u{1F1E8}\u{1F1F1}' },
    'ARG': { name: 'Argentina', flag: '\u{1F1E6}\u{1F1F7}' },
    'HKG': { name: 'Hong Kong', flag: '\u{1F1ED}\u{1F1F0}' },
    'KOR': { name: 'South Korea', flag: '\u{1F1F0}\u{1F1F7}' },
    'TWN': { name: 'Taiwan', flag: '\u{1F1F9}\u{1F1FC}' },
  };

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadCorridors();

    this.amountSubject.pipe(
      debounceTime(500),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.fetchQuote();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadCorridors(): void {
    this.http.get<any>(`${environment.apiUrl}/corridors`).subscribe({
      next: (res) => {
        this.corridors = res.data || res;
        this.extractSendCountries();
      },
      error: () => {
        // Silently fail - calculator just won't populate
      }
    });
  }

  private getCountryDisplay(code: string, currency: string): { code: string; name: string; currency: string; flag: string } {
    const info = this.countryInfo[code];
    return {
      code,
      currency,
      name: info ? info.name : code,
      flag: info ? info.flag : '\u{1F3F3}\u{FE0F}'
    };
  }

  private extractSendCountries(): void {
    const seen = new Set<string>();
    this.sendCountries = [];

    for (const c of this.corridors) {
      const code = c.sendCountry;
      if (!seen.has(code)) {
        seen.add(code);
        this.sendCountries.push(this.getCountryDisplay(code, c.sendCurrency));
      }
    }

    if (this.sendCountries.length > 0) {
      this.selectedSendCountry = this.sendCountries[0];
      this.updateReceiveCountries();
    }
  }

  private updateReceiveCountries(): void {
    if (!this.selectedSendCountry) return;

    const seen = new Set<string>();
    this.receiveCountries = [];

    for (const c of this.corridors) {
      if (c.sendCountry === this.selectedSendCountry.code) {
        const code = c.receiveCountry;
        if (!seen.has(code)) {
          seen.add(code);
          this.receiveCountries.push(this.getCountryDisplay(code, c.receiveCurrency));
        }
      }
    }

    if (this.receiveCountries.length > 0) {
      this.selectedReceiveCountry = this.receiveCountries[0];
      this.updateCorridor();
      this.fetchQuote();
    } else {
      this.selectedReceiveCountry = null;
      this.resetQuote();
    }
  }

  private updateCorridor(): void {
    if (!this.selectedSendCountry || !this.selectedReceiveCountry) return;

    this.selectedCorridor = this.corridors.find(
      c => c.sendCountry === this.selectedSendCountry!.code &&
           c.receiveCountry === this.selectedReceiveCountry!.code
    );

    if (this.selectedCorridor && this.selectedCorridor.deliveryMethods?.length > 0) {
      this.deliveryMethod = this.selectedCorridor.deliveryMethods[0];
    }
  }

  onSendCountryChange(): void {
    this.updateReceiveCountries();
  }

  onReceiveCountryChange(): void {
    this.updateCorridor();
    this.fetchQuote();
  }

  onAmountChange(): void {
    this.amountSubject.next(this.sendAmount);
  }

  private resetQuote(): void {
    this.receiveAmount = 0;
    this.exchangeRate = 0;
    this.fee = 0;
    this.totalCost = 0;
  }

  private fetchQuote(): void {
    if (!this.selectedCorridor || !this.sendAmount || this.sendAmount <= 0) {
      this.resetQuote();
      return;
    }

    this.loading = true;

    const payload = {
      sendCurrency: this.selectedSendCountry!.currency,
      receiveCurrency: this.selectedReceiveCountry!.currency,
      sendAmount: this.sendAmount,
      deliveryMethod: this.deliveryMethod,
      corridorId: this.selectedCorridor.id
    };

    this.http.post<any>(`${environment.apiUrl}/fx/quote`, payload).subscribe({
      next: (res) => {
        const quote = res.data || res;
        this.receiveAmount = quote.receiveAmount;
        this.exchangeRate = quote.appliedRate || quote.exchangeRate;
        this.fee = quote.fee;
        this.totalCost = quote.totalCost;
        this.loading = false;
      },
      error: () => {
        this.resetQuote();
        this.loading = false;
      }
    });
  }
}
