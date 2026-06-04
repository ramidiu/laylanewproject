import { Component, OnInit, ViewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IonContent } from '@ionic/angular';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-landing',
  templateUrl: './landing.page.html',
  styleUrls: ['./landing.page.scss'],
})
export class LandingPage implements OnInit {
  @ViewChild(IonContent) content!: IonContent;

  supportedCountries: { code: string; name: string; flag: string }[] = [];
  corridorCount: number = 0;

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
    'TZA': { name: 'Tanzania', flag: '\u{1F1F9}\u{1F1FF}' },
    'UGA': { name: 'Uganda', flag: '\u{1F1FA}\u{1F1EC}' },
    'EGY': { name: 'Egypt', flag: '\u{1F1EA}\u{1F1EC}' },
    'MAR': { name: 'Morocco', flag: '\u{1F1F2}\u{1F1E6}' },
    'ETH': { name: 'Ethiopia', flag: '\u{1F1EA}\u{1F1F9}' },
    'SEN': { name: 'Senegal', flag: '\u{1F1F8}\u{1F1F3}' },
    'SAU': { name: 'Saudi Arabia', flag: '\u{1F1F8}\u{1F1E6}' },
    'QAT': { name: 'Qatar', flag: '\u{1F1F6}\u{1F1E6}' },
    'KWT': { name: 'Kuwait', flag: '\u{1F1F0}\u{1F1FC}' },
  };

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadCorridors();
  }

  scrollTo(elementId: string): void {
    const el = document.getElementById(elementId);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  }

  private loadCorridors(): void {
    this.http.get<any>(`${environment.apiUrl}/corridors`).subscribe({
      next: (res) => {
        const data = res.data || res;
        this.corridorCount = data.length;
        const allCodes = new Set<string>();
        for (const c of data) {
          allCodes.add(c.sendCountry);
          allCodes.add(c.receiveCountry);
        }
        this.supportedCountries = Array.from(allCodes).map(code => {
          const info = this.countryInfo[code];
          return {
            code,
            name: info ? info.name : code,
            flag: info ? info.flag : '\u{1F3F3}\u{FE0F}'
          };
        });
      },
      error: () => {}
    });
  }
}
