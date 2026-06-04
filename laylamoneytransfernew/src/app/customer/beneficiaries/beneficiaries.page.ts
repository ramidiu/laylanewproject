import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, NavigationEnd } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular';
import { Subject } from 'rxjs';
import { takeUntil, distinctUntilChanged, debounceTime, filter } from 'rxjs/operators';
import { BeneficiaryService } from '../../core/services/beneficiary.service';
import { ConfigService } from '../../core/services/config.service';
import { FxService } from '../../core/services/fx.service';
import { AuthService } from '../../core/services/auth.service';
import { UsiMoneyService, UsiCollectionPoint } from '../../core/services/usi-money.service';
import { AddressDetail } from '../../core/services/address.service';
import { BeneficiaryResponse } from '../../core/models/beneficiary.model';

@Component({
  selector: 'app-beneficiaries',
  templateUrl: './beneficiaries.page.html',
  styleUrls: ['./beneficiaries.page.scss']
})
export class BeneficiariesPage implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();
  beneficiaries: BeneficiaryResponse[] = [];
  filteredBeneficiaries: BeneficiaryResponse[] = [];
  showFavouritesOnly = false;
  loading = true;
  searchQuery = '';
  showAddForm = false;
  addForm!: FormGroup;
  preselectedCountry = '';
  preselectedDeliveryMethod = '';
  returnTo = '';
  addFormSubmitted = false;

  // Edit state
  showEditForm = false;
  editForm!: FormGroup;
  editingBeneficiary: BeneficiaryResponse | null = null;
  editBankConfig: any = null;
  editBankCodeLabel = 'Bank Code';
  editBankCodePlaceholder = 'Enter code';
  editBankCodeFormat = '';

  // Bank config for selected country
  bankConfig: any = null;
  bankCodeLabel = 'Bank Code';
  bankCodePlaceholder = 'Enter code';
  bankCodeFormat = '';

  // USI Money collection points for the chosen receive country (CASH_PICKUP only).
  usiCollectionPoints: any[] = [];
  loadingUsiCollectionPoints = false;
  selectedUsiCollectionPointCode = '';

  // Active receive countries from API
  receiveCountries: { code: string; name: string }[] = [];

  // Dynamic delivery methods based on selected country
  availableDeliveryMethods: { value: string; label: string }[] = [];

  // Mobile wallets for selected country
  mobileWallets: { serviceName: string; isActive: boolean }[] = [];
  editMobileWallets: { serviceName: string; isActive: boolean }[] = [];

  // Bank name lists for dropdowns
  bankNames: string[] = [];
  editBankNames: string[] = [];

  // Cash collection points for dropdown
  cashPoints: string[] = [];
  editCashPoints: string[] = [];

  // Dial code for selected country
  dialCode = '';
  editDialCode = '';

  private countryDialCodes: Record<string, string> = {
    SD: '+249', IN: '+91', PK: '+92', NG: '+234', GH: '+233', PH: '+63',
    KE: '+254', NP: '+977', BD: '+880', LK: '+94', AU: '+61', GB: '+44',
    US: '+1', DE: '+49', AE: '+971', ZA: '+27', UG: '+256', TZ: '+255',
    TR: '+90', EG: '+20', SA: '+966', QA: '+974',
    IND: '+91', PAK: '+92', NGA: '+234', GHA: '+233', PHL: '+63',
    KEN: '+254', NPL: '+977', BGD: '+880', AUS: '+61', GBR: '+44',
    USA: '+1', DEU: '+49', ARE: '+971', ZAF: '+27', UGA: '+256', TZA: '+255',
    SDN: '+249', TUR: '+90', EGY: '+20', SAU: '+966', QAT: '+974'
  };

  private allDeliveryMethodLabels: Record<string, string> = {
    BANK_DEPOSIT: 'Bank Deposit',
    MOBILE_WALLET: 'Mobile Wallet',
    CASH_PICKUP: 'Cash Pickup',
    UPI: 'UPI Transfer',
    HOME_DELIVERY: 'Home Delivery',
    AIRTIME_TOPUP: 'Airtime Top-up'
  };

  // Map payout_types DB values to delivery method values
  private payoutToDelivery: Record<string, string> = {
    BANK_TRANSFER: 'BANK_DEPOSIT',
    MOBILE_MONEY: 'MOBILE_WALLET',
    CASH_COLLECTION: 'CASH_PICKUP',
    UPI: 'UPI',
    HOME_DELIVERY: 'HOME_DELIVERY',
    AIRTIME_TOPUP: 'AIRTIME_TOPUP'
  };

  constructor(
    private fb: FormBuilder,
    private beneficiaryService: BeneficiaryService,
    private configService: ConfigService,
    private fxService: FxService,
    private authService: AuthService,
    private usiMoneyService: UsiMoneyService,
    private router: Router,
    private route: ActivatedRoute,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    // ONE-TIME setup only: forms + valueChanges subscriptions + static reference data.
    // Per-entry work (queryParams → data loads → showAddForm) runs from the
    // route.queryParams subscription below — because the app registers
    // IonicRouteStrategy (app.module.ts:28), Angular REUSES this component on
    // navigation back to the same route with different queryParams. ngOnInit
    // fires only on first mount; queryParams fires on every URL change.

    this.addForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(2)]],
      country: ['', Validators.required],
      deliveryMethod: ['', Validators.required],
      mobileNumber: [''],
      address: [''],
      relationship: [''],
      bankName: [''],
      accountNumber: [''],
      iban: [''],
      swiftBic: [''],
      sortCode: [''],
      branchState: ['Any Branch'],
      branchCity: ['Any Branch'],
      mobileProvider: [''],
      idNumber: [''],
      addressLine1: [''],
      city: ['']
    });

    this.editForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(2)]],
      country: [''],
      deliveryMethod: [''],
      mobileNumber: [''],
      address: [''],
      relationship: [''],
      bankName: [''],
      accountNumber: [''],
      iban: [''],
      swiftBic: [''],
      sortCode: [''],
      branchState: [''],
      branchCity: [''],
      mobileProvider: [''],
      idNumber: [''],
      city: ['']
    });

    // Dynamically add/remove validators when delivery method changes.
    // Also (re)load bank/mobile/cash lists in case they haven't resolved yet.
    this.addForm.get('deliveryMethod')?.valueChanges
      .pipe(distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(method => {
        this.updateDeliveryValidators(method);
        this.loadUsiCollectionPointsIfNeeded();
        const country = this.addForm.get('country')?.value;
        if (!country) return;
        if (method === 'BANK_DEPOSIT' && this.bankNames.length === 0) {
          this.configService.getBankNames(country).pipe(takeUntil(this.destroy$)).subscribe({
            next: (res: any) => { this.bankNames = res?.data || res || []; },
            error: () => {}
          });
        }
        if (method === 'MOBILE_WALLET' && this.mobileWallets.length === 0) {
          const iso2 = country.length === 3 ? (this.iso3ToIso2[country] || country) : country;
          this.configService.getMobileServices(iso2).pipe(takeUntil(this.destroy$)).subscribe({
            next: (res: any) => {
              const data: any[] = res?.data || res || [];
              this.mobileWallets = data.filter((s: any) => s.isActive)
                .sort((a: any, b: any) => a.serviceName.localeCompare(b.serviceName));
            },
            error: () => {}
          });
        }
        if (method === 'CASH_PICKUP' && this.cashPoints.length === 0) {
          this.configService.getCashPoints(country).pipe(takeUntil(this.destroy$)).subscribe({
            next: (res: any) => {
              const data: any[] = res?.data || res || [];
              this.cashPoints = data.filter((p: any) => p.isActive).map((p: any) => p.pointName);
            },
            error: () => {}
          });
        }
      });

    // Single country-change subscription — distinctUntilChanged prevents duplicate API calls.
    this.addForm.get('country')?.valueChanges
      .pipe(
        distinctUntilChanged(),
        debounceTime(50),
        takeUntil(this.destroy$)
      )
      .subscribe((country: string) => {
        if (country) {
          this.dialCode = this.countryDialCodes[country] || '';
          this.loadBankConfig(country);
          this.loadDeliveryMethodsForCountry(country);
          this.loadMobileWallets(country);
          this.addForm.patchValue(
            { deliveryMethod: '', mobileProvider: '' },
            { emitEvent: false }
          );
          // Country switch flips IBAN vs Account Number requirements — re-apply validators
          // for the current delivery method (no-op until method is picked).
          const method = this.addForm.get('deliveryMethod')?.value;
          if (method) this.updateDeliveryValidators(method);
          this.loadUsiCollectionPointsIfNeeded();
        }
      });

    // Load active receive countries for the dropdown (static data — one-time).
    this.configService.getActiveReceiveCountries().pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => {
        const data: any[] = res || [];
        const seen = new Set<string>();
        this.receiveCountries = data
          .filter((c: any) => c.isActive && !seen.has(c.countryCode) && seen.add(c.countryCode))
          .map((c: any) => ({ code: c.countryCode, name: c.countryName }))
          .sort((a, b) => a.name.localeCompare(b.name));
      },
      error: () => {}
    });

    // Per-entry work — fires on first mount AND on every queryParams change when the
    // component is reused by IonicRouteStrategy.
    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.applyRouteParams(params);
    });

    // Safety-net: if queryParams observable is suppressed on a reused component, catch
    // the route re-activation via Router.events instead and re-apply.
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      takeUntil(this.destroy$)
    ).subscribe((e: any) => {
      if (e.urlAfterRedirects && e.urlAfterRedirects.startsWith('/home/beneficiaries')) {
        this.applyRouteParams(this.route.snapshot.queryParams);
      }
    });
  }

  private applyRouteParams(params: any): void {
    this.preselectedCountry = params['country'] || '';
    this.preselectedDeliveryMethod = params['deliveryMethod'] || '';
    this.returnTo = params['returnTo'] || '';
    const autoAdd = params['addNew'] === 'true';

    // Normalise ISO-3 → ISO-2 so the form dropdown and country filter align with stored beneficiaries.
    if (this.preselectedCountry) {
      const iso2Map: Record<string, string> = {
        IND: 'IN', PAK: 'PK', NGA: 'NG', GHA: 'GH', PHL: 'PH', KEN: 'KE',
        NPL: 'NP', BGD: 'BD', AUS: 'AU', GBR: 'GB', USA: 'US',
        DEU: 'DE', ARE: 'AE', ZAF: 'ZA', UGA: 'UG', TZA: 'TZ',
        SDN: 'SD', TUR: 'TR', EGY: 'EG', SAU: 'SA', QAT: 'QA'
      };
      if (this.preselectedCountry.length === 3 && iso2Map[this.preselectedCountry]) {
        this.preselectedCountry = iso2Map[this.preselectedCountry];
      }
    }

    // Reset stale per-entry UI state from a reused component instance.
    this.showAddForm = false;
    this.showEditForm = false;
    this.editingBeneficiary = null;
    this.addFormSubmitted = false;
    this.showFavouritesOnly = false;
    this.searchQuery = '';
    this.addForm.patchValue({ country: this.preselectedCountry || '' }, { emitEvent: false });

    // Refresh list (filtered by preselectedCountry in applyFilter).
    this.loadBeneficiaries();

    // Batch-load country config + auto-show form when requested.
    if (this.preselectedCountry) {
      this.batchLoadCountryConfig(this.preselectedCountry, autoAdd);
    } else if (autoAdd) {
      this.showAddForm = true;
    }
  }

  private batchLoadCountryConfig(countryCode: string, autoShowForm: boolean): void {
    const receiveCurrency = this.countryToCurrency[countryCode] || countryCode;
    const iso2 = countryCode.length === 3 ? (this.iso3ToIso2[countryCode] || countryCode) : countryCode;

    const userCountry = this.authService.getCurrentUser()?.country || '';
    if (iso2 && userCountry && iso2.toUpperCase() === userCountry.toUpperCase()) {
      this.availableDeliveryMethods = [];
      this.mobileWallets = [];
      this.bankConfig = null;
      this.bankNames = [];
      this.cashPoints = [];
      if (autoShowForm && !this.showAddForm) this.showAddForm = true;
      return;
    }

    // Show the form immediately — each field shows "Loading..." until its data arrives.
    this.dialCode = this.countryDialCodes[countryCode] || '';
    if (autoShowForm && !this.showAddForm) this.showAddForm = true;

    // Fire all calls independently so each populates state as soon as it resolves.
    this.configService.getBankConfig(countryCode).pipe(takeUntil(this.destroy$)).subscribe({
      next: (bank: any) => {
        const bankCfg = bank?.data || bank;
        this.bankConfig = bankCfg;
        this.bankCodeLabel = bankCfg?.identifierLabel || 'Bank Code';
        this.bankCodePlaceholder = bankCfg?.identifierFormat ? `Format: ${bankCfg.identifierFormat}` : 'Enter code';
        this.bankCodeFormat = bankCfg?.identifierFormat || '';
      },
      error: () => {}
    });

    this.configService.getBankNames(iso2).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => { this.bankNames = res?.data || res || []; },
      error: () => { this.bankNames = []; }
    });

    this.configService.getPayoutTypesByCurrency(receiveCurrency).pipe(takeUntil(this.destroy$)).subscribe({
      next: (payouts: any) => {
        const payoutData: any[] = (payouts?.data || payouts || []) as any[];
        const types = payoutData.filter((t: any) => t.isActive);
        const seen = new Set<string>();
        this.availableDeliveryMethods = types
          .map((t: any) => this.payoutToDelivery[t.payoutType] || t.payoutType)
          .filter((v: string) => v && !seen.has(v) && !!seen.add(v))
          .map((v: string) => ({ value: v, label: this.allDeliveryMethodLabels[v] || v }));
        // Pre-select the delivery method chosen on Send Money (so we don't ask again).
        if (this.preselectedDeliveryMethod &&
            this.availableDeliveryMethods.some(d => d.value === this.preselectedDeliveryMethod)) {
          this.addForm.patchValue({ deliveryMethod: this.preselectedDeliveryMethod });
        }
        // Ensure collection points load for the preselected corridor (country patched with emitEvent:false).
        this.loadUsiCollectionPointsIfNeeded();
      },
      error: () => { this.availableDeliveryMethods = []; }
    });

    this.configService.getMobileServices(iso2).pipe(takeUntil(this.destroy$)).subscribe({
      next: (wallets: any) => {
        const walletData: any[] = (wallets?.data || wallets || []) as any[];
        this.mobileWallets = walletData
          .filter((s: any) => s.isActive)
          .sort((a: any, b: any) => a.serviceName.localeCompare(b.serviceName));
      },
      error: () => { this.mobileWallets = []; }
    });

    this.configService.getCashPoints(iso2).pipe(takeUntil(this.destroy$)).subscribe({
      next: (cashPoints: any) => {
        const cashData: any[] = (cashPoints?.data || cashPoints || []) as any[];
        this.cashPoints = cashData.filter((p: any) => p.isActive).map((p: any) => p.pointName);
      },
      error: () => { this.cashPoints = []; }
    });
  }

  loadBankConfig(countryCode: string): void {
    this.configService.getBankConfig(countryCode).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => {
        const config = res;
        this.bankConfig = config;
        this.bankCodeLabel = config?.identifierLabel || 'Bank Code';
        this.bankCodePlaceholder = config?.identifierFormat
          ? `Format: ${config.identifierFormat}`
          : 'Enter code';
        this.bankCodeFormat = config?.identifierFormat || '';
      },
      error: () => {
        this.bankConfig = null;
        this.bankCodeLabel = 'Bank Code';
        this.bankCodePlaceholder = 'Enter code';
        this.bankCodeFormat = '';
      }
    });
    this.configService.getBankNames(countryCode).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => { this.bankNames = res?.data || res || []; },
      error: () => { this.bankNames = []; }
    });
    this.configService.getCashPoints(countryCode).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => {
        const data: any[] = res?.data || res || [];
        this.cashPoints = data.filter((p: any) => p.isActive).map((p: any) => p.pointName);
      },
      error: () => { this.cashPoints = []; }
    });
  }

  loadBeneficiaries(): void {
    this.loading = true;
    this.beneficiaryService.list().pipe(takeUntil(this.destroy$)).subscribe({
      next: (bens) => {
        this.beneficiaries = bens;
        this.applyFilter();
        this.loading = false;
      },
      error: (err) => {
        this.beneficiaries = [];
        this.filteredBeneficiaries = [];
        this.loading = false;
      }
    });
  }

  toggleFavourites(): void {
    this.showFavouritesOnly = !this.showFavouritesOnly;
    this.applyFilter();
  }

  // Value-based handler — safe against programmatic [value] changes.
  // (toggleFavourites flips state unconditionally → caused an infinite CD loop
  // when applyRouteParams reset showFavouritesOnly on a reused component.)
  onSegmentChange(event: any): void {
    const val = event?.detail?.value ?? 'all';
    const newVal = val === 'favourites';
    if (newVal !== this.showFavouritesOnly) {
      this.showFavouritesOnly = newVal;
      this.applyFilter();
    }
  }

  onSearch(event: any): void {
    const val = event?.detail?.value ?? event?.target?.value ?? '';
    this.searchQuery = val.toLowerCase();
    this.applyFilter();
  }

  applyFilter(): void {
    let list = this.beneficiaries;
    // Scope to the country the user came from (Send Money → Select Recipient flow).
    // Stored beneficiaries may be in ISO-2 ('DE') while some corridors use ISO-3 ('DEU') —
    // match both so Australia/Germany/etc. work regardless of storage format.
    if (this.preselectedCountry) {
      const alt = this.preselectedCountry.length === 2
        ? this.iso2ToIso3Map[this.preselectedCountry]
        : this.iso3ToIso2[this.preselectedCountry];
      list = list.filter(b => b.country === this.preselectedCountry || (alt && b.country === alt));
    }
    if (this.showFavouritesOnly) {
      list = list.filter(b => b.isFavourite);
    }
    if (this.searchQuery) {
      list = list.filter(b =>
        b.fullName.toLowerCase().includes(this.searchQuery) ||
        (b.country || '').toLowerCase().includes(this.searchQuery)
      );
    }
    this.filteredBeneficiaries = list;
  }

  private iso2ToIso3Map: Record<string, string> = {
    IN: 'IND', PK: 'PAK', NG: 'NGA', GH: 'GHA', PH: 'PHL', KE: 'KEN',
    NP: 'NPL', BD: 'BGD', AU: 'AUS', GB: 'GBR', US: 'USA',
    DE: 'DEU', AE: 'ARE', ZA: 'ZAF', UG: 'UGA', TZ: 'TZA', LK: 'LKA'
  };

  /** IBAN-required countries for USI Money corridors + standard SEPA. */
  isIbanCountry(country?: string | null): boolean {
    const c = (country || this.addForm.get('country')?.value || '').toString().toUpperCase();
    if (!c) return false;
    const ibanSet = ['TR','TUR','SA','SAU','AE','ARE','QA','QAT',
                     'DE','DEU','FR','FRA','ES','ESP','IT','ITA','NL','NLD','BE','BEL','AT','AUT'];
    return ibanSet.includes(c) || ibanSet.includes(c.substring(0, Math.min(3, c.length)));
  }

  /**
   * Countries where no bank identifier (SWIFT/IBAN) is needed at all — server flags this
   * via country_bank_config.identifier_name='NONE' (currently: Egypt for USI Money).
   */
  isNoSwiftCountry(): boolean {
    const id = (this.bankConfig?.identifierName || '').toString().toUpperCase();
    return id === 'NONE';
  }

  /** Sudan uses a minimal beneficiary form: full name, mobile, bank name, account number only. */
  isSudan(country?: string | null): boolean {
    const c = (country || this.addForm.get('country')?.value || '').toString().toUpperCase();
    return c === 'SD' || c === 'SDN' || c.includes('SUDAN');
  }

  /** Sudan minimal form applies to bank-deposit beneficiaries (hide address/relationship). */
  isSudanBank(): boolean {
    return this.isSudan() && this.addForm.value.deliveryMethod === 'BANK_DEPOSIT';
  }

  /** Countries routed via USI Money — used to decide whether to show the dropdown. */
  isUsiCountry(): boolean {
    const c = (this.addForm.get('country')?.value || '').toString().toUpperCase();
    return ['UG','UGA','TR','TUR','EG','EGY','QA','QAT','SA','SAU','AE','ARE','UAE'].includes(c);
  }

  /** ISO/short code → full country name USI expects (Qatar, Uganda, etc.). */
  private countryShortToFullName: Record<string, string> = {
    UG: 'Uganda', UGA: 'Uganda',
    TR: 'Turkey', TUR: 'Turkey',
    EG: 'Egypt',  EGY: 'Egypt',
    QA: 'Qatar',  QAT: 'Qatar',
    SA: 'Saudi Arabia', SAU: 'Saudi Arabia',
    AE: 'United Arab Emirates', ARE: 'United Arab Emirates', UAE: 'United Arab Emirates'
  };

  /** Called when country / delivery-method changes — refreshes the collection-point dropdown. */
  loadUsiCollectionPointsIfNeeded(): void {
    this.usiCollectionPoints = [];
    this.selectedUsiCollectionPointCode = '';
    if (this.addForm.value.deliveryMethod !== 'CASH_PICKUP') return;
    const countryCode = (this.addForm.get('country')?.value || '').toString().toUpperCase();
    if (!countryCode) return;
    this.loadingUsiCollectionPoints = true;
    // USI countries: pull live points from USI Money; fall back to our DB list if none.
    if (this.isUsiCountry()) {
      const fullName = this.countryShortToFullName[countryCode] || countryCode;
      this.usiMoneyService.getCollectionPoints(fullName).subscribe({
        next: (pts) => {
          if (pts && pts.length) { this.usiCollectionPoints = pts; this.loadingUsiCollectionPoints = false; }
          else { this.loadCashPointsFromDb(countryCode); }
        },
        error: () => this.loadCashPointsFromDb(countryCode)
      });
    } else {
      // Non-USI countries (e.g. Sudan): use our cash_collection_points table.
      this.loadCashPointsFromDb(countryCode);
    }
  }

  /** Load cash collection points from our own DB and map them to the dropdown shape. */
  private loadCashPointsFromDb(countryCode: string): void {
    this.configService.getCashPoints(countryCode).subscribe({
      next: (res: any) => {
        const data: any[] = res?.data || res || [];
        this.usiCollectionPoints = data.map(p => ({
          name: p.pointName, code: p.pointName, address: p.address || '', city: p.city || ''
        }));
        this.loadingUsiCollectionPoints = false;
      },
      error: () => { this.usiCollectionPoints = []; this.loadingUsiCollectionPoints = false; }
    });
  }

  /** Auto-fill the cash-collection form fields from the chosen USI collection point. */
  onUsiCollectionPointPick(code: string): void {
    this.selectedUsiCollectionPointCode = code;
    const pt = this.usiCollectionPoints.find(p => p.code === code);
    if (!pt) return;
    this.addForm.patchValue({
      bankName:      pt.name,           // Collection Point Name
      accountNumber: pt.code,           // Collection Code
      idNumber:      pt.address,        // Collection Address
      city:          pt.city            // Collection City
    });
  }

  /** Countries where USI deals via mobile money (Uganda). */
  isMobileMoneyCountry(country?: string | null): boolean {
    const c = (country || this.addForm.get('country')?.value || '').toString().toUpperCase();
    return c === 'UG' || c === 'UGA' || c.includes('UGANDA');
  }

  private updateDeliveryValidators(method: string): void {
    const fields = ['bankName', 'accountNumber', 'iban', 'swiftBic', 'sortCode', 'branchState', 'branchCity',
                    'mobileNumber', 'mobileProvider', 'idNumber', 'address', 'relationship', 'addressLine1', 'city'];
    fields.forEach(f => {
      const ctrl = this.addForm.get(f);
      if (ctrl) { ctrl.clearValidators(); ctrl.updateValueAndValidity(); }
    });

    const ibanCountry = this.isIbanCountry();

    switch (method) {
      case 'BANK_DEPOSIT':
        this.addForm.get('mobileNumber')!.setValidators([Validators.required, Validators.minLength(7)]);
        this.addForm.get('bankName')!.setValidators([Validators.required]);
        // Account number is required for every bank deposit. For IBAN countries it equals the IBAN.
        this.addForm.get('accountNumber')!.setValidators([Validators.required, Validators.minLength(5)]);
        // Sudan: minimal form — only name, mobile, bank name, account number.
        if (this.isSudan()) {
          break;
        }
        this.addForm.get('address')!.setValidators([Validators.required]);
        this.addForm.get('relationship')!.setValidators([Validators.required]);
        this.addForm.get('sortCode')!.setValidators([Validators.required]);
        this.addForm.get('branchState')!.setValidators([Validators.required]);
        this.addForm.get('branchCity')!.setValidators([Validators.required]);
        if (ibanCountry) {
          this.addForm.get('iban')!.setValidators([Validators.required, Validators.minLength(15), Validators.maxLength(34)]);
          // SWIFT optional in USI IBAN corridors
        } else if (!this.isNoSwiftCountry()) {
          // Egypt (and any other country flagged identifier_name='NONE') skips SWIFT entirely.
          this.addForm.get('swiftBic')!.setValidators([Validators.required]);
        }
        break;
      case 'CASH_PICKUP':
        this.addForm.get('mobileNumber')!.setValidators([Validators.required, Validators.minLength(7)]);
        this.addForm.get('address')!.setValidators([Validators.required]);
        this.addForm.get('relationship')!.setValidators([Validators.required]);
        this.addForm.get('bankName')!.setValidators([Validators.required]);
        this.addForm.get('accountNumber')!.setValidators([Validators.required]);
        this.addForm.get('idNumber')!.setValidators([Validators.required]);
        this.addForm.get('city')!.setValidators([Validators.required]);
        break;
      case 'MOBILE_WALLET':
        this.addForm.get('mobileProvider')!.setValidators([Validators.required]);
        this.addForm.get('mobileNumber')!.setValidators([Validators.required, Validators.minLength(7)]);
        this.addForm.get('address')!.setValidators([Validators.required]);
        this.addForm.get('relationship')!.setValidators([Validators.required]);
        break;
      case 'UPI':
        this.addForm.get('accountNumber')!.setValidators([Validators.required, Validators.pattern(/^[\w.-]+@[\w]+$/)]);
        break;
      case 'HOME_DELIVERY':
        this.addForm.get('addressLine1')!.setValidators([Validators.required]);
        this.addForm.get('city')!.setValidators([Validators.required]);
        this.addForm.get('mobileNumber')!.setValidators([Validators.required, Validators.minLength(7)]);
        break;
      case 'AIRTIME_TOPUP':
        this.addForm.get('mobileNumber')!.setValidators([Validators.required, Validators.minLength(7)]);
        break;
    }

    fields.forEach(f => this.addForm.get(f)?.updateValueAndValidity());
  }

  /** Lookup the currency code for an ISO-2 or ISO-3 country code (e.g. SD → SDG, GBR → GBP). */
  currencyFor(country: string | null | undefined): string {
    if (!country) return '';
    return this.countryToCurrency[country.toUpperCase()] || country.toUpperCase();
  }

  private countryToCurrency: Record<string, string> = {
    // ISO-2 codes
    IN: 'INR', PK: 'PKR', NG: 'NGN', GH: 'GHS', PH: 'PHP', KE: 'KES',
    NP: 'NPR', BD: 'BDT', LK: 'LKR', AU: 'AUD', GB: 'GBP', US: 'USD',
    DE: 'EUR', AE: 'AED', ZA: 'ZAR', UG: 'UGX', TZ: 'TZS',
    SD: 'SDG', TR: 'TRY', EG: 'EGP', SA: 'SAR', QA: 'QAR',
    // ISO-3 codes (corridors use these)
    IND: 'INR', PAK: 'PKR', NGA: 'NGN', GHA: 'GHS', PHL: 'PHP', KEN: 'KES',
    NPL: 'NPR', BGD: 'BDT', AUS: 'AUD', GBR: 'GBP', USA: 'USD',
    DEU: 'EUR', ARE: 'AED', ZAF: 'ZAR', UGA: 'UGX', TZA: 'TZS',
    SDN: 'SDG', TUR: 'TRY', EGY: 'EGP', SAU: 'SAR', QAT: 'QAR'
  };

  /**
   * Loads delivery methods for the chosen receive country using the SAME source of truth
   * as the Send Money screen: active payout_types from Transfer Config.
   */
  private loadDeliveryMethodsForCountry(countryCode: string): void {
    const receiveCurrency = this.countryToCurrency[countryCode] || countryCode;

    this.configService.getPayoutTypesByCurrency(receiveCurrency).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => {
        const types: any[] = (res?.data || res || []).filter((t: any) => t.isActive);
        const seen = new Set<string>();
        this.availableDeliveryMethods = types
          .map((t: any) => this.payoutToDelivery[t.payoutType] || t.payoutType)
          .filter((v: string) => v && !seen.has(v) && !!seen.add(v))
          .map((v: string) => ({ value: v, label: this.allDeliveryMethodLabels[v] || v }));
        // Pre-select the delivery method chosen on Send Money (so we don't ask again).
        if (this.preselectedDeliveryMethod &&
            this.availableDeliveryMethods.some(d => d.value === this.preselectedDeliveryMethod)) {
          this.addForm.patchValue({ deliveryMethod: this.preselectedDeliveryMethod });
        }
        // Ensure collection points load for the preselected corridor (country patched with emitEvent:false).
        this.loadUsiCollectionPointsIfNeeded();
      },
      error: () => { this.availableDeliveryMethods = []; }
    });
  }

  private iso3ToIso2: Record<string, string> = {
    IND: 'IN', PAK: 'PK', NGA: 'NG', GHA: 'GH', PHL: 'PH', KEN: 'KE',
    NPL: 'NP', BGD: 'BD', AUS: 'AU', GBR: 'GB', USA: 'US',
    DEU: 'DE', ARE: 'AE', ZAF: 'ZA', UGA: 'UG', TZA: 'TZ',
    SDN: 'SD', TUR: 'TR', EGY: 'EG', SAU: 'SA', QAT: 'QA'
  };

  private loadMobileWallets(countryCode: string): void {
    // Mobile services table uses ISO-2, convert if needed
    const iso2 = countryCode.length === 3 ? (this.iso3ToIso2[countryCode] || countryCode) : countryCode;
    this.configService.getMobileServices(iso2).pipe(takeUntil(this.destroy$)).subscribe({
      next: (res: any) => {
        const data: any[] = res?.data || res || [];
        this.mobileWallets = data
          .filter((s: any) => s.isActive)
          .sort((a: any, b: any) => a.serviceName.localeCompare(b.serviceName));
      },
      error: () => { this.mobileWallets = []; }
    });
  }

  /**
   * Fill a beneficiary form's address fields from a Smarty autocomplete selection.
   * Works for both addForm and editForm (passed in from the template).
   */
  onBeneficiaryAddressSelected(form: FormGroup, detail: AddressDetail): void {
    if (!form) return;
    const street = detail.street || detail.fullAddress || '';
    const patch: any = {};
    if (form.get('address')) {
      patch.address = detail.fullAddress || street;
    }
    if (form.get('city') && detail.city) {
      patch.city = detail.city;
    }
    if (form.get('addressLine1')) {
      patch.addressLine1 = street;
    }
    if (form.get('postcode') && detail.postcode) {
      patch.postcode = detail.postcode;
    }
    form.patchValue(patch);
    form.markAsDirty();
  }

  onAddBeneficiary(): void {
    this.addFormSubmitted = true;
    if (this.addForm.invalid) return;

    const val = this.addForm.value;
    const request = {
      fullName: val.fullName,
      country: val.country,
      deliveryMethod: val.deliveryMethod,
      mobileNumber: val.mobileNumber || null,
      address: val.address || null,
      relationship: val.relationship || null,
      bankName: val.bankName || null,
      // For IBAN countries account_number is the IBAN itself (matches USI Money convention)
      accountNumber: val.accountNumber || val.iban || null,
      iban: val.iban || null,
      swiftBic: val.swiftBic || null,
      sortCode: val.sortCode || null,
      branchState: val.branchState || null,
      branchCity: val.branchCity || null,
      mobileProvider: val.mobileProvider || null,
      idNumber: val.idNumber ? (val.city ? `${val.idNumber}, ${val.city}` : val.idNumber) : null,
      idType: null
    };

    this.beneficiaryService.add(request).subscribe({
      next: (ben) => {
        this.showToast('Recipient added successfully!', 'success');
        this.showAddForm = false;
        this.addFormSubmitted = false;
        this.addForm.reset();
        if (this.returnTo === 'send-money') {
          this.router.navigate(['/home/send'], { queryParams: { beneficiaryId: ben.id } });
        } else {
          this.loadBeneficiaries();
        }
      },
      error: (err) => {
        this.showToast(err.error?.message || 'Failed to add recipient', 'danger');
      }
    });
  }

  toggleFavourite(ben: BeneficiaryResponse, event: Event): void {
    event.stopPropagation();
    this.beneficiaryService.toggleFavourite(ben.id).subscribe({
      next: () => {
        ben.isFavourite = !ben.isFavourite;
        this.showToast(ben.isFavourite ? 'Added to favourites' : 'Removed from favourites', 'success');
      },
      error: () => this.showToast('Failed to update favourite', 'danger')
    });
  }

  async deleteBeneficiary(ben: BeneficiaryResponse, event: Event): Promise<void> {
    event.stopPropagation();
    const alert = await this.alertCtrl.create({
      header: 'Delete Recipient',
      message: `Are you sure you want to delete ${ben.fullName}?`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Delete',
          cssClass: 'alert-danger',
          handler: () => {
            this.beneficiaryService.delete(ben.id).subscribe({
              next: () => {
                this.beneficiaries = this.beneficiaries.filter(b => b.id !== ben.id);
                this.applyFilter();
                this.showToast('Recipient deleted', 'success');
              },
              error: () => this.showToast('Failed to delete recipient', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  openEdit(ben: BeneficiaryResponse, event: Event): void {
    event.stopPropagation();
    this.editingBeneficiary = ben;
    this.editForm.patchValue({
      fullName: ben.fullName,
      country: ben.country,
      deliveryMethod: ben.deliveryMethod,
      mobileNumber: ben.mobileNumber || '',
      address: ben.address || '',
      relationship: ben.relationship || ben.idType || '',
      bankName: ben.bankName || '',
      accountNumber: ben.accountNumber || '',
      swiftBic: ben.swiftBic || '',
      sortCode: ben.sortCode || '',
      mobileProvider: ben.mobileProvider || '',
      idNumber: ben.idNumber || '',
      city: ''
    });
    if (ben.country) {
      this.editDialCode = this.countryDialCodes[ben.country] || '';
      this.loadEditBankConfig(ben.country);
      this.loadEditMobileWallets(ben.country);
    }
    this.showEditForm = true;
  }

  closeEdit(): void {
    this.showEditForm = false;
    this.editingBeneficiary = null;
  }

  private loadEditBankConfig(countryCode: string): void {
    this.configService.getBankConfig(countryCode).subscribe({
      next: (res: any) => {
        const config = res;
        this.editBankConfig = config;
        this.editBankCodeLabel = config?.identifierLabel || 'Bank Code';
        this.editBankCodePlaceholder = config?.identifierFormat
          ? `Format: ${config.identifierFormat}`
          : 'Enter code';
        this.editBankCodeFormat = config?.identifierFormat || '';
      },
      error: () => {
        this.editBankConfig = null;
        this.editBankCodeLabel = 'Bank Code';
        this.editBankCodePlaceholder = 'Enter code';
        this.editBankCodeFormat = '';
      }
    });
    this.configService.getBankNames(countryCode).subscribe({
      next: (res: any) => { this.editBankNames = res?.data || res || []; },
      error: () => { this.editBankNames = []; }
    });
    this.configService.getCashPoints(countryCode).subscribe({
      next: (res: any) => {
        const data: any[] = res?.data || res || [];
        this.editCashPoints = data.filter((p: any) => p.isActive).map((p: any) => p.pointName);
      },
      error: () => { this.editCashPoints = []; }
    });
  }

  private loadEditMobileWallets(countryCode: string): void {
    const iso2 = countryCode.length === 3 ? (this.iso3ToIso2[countryCode] || countryCode) : countryCode;
    this.configService.getMobileServices(iso2).subscribe({
      next: (res: any) => {
        const data: any[] = res?.data || res || [];
        this.editMobileWallets = data.filter((s: any) => s.isActive)
          .sort((a: any, b: any) => a.serviceName.localeCompare(b.serviceName));
      },
      error: () => { this.editMobileWallets = []; }
    });
  }

  onUpdateBeneficiary(): void {
    if (!this.editingBeneficiary || this.editForm.invalid) return;

    const val = this.editForm.value;
    const request: any = {};

    if (val.fullName && val.fullName !== this.editingBeneficiary.fullName) request.fullName = val.fullName;
    if (val.mobileNumber) request.mobileNumber = val.mobileNumber;
    if (val.address) request.address = val.address;
    if (val.relationship) request.relationship = val.relationship;
    if (val.bankName) request.bankName = val.bankName;
    if (val.accountNumber) request.accountNumber = val.accountNumber;
    if (val.swiftBic) request.swiftBic = val.swiftBic;
    if (val.sortCode) request.sortCode = val.sortCode;
    if (val.mobileProvider) request.mobileProvider = val.mobileProvider;
    if (val.idNumber) request.idNumber = val.idNumber;

    this.beneficiaryService.update(this.editingBeneficiary.id, request).subscribe({
      next: () => {
        this.showToast('Recipient updated successfully!', 'success');
        this.closeEdit();
        this.loadBeneficiaries();
      },
      error: (err) => {
        this.showToast(err.error?.message || 'Failed to update recipient', 'danger');
      }
    });
  }

  sendTo(ben: BeneficiaryResponse): void {
    this.router.navigate(['/home/send'], { queryParams: { beneficiaryId: ben.id } });
  }

  handleRefresh(event: any): void {
    this.loadBeneficiaries();
    setTimeout(() => event.target.complete(), 1000);
  }

  getInitials(name: string): string {
    return name.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2);
  }

  private countryFlags: Record<string, string> = {
    IND: '\u{1F1EE}\u{1F1F3}', PAK: '\u{1F1F5}\u{1F1F0}', NGA: '\u{1F1F3}\u{1F1EC}',
    GHA: '\u{1F1EC}\u{1F1ED}', PHL: '\u{1F1F5}\u{1F1ED}', AUS: '\u{1F1E6}\u{1F1FA}',
    NPL: '\u{1F1F3}\u{1F1F5}', BGD: '\u{1F1E7}\u{1F1E9}', KEN: '\u{1F1F0}\u{1F1EA}',
    ZAF: '\u{1F1FF}\u{1F1E6}', LKA: '\u{1F1F1}\u{1F1F0}', GBR: '\u{1F1EC}\u{1F1E7}',
    USA: '\u{1F1FA}\u{1F1F8}', ARE: '\u{1F1E6}\u{1F1EA}', CAN: '\u{1F1E8}\u{1F1E6}',
    SGP: '\u{1F1F8}\u{1F1EC}', MYS: '\u{1F1F2}\u{1F1FE}',
  };

  private countryNames: Record<string, string> = {
    IND: 'India', PAK: 'Pakistan', NGA: 'Nigeria', GHA: 'Ghana', PHL: 'Philippines',
    AUS: 'Australia', NPL: 'Nepal', BGD: 'Bangladesh', KEN: 'Kenya', ZAF: 'South Africa',
    LKA: 'Sri Lanka', GBR: 'United Kingdom', USA: 'United States', ARE: 'UAE',
    CAN: 'Canada', SGP: 'Singapore', MYS: 'Malaysia',
  };

  getCountryFlag(code: string): string {
    return this.countryFlags[code] || '';
  }

  getCountryName(code: string): string {
    return this.countryNames[code] || code;
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  trackByBeneficiaryId(_i: number, ben: BeneficiaryResponse): any {
    return ben.id;
  }

  trackByCode(_i: number, item: any): string {
    return item.code || item.value || item.serviceName || item;
  }
}
