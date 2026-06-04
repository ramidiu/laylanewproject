import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { Subject, debounceTime, switchMap, takeUntil, of } from 'rxjs';
import { AddressService, AddressSuggestion, AddressDetail } from '../../../core/services/address.service';

/**
 * Reusable Smarty address autocomplete.
 *
 * Drop <app-address-autocomplete [country]="'GB'" (addressSelected)="..."></app-address-autocomplete>
 * above any address field group. The parent decides how to map the emitted AddressDetail
 * onto its own form controls.
 *
 * Backed by the existing AddressService -> /api/address (Smarty international-autocomplete).
 */
@Component({
  selector: 'app-address-autocomplete',
  templateUrl: './address-autocomplete.component.html',
  styleUrls: ['./address-autocomplete.component.scss'],
})
export class AddressAutocompleteComponent implements OnDestroy {
  /** ISO-2 country code Smarty should search within. Defaults to GB. */
  @Input() country = 'GB';
  @Input() placeholder = 'Type postcode or street…';
  /** Emitted when the user picks a final (single-entry) address. */
  @Output() addressSelected = new EventEmitter<AddressDetail>();

  query = '';
  suggestions: AddressSuggestion[] = [];
  loading = false;
  showList = false;

  private search$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  constructor(private addressService: AddressService) {
    this.search$
      .pipe(
        takeUntil(this.destroy$),
        debounceTime(400),
        switchMap((q) => {
          if (!q || q.length < 3) {
            this.suggestions = [];
            this.showList = false;
            this.loading = false;
            return of([] as AddressSuggestion[]);
          }
          this.loading = true;
          return this.addressService.lookup(q, this.resolvedCountry());
        })
      )
      .subscribe({
        next: (s) => {
          this.suggestions = s;
          this.loading = false;
          this.showList = s.length > 0;
        },
        error: () => {
          this.loading = false;
          this.suggestions = [];
          this.showList = false;
        },
      });
  }

  private resolvedCountry(): string {
    const c = (this.country || '').trim().toUpperCase();
    // Smarty expects ISO-2; if an ISO-3 sneaks in, fall back to GB rather than erroring.
    return c.length === 2 ? c : 'GB';
  }

  onInput(event: Event): void {
    this.query = (event.target as HTMLInputElement).value;
    this.search$.next(this.query);
  }

  pick(s: AddressSuggestion): void {
    const c = this.resolvedCountry();
    // Multi-entry (e.g. a building with many flats) — drill down instead of resolving.
    if (s.entries > 1) {
      this.loading = true;
      this.showList = false;
      this.addressService.lookup(s.addressText, c, s.addressId).subscribe({
        next: (subs) => {
          this.loading = false;
          this.suggestions = subs;
          this.showList = subs.length > 0;
        },
        error: () => {
          this.loading = false;
        },
      });
      return;
    }

    this.showList = false;
    this.query = s.addressText;
    this.loading = true;
    this.addressService.retrieve(s.addressId, c).subscribe({
      next: (detail) => {
        this.loading = false;
        this.addressSelected.emit(detail);
      },
      error: () => {
        this.loading = false;
        // Fall back to the raw text so the user still gets something usable.
        this.addressSelected.emit({
          street: s.addressText,
          city: '',
          postcode: '',
          state: '',
          fullAddress: s.addressText,
          address2: '',
        });
      },
    });
  }

  clear(): void {
    this.query = '';
    this.suggestions = [];
    this.showList = false;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
