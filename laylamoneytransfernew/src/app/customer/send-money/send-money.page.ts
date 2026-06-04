import { Component, OnInit, OnDestroy, ElementRef, ViewChild, NgZone } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastController, ViewWillEnter } from '@ionic/angular';
import { Subject, takeUntil, debounceTime, switchMap } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { FxService } from '../../core/services/fx.service';
import { BeneficiaryService } from '../../core/services/beneficiary.service';
import { TransactionService } from '../../core/services/transaction.service';
import { ConfigService } from '../../core/services/config.service';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import { WalletService } from '../../core/services/wallet.service';
import { ReferralService } from '../../core/services/referral.service';
import { environment } from '../../../environments/environment';
import { BeneficiaryResponse } from '../../core/models/beneficiary.model';
import { CorridorResponse, QuoteResponse } from '../../core/models/fx.model';

@Component({
  selector: 'app-send-money',
  templateUrl: './send-money.page.html',
  styleUrls: ['./send-money.page.scss']
})
export class SendMoneyPage implements OnInit, OnDestroy, ViewWillEnter {
  // Section visibility driven by currentStep
  currentStep: 'country' | 'calculator' | 'recipient' | 'review' = 'country';

  corridors: CorridorResponse[] = [];
  beneficiaries: BeneficiaryResponse[] = [];
  filteredBeneficiaries: BeneficiaryResponse[] = [];
  deliveryMethods: string[] = [];
  quote: QuoteResponse | null = null;
  rateLockPercent = 100;
  rateLockTimer: any;

  // ngModel-bound fields
  sendAmount = 10;
  receiveAmountInput = 0;
  private isReverseCalc = false;
  selectedDeliveryMethod = '';
  beneficiarySearch = '';
  beneficiaryFilter: 'all' | 'favourites' = 'all';

  selectedCorridor: CorridorResponse | null = null;
  selectedBeneficiary: BeneficiaryResponse | null = null;

  loadingQuote = false;
  submitting = false;
  showRecipientPanel = false;

  // Payment method selection
  selectedPaymentMethod: 'OPEN_BANKING' | 'CARD' = 'OPEN_BANKING';

  // Volume Pay state
  transactionCreated = false;
  createdTxnRef = '';
  volumeReady = false;
  volumePayStarted = false;
  merchantPaymentId = '';
  private volumeInstance: any = null;
  private volumeScriptEl: HTMLScriptElement | null = null;

  // Trust Payments billing fields (populated from user profile)
  billingInfoLoaded = false;
  billingFirstName = '';
  billingLastName = '';
  billingDob = '';
  billingPremise = '';
  billingStreet = '';
  billingTown = '';
  billingCounty = '';
  billingPostcode = '';
  billingCountryIso2 = 'GB';

  // Referral
  referralCode = '';
  referralValid: boolean | null = null;
  referralBoostPct = 0;
  isFirstTransaction = true;
  private referralDebounce: any;

  // Wallet
  walletBalance = 0;
  useWallet = false;
  walletAmountToUse = 0;
  readonly Math = Math;

  private destroy$ = new Subject<void>();
  private amountChange$ = new Subject<number>();

  @ViewChild('calculatorSection', { read: ElementRef }) calculatorSection!: ElementRef;
  @ViewChild('recipientSection', { read: ElementRef }) recipientSection!: ElementRef;

  countryNames: Record<string, string> = {
    GBR: 'United Kingdom', IND: 'India', PAK: 'Pakistan',
    NGA: 'Nigeria', GHA: 'Ghana', PHL: 'Philippines',
    AUS: 'Australia', NPL: 'Nepal', BGD: 'Bangladesh',
    KEN: 'Kenya', ZAF: 'South Africa', LKA: 'Sri Lanka',
    USA: 'United States', ARE: 'UAE', CAN: 'Canada',
    SGP: 'Singapore', MYS: 'Malaysia',
    DEU: 'Germany', FRA: 'France', ITA: 'Italy', ESP: 'Spain',
    UGA: 'Uganda', TZA: 'Tanzania', EGY: 'Egypt', MAR: 'Morocco',
    // ISO2 codes used by new corridors
    EG: 'Egypt', SD: 'Sudan', TR: 'Turkey',
    SA: 'Saudi Arabia', QA: 'Qatar', UG: 'Uganda', AE: 'UAE'
  };

  countryFlags: Record<string, string> = {
    GBR: '\u{1F1EC}\u{1F1E7}', IND: '\u{1F1EE}\u{1F1F3}', PAK: '\u{1F1F5}\u{1F1F0}',
    NGA: '\u{1F1F3}\u{1F1EC}', GHA: '\u{1F1EC}\u{1F1ED}', PHL: '\u{1F1F5}\u{1F1ED}',
    AUS: '\u{1F1E6}\u{1F1FA}', NPL: '\u{1F1F3}\u{1F1F5}', BGD: '\u{1F1E7}\u{1F1E9}',
    KEN: '\u{1F1F0}\u{1F1EA}', ZAF: '\u{1F1FF}\u{1F1E6}', LKA: '\u{1F1F1}\u{1F1F0}',
    USA: '\u{1F1FA}\u{1F1F8}', ARE: '\u{1F1E6}\u{1F1EA}', CAN: '\u{1F1E8}\u{1F1E6}',
    SGP: '\u{1F1F8}\u{1F1EC}', MYS: '\u{1F1F2}\u{1F1FE}',
    DEU: '\u{1F1E9}\u{1F1EA}', FRA: '\u{1F1EB}\u{1F1F7}', ITA: '\u{1F1EE}\u{1F1F9}', ESP: '\u{1F1EA}\u{1F1F8}',
    UGA: '\u{1F1FA}\u{1F1EC}', TZA: '\u{1F1F9}\u{1F1FF}', EGY: '\u{1F1EA}\u{1F1EC}', MAR: '\u{1F1F2}\u{1F1E6}',
    // ISO2 codes used by new corridors
    EG: '\u{1F1EA}\u{1F1EC}', SD: '\u{1F1F8}\u{1F1E9}', TR: '\u{1F1F9}\u{1F1F7}',
    SA: '\u{1F1F8}\u{1F1E6}', QA: '\u{1F1F6}\u{1F1E6}', UG: '\u{1F1FA}\u{1F1EC}', AE: '\u{1F1E6}\u{1F1EA}'
  };

  deliveryMethodLabels: Record<string, string> = {
    BANK_DEPOSIT: 'Bank Deposit',
    MOBILE_WALLET: 'Mobile Wallet',
    CASH_PICKUP: 'Cash Pickup',
    HOME_DELIVERY: 'Home Delivery',
    AIRTIME_TOPUP: 'Airtime Top-up'
  };

  private activeReceiveCountryCodes: string[] = [];
  userSendCurrency = 'GBP';
  userCountry = 'GB';

  private countryToCurrency: Record<string, string> = {
    'GB': 'GBP', 'US': 'USD', 'AU': 'AUD', 'AE': 'AED', 'DE': 'EUR',
    'IN': 'INR', 'PK': 'PKR', 'NG': 'NGN', 'GH': 'GHS', 'PH': 'PHP',
    'KE': 'KES', 'BD': 'BDT', 'ZA': 'ZAR', 'LK': 'LKR', 'NP': 'NPR'
  };

  private readonly apiUrl = environment.apiUrl;

  constructor(
    private fxService: FxService,
    private beneficiaryService: BeneficiaryService,
    private transactionService: TransactionService,
    private configService: ConfigService,
    private authService: AuthService,
    private userService: UserService,
    private walletService: WalletService,
    private referralService: ReferralService,
    private route: ActivatedRoute,
    private router: Router,
    private toastCtrl: ToastController,
    private ngZone: NgZone,
    private http: HttpClient
  ) {
    // Determine user's send currency from their country
    const user = this.authService.getCurrentUser();
    if (user?.country) {
      this.userCountry = user.country;
      this.userSendCurrency = this.countryToCurrency[user.country] || 'GBP';
    }
  }

  ngOnInit(): void {
    this.loadCorridors();
    this.loadWalletBalance();
    this.loadUserBillingInfo();

    // Debounced quote fetch — switchMap cancels any in-flight request when a new amount arrives,
    // preventing a slow response for amount=10 from overwriting a faster response for amount=105
    this.amountChange$.pipe(
      takeUntil(this.destroy$),
      debounceTime(800),
      switchMap(() => {
        if (!this.sendAmount || this.sendAmount <= 0 || !this.selectedCorridor || !this.selectedDeliveryMethod) {
          return [];
        }
        this.loadingQuote = true;
        return this.fxService.getQuote({
          sendCurrency: this.selectedCorridor.sendCurrency,
          receiveCurrency: this.selectedCorridor.receiveCurrency,
          sendAmount: this.sendAmount,
          deliveryMethod: this.selectedDeliveryMethod,
          corridorId: this.selectedCorridor.id
        });
      })
    ).subscribe({
      next: (quote: any) => {
        this.quote = quote;
        if (quote) {
          quote._baseRate = quote.appliedRate;
          quote._baseReceiveAmount = quote.receiveAmount;
          if (!this.isReverseCalc) {
            this.receiveAmountInput = quote.receiveAmount;
          }
        }
        this.loadingQuote = false;
        if (quote?.expiresInSeconds) {
          this.startRateLockTimer(quote.expiresInSeconds);
        }
        if (this.referralValid && this.referralBoostPct > 0) {
          this.applyReferralBoostToQuote();
        }
      },
      error: () => {
        this.loadingQuote = false;
        this.quote = null;
      }
    });

    // Check for pre-selected beneficiary from query params
    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe(params => {
      const beneficiaryId = params['beneficiaryId'];
      if (beneficiaryId) {
        this.handlePreSelectedBeneficiary(beneficiaryId);
      }
    });
  }

  ionViewWillEnter(): void {
    const beneficiaryId = this.route.snapshot.queryParams['beneficiaryId'];

    // If returning from add-recipient with a pre-selected beneficiary, don't reset —
    // restore the saved draft (amount/delivery/referral) so the review shows what was entered.
    if (beneficiaryId) {
      try {
        const draft = sessionStorage.getItem('layla_send_draft');
        if (draft) {
          const d = JSON.parse(draft);
          if (d.sendAmount) this.sendAmount = d.sendAmount;
          if (d.receiveAmountInput) this.receiveAmountInput = d.receiveAmountInput;
          if (d.deliveryMethod) this.selectedDeliveryMethod = d.deliveryMethod;
          if (d.referralCode) this.referralCode = d.referralCode;
        }
      } catch {}
      this.loadCorridors();
      this.checkFirstTransaction();
      this.handlePreSelectedBeneficiary(beneficiaryId);
      return;
    }

    this.currentStep = 'country';
    this.selectedCorridor = null;
    this.selectedBeneficiary = null;
    this.quote = null;
    this.sendAmount = 10;
    this.selectedDeliveryMethod = '';
    this.showRecipientPanel = false;
    this.beneficiarySearch = '';
    this.beneficiaryFilter = 'all';
    this.referralCode = '';
    this.referralValid = null;
    this.referralBoostPct = 0;
    this.useWallet = false;
    this.walletAmountToUse = 0;
    this.transactionCreated = false;
    this.createdTxnRef = '';
    this.volumeReady = false;
    this.volumePayStarted = false;
    this.merchantPaymentId = '';
    this.volumeInstance = null;
    this.selectedPaymentMethod = 'OPEN_BANKING';
    this.billingInfoLoaded = false;
    this.loadUserBillingInfo();
    this.loadCorridors();
    this.checkFirstTransaction();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.rateLockTimer) clearInterval(this.rateLockTimer);
    if (this.referralDebounce) clearTimeout(this.referralDebounce);
    if (this.volumeScriptEl?.parentNode) {
      this.volumeScriptEl.parentNode.removeChild(this.volumeScriptEl);
    }
  }

  // --- First Transaction Check ---

  checkFirstTransaction(): void {
    this.transactionService.getRecent(1).subscribe({
      next: (txns) => { this.isFirstTransaction = !txns || txns.length === 0; },
      error: () => { this.isFirstTransaction = false; }
    });
  }

  // --- Wallet & Referral ---

  loadWalletBalance(): void {
    this.walletService.getWallet().subscribe({
      next: (w) => { this.walletBalance = w?.balance || 0; },
      error: () => { this.walletBalance = 0; }
    });
  }

  /**
   * Parse dateOfBirth from the API into Trust Payments YYYY-MM-DD format.
   * Handles every serialisation Jackson can produce:
   *   "2002-01-01"   ISO string  (Spring Boot default with JavaTimeModule)
   *   [2002, 1, 1]   JS array    (Jackson write-dates-as-timestamps=true)
   *   "2002,1,1"     array.toString() fallback
   * Returns "" when the value cannot be turned into a valid date.
   */
  private parseDobForTrust(raw: any): string {
    if (raw === null || raw === undefined || raw === '') return '';

    let year: number, month: number, day: number;

    // Jackson array format: [2002, 1, 1]
    if (Array.isArray(raw) && raw.length >= 3) {
      [year, month, day] = raw.map(Number);
    } else {
      const s = String(raw).trim();
      // ISO "2002-01-01" or comma-separated "2002,1,1" (Array.toString fallback)
      const parts = s.split(/[-,]/);
      if (parts.length < 3) return '';
      year  = Number(parts[0]);
      month = Number(parts[1]);
      day   = Number(parts[2].split('T')[0]); // strip time component if present
    }

    // Validate calendar values
    if (
      isNaN(year)  || isNaN(month) || isNaN(day) ||
      year  < 1900 || year  > new Date().getFullYear() ||
      month < 1    || month > 12  ||
      day   < 1    || day   > 31
    ) {
      console.warn('[Trust] billingdob validation failed:', { raw, year, month, day });
      return '';
    }

    // Trust Payments expects YYYY-MM-DD
    const dd = String(day).padStart(2, '0');
    const mm = String(month).padStart(2, '0');
    return `${year}-${mm}-${dd}`;
  }

  private loadUserBillingInfo(): void {
    this.userService.getProfile().subscribe({
      next: (user) => {
        this.billingFirstName = (user.firstName || '').trim();
        this.billingLastName  = (user.lastName  || '').trim();
        this.billingDob       = this.parseDobForTrust((user as any).dateOfBirth);

        const addr     = (user.addressLine1 || '').trim();
        const addrParts = addr.split(/\s+/);
        this.billingPremise  = addrParts[0] || '';
        this.billingStreet   = addrParts.slice(1).join(' ') || addr;
        this.billingTown     = (user.city     || '').trim();
        this.billingCounty   = (user.city     || '').trim();
        this.billingPostcode = (user.postcode || '').trim();
        this.billingInfoLoaded = true;

        console.log('[Trust] billing info loaded:', {
          name:     `${this.billingFirstName} ${this.billingLastName}`,
          dob:      this.billingDob,
          postcode: this.billingPostcode,
        });
      },
      error: (err) => {
        console.warn('[Trust] could not load billing info:', err?.message);
        this.billingInfoLoaded = true;
      }
    });
  }

  onReferralCodeChange(): void {
    this.referralValid = null;
    this.referralBoostPct = 0;
    this.applyReferralBoostToQuote();
    if (this.referralDebounce) clearTimeout(this.referralDebounce);
    if (!this.referralCode || this.referralCode.length < 6) return;
    this.referralDebounce = setTimeout(() => {
      this.referralService.validateCode(this.referralCode, this.selectedCorridor?.id).subscribe({
        next: (res) => {
          this.referralValid = res.valid;
          this.referralBoostPct = res.valid ? (res.rateBoostPercentage || 0) : 0;
          this.applyReferralBoostToQuote();
        },
        error: () => { this.referralValid = false; }
      });
    }, 600);
  }

  private applyReferralBoostToQuote(): void {
    if (!this.quote || !this.quote._baseRate) {
      // Store the original base rate on first call
      if (this.quote && !this.quote._baseRate) {
        this.quote._baseRate = this.quote.appliedRate;
        this.quote._baseReceiveAmount = this.quote.receiveAmount;
      }
    }
    if (!this.quote) return;

    if (this.referralValid && this.referralBoostPct > 0) {
      const baseRate = this.quote._baseRate || this.quote.appliedRate;
      const boost = baseRate * (this.referralBoostPct / 100);
      this.quote.appliedRate = +(baseRate + boost).toFixed(4);
      this.quote.receiveAmount = +(this.sendAmount * this.quote.appliedRate).toFixed(2);
    } else if (this.quote._baseRate) {
      // Revert to base rate
      this.quote.appliedRate = this.quote._baseRate;
      this.quote.receiveAmount = this.quote._baseReceiveAmount;
    }
  }

  onUseWalletChange(): void {
    if (this.useWallet) {
      const total = this.quote?.totalCost || this.sendAmount;
      this.walletAmountToUse = Math.min(this.walletBalance, total);
    } else {
      this.walletAmountToUse = 0;
    }
  }

  get effectiveTotal(): number {
    const total = this.quote?.totalCost || 0;
    if (this.useWallet && this.walletAmountToUse > 0) {
      return Math.max(0, total - this.walletAmountToUse);
    }
    return total;
  }

  // --- Data Loading ---

  loadCorridors(): void {
    // Gate 1: verify the user's OWN send currency has at least one ACTIVE payment method
    // in Transfer Config. If an admin disabled all payment methods for this country (e.g.
    // AU disabled), no corridors should appear and the user cannot initiate a transaction.
    this.configService.getActiveCountries().subscribe({
      next: (sendRes: any) => {
        const activeSendCountries = (sendRes?.data || sendRes || []) as any[];
        const activeSendCurrencies = new Set(activeSendCountries.map((c: any) => c.currency).filter(Boolean));
        if (!activeSendCurrencies.has(this.userSendCurrency)) {
          this.corridors = [];
          this.showToast(`Sending from ${this.userSendCurrency} is currently unavailable. Please contact support.`, 'warning');
          return;
        }
        this.loadCorridorsForActiveSend();
      },
      error: () => this.loadCorridorsForActiveSend()
    });
  }

  private loadCorridorsForActiveSend(): void {
    this.configService.getActiveReceiveCountries().subscribe({
      next: (res) => {
        const activeCountries = (res?.data || res || []) as any[];
        // Transfer Config returns ISO-2 country codes (IN, BD, AU, DE), but corridors
        // store receiveCountry as ISO-3 (IND, BGD, AUS, DEU). Build a set that contains
        // BOTH forms so the strict country match works regardless of storage format.
        const iso2ToIso3: Record<string, string> = {
          IN: 'IND', PK: 'PAK', NG: 'NGA', GH: 'GHA', PH: 'PHL', KE: 'KEN',
          NP: 'NPL', BD: 'BGD', AU: 'AUS', GB: 'GBR', US: 'USA',
          DE: 'DEU', AE: 'ARE', ZA: 'ZAF', UG: 'UGA', TZ: 'TZA', LK: 'LKA',
          SD: 'SDN', TR: 'TUR', EG: 'EGY', SA: 'SAU', QA: 'QAT'
        };
        this.activeReceiveCountryCodes = activeCountries.map((c: any) => c.countryCode);
        const activeSet = new Set<string>();
        for (const code of this.activeReceiveCountryCodes) {
          activeSet.add(code);
          if (code.length === 2 && iso2ToIso3[code]) activeSet.add(iso2ToIso3[code]);
        }

        this.fxService.getCorridors().subscribe({
          next: (corridors) => {
            const filtered = corridors.filter((c: CorridorResponse) =>
              c.isActive &&
              c.sendCurrency === this.userSendCurrency &&
              activeSet.has(c.receiveCountry)
            );
            // Deduplicate by receiveCountry — one tile per destination country
            const seen = new Set<string>();
            this.corridors = filtered.filter(c => {
              const key = c.receiveCountry;
              if (seen.has(key)) return false;
              seen.add(key);
              return true;
            });
            // Auto-select first corridor
            if (this.corridors.length > 0 && !this.selectedCorridor) {
              this.onCorridorSelect(this.corridors[0]);
            }
          },
          error: () => this.corridors = []
        });
      },
      error: () => this.corridors = []
    });
  }

  loadBeneficiaries(): void {
    this.beneficiaryService.list().subscribe({
      next: (bens) => {
        this.beneficiaries = bens;
        this.applyBeneficiaryFilter();
      },
      error: () => {
        this.beneficiaries = [];
        this.filteredBeneficiaries = [];
      }
    });
  }

  // --- Country Selection ---

  // Map payout type names to corridor delivery method names
  private payoutToDelivery: Record<string, string> = {
    'BANK_TRANSFER': 'BANK_DEPOSIT',
    'MOBILE_MONEY': 'MOBILE_WALLET',
    'CASH_COLLECTION': 'CASH_PICKUP',
    'HOME_DELIVERY': 'HOME_DELIVERY',
    'AIRTIME_TOPUP': 'AIRTIME_TOPUP'
  };

  onCorridorSelect(corridor: CorridorResponse): void {
    this.selectedCorridor = corridor;
    this.quote = null;
    this.selectedBeneficiary = null;
    this.showRecipientPanel = false;
    this.currentStep = 'calculator';
    this.loadBeneficiaries();

    // Delivery methods are driven by Transfer Config (active payout_types) — the admin's single source of truth.
    this.configService.getPayoutTypesByCurrency(corridor.receiveCurrency).subscribe({
      next: (res: any) => {
        const data: any[] = res?.data || res || [];
        const seen = new Set<string>();
        this.deliveryMethods = data
          .filter((p: any) => p.isActive)
          .map((p: any) => this.payoutToDelivery[p.payoutType] || p.payoutType)
          .filter((m: string) => m && !seen.has(m) && !!seen.add(m));
        this.selectedDeliveryMethod = this.deliveryMethods[0] || '';
        this.applyBeneficiaryFilter();
        if (this.deliveryMethods.length === 0) {
          this.showToast('No delivery methods are enabled for this country. Please contact support.', 'warning');
        }
        this.fetchQuote();
      },
      error: () => {
        this.deliveryMethods = [];
        this.selectedDeliveryMethod = '';
        this.showToast('Unable to load delivery options. Please try again.', 'danger');
      }
    });

    setTimeout(() => {
      this.calculatorSection?.nativeElement?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  }

  // --- Calculator ---

  onAmountChange(): void {
    this.isReverseCalc = false;
    this.amountChange$.next(this.sendAmount);
  }

  onReceiveAmountChange(): void {
    if (!this.quote || !this.quote.appliedRate || this.quote.appliedRate <= 0) return;
    // Local reverse calculation for instant feedback (no API call)
    // Backend formula: receiveAmount = sendAmount * rate, totalCost = sendAmount + fee
    const rate = this.quote.appliedRate;
    const fee = this.quote.fee || 0;
    this.sendAmount = Math.ceil((this.receiveAmountInput / rate) * 100) / 100;
    // Update quote display locally — keep sendAmount in sync
    this.quote.sendAmount = this.sendAmount;
    this.quote.receiveAmount = this.receiveAmountInput;
    this.quote.totalCost = this.sendAmount + fee;
    this.isReverseCalc = true;
  }

  onDeliveryMethodChange(): void {
    this.applyBeneficiaryFilter();
    this.fetchQuote();
  }

  fetchQuote(): void {
    if (!this.sendAmount || this.sendAmount <= 0 || !this.selectedCorridor || !this.selectedDeliveryMethod) return;

    this.loadingQuote = true;
    this.fxService.getQuote({
      sendCurrency: this.selectedCorridor.sendCurrency,
      receiveCurrency: this.selectedCorridor.receiveCurrency,
      sendAmount: this.sendAmount,
      deliveryMethod: this.selectedDeliveryMethod,
      corridorId: this.selectedCorridor.id
    }).subscribe({
      next: (quote) => {
        this.quote = quote;
        if (quote) {
          quote._baseRate = quote.appliedRate;
          quote._baseReceiveAmount = quote.receiveAmount;
          if (!this.isReverseCalc) {
            this.receiveAmountInput = quote.receiveAmount;
          }
        }
        this.loadingQuote = false;
        if (quote?.expiresInSeconds) {
          this.startRateLockTimer(quote.expiresInSeconds);
        }
        // Re-apply referral boost if code is already validated
        if (this.referralValid && this.referralBoostPct > 0) {
          this.applyReferralBoostToQuote();
        }
      },
      error: () => {
        this.loadingQuote = false;
        this.quote = null;
      }
    });
  }

  // --- Recipient Selection ---

  openRecipientPanel(): void {
    this.beneficiarySearch = '';
    this.beneficiaryFilter = 'all';
    this.applyBeneficiaryFilter();

    // If no recipients for this country, go directly to add recipient page
    if (this.filteredBeneficiaries.length === 0) {
      this.goToAddRecipient();
      return;
    }

    this.showRecipientPanel = true;

    setTimeout(() => {
      this.recipientSection?.nativeElement?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 100);
  }

  onBeneficiarySelect(ben: BeneficiaryResponse): void {
    this.selectedBeneficiary = ben;
    this.showRecipientPanel = false;
    this.currentStep = 'review';
    // Lock delivery method to the recipient's stored method (recipients are bound to one method)
    if (ben.deliveryMethod && this.selectedDeliveryMethod !== ben.deliveryMethod) {
      this.selectedDeliveryMethod = ben.deliveryMethod;
      this.onDeliveryMethodChange();
    }
    this.loadWalletBalance();
  }

  clearBeneficiary(): void {
    this.selectedBeneficiary = null;
    this.openRecipientPanel();
  }

  onBeneficiarySearchChange(): void {
    this.applyBeneficiaryFilter();
  }

  onBeneficiaryFilterChange(filter: 'all' | 'favourites'): void {
    this.beneficiaryFilter = filter;
    this.applyBeneficiaryFilter();
  }

  applyBeneficiaryFilter(): void {
    let list = this.beneficiaries;

    // Filter by selected country
    if (this.selectedCorridor) {
      list = list.filter(b => b.country === this.selectedCorridor!.receiveCountry);
    }

    // Only show recipients matching the selected delivery method (bank / mobile / cash).
    if (this.selectedDeliveryMethod) {
      list = list.filter(b => (b as any).deliveryMethod === this.selectedDeliveryMethod);
    }

    // Filter by favourites
    if (this.beneficiaryFilter === 'favourites') {
      list = list.filter(b => b.isFavourite);
    }

    // Filter by search term
    if (this.beneficiarySearch.trim()) {
      const term = this.beneficiarySearch.toLowerCase();
      list = list.filter(b =>
        b.fullName.toLowerCase().includes(term) ||
        (b.bankName && b.bankName.toLowerCase().includes(term))
      );
    }

    this.filteredBeneficiaries = list;
  }

  toggleFavourite(event: Event, ben: BeneficiaryResponse): void {
    event.stopPropagation();
    this.beneficiaryService.update(ben.id, { isFavourite: !ben.isFavourite }).subscribe({
      next: () => {
        ben.isFavourite = !ben.isFavourite;
        this.applyBeneficiaryFilter();
      }
    });
  }

  deleteBeneficiary(event: Event, ben: BeneficiaryResponse): void {
    event.stopPropagation();
    this.beneficiaryService.delete(ben.id).subscribe({
      next: () => {
        this.beneficiaries = this.beneficiaries.filter(b => b.id !== ben.id);
        this.applyBeneficiaryFilter();
        if (this.selectedBeneficiary?.id === ben.id) {
          this.selectedBeneficiary = null;
        }
      },
      error: () => this.showToast('Failed to delete recipient', 'danger')
    });
  }

  goToAddRecipient(): void {
    const params: any = { addNew: 'true' };
    // Carry the delivery method already chosen on Send Money so the recipient form doesn't re-ask.
    if (this.selectedDeliveryMethod) {
      params.deliveryMethod = this.selectedDeliveryMethod;
    }
    if (this.selectedCorridor) {
      // Normalize receive country to ISO-2 (corridor uses ISO-3 like AUS/IND,
      // but beneficiaries dropdown uses ISO-2 like AU/IN)
      const iso3ToIso2: Record<string, string> = {
        IND: 'IN', PAK: 'PK', NGA: 'NG', GHA: 'GH', PHL: 'PH', KEN: 'KE',
        NPL: 'NP', BGD: 'BD', AUS: 'AU', GBR: 'GB', USA: 'US',
        DEU: 'DE', ARE: 'AE', ZAF: 'ZA', UGA: 'UG', TZA: 'TZ'
      };
      const rc = this.selectedCorridor.receiveCountry;
      params.country = rc.length === 3 ? (iso3ToIso2[rc] || rc) : rc;
      params.returnTo = 'send-money';
    }
    // Persist the in-progress transfer so the amount/corridor/delivery survive the round-trip
    // to the Add Recipient page (the component is re-created on return, losing in-memory state).
    try {
      sessionStorage.setItem('layla_send_draft', JSON.stringify({
        sendAmount: this.sendAmount,
        deliveryMethod: this.selectedDeliveryMethod,
        receiveAmountInput: this.receiveAmountInput,
        corridorId: this.selectedCorridor?.id ?? null,
        referralCode: this.referralCode
      }));
    } catch {}
    this.router.navigate(['/home/beneficiaries'], { queryParams: params });
  }

  // --- Continue to Review ---

  continueToReview(): void {
    this.currentStep = 'review';
    // Land the user on the payment-method section (where the actionable controls live),
    // not the top of the review summary. Users were missing that this is a review screen
    // because the top of the page looks identical to the calculator.
    setTimeout(() => {
      const el = document.getElementById('payment-method-section');
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      } else {
        document.querySelector('ion-content')?.scrollToBottom?.(300);
      }
    }, 120);
  }

  backFromReview(): void {
    this.currentStep = 'recipient';
  }

  // --- Confirm & Send ---
  // Creates the transaction as PENDING in DB, then reveals the Volume Pay button.

  confirmSend(): void {
    if (!this.quote || !this.selectedBeneficiary || !this.selectedCorridor || this.submitting) return;

    // Capture all values NOW before any async work — prevents stale-closure bugs
    const snapshotAmount     = this.sendAmount;
    const snapshotCorridor   = this.selectedCorridor;
    const snapshotBeneficiary = this.selectedBeneficiary;
    const snapshotDelivery   = this.selectedDeliveryMethod;

    this.submitting = true;
    const idempotencyKey = this.generateUuid();

    this.fxService.getQuote({
      sendCurrency: snapshotCorridor.sendCurrency,
      receiveCurrency: snapshotCorridor.receiveCurrency,
      sendAmount: snapshotAmount,
      deliveryMethod: snapshotDelivery,
      corridorId: snapshotCorridor.id
    }).subscribe({
      next: (freshQuote) => {
        if (!freshQuote?.quoteId) {
          this.submitting = false;
          this.showToast('Could not get quote. Please try again.', 'danger');
          return;
        }

        const paymentMethodType = this.selectedPaymentMethod === 'CARD' ? 'CARD' : 'OPEN_BANKING';

        this.transactionService.create({
          quoteId: freshQuote.quoteId,
          beneficiaryId: Number(snapshotBeneficiary.id),
          corridorId: Number(snapshotCorridor.id),
          deliveryMethod: snapshotDelivery,
          sendAmount: snapshotAmount,
          sendCurrency: snapshotCorridor.sendCurrency,
          paymentMethodType: paymentMethodType,
          idempotencyKey: idempotencyKey,
          referralCode: this.referralValid ? this.referralCode : undefined,
          useWallet: this.useWallet && this.walletAmountToUse > 0,
          walletAmountToUse: this.useWallet ? this.walletAmountToUse : undefined
        }).subscribe({
          next: (txn: any) => {
            this.submitting = false;
            this.transactionCreated = true;
            this.createdTxnRef = txn?.referenceNumber || '';

            // Use the actual transaction response values — authoritative from the backend
            const txnData = {
              ref:             txn?.referenceNumber   || '',
              sendAmount:      Number(txn?.sendAmount)      || snapshotAmount,
              sendCurrency:    txn?.sendCurrency      || snapshotCorridor.sendCurrency,
              receiveAmount:   Number(txn?.receiveAmount)   || freshQuote.receiveAmount || 0,
              receiveCurrency: txn?.receiveCurrency   || snapshotCorridor.receiveCurrency || '',
              recipientName:   txn?.beneficiaryName   || snapshotBeneficiary.fullName || '',
              fee:             Number(txn?.feeAmount)       || freshQuote.fee          || 0,
              exchangeRate:    Number(txn?.appliedRate)     || freshQuote.appliedRate  || 0
            };
            localStorage.setItem('layla_last_txn', JSON.stringify(txnData));

            if (this.selectedPaymentMethod === 'CARD') {
              this.payWithCard(txn);
            } else {
              this.merchantPaymentId = this.generateMerchantPaymentId();
              this.loadVolumeSdk();
              setTimeout(() => {
                document.getElementById('volume-pay-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
              }, 200);
            }
          },
          error: (err) => {
            this.submitting = false;
            this.showToast(err?.error?.message || 'Failed to create transfer.', 'danger');
          }
        });
      },
      error: () => {
        this.submitting = false;
        this.showToast('Failed to get quote. Please try again.', 'danger');
      }
    });
  }

  // --- Volume Pay (Open Banking) ---

  private loadVolumeSdk(): void {
    if ((window as any).Volume) {
      this.volumeReady = true;
      this.payWithBank();
      return;
    }
    this.volumeScriptEl = document.createElement('script');
    this.volumeScriptEl.src = 'https://js.volumepay.io';
    this.volumeScriptEl.onload = () => {
      this.ngZone.run(() => {
        this.volumeReady = true;
        this.payWithBank();
      });
    };
    this.volumeScriptEl.onerror = () => {
      this.showToast('Could not load Open Banking SDK. Please try again.', 'danger');
    };
    document.head.appendChild(this.volumeScriptEl);
  }

  payWithBank(): void {
    if (this.volumePayStarted || !this.volumeReady) return;
    this.volumePayStarted = true;

    // Register payment intent with our backend first
    this.http.post<any>(`${this.apiUrl}/volume/payment-intent`, {
      transactionId: this.createdTxnRef,
      merchantPaymentId: this.merchantPaymentId,
      amount: this.sendAmount,
      currency: this.selectedCorridor?.sendCurrency || 'GBP'
    }).subscribe({
      next: (res) => {
        this.initVolumeWidget(res.applicationId, res.environment);
      },
      error: () => {
        this.volumePayStarted = false;
        this.showToast('Could not initialise Open Banking. Please try again.', 'danger');
      }
    });
  }

  private initVolumeWidget(applicationId: string, environment: string): void {
    const volume = new (window as any).Volume({
      environment: environment || 'SANDBOX',
      applicationId: applicationId,
      agentType: 'WEB_BROWSER',
      isWebView: false,
      eventConsumer: (event: any) => {
        console.log('Volume event:', event);
        this.ngZone.run(() => this.handleVolumeEvent(event));
      },
      errorConsumer: (error: any) => {
        console.error('Volume error:', error);
        this.ngZone.run(() => {
          this.volumePayStarted = false;
          this.showToast('Payment error. Please try again.', 'danger');
        });
      }
    });

    volume.createPayment({
      amount: this.sendAmount,
      merchantPaymentId: this.merchantPaymentId,
      paymentReference: this.createdTxnRef,
      agentType: 'WEB_BROWSER'
    });

    volume.injectComponent('volume-element-container');
    this.volumeInstance = volume;
  }

  /**
   * Volume emits lifecycle/status events through eventConsumer. When the payment completes,
   * route the customer to the success page (volume-callback). The backend webhook remains the
   * source of truth for the transaction status; this is the on-screen confirmation.
   */
  private handleVolumeEvent(event: any): void {
    const status = (event?.paymentStatus || event?.status || event?.type || event?.name || '')
      .toString().toUpperCase();
    const done = ['COMPLETED', 'SETTLED', 'SUCCESS', 'PAID', 'PAYMENT_COMPLETED'];
    if (done.some(s => status.includes(s))) {
      this.router.navigate(['/home/volume-callback'], {
        queryParams: {
          merchantPaymentId: this.merchantPaymentId,
          amount: this.sendAmount,
          currency: this.selectedCorridor?.sendCurrency || 'GBP',
          status: 'COMPLETED'
        }
      });
    }
  }

  private generateMerchantPaymentId(): string {
    let id = '';
    for (let i = 0; i < 18; i++) id += Math.floor(Math.random() * 10).toString();
    return id;
  }

  // --- Trust Payments (Credit/Debit Card) ---

  private readonly iso3ToIso2: Record<string, string> = {
    IND: 'IN', PAK: 'PK', NGA: 'NG', GHA: 'GH', PHL: 'PH', KEN: 'KE',
    NPL: 'NP', BGD: 'BD', AUS: 'AU', GBR: 'GB', USA: 'US', ARE: 'AE',
    DEU: 'DE', ZAF: 'ZA', UGA: 'UG', TZA: 'TZ', LKA: 'LK', EGY: 'EG',
    MAR: 'MA', SGP: 'SG', MYS: 'MY', CAN: 'CA', FRA: 'FR', ITA: 'IT', ESP: 'ES'
  };

  private toIso2(code: string): string {
    if (!code) return '';
    return (code.length === 3 && this.iso3ToIso2[code]) ? this.iso3ToIso2[code] : code;
  }

  /** Trim whitespace and replace non-breaking / zero-width spaces with nothing. */
  private sanitize(v: string): string {
    return (v || '').replace(/[ ​‌‍﻿]/g, '').trim();
  }

  private payWithCard(txn: any): void {
    const total      = this.effectiveTotal;
    const mainamount = total.toFixed(2);

    const bene        = this.selectedBeneficiary;
    const nameParts   = (bene?.fullName || '').trim().split(/\s+/);
    const custFirst   = this.sanitize(nameParts[0] || '');
    const custLast    = this.sanitize(nameParts.slice(1).join(' ') || nameParts[0] || '');
    const custCountry = this.toIso2((bene?.country || '').trim());
    const custAccount = this.sanitize(
      bene?.accountNumber || bene?.iban || txn?.referenceNumber || ''
    );

    const safeDob = this.parseDobForTrust(this.billingDob);
    const callbackUrl = `${window.location.origin}/home/trust-callback`;

    const fields: Array<[string, string]> = [
      ['sitereference',         'laylalondo147951'],
      ['stprofile',             'default'],
      ['currencyiso3a',         'GBP'],
      ['mainamount',            mainamount],
      ['version',               '2'],
      ['billingfirstname',      this.sanitize(this.billingFirstName)],
      ['billinglastname',       this.sanitize(this.billingLastName)],
      ['billingcountryiso2a',   'GB'],
      ['customerfirstname',     custFirst],
      ['customerlastname',      custLast],
      ['ruleidentifier',        'STR-6'],
      ['ruleidentifier',        'STR-11'],
      ['successfulurlredirect', callbackUrl],
      ['declinedurlredirect',   callbackUrl],
    ];

    // Only send DOB if it parsed to a valid YYYY-MM-DD
    if (safeDob) fields.splice(6, 0, ['billingdob', safeDob]);

    if (custCountry) fields.push(['customercountryiso2a', custCountry]);
    if (custAccount) fields.push(['customeraccountnumber', custAccount],
                                 ['customeraccountnumbertype', 'ACCOUNT']);

    console.log('[Trust] submitting form fields:', Object.fromEntries(
      fields.map(([k, v]) => [k, v])
    ));

    const form = document.createElement('form');
    form.method = 'POST';
    form.action = 'https://payments.securetrading.net/process/payments/details';
    fields.forEach(([name, value]) => {
      const inp = document.createElement('input');
      inp.type  = 'hidden';
      inp.name  = name;
      inp.value = value;
      form.appendChild(inp);
    });
    document.body.appendChild(form);
    form.submit();
  }

  // --- Helpers ---

  getCountryName(code: string): string {
    return this.countryNames[code] || code;
  }

  getFlag(code: string): string {
    return this.countryFlags[code] || '';
  }

  getDeliveryLabel(method: string): string {
    return this.deliveryMethodLabels[method] || method;
  }

  getInitials(name: string): string {
    return (name || '??').split(' ').map(n => n[0]).join('').substring(0, 2).toUpperCase();
  }

  private startRateLockTimer(seconds: number): void {
    if (this.rateLockTimer) clearInterval(this.rateLockTimer);
    this.rateLockPercent = 100;
    const decrement = 100 / seconds;
    this.rateLockTimer = setInterval(() => {
      this.rateLockPercent -= decrement;
      if (this.rateLockPercent <= 0) {
        this.rateLockPercent = 0;
        clearInterval(this.rateLockTimer);
      }
    }, 1000);
  }

  private handlePreSelectedBeneficiary(beneficiaryId: string): void {
    // Beneficiaries store country in ISO-2 (e.g. 'AU'), corridors use ISO-3 (e.g. 'AUS').
    // Normalize both directions so the match works regardless of which format each side uses.
    const iso2ToIso3: Record<string, string> = {
      IN: 'IND', PK: 'PAK', NG: 'NGA', GH: 'GHA', PH: 'PHL', KE: 'KEN',
      NP: 'NPL', BD: 'BGD', AU: 'AUS', GB: 'GBR', US: 'USA',
      DE: 'DEU', AE: 'ARE', ZA: 'ZAF', UG: 'UGA', TZ: 'TZA', LK: 'LKA'
    };
    this.beneficiaryService.getById(beneficiaryId).subscribe({
      next: (ben) => {
        const benCountry = ben.country || '';
        const benCountry3 = benCountry.length === 2 ? (iso2ToIso3[benCountry] || benCountry) : benCountry;
        const setupWithCorridor = (corridors: any[]) => {
          const match = corridors.find((c: any) =>
            c.receiveCountry === benCountry || c.receiveCountry === benCountry3
          );
          if (match) {
            this.selectedCorridor = match;
            this.selectedBeneficiary = ben;
            this.showRecipientPanel = false;
            // Recipient is already chosen — skip the calculator/recipient steps and land on review.
            // (User came back from the Add Recipient flow with sendAmount already in component state.)
            this.currentStep = this.sendAmount > 0 ? 'review' : 'calculator';
            this.loadWalletBalance();
            this.loadBeneficiaries();

            // Filter delivery methods by active payout types
            const allMethods = match.deliveryMethods || [];
            this.configService.getPayoutTypesByCurrency(match.receiveCurrency).subscribe({
              next: (res: any) => {
                const data: any[] = res?.data || res || [];
                const activePayoutTypes = data
                  .filter((p: any) => p.isActive)
                  .map((p: any) => this.payoutToDelivery[p.payoutType] || p.payoutType);
                this.deliveryMethods = allMethods.filter((m: string) => activePayoutTypes.includes(m));
                if (this.deliveryMethods.length === 0) this.deliveryMethods = allMethods;
                if (this.deliveryMethods.length > 0 && !this.selectedDeliveryMethod) {
                  this.selectedDeliveryMethod = this.deliveryMethods[0];
                }
                if (this.sendAmount > 0) this.fetchQuote();
              },
              error: () => {
                this.deliveryMethods = allMethods;
                if (this.deliveryMethods.length > 0 && !this.selectedDeliveryMethod) {
                  this.selectedDeliveryMethod = this.deliveryMethods[0];
                }
                if (this.sendAmount > 0) this.fetchQuote();
              }
            });
          }
        };

        if (this.corridors.length > 0) {
          setupWithCorridor(this.corridors);
        } else {
          // Apply the SAME Transfer Config filter as loadCorridors — otherwise the
          // beneficiaryId fallback path shows corridors for countries the admin disabled.
          this.configService.getActiveReceiveCountries().subscribe({
            next: (cres: any) => {
              const activeCountries = (cres?.data || cres || []) as any[];
              const iso2ToIso3: Record<string, string> = {
                IN: 'IND', PK: 'PAK', NG: 'NGA', GH: 'GHA', PH: 'PHL', KE: 'KEN',
                NP: 'NPL', BD: 'BGD', AU: 'AUS', GB: 'GBR', US: 'USA',
                DE: 'DEU', AE: 'ARE', ZA: 'ZAF', UG: 'UGA', TZ: 'TZA', LK: 'LKA'
              };
              const activeSet = new Set<string>();
              for (const c of activeCountries) {
                const code = c.countryCode;
                activeSet.add(code);
                if (code?.length === 2 && iso2ToIso3[code]) activeSet.add(iso2ToIso3[code]);
              }
              this.fxService.getCorridors().subscribe(corridors => {
                const filtered = corridors.filter((c: any) =>
                  c.isActive && c.sendCurrency === this.userSendCurrency && activeSet.has(c.receiveCountry)
                );
                const seen = new Set<string>();
                this.corridors = filtered.filter(c => {
                  if (seen.has(c.receiveCountry)) return false;
                  seen.add(c.receiveCountry);
                  return true;
                });
                setupWithCorridor(this.corridors);
              });
            },
            error: () => {
              this.fxService.getCorridors().subscribe(corridors => {
                this.corridors = corridors.filter((c: any) =>
                  c.isActive && c.sendCurrency === this.userSendCurrency
                );
                setupWithCorridor(this.corridors);
              });
            }
          });
        }
      },
      error: () => {
        this.showToast('Could not load recipient details', 'danger');
      }
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }

  private generateUuid(): string {
    if (typeof crypto !== 'undefined' && (crypto as any).randomUUID) {
      return (crypto as any).randomUUID();
    }
    // RFC4122 v4 fallback
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
      const r = Math.random() * 16 | 0;
      const v = c === 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }
}
