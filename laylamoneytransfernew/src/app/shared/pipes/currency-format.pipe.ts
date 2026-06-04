import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'currencyFormat'
})
export class CurrencyFormatPipe implements PipeTransform {
  private readonly currencySymbols: Record<string, string> = {
    USD: '$',
    GBP: '\u00A3',
    EUR: '\u20AC',
    NGN: '\u20A6',
    KES: 'KSh',
    GHS: 'GH\u20B5',
    ZAR: 'R',
    INR: '\u20B9',
    AED: 'AED',
    CAD: 'C$',
    AUD: 'A$'
  };

  transform(value: number | string, currencyCode: string = 'USD', showCode: boolean = true): string {
    const numValue = typeof value === 'string' ? parseFloat(value) : value;
    if (isNaN(numValue)) return '--';

    const symbol = this.currencySymbols[currencyCode] || currencyCode;
    const formatted = numValue.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });

    return showCode ? `${symbol}${formatted} ${currencyCode}` : `${symbol}${formatted}`;
  }
}
