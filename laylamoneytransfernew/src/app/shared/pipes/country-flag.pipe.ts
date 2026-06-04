import { Pipe, PipeTransform } from '@angular/core';

const ALPHA3_TO_ALPHA2: Record<string, string> = {
  GBR: 'gb', AUS: 'au', DEU: 'de', FRA: 'fr', IND: 'in', PAK: 'pk',
  NGA: 'ng', GHA: 'gh', PHL: 'ph', NPL: 'np', KEN: 'ke', ZAF: 'za',
  BGD: 'bd', LKA: 'lk', USA: 'us', ARE: 'ae', CAN: 'ca', SGP: 'sg',
  MYS: 'my', JPN: 'jp', CHN: 'cn', BRA: 'br', MEX: 'mx', TZA: 'tz',
  UGA: 'ug', RWA: 'rw', ETH: 'et', EGY: 'eg', MAR: 'ma', TUN: 'tn',
  ZMB: 'zm', ZWE: 'zw', MMR: 'mm', IDN: 'id', THA: 'th', VNM: 'vn',
  KHM: 'kh', TUR: 'tr', SAU: 'sa', QAT: 'qa', KWT: 'kw', BHR: 'bh',
  OMN: 'om', JOR: 'jo', LBN: 'lb', EUR: 'eu', NZL: 'nz', IRL: 'ie',
  POL: 'pl', ROU: 'ro', HUN: 'hu', CZE: 'cz', SWE: 'se', NOR: 'no',
  DNK: 'dk', FIN: 'fi', CHE: 'ch', AUT: 'at', BEL: 'be', NLD: 'nl',
  PRT: 'pt', ESP: 'es', ITA: 'it', GRC: 'gr', HKG: 'hk', TWN: 'tw',
  KOR: 'kr', COL: 'co', PER: 'pe', CHL: 'cl', ARG: 'ar', ECU: 'ec',
};

@Pipe({ name: 'countryFlagUrl' })
export class CountryFlagUrlPipe implements PipeTransform {
  transform(alpha3: string, _size: number = 24): string {
    const alpha2 = ALPHA3_TO_ALPHA2[alpha3?.toUpperCase()] || alpha3?.toLowerCase()?.substring(0, 2);
    return `assets/flags/${alpha2}.svg`;
  }
}

@Pipe({ name: 'countryFlagSvg' })
export class CountryFlagSvgPipe implements PipeTransform {
  transform(alpha3: string): string {
    const alpha2 = ALPHA3_TO_ALPHA2[alpha3?.toUpperCase()] || alpha3?.toLowerCase()?.substring(0, 2);
    return `assets/flags/${alpha2}.svg`;
  }
}
