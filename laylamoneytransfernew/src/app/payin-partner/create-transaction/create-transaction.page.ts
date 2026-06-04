import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Subject, debounceTime, takeUntil } from 'rxjs';
import { PartnerService } from '../../core/services/partner.service';
import { FxService } from '../../core/services/fx.service';
import { UsiMoneyService } from '../../core/services/usi-money.service';
import { ConfigService } from '../../core/services/config.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-create-transaction',
  template: `
    <div class="ct-page">

      <!-- Header -->
      <div class="ct-header">
        <button class="ct-back-btn" (click)="router.navigate(['/payin-partner/transactions'])">
          <ion-icon name="arrow-back-outline"></ion-icon>
        </button>
        <div>
          <h1 class="ct-title">Create Transaction</h1>
          <p class="ct-sub">Complete all steps to submit a PayIn transaction</p>
        </div>
      </div>

      <!-- Step Indicator -->
      <div class="ct-steps">
        <div class="ct-step" *ngFor="let s of steps; let i = index" [class.active]="currentStep === i" [class.done]="currentStep > i">
          <div class="ct-step__dot">
            <ion-icon *ngIf="currentStep > i" name="checkmark"></ion-icon>
            <span *ngIf="currentStep <= i">{{ i + 1 }}</span>
          </div>
          <span class="ct-step__label">{{ s }}</span>
        </div>
      </div>

      <!-- ─── STEP 0: Select Customer ─────────────────────────────────── -->
      <div class="ct-card" *ngIf="currentStep === 0">
        <h2 class="ct-section-title">
          <ion-icon name="person-outline"></ion-icon> Select Customer
        </h2>

        <div class="ct-search-box">
          <ion-icon name="search-outline"></ion-icon>
          <input
            [(ngModel)]="customerSearch"
            (ngModelChange)="filterCustomers()"
            placeholder="Search by name, email or phone..."
            class="ct-search-input"
          />
        </div>

        <div *ngIf="loadingCustomers" class="ct-loading">
          <div class="ct-skeleton" *ngFor="let i of [1,2,3]"></div>
        </div>

        <div class="ct-list" *ngIf="!loadingCustomers">
          <div class="ct-search-hint" *ngIf="customerSearch.length < 2">
            <ion-icon name="search-outline"></ion-icon>
            Type at least 2 characters to search {{ customers.length }} customers…
          </div>
          <ng-container *ngIf="customerSearch.length >= 2">
            <div
              class="ct-list-item ct-customer-item"
              *ngFor="let c of filteredCustomers"
              (click)="selectCustomer(c)"
            >
              <div class="ct-avatar">{{ avatarInitials(c) }}</div>
              <div class="ct-item-info">
                <div class="ct-item-name">{{ c.firstName || '—' }} {{ c.lastName || '' }}</div>
                <div class="ct-item-sub">{{ c.email }} &nbsp;·&nbsp; {{ c.phone }}</div>
              </div>
              <ion-badge color="success" *ngIf="c.isVerified">Verified</ion-badge>
              <ion-badge color="medium" *ngIf="!c.isVerified">Unverified</ion-badge>
            </div>
            <div class="ct-empty" *ngIf="filteredCustomers.length === 0">
              No customers found. <a routerLink="/payin-partner/create-customer">Create one?</a>
            </div>
          </ng-container>
        </div>
      </div>

      <!-- ─── KYC GATE: Shown when selected customer is unverified ───── -->
      <div class="ct-card" *ngIf="kycStep">
        <h2 class="ct-section-title">
          <ion-icon name="shield-outline"></ion-icon> Complete KYC for {{ selectedCustomer?.firstName }} {{ selectedCustomer?.lastName }}
        </h2>

        <div *ngIf="kycExistingLoading" class="ct-section-sub">Loading existing KYC data…</div>

        <!-- ── Existing data already on file → Verify button (no re-upload) ── -->
        <div *ngIf="!kycExistingLoading && kycHasExisting && !kycEditMode">
          <p class="ct-section-sub">
            This customer already has all required KYC details on file. Review and click <strong>Verify</strong> to continue.
          </p>

          <div class="ct-kyc-summary">
            <div class="ct-kyc-row">
              <span>Date of Birth</span>
              <strong *ngIf="kycDob">{{ kycDob }}</strong>
              <input *ngIf="!kycDob" type="date" [(ngModel)]="kycDob" class="ct-input ct-input--inline" />
            </div>
            <div class="ct-kyc-row"><span>ID Type</span><strong>{{ kycIdType }}</strong></div>
            <div class="ct-kyc-row"><span>Document Number</span><strong>{{ kycDocNumber || '—' }}</strong></div>
            <div class="ct-kyc-row"><span>Issue Date</span><strong>{{ kycIssueDate || '—' }}</strong></div>
            <div class="ct-kyc-row"><span>Expiry Date</span><strong>{{ kycExpiryDate || '—' }}</strong></div>
            <div class="ct-kyc-row"><span>Address Proof Type</span><strong>{{ kycAddrType }}</strong></div>
          </div>

          <div class="ct-kyc-previews">
            <div class="ct-kyc-preview" *ngIf="kycExistingIdBlobUrl || kycExistingIdFileUrl">
              <div class="ct-kyc-preview-label">Identity proof</div>
              <img *ngIf="kycExistingIdBlobUrl" [src]="kycExistingIdBlobUrl" alt="Identity proof" />
              <div *ngIf="!kycExistingIdBlobUrl" class="ct-kyc-preview-loading">Loading preview…</div>
            </div>
            <div class="ct-kyc-preview" *ngIf="kycExistingAddrBlobUrl || kycExistingAddrFileUrl">
              <div class="ct-kyc-preview-label">Address proof</div>
              <img *ngIf="kycExistingAddrBlobUrl" [src]="kycExistingAddrBlobUrl" alt="Address proof" />
              <div *ngIf="!kycExistingAddrBlobUrl" class="ct-kyc-preview-loading">Loading preview…</div>
            </div>
          </div>

          <div class="ct-error" *ngIf="kycError">{{ kycError }}</div>

          <div class="ct-actions">
            <button class="ct-btn ct-btn--outline" (click)="cancelKyc()" [disabled]="kycSubmitting">Back</button>
            <button class="ct-btn ct-btn--outline" (click)="enterEditMode()" [disabled]="kycSubmitting">Edit / Re-upload</button>
            <button class="ct-btn ct-btn--primary" (click)="verifyExistingCustomer()" [disabled]="kycSubmitting">
              <ion-icon name="checkmark-circle-outline"></ion-icon>
              {{ kycSubmitting ? 'Verifying…' : 'Verify Customer' }}
            </button>
          </div>
        </div>

        <!-- ── No existing data (or edit mode) → full upload form ── -->
        <ng-container *ngIf="!kycExistingLoading && (!kycHasExisting || kycEditMode)">
        <p class="ct-section-sub">
          This customer is not verified yet. Please upload an ID proof and an address proof, plus confirm date of birth, before continuing.
        </p>

        <div class="ct-field">
          <label>Date of Birth *</label>
          <input type="date" [(ngModel)]="kycDob" class="ct-input" />
        </div>

        <!-- ID Proof section -->
        <h3 class="ct-subsection">Identity proof</h3>

        <div class="ct-field">
          <label>ID Type *</label>
          <select [(ngModel)]="kycIdType" class="ct-input">
            <option value="">-- select --</option>
            <option value="PASSPORT">Passport</option>
            <option value="DRIVING_LICENCE">Driver's licence</option>
            <option value="NATIONAL_ID">National ID</option>
            <option value="RESIDENCE_PERMIT">Residence permit (BRP)</option>
          </select>
        </div>

        <div class="ct-field">
          <label>ID Document Number *</label>
          <input type="text" [(ngModel)]="kycDocNumber" placeholder="e.g. P12345678" class="ct-input" />
        </div>

        <div class="ct-row">
          <div class="ct-field ct-field--half">
            <label>Issue date (from) *</label>
            <input type="date" [(ngModel)]="kycIssueDate" class="ct-input" />
          </div>
          <div class="ct-field ct-field--half">
            <label>Expiry date (to) *</label>
            <input type="date" [(ngModel)]="kycExpiryDate" class="ct-input" />
          </div>
        </div>

        <div class="ct-field">
          <label>ID Proof (front) *</label>
          <input type="file" accept="image/jpeg,image/png,application/pdf" (change)="onKycIdFile($event)" />
          <div class="ct-hint" *ngIf="kycIdFile">{{ kycIdFile.name }}</div>
        </div>

        <!-- Address Proof section -->
        <h3 class="ct-subsection">Address proof</h3>

        <div class="ct-field">
          <label>Address Proof Type *</label>
          <select [(ngModel)]="kycAddrType" class="ct-input">
            <option value="">-- select --</option>
            <option value="UTILITY_BILL">Utility bill (within 3 months)</option>
            <option value="BANK_STATEMENT">Bank statement (within 3 months)</option>
            <option value="COUNCIL_TAX">Council tax bill</option>
            <option value="LEASE_AGREEMENT">Lease / rental agreement</option>
            <option value="GOVT_LETTER">Government letter (within 3 months)</option>
            <option value="CREDIT_CARD_STATEMENT">Credit card statement (within 3 months)</option>
          </select>
        </div>

        <div class="ct-field">
          <label>Address Proof File *</label>
          <input type="file" accept="image/jpeg,image/png,application/pdf" (change)="onKycAddrFile($event)" />
          <div class="ct-hint" *ngIf="kycAddrFile">{{ kycAddrFile.name }}</div>
        </div>

        <div class="ct-error" *ngIf="kycError">{{ kycError }}</div>

        <div class="ct-actions">
          <button class="ct-btn ct-btn--outline" (click)="cancelKyc()" [disabled]="kycSubmitting">Back</button>
          <button class="ct-btn ct-btn--primary" (click)="submitKyc()" [disabled]="kycSubmitting">
            <ion-icon name="cloud-upload-outline"></ion-icon>
            {{ kycSubmitting ? 'Uploading…' : 'Submit & Continue' }}
          </button>
        </div>
        </ng-container>
      </div>

      <!-- ─── STEP 1: Amount & Rate ──────────────────────────────────── -->
      <div class="ct-card" *ngIf="currentStep === 1 && !kycStep">
        <h2 class="ct-section-title">
          <ion-icon name="calculator-outline"></ion-icon> Amount &amp; Exchange Rate
        </h2>

        <div class="ct-selected-customer">
          <ion-icon name="person-circle-outline"></ion-icon>
          <span><strong>{{ selectedCustomer?.firstName }} {{ selectedCustomer?.lastName }}</strong> &nbsp;·&nbsp; {{ selectedCustomer?.email }}</span>
          <button class="ct-change-btn" (click)="currentStep = 0">Change</button>
        </div>

        <!-- Receive country cards -->
        <label class="ct-corridor-label">Receive country <span class="ct-req">*</span></label>
        <div class="ct-corridor-grid">
          <button type="button" class="ct-corridor-card"
                  *ngFor="let c of availableReceiveCurrencies"
                  [class.ct-corridor-card--active]="receiveCurrency === c.currency"
                  (click)="selectReceiveCountry(c)">
            <img *ngIf="flagFor(c.currency, c.country)" [src]="flagFor(c.currency, c.country)"
                 class="ct-corridor-flag" alt="" onerror="this.style.display='none'">
            <div>
              <div class="ct-corridor-country">{{ (c.country || c.currency) | uppercase }}</div>
              <div class="ct-corridor-ccy">{{ c.currency }}</div>
            </div>
          </button>
        </div>

        <!-- Amount row -->
        <div class="ct-amount-row">
          <div class="ct-amount-group">
            <label>You Collect</label>
            <div class="ct-amount-input-wrap">
              <input
                type="number"
                [(ngModel)]="sendAmount"
                (ngModelChange)="onSendAmountChange()"
                min="1"
                step="0.01"
                class="ct-amount-input"
              />
              <img *ngIf="flagFor(sendCurrency)" [src]="flagFor(sendCurrency)" class="ct-flag" alt="" />
              <select [(ngModel)]="sendCurrency" (ngModelChange)="onCorridorChange()" class="ct-currency-select">
                <option value="GBP">GBP</option>
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
                <option value="AED">AED</option>
                <option value="AUD">AUD</option>
                <option value="CAD">CAD</option>
                <option value="SGD">SGD</option>
              </select>
            </div>
          </div>

          <div class="ct-rate-arrow">
            <div *ngIf="loadingQuote" class="ct-rate-spinner">
              <ion-spinner name="dots"></ion-spinner>
            </div>
            <div *ngIf="!loadingQuote && quote" class="ct-rate-box">
              <div class="ct-rate-value">1 {{ sendCurrency }} = {{ quote.appliedRate | number:'1.4-4' }} {{ receiveCurrency }}</div>
              <div class="ct-fee-value">Fee: {{ sendCurrency }} {{ quote.fee | number:'1.2-2' }}</div>
            </div>
            <ion-icon name="arrow-forward-outline" *ngIf="!loadingQuote && !quote"></ion-icon>
          </div>

          <div class="ct-amount-group">
            <label>Beneficiary Receives</label>
            <div class="ct-amount-input-wrap">
              <input
                type="number"
                [value]="quote?.receiveAmount || 0"
                readonly
                class="ct-amount-input ct-amount-input--readonly"
              />
              <img *ngIf="flagFor(receiveCurrency, receiveCountry)" [src]="flagFor(receiveCurrency, receiveCountry)" class="ct-flag" alt="" />
              <select [(ngModel)]="receiveCurrency" disabled class="ct-currency-select">
                <option [value]="receiveCurrency">{{ receiveCurrency }}</option>
              </select>
            </div>
          </div>
        </div>

        <!-- Total cost -->
        <div class="ct-total-row" *ngIf="quote">
          <span>Total to collect from customer:</span>
          <strong>{{ sendCurrency }} {{ quote.totalCost | number:'1.2-2' }}</strong>
        </div>

        <!-- Delivery Method -->
        <div class="ct-field-group">
          <label class="ct-label">Delivery Method <span class="ct-req">*</span></label>
          <div class="ct-pill-group">
            <button
              *ngFor="let m of deliveryMethods"
              type="button"
              class="ct-pill"
              [class.ct-pill--active]="selectedDeliveryMethod === m.value"
              (click)="selectedDeliveryMethod = m.value; loadUsiCashPointsIfNeeded(); filterBeneficiaries()"
            >
              <ion-icon [name]="m.icon"></ion-icon> {{ m.label }}
            </button>
          </div>
        </div>

        <!-- Collection Type -->
        <div class="ct-field-group">
          <label class="ct-label">Collection Type <span class="ct-req">*</span> <span class="ct-hint">(how you received the money)</span></label>
          <div class="ct-pill-group">
            <button
              *ngFor="let c of collectionTypes"
              type="button"
              class="ct-pill"
              [class.ct-pill--active]="selectedCollectionType === c.value"
              (click)="selectedCollectionType = c.value"
            >
              <ion-icon [name]="c.icon"></ion-icon> {{ c.label }}
            </button>
          </div>
        </div>

        <div class="ct-error" *ngIf="stepError">{{ stepError }}</div>
        <div class="ct-nav-row">
          <button class="ct-btn ct-btn--outline" (click)="currentStep = 0">Back</button>
          <button class="ct-btn ct-btn--primary" (click)="goToStep2()">Next</button>
        </div>
      </div>

      <!-- ─── STEP 2: Beneficiary ────────────────────────────────────── -->
      <div class="ct-card" *ngIf="currentStep === 2">
        <h2 class="ct-section-title">
          <ion-icon name="people-outline"></ion-icon> Beneficiary
        </h2>

        <div class="ct-selected-customer" style="margin-bottom:16px;">
          <ion-icon name="person-circle-outline"></ion-icon>
          <span>{{ selectedCustomer?.firstName }} {{ selectedCustomer?.lastName }}
            &nbsp;·&nbsp; {{ sendCurrency }} {{ sendAmount | number:'1.2-2' }}
            → {{ receiveCurrency }} {{ quote?.receiveAmount | number:'1.2-2' }}
          </span>
        </div>

        <!-- Existing beneficiaries -->
        <div *ngIf="!showAddBeneficiary">
          <div class="ct-search-box" *ngIf="beneficiaries.length > 0">
            <ion-icon name="search-outline"></ion-icon>
            <input
              [(ngModel)]="beneficiarySearch"
              (ngModelChange)="filterBeneficiaries()"
              placeholder="Search by name or bank..."
              class="ct-search-input"
            />
          </div>

          <div *ngIf="loadingBeneficiaries" class="ct-loading">
            <div class="ct-skeleton" *ngFor="let i of [1,2]"></div>
          </div>

          <div class="ct-list" *ngIf="!loadingBeneficiaries">
            <div
              class="ct-list-item ct-ben-item"
              *ngFor="let b of filteredBeneficiaries"
              [class.ct-ben-item--selected]="selectedBeneficiary?.id === b.id"
              (click)="selectBeneficiary(b)"
            >
              <div class="ct-ben-avatar">{{ b.name[0].toUpperCase() }}</div>
              <div class="ct-item-info">
                <div class="ct-item-name">{{ b.name }}</div>
                <div class="ct-item-sub">{{ b.bankName }} &nbsp;·&nbsp; ****{{ b.accountNumber.slice(-4) }}</div>
                <div class="ct-item-sub" *ngIf="b.ifscCode">IFSC: {{ b.ifscCode }}</div>
              </div>
              <ion-icon name="checkmark-circle" color="success" *ngIf="selectedBeneficiary?.id === b.id"></ion-icon>
            </div>
            <div class="ct-empty" *ngIf="filteredBeneficiaries.length === 0 && !beneficiarySearch">
              No beneficiaries yet for this customer.
            </div>
            <div class="ct-empty" *ngIf="filteredBeneficiaries.length === 0 && beneficiarySearch">
              No matches found.
            </div>
          </div>

          <button class="ct-add-ben-btn" (click)="showAddBeneficiary = true">
            <ion-icon name="add-circle-outline"></ion-icon> Add New Beneficiary
          </button>
        </div>

        <!-- Add new beneficiary inline form -->
        <div class="ct-add-ben-form" *ngIf="showAddBeneficiary">
          <h3 class="ct-add-ben-title">New Beneficiary Details</h3>

          <!-- Common fields -->
          <div class="ct-form-grid">
            <div class="ct-field">
              <label>Full Name <span class="ct-req">*</span></label>
              <input [(ngModel)]="newBen.name" placeholder="Beneficiary full name" />
            </div>
            <div class="ct-field">
              <label>Mobile Number <span class="ct-req">*</span></label>
              <input [(ngModel)]="newBen.mobileNumber" placeholder="e.g. +256781234567" />
            </div>
            <div class="ct-field ct-field--full" *ngIf="!isSudanBank()">
              <label>Address <span class="ct-req">*</span></label>
              <input [(ngModel)]="newBen.address" placeholder="Beneficiary address" />
            </div>
          </div>

          <!-- Bank Deposit fields -->
          <ng-container *ngIf="selectedDeliveryMethod === 'BANK_DEPOSIT' || selectedDeliveryMethod === 'BANK_TRANSFER'">
            <h4 class="ct-subsection">Bank Details</h4>
            <div class="ct-form-grid">
              <div class="ct-field">
                <label>Bank Name <span class="ct-req">*</span></label>
                <select [(ngModel)]="newBen.bankName" class="ct-input">
                  <option value="" disabled>{{ loadingBanks ? 'Loading banks…' : (bankNames.length ? 'Select bank…' : 'No banks found') }}</option>
                  <option *ngFor="let b of bankNames" [value]="b">{{ b }}</option>
                </select>
              </div>
              <div class="ct-field" *ngIf="!isSudan()">
                <label>Branch Name <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.sortCode" placeholder="e.g. First Abu Dhabi Bank" />
              </div>
              <div class="ct-field" *ngIf="!isSudan()">
                <label>Bank State <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.branchState" placeholder="Any Branch" />
              </div>
              <div class="ct-field" *ngIf="!isSudan()">
                <label>Bank City <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.branchCity" placeholder="Any Branch" />
              </div>
              <div class="ct-field">
                <label>Account Number <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.accountNumber" [placeholder]="isUsiIbanCountry() ? 'Same as IBAN' : 'Bank account number'" />
              </div>
              <div class="ct-field" *ngIf="isUsiIbanCountry()">
                <label>IBAN <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.iban" placeholder="e.g. AE070331234567890123456" />
              </div>
              <div class="ct-field" *ngIf="!isUsiIbanCountry() && !isNoSwiftCountry() && !isSudan()">
                <label>SWIFT / BIC Code <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.swiftBic" placeholder="e.g. CITIUS33" />
              </div>
            </div>
          </ng-container>

          <!-- Mobile Wallet fields -->
          <ng-container *ngIf="selectedDeliveryMethod === 'MOBILE_WALLET' || selectedDeliveryMethod === 'MOBILE_MONEY'">
            <h4 class="ct-subsection">Mobile Wallet Details</h4>
            <div class="ct-form-grid">
              <div class="ct-field">
                <label>Mobile Network <span class="ct-req">*</span></label>
                <select [(ngModel)]="newBen.mobileProvider">
                  <option value="" disabled>Select network…</option>
                  <option value="MTN Mobile Money">MTN Mobile Money</option>
                  <option value="Airtel Money">Airtel Money</option>
                  <option value="Vodafone Cash">Vodafone Cash</option>
                  <option value="Orange Money">Orange Money</option>
                </select>
              </div>
              <div class="ct-field">
                <label>Wallet Number <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.accountNumber" placeholder="e.g. +256781234567" />
              </div>
            </div>
          </ng-container>

          <!-- Cash Collection fields -->
          <ng-container *ngIf="selectedDeliveryMethod === 'CASH_PICKUP'">
            <h4 class="ct-subsection">Cash Collection Point</h4>
            <div class="ct-form-grid">
              <div class="ct-field ct-field--full" *ngIf="isUsiCountry() && usiCollectionPoints.length > 0">
                <label>Collection Point (from USI)</label>
                <select (change)="applyUsiCashPoint($any($event.target).value)">
                  <option value="">— pick or fill manually —</option>
                  <option *ngFor="let p of usiCollectionPoints" [value]="p.code">
                    {{ p.name }} — {{ p.city || 'Anywhere' }} ({{ p.code }})
                  </option>
                </select>
              </div>
              <div class="ct-field">
                <label>Collection Point Name <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.collectionPointName" placeholder="e.g. Cash Payout Anywhere" />
              </div>
              <div class="ct-field">
                <label>Collection Code <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.collectionPointCode" placeholder="e.g. BR-QAT" />
              </div>
              <div class="ct-field">
                <label>Collection Address <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.collectionPointAddress" placeholder="Pickup address" />
              </div>
              <div class="ct-field">
                <label>Collection City <span class="ct-req">*</span></label>
                <input [(ngModel)]="newBen.collectionPointCity" placeholder="e.g. Doha" />
              </div>
            </div>
          </ng-container>

          <div class="ct-add-ben-actions">
            <button class="ct-btn ct-btn--outline" (click)="showAddBeneficiary = false">Cancel</button>
            <button class="ct-btn ct-btn--success" (click)="useNewBeneficiary()">Use This Beneficiary</button>
          </div>
        </div>

        <div class="ct-error" *ngIf="stepError">{{ stepError }}</div>
        <div class="ct-nav-row">
          <button class="ct-btn ct-btn--outline" (click)="currentStep = 1; stepError = ''">Back</button>
          <button class="ct-btn ct-btn--primary" (click)="goToStep3()">Next</button>
        </div>
      </div>

      <!-- ─── STEP 4: Receipt ──────────────────────────────────────── -->
      <div class="ct-card ct-receipt" *ngIf="currentStep === 4">
        <div class="ct-receipt-icon">
          <ion-icon name="checkmark-circle" color="success"></ion-icon>
        </div>
        <h2 class="ct-receipt-title">Transaction Created!</h2>
        <p class="ct-receipt-sub">Your PayIn transaction has been submitted successfully.</p>

        <!-- Embedded branded receipt -->
        <div class="ct-receipt-doc">
          <div *ngIf="receiptLoading" class="ct-receipt-loading">
            <ion-spinner name="dots"></ion-spinner> Loading receipt…
          </div>
          <div *ngIf="receiptError" class="ct-receipt-loading">
            {{ receiptError }}
            <button class="ct-btn ct-btn--outline" (click)="loadReceiptInline()" style="margin-left:10px">Retry</button>
          </div>
          <iframe *ngIf="receiptPdfUrl && !receiptLoading" [src]="receiptPdfUrl" class="ct-receipt-frame" title="Receipt"></iframe>
        </div>

        <div class="ct-receipt-actions">
          <button class="ct-btn ct-btn--outline" (click)="downloadReceipt()">
            <ion-icon name="download-outline" style="margin-right:6px"></ion-icon> Download
          </button>
          <button class="ct-btn ct-btn--outline" (click)="router.navigate(['/payin-partner/transactions'])">
            <ion-icon name="list-outline" style="margin-right:6px"></ion-icon> View All Transactions
          </button>
          <button class="ct-btn ct-btn--primary" (click)="resetForm()">
            <ion-icon name="add-outline" style="margin-right:6px"></ion-icon> New Transaction
          </button>
        </div>
      </div>

      <!-- ─── STEP 3: Review & Confirm ──────────────────────────────── -->
      <div class="ct-card" *ngIf="currentStep === 3">
        <h2 class="ct-section-title">
          <ion-icon name="checkmark-done-outline"></ion-icon> Review &amp; Confirm
        </h2>

        <div class="ct-review-grid">
          <div class="ct-review-row">
            <span class="ct-review-label">Customer</span>
            <span class="ct-review-value">{{ selectedCustomer?.firstName }} {{ selectedCustomer?.lastName }}</span>
          </div>
          <div class="ct-review-row">
            <span class="ct-review-label">Customer ID</span>
            <span class="ct-review-value ct-mono">{{ selectedCustomer?.customerId }}</span>
          </div>
          <div class="ct-review-row ct-review-highlight">
            <span class="ct-review-label">You Collect</span>
            <span class="ct-review-value ct-review-big">{{ sendCurrency }} {{ sendAmount | number:'1.2-2' }}</span>
          </div>
          <div class="ct-review-row ct-review-highlight">
            <span class="ct-review-label">Beneficiary Receives</span>
            <span class="ct-review-value ct-review-big">{{ receiveCurrency }} {{ quote?.receiveAmount | number:'1.2-2' }}</span>
          </div>
          <div class="ct-review-row">
            <span class="ct-review-label">Exchange Rate</span>
            <span class="ct-review-value">1 {{ sendCurrency }} = {{ quote?.appliedRate | number:'1.4-4' }} {{ receiveCurrency }}</span>
          </div>
          <div class="ct-review-row">
            <span class="ct-review-label">Fee</span>
            <span class="ct-review-value">{{ sendCurrency }} {{ quote?.fee | number:'1.2-2' }}</span>
          </div>
          <div class="ct-review-row">
            <span class="ct-review-label">Total to Collect</span>
            <span class="ct-review-value"><strong>{{ sendCurrency }} {{ quote?.totalCost | number:'1.2-2' }}</strong></span>
          </div>
          <div class="ct-review-row">
            <span class="ct-review-label">Delivery Method</span>
            <span class="ct-review-value">{{ deliveryMethodLabel(selectedDeliveryMethod) }}</span>
          </div>
          <div class="ct-review-row">
            <span class="ct-review-label">Collection Type</span>
            <span class="ct-review-value">{{ collectionTypeLabel(selectedCollectionType) }}</span>
          </div>
          <div class="ct-review-row">
            <span class="ct-review-label">Beneficiary</span>
            <span class="ct-review-value" *ngIf="selectedBeneficiary">
              {{ selectedBeneficiary.name }}
              <span class="ct-review-sub">{{ selectedBeneficiary.bankName }} · ****{{ selectedBeneficiary.accountNumber?.slice(-4) }}</span>
            </span>
            <span class="ct-review-value ct-review-new" *ngIf="!selectedBeneficiary && newBen.name">
              {{ newBen.name }} (New)
              <span class="ct-review-sub">{{ newBen.bankName }} · {{ newBen.accountNumber }}</span>
            </span>
          </div>
        </div>

        <!-- Optional reference -->
        <div class="ct-field" style="margin-top:20px;">
          <label>External Reference ID <span class="ct-hint">(optional — for idempotency)</span></label>
          <input [(ngModel)]="externalReferenceId" placeholder="Your system reference" />
        </div>

        <div class="ct-error" *ngIf="stepError">{{ stepError }}</div>

        <div class="ct-nav-row">
          <button class="ct-btn ct-btn--outline" (click)="currentStep = 2; stepError = ''">Back</button>
          <button class="ct-btn ct-btn--primary ct-btn--submit" (click)="submit()" [disabled]="submitting">
            <ion-spinner *ngIf="submitting" name="crescent" style="width:16px;height:16px;margin-right:6px;"></ion-spinner>
            {{ submitting ? 'Submitting...' : 'Confirm &amp; Submit' }}
          </button>
        </div>
      </div>

    </div>
  `,
  styles: [`
    .ct-page { padding: 24px; max-width: 1100px; margin: 0 auto; width: 100%; box-sizing: border-box; }
    .ct-page *, .ct-page *::before, .ct-page *::after { box-sizing: border-box; }
    /* Stack the amount row + form grids on narrower widths so nothing overflows */
    @media (max-width: 720px) {
      .ct-amount-row { grid-template-columns: 1fr !important; }
      .ct-rate-arrow { transform: rotate(90deg); }
      .ct-form-grid { grid-template-columns: 1fr !important; }
    }
    .ct-header { display:flex; align-items:flex-start; gap:14px; margin-bottom:24px; }
    .ct-back-btn { background:none; border:none; cursor:pointer; color:#1B3571; font-size:1.4rem; padding:4px 0; }
    .ct-title { font-size:1.5rem; font-weight:700; margin:0 0 2px; color:#111827; }
    .ct-sub { color:#6b7280; margin:0; font-size:0.9rem; }

    /* Steps — full-width connector, dot on top, label below (matches partner portal design) */
    .ct-steps { display:flex; align-items:flex-start; gap:8px; margin:24px 0 28px; padding:0 8px; }
    .ct-step { flex:1; display:flex; flex-direction:column; align-items:center; gap:6px; position:relative; min-width:0; }
    .ct-step:not(:last-child)::after { content:''; position:absolute; top:17px; left:55%; right:-45%; height:2px; background:#e5e7eb; }
    .ct-step.done:not(:last-child)::after { background:#10b981; }
    .ct-step__dot { width:36px; height:36px; border-radius:50%; background:#e5e7eb; color:#6b7280; display:flex; align-items:center; justify-content:center; font-size:0.85rem; font-weight:700; flex-shrink:0; z-index:1; }
    .ct-step.active .ct-step__dot { background:#1B3571; color:#fff; }
    .ct-step.done .ct-step__dot { background:#10b981; color:#fff; }
    .ct-step__label { font-size:0.75rem; color:#6b7280; text-align:center; }
    .ct-step.active .ct-step__label { color:#1B3571; font-weight:600; }

    /* Card */
    .ct-card { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:24px; box-shadow:0 1px 4px rgba(0,0,0,.06); }
    .ct-section-title { display:flex; align-items:center; gap:8px; font-size:1.05rem; font-weight:700; color:#1B3571; margin:0 0 20px; padding-bottom:12px; border-bottom:1px solid #f3f4f6; }

    /* Selected customer banner */
    .ct-selected-customer { display:flex; align-items:center; gap:10px; background:#f0f4ff; padding:10px 14px; border-radius:8px; margin-bottom:20px; font-size:0.875rem; }
    .ct-change-btn { margin-left:auto; background:none; border:1px solid #1B3571; color:#1B3571; padding:3px 10px; border-radius:4px; cursor:pointer; font-size:0.8rem; }

    /* Search */
    .ct-search-box { display:flex; align-items:center; gap:10px; border:1px solid #d1d5db; border-radius:8px; padding:10px 14px; margin-bottom:16px; }
    .ct-search-input { border:none; outline:none; flex:1; font-size:0.9rem; }

    /* Lists */
    .ct-list { display:flex; flex-direction:column; gap:8px; max-height:360px; overflow-y:auto; margin-bottom:16px; }
    .ct-list-item { display:flex; align-items:center; gap:12px; padding:12px 14px; border:1px solid #e5e7eb; border-radius:8px; cursor:pointer; transition:all .15s; }
    .ct-list-item:hover { border-color:#1B3571; background:#f8faff; }
    .ct-avatar, .ct-ben-avatar { width:36px; height:36px; border-radius:50%; background:#1B3571; color:#fff; display:flex; align-items:center; justify-content:center; font-size:0.85rem; font-weight:700; flex-shrink:0; }
    .ct-ben-avatar { background:#00B894; }
    .ct-item-info { flex:1; }
    .ct-item-name { font-weight:600; font-size:0.9rem; color:#111827; }
    .ct-item-sub { font-size:0.78rem; color:#6b7280; margin-top:2px; }
    .ct-empty { text-align:center; color:#9ca3af; padding:24px; font-size:0.875rem; }
    .ct-search-hint { display:flex; align-items:center; gap:8px; color:#9ca3af; font-size:0.85rem; padding:20px; justify-content:center; }

    .ct-ben-item--selected { border-color:#00B894 !important; background:#f0fdf8 !important; }

    /* Receive country cards */
    .ct-corridor-label { display:block; font-size:0.875rem; font-weight:600; color:#374151; margin:4px 0 10px; }
    .ct-req { color:#C0203E; }
    .ct-corridor-grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(160px, 1fr)); gap:10px; margin-bottom:20px; }
    .ct-corridor-card { display:flex; align-items:center; gap:10px; padding:10px 12px; border:2px solid #e5e7eb; background:#fff; border-radius:8px; cursor:pointer; transition:all .15s; text-align:left; }
    .ct-corridor-card:hover { border-color:#9fb3d9; }
    .ct-corridor-card--active { border-color:#1B3571; background:#f0f4ff; }
    .ct-corridor-flag { width:32px; height:24px; object-fit:cover; border-radius:2px; flex-shrink:0; border:1px solid #e5e7eb; }
    .ct-corridor-country { font-weight:700; font-size:0.9rem; color:#111827; }
    .ct-corridor-ccy { color:#6b7280; font-size:0.75rem; }

    /* Amount row */
    .ct-amount-row { display:grid; grid-template-columns:1fr auto 1fr; gap:16px; align-items:center; margin-bottom:20px; }
    .ct-amount-group { display:flex; flex-direction:column; gap:6px; }
    .ct-amount-group label { font-size:0.8rem; font-weight:600; color:#374151; text-transform:uppercase; letter-spacing:.04em; }
    .ct-amount-input-wrap { display:flex; border:1px solid #d1d5db; border-radius:8px; overflow:hidden; }
    .ct-amount-input { flex:1; padding:12px; font-size:1.1rem; font-weight:600; border:none; outline:none; min-width:0; }
    .ct-amount-input--readonly { background:#f9fafb; color:#374151; }
    .ct-flag { width:24px; height:18px; object-fit:cover; align-self:center; margin-left:10px; border:1px solid #e5e7eb; border-radius:3px; flex:none; }
    .ct-currency-select { padding:0 10px; background:#f9fafb; border:none; border-left:1px solid #d1d5db; font-size:0.85rem; font-weight:600; outline:none; cursor:pointer; min-width:70px; }
    .ct-rate-arrow { display:flex; flex-direction:column; align-items:center; gap:4px; }
    .ct-rate-box { text-align:center; }
    .ct-rate-value { font-size:0.75rem; font-weight:700; color:#1B3571; white-space:nowrap; }
    .ct-fee-value { font-size:0.7rem; color:#6b7280; }
    .ct-total-row { display:flex; justify-content:space-between; align-items:center; background:#fef9f0; border:1px solid #fde68a; border-radius:8px; padding:10px 16px; margin-bottom:20px; font-size:0.9rem; }

    /* Pills */
    .ct-field-group { margin-bottom:20px; }
    .ct-label { display:block; font-size:0.875rem; font-weight:600; color:#374151; margin-bottom:10px; }
    .ct-hint { font-weight:400; color:#9ca3af; font-size:0.8rem; }
    .ct-pill-group { display:flex; flex-wrap:wrap; gap:8px; }
    .ct-pill { display:flex; align-items:center; gap:6px; padding:8px 16px; border:1px solid #d1d5db; border-radius:20px; background:#fff; cursor:pointer; font-size:0.875rem; transition:all .15s; }
    .ct-pill:hover { border-color:#1B3571; color:#1B3571; }
    .ct-pill--active { border-color:#1B3571; background:#1B3571; color:#fff; }

    /* Loading */
    .ct-loading { display:flex; flex-direction:column; gap:10px; padding:8px 0; }
    .ct-skeleton { height:56px; background:linear-gradient(90deg,#f3f4f6 25%,#e5e7eb 50%,#f3f4f6 75%); border-radius:8px; animation:shimmer 1.2s infinite; background-size:200%; }
    @keyframes shimmer { 0%{background-position:200%} 100%{background-position:-200%} }

    /* Add beneficiary */
    .ct-add-ben-btn { display:flex; align-items:center; gap:8px; background:none; border:2px dashed #d1d5db; border-radius:8px; padding:12px 16px; width:100%; cursor:pointer; color:#6b7280; font-size:0.875rem; justify-content:center; transition:all .15s; }
    .ct-add-ben-btn:hover { border-color:#1B3571; color:#1B3571; }
    .ct-add-ben-form { border:1px solid #e5e7eb; border-radius:8px; padding:16px; margin-bottom:16px; }
    .ct-add-ben-title { font-size:0.9rem; font-weight:600; color:#1B3571; margin:0 0 14px; }
    .ct-form-grid { display:grid; grid-template-columns:1fr 1fr; gap:12px; margin-bottom:14px; }
    .ct-field { display:flex; flex-direction:column; gap:4px; margin-bottom:14px; }
    .ct-field label { font-size:0.8rem; font-weight:500; color:#374151; }
    .ct-field input, .ct-field select { padding:9px 12px; border:1px solid #d1d5db; border-radius:6px; font-size:0.875rem; outline:none; background:#fff; }
    .ct-field input:focus, .ct-field select:focus { border-color:#1B3571; }
    .ct-row { display:flex; gap:12px; }
    .ct-row > .ct-field--half { flex:1 1 0; min-width:0; }
    @media (max-width: 480px) { .ct-row { flex-direction:column; gap:0; } }
    .ct-subsection { font-size:0.82rem; font-weight:700; color:#1B3571; text-transform:uppercase; letter-spacing:0.05em; margin:18px 0 10px; padding-top:8px; border-top:1px solid #f3f4f6; }
    .ct-section-sub { font-size:0.85rem; color:#6b7280; margin:0 0 16px; }
    .ct-hint { font-size:0.75rem; color:#6b7280; }
    .ct-error { color:#dc2626; font-size:0.85rem; margin:10px 0; }
    .ct-actions { display:flex; gap:10px; justify-content:flex-end; margin-top:16px; flex-wrap:wrap; }
    .ct-add-ben-actions { display:flex; gap:10px; }
    .ct-req { color:#ef4444; }

    /* Existing-KYC summary */
    .ct-kyc-summary { border:1px solid #e5e7eb; border-radius:10px; overflow:hidden; margin:12px 0; }
    .ct-kyc-row { display:flex; justify-content:space-between; align-items:center; padding:10px 16px; border-bottom:1px solid #f3f4f6; font-size:0.9rem; gap:12px; }
    .ct-kyc-row:last-child { border-bottom:none; }
    .ct-kyc-row span { color:#6b7280; }
    .ct-kyc-row strong, .ct-kyc-row a { color:#111827; font-weight:500; }
    .ct-kyc-row a { color:#1B3A6B; text-decoration:underline; }
    .ct-input--inline { max-width:200px; padding:6px 10px; font-size:0.85rem; }

    .ct-kyc-previews { display:grid; grid-template-columns:repeat(auto-fit, minmax(220px, 1fr)); gap:12px; margin:12px 0 4px; }
    .ct-kyc-preview { border:1px solid #e5e7eb; border-radius:10px; padding:10px; background:#f8fafc; }
    .ct-kyc-preview-label { font-size:0.78rem; color:#6b7280; margin-bottom:8px; font-weight:600; text-transform:uppercase; letter-spacing:0.04em; }
    .ct-kyc-preview img { width:100%; max-height:240px; object-fit:contain; border-radius:6px; background:#fff; }
    .ct-kyc-preview-loading { padding:40px 0; text-align:center; color:#9ca3af; font-size:0.85rem; }

    /* Review */
    .ct-review-grid { display:flex; flex-direction:column; border:1px solid #e5e7eb; border-radius:8px; overflow:hidden; margin-bottom:4px; }
    .ct-review-row { display:flex; justify-content:space-between; align-items:flex-start; padding:11px 16px; border-bottom:1px solid #f3f4f6; }
    .ct-review-row:last-child { border-bottom:none; }
    .ct-review-highlight { background:#f8faff; }
    .ct-review-label { font-size:0.82rem; color:#6b7280; }
    .ct-review-value { font-size:0.9rem; color:#111827; text-align:right; }
    .ct-review-sub { display:block; font-size:0.78rem; color:#9ca3af; margin-top:2px; }
    .ct-review-big { font-size:1.1rem; font-weight:700; color:#1B3571; }
    .ct-review-new { color:#00B894; }
    .ct-mono { font-family:monospace; font-size:0.8rem; }

    /* Nav & buttons */
    .ct-nav-row { display:flex; justify-content:flex-end; gap:12px; margin-top:24px; padding-top:16px; border-top:1px solid #f3f4f6; }
    .ct-btn { padding:10px 24px; border-radius:8px; font-size:0.9rem; font-weight:600; cursor:pointer; border:none; transition:all .15s; }
    .ct-btn--outline { background:#fff; border:1px solid #d1d5db; color:#374151; }
    .ct-btn--outline:hover { border-color:#1B3571; color:#1B3571; }
    .ct-btn--primary { background:#1B3571; color:#fff; }
    .ct-btn--primary:hover { background:#122550; }
    .ct-btn--success { background:#00B894; color:#fff; }
    .ct-btn--success:hover { background:#00a381; }
    .ct-btn--submit { display:flex; align-items:center; }
    .ct-btn:disabled { opacity:.6; cursor:not-allowed; }
    .ct-error { color:#ef4444; font-size:0.875rem; padding:10px 14px; background:#fef2f2; border:1px solid #fecaca; border-radius:6px; margin-top:12px; }

    /* Receipt */
    .ct-receipt { text-align:center; }
    .ct-receipt-icon { font-size:64px; margin-bottom:12px; line-height:1; }
    .ct-receipt-title { font-size:1.4rem; font-weight:700; color:#065f46; margin:0 0 6px; }
    .ct-receipt-sub { color:#6b7280; margin:0 0 4px; font-size:0.9rem; }
    .ct-receipt-doc { margin:16px 0; }
    .ct-receipt-frame { width:100%; height:780px; border:1px solid #e5e7eb; border-radius:8px; background:#f9fafb; }
    .ct-receipt-loading { display:flex; align-items:center; justify-content:center; gap:8px; padding:40px; color:#6b7280; font-size:0.9rem; }
    .ct-receipt-actions { display:flex; justify-content:center; gap:12px; margin-top:8px; flex-wrap:wrap; }
    .ct-status-chip { display:inline-block; padding:3px 10px; border-radius:12px; font-size:0.75rem; font-weight:700; text-transform:uppercase; }
    .ct-status-chip--pending { background:#fef3c7; color:#92400e; }
    .ct-status-chip--processing { background:#dbeafe; color:#1e40af; }
    .ct-status-chip--initiated { background:#e5e7eb; color:#374151; }
    .ct-status-chip--success { background:#d1fae5; color:#065f46; }
    .ct-status-chip--failed { background:#fee2e2; color:#991b1b; }

    @media (max-width:600px) {
      .ct-amount-row { grid-template-columns:1fr; }
      .ct-form-grid { grid-template-columns:1fr; }
      .ct-rate-arrow { display:none; }
    }
  `]
})
export class CreateTransactionPage implements OnInit, OnDestroy {
  steps = ['Customer', 'Amount & Rate', 'Beneficiary', 'Review', 'Receipt'];
  currentStep = 0;

  // Customer
  customers: any[] = [];
  filteredCustomers: any[] = [];
  customerSearch = '';
  loadingCustomers = true;
  selectedCustomer: any = null;

  // Map a currency code -> ISO-3166 alpha-2 country code for flag images.
  private currencyCountry: { [k: string]: string } = {
    GBP: 'gb', USD: 'us', EUR: 'eu', AED: 'ae', AUD: 'au', CAD: 'ca', SGD: 'sg',
    QAR: 'qa', SDG: 'sd', SAR: 'sa', EGP: 'eg', KWD: 'kw', BHD: 'bh', OMR: 'om',
    JOD: 'jo', TRY: 'tr', INR: 'in', PKR: 'pk', BDT: 'bd', NGN: 'ng', KES: 'ke',
    GHS: 'gh', ETB: 'et', UGX: 'ug', TZS: 'tz', ZAR: 'za'
  };

  /** flagcdn URL for a currency; falls back to a globe-coloured placeholder. */
  flagFor(currency: string, country?: string): string {
    const cc = (country && country.length === 2 ? country : this.currencyCountry[currency] || '').toLowerCase();
    return cc ? `https://flagcdn.com/24x18/${cc}.png` : '';
  }

  // Amount / FX
  sendAmount = 100;
  sendCurrency = 'GBP';
  receiveCurrency = '';
  receiveCountry = '';
  availableReceiveCurrencies: { currency: string; country: string }[] = [];
  corridors: any[] = [];
  quote: any = null;
  loadingQuote = false;
  selectedDeliveryMethod = 'BANK_TRANSFER';
  selectedCollectionType = 'CASH_COLLECTION';

  // All methods we MAY support; the active subset for the chosen receive currency
  // gets populated into deliveryMethods after each corridor change (mirrors the
  // customer Add Recipient logic — uses the same payout_types API).
  private allDeliveryMethods = [
    { label: 'Bank Transfer', value: 'BANK_TRANSFER',  icon: 'business-outline',         payoutTypes: ['BANK_TRANSFER', 'BANK_DEPOSIT'] },
    { label: 'Mobile Money',  value: 'MOBILE_MONEY',   icon: 'phone-portrait-outline',   payoutTypes: ['MOBILE_MONEY', 'MOBILE_WALLET'] },
    { label: 'Cash Pickup',   value: 'CASH_PICKUP',    icon: 'cash-outline',             payoutTypes: ['CASH_COLLECTION', 'CASH_PICKUP'] }
  ];
  deliveryMethods: { label: string; value: string; icon: string }[] = [];

  collectionTypes = [
    { label: 'Cash', value: 'CASH_COLLECTION', icon: 'cash-outline' },
    { label: 'Credit Card', value: 'CREDIT_CARD', icon: 'card-outline' },
    { label: 'Debit Card', value: 'DEBIT_CARD', icon: 'card-outline' },
    { label: 'Internet Banking', value: 'INTERNET_BANKING', icon: 'globe-outline' }
  ];

  // Beneficiary
  beneficiaries: any[] = [];
  filteredBeneficiaries: any[] = [];
  beneficiarySearch = '';
  loadingBeneficiaries = false;
  selectedBeneficiary: any = null;
  showAddBeneficiary = false;
  newBen: any = {
    name: '', address: '', mobileNumber: '',
    // Bank fields
    bankName: '', sortCode: '', branchState: 'Any Branch', branchCity: 'Any Branch',
    accountNumber: '', iban: '', ifscCode: '', swiftBic: '',
    // Mobile wallet
    mobileProvider: '',
    // Cash collection
    collectionPointName: '', collectionPointCode: '', collectionPointAddress: '', collectionPointCity: ''
  };

  /** Bank list for the chosen receive country (drives the Bank Name dropdown). */
  bankNames: string[] = [];
  loadingBanks = false;

  /** Load the bank-name list for the current receive country (same API as the customer frontend). */
  loadBankNames(): void {
    const country = (this.receiveCountry || '').toString();
    if (!country) { this.bankNames = []; return; }
    this.loadingBanks = true;
    this.configService.getBankNames(country).subscribe({
      next: (res: any) => { this.bankNames = res?.data || res || []; this.loadingBanks = false; },
      error: () => { this.bankNames = []; this.loadingBanks = false; }
    });
  }

  /** Sudan uses a minimal beneficiary form (full name, mobile, bank name, account number). */
  isSudan(): boolean {
    const c = (this.receiveCountry || '').toString().toUpperCase();
    return c === 'SD' || c === 'SDN' || c.includes('SUDAN');
  }
  /** Sudan minimal form applies to bank-deposit beneficiaries. */
  isSudanBank(): boolean {
    return this.isSudan() && (this.selectedDeliveryMethod === 'BANK_DEPOSIT' || this.selectedDeliveryMethod === 'BANK_TRANSFER');
  }

  /** Same set of countries USI handles — show IBAN field for these. */
  isUsiIbanCountry(): boolean {
    const c = (this.receiveCountry || '').toString().toUpperCase();
    return ['TR','TUR','SA','SAU','AE','ARE','QA','QAT'].includes(c);
  }
  isUsiCountry(): boolean {
    const c = (this.receiveCountry || '').toString().toUpperCase();
    return ['UG','UGA','TR','TUR','EG','EGY','QA','QAT','SA','SAU','AE','ARE'].includes(c);
  }
  isNoSwiftCountry(): boolean {
    // Uganda + Egypt — USI doesn't require SWIFT for these
    const c = (this.receiveCountry || '').toString().toUpperCase();
    return ['UG','UGA','EG','EGY'].includes(c);
  }

  // Submit
  externalReferenceId = '';
  submitting = false;
  stepError = '';

  // Receipt (step 4)
  receiptTransactionId = '';
  receiptStatus = '';
  downloadingReceipt = false;
  receiptPdfUrl: SafeResourceUrl | null = null;
  receiptObjectUrl = '';
  receiptLoading = false;
  receiptError = '';
  receiptCustomerSource = '';

  private amountChange$ = new Subject<void>();
  private destroy$ = new Subject<void>();

  constructor(
    private partnerService: PartnerService,
    private fxService: FxService,
    private usiMoneyService: UsiMoneyService,
    private configService: ConfigService,
    private sanitizer: DomSanitizer,
    public router: Router
  ) {}

  /**
   * Loads the active payout types for the current receive currency and filters the
   * delivery-method pills down to what USI Money / corridors actually support.
   * If the previously-selected method is no longer in the list, falls back to the first.
   */
  refreshAvailableDeliveryMethods(): void {
    if (!this.receiveCurrency) {
      this.deliveryMethods = [...this.allDeliveryMethods];
      return;
    }
    this.configService.getPayoutTypesByCurrency(this.receiveCurrency).subscribe({
      next: (res: any) => {
        const types = ((res?.data || res || []) as any[])
          .filter(t => t.isActive)
          .map(t => (t.payoutType || '').toUpperCase());
        this.deliveryMethods = this.allDeliveryMethods.filter(m =>
          m.payoutTypes.some(pt => types.includes(pt.toUpperCase())));
        if (this.deliveryMethods.length === 0) {
          this.deliveryMethods = [...this.allDeliveryMethods];
        }
        if (!this.deliveryMethods.find(m => m.value === this.selectedDeliveryMethod)) {
          this.selectedDeliveryMethod = this.deliveryMethods[0].value;
        }
        this.loadUsiCashPointsIfNeeded();
      },
      error: () => { this.deliveryMethods = [...this.allDeliveryMethods]; }
    });
  }

  ngOnInit(): void {
    this.loadCustomers();
    this.loadCorridors();

    this.amountChange$.pipe(debounceTime(600), takeUntil(this.destroy$))
      .subscribe(() => this.fetchQuote());
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.revokeBlobUrls();
  }

  // ── Step 0: Customer ──────────────────────────────────────────

  loadCustomers(): void {
    this.loadingCustomers = true;
    this.partnerService.getPayinCustomers().subscribe({
      next: (res: any) => {
        const all = Array.isArray(res) ? res : (res?.data || []);
        const filtered = all.filter((c: any) => !c.hasExpiredDocuments);
        const fullName = (c: any) => ((c.firstName || '') + ' ' + (c.lastName || '')).trim().toLowerCase();
        filtered.sort((a: any, b: any) => {
          const an = fullName(a), bn = fullName(b);
          if (!an && !bn) return 0;
          if (!an) return 1;
          if (!bn) return -1;
          return an.localeCompare(bn);
        });
        this.customers = filtered;
        this.filteredCustomers = [...this.customers];
        this.loadingCustomers = false;
      },
      error: () => { this.customers = []; this.filteredCustomers = []; this.loadingCustomers = false; }
    });
  }

  filterCustomers(): void {
    const t = this.customerSearch.trim().toLowerCase();
    if (t.length < 2) { this.filteredCustomers = []; return; }
    this.filteredCustomers = this.customers.filter(c =>
      ((c.firstName || '') + ' ' + (c.lastName || '')).toLowerCase().includes(t) ||
      (c.email || '').toLowerCase().includes(t) ||
      (c.phone || '').includes(t)
    );
  }

  avatarInitials(c: any): string {
    const f = (c.firstName || '?')[0] || '?';
    const l = (c.lastName || '?')[0] || '?';
    return (f + l).toUpperCase();
  }

  // KYC gate state
  kycStep = false;
  kycDob: string = '';
  kycIdType: string = '';
  kycDocNumber: string = '';
  kycIssueDate: string = '';
  kycExpiryDate: string = '';
  kycIdFile: File | null = null;
  kycAddrType: string = '';
  kycAddrFile: File | null = null;
  kycSubmitting = false;
  kycError = '';
  // Pre-existing KYC on file → enables one-click Verify (no re-upload).
  kycExistingLoading = false;
  kycHasExisting = false;
  kycExistingIdFileUrl: string | null = null;
  kycExistingAddrFileUrl: string | null = null;
  kycExistingIdBlobUrl: string | null = null;
  kycExistingAddrBlobUrl: string | null = null;
  kycEditMode = false;
  private blobUrls: string[] = [];

  selectCustomer(c: any): void {
    this.selectedCustomer = c;
    this.stepError = '';
    if (!c?.isVerified) {
      this.kycStep = true;
      this.resetKycForm();
      this.currentStep = 1;
      this.loadExistingKyc(c.customerId);
      return;
    }
    this.kycStep = false;
    this.currentStep = 1;
    if (!this.quote && this.sendAmount > 0 && this.receiveCurrency) {
      this.fetchQuote();
    }
  }

  private resetKycForm(): void {
    this.kycDob = '';
    this.kycIdType = '';
    this.kycDocNumber = '';
    this.kycIssueDate = '';
    this.kycExpiryDate = '';
    this.kycIdFile = null;
    this.kycAddrType = '';
    this.kycAddrFile = null;
    this.kycError = '';
    this.kycHasExisting = false;
    this.kycExistingIdFileUrl = null;
    this.kycExistingAddrFileUrl = null;
    this.revokeBlobUrls();
    this.kycExistingIdBlobUrl = null;
    this.kycExistingAddrBlobUrl = null;
    this.kycEditMode = false;
  }

  private revokeBlobUrls(): void {
    for (const u of this.blobUrls) {
      try { URL.revokeObjectURL(u); } catch {}
    }
    this.blobUrls = [];
  }

  private fetchExistingFileAsBlob(url: string, isIdProof: boolean): void {
    this.partnerService.fetchBlob(url).subscribe({
      next: (blob) => {
        const obj = URL.createObjectURL(blob);
        this.blobUrls.push(obj);
        if (isIdProof) this.kycExistingIdBlobUrl = obj;
        else this.kycExistingAddrBlobUrl = obj;
      },
      error: () => {}
    });
  }

  private loadExistingKyc(customerId: string): void {
    this.kycExistingLoading = true;
    this.partnerService.getExistingKyc(customerId).subscribe({
      next: (res: any) => {
        const d = res?.data || res || {};
        const hasId = !!d.hasIdentity;
        const hasAddr = !!d.hasAddress;
        // Prefer user.date_of_birth, fall back to payin_customer.dob from the selected customer row.
        if (d.dob) this.kycDob = d.dob;
        else if (this.selectedCustomer?.dob) this.kycDob = this.selectedCustomer.dob;
        if (hasId) {
          this.kycIdType = d.idType || '';
          this.kycDocNumber = d.idDocumentNumber || '';
          this.kycIssueDate = d.idIssueDate || '';
          this.kycExpiryDate = d.idExpiryDate || '';
          if (d.idDocId) {
            this.kycExistingIdFileUrl = `${environment.apiUrl}/payin/customer/kyc-document/${d.idDocId}/file`;
            this.fetchExistingFileAsBlob(this.kycExistingIdFileUrl, true);
          }
        }
        if (hasAddr) {
          this.kycAddrType = d.addressType || '';
          if (d.addressDocId) {
            this.kycExistingAddrFileUrl = `${environment.apiUrl}/payin/customer/kyc-document/${d.addressDocId}/file`;
            this.fetchExistingFileAsBlob(this.kycExistingAddrFileUrl, false);
          }
        }
        // Show the summary whenever BOTH proofs exist — partner can fill any missing fields inline
        // and still verify in one click; we no longer block on DOB / expiry.
        this.kycHasExisting = hasId && hasAddr;
        this.kycExistingLoading = false;
      },
      error: () => { this.kycExistingLoading = false; }
    });
  }

  isImageUrl(url: string | null): boolean {
    if (!url) return false;
    // The URL is an API endpoint; the file extension lives on the original file path.
    // We assume image; if the server returns PDF, the <img> will fail silently and the
    // user can still click the "Open in new tab" link beneath each preview.
    return true;
  }

  verifyExistingCustomer(): void {
    if (!this.selectedCustomer?.customerId) return;
    this.kycSubmitting = true;
    this.kycError = '';
    const customerId = this.selectedCustomer.customerId;

    const finalize = () => {
      this.partnerService.verifyExistingCustomer(customerId).subscribe({
        next: () => {
          if (this.selectedCustomer) this.selectedCustomer.isVerified = true;
          this.kycStep = false;
          this.kycSubmitting = false;
          this.currentStep = 1;
          if (!this.quote && this.sendAmount > 0 && this.receiveCurrency) {
            this.fetchQuote();
          }
        },
        error: (err) => {
          this.kycSubmitting = false;
          this.kycError = 'Verification failed: ' + (err?.error?.message || err?.message || 'Unknown');
        }
      });
    };

    // Persist DOB if the partner filled it in inline before verifying.
    if (this.kycDob) {
      this.partnerService.updatePayinCustomerProfile(customerId, this.kycDob, true).subscribe({
        next: () => finalize(),
        error: () => finalize()  // verify endpoint is still safe to call even if profile save failed
      });
    } else {
      finalize();
    }
  }

  enterEditMode(): void { this.kycEditMode = true; }

  onKycIdFile(ev: any): void {
    const f = ev.target.files?.[0];
    if (f) this.kycIdFile = f;
  }
  onKycAddrFile(ev: any): void {
    const f = ev.target.files?.[0];
    if (f) this.kycAddrFile = f;
  }
  cancelKyc(): void {
    this.kycStep = false;
    this.selectedCustomer = null;
    this.currentStep = 0;
  }

  submitKyc(): void {
    if (!this.selectedCustomer?.customerId) {
      this.kycError = 'No customer selected';
      return;
    }
    if (!this.kycDob)         { this.kycError = 'Date of birth is required'; return; }
    if (!this.kycIdType)      { this.kycError = 'ID type is required'; return; }
    if (!this.kycDocNumber)   { this.kycError = 'ID document number is required'; return; }
    if (!this.kycIssueDate)   { this.kycError = 'ID issue date is required'; return; }
    if (!this.kycExpiryDate)  { this.kycError = 'ID expiry date is required'; return; }
    if (!this.kycIdFile)      { this.kycError = 'ID proof file is required'; return; }
    if (!this.kycAddrType)    { this.kycError = 'Address proof type is required'; return; }
    if (!this.kycAddrFile)    { this.kycError = 'Address proof file is required'; return; }

    this.kycSubmitting = true;
    this.kycError = '';
    const customerId = this.selectedCustomer.customerId;

    // 1. Update DOB on the customer profile
    this.partnerService.updatePayinCustomerProfile(customerId, this.kycDob, true).subscribe({
      next: () => {
        // 2. Upload ID proof (docCategory carries the specific ID type — PASSPORT / DRIVING_LICENCE etc.)
        this.partnerService.uploadPayinCustomerDocument(
          customerId, this.kycIdFile!, this.kycIdType, 'FRONT',
          this.kycDocNumber, this.kycIssueDate, this.kycExpiryDate
        ).subscribe({
          next: () => {
            // 3. Upload address proof (docCategory carries the specific address-proof type)
            this.partnerService.uploadPayinCustomerDocument(
              customerId, this.kycAddrFile!, this.kycAddrType, 'FRONT'
            ).subscribe({
              next: () => {
                // Done — mark verified locally and continue to step 1
                if (this.selectedCustomer) this.selectedCustomer.isVerified = true;
                this.kycStep = false;
                this.kycSubmitting = false;
                this.currentStep = 1;
                if (!this.quote && this.sendAmount > 0 && this.receiveCurrency) {
                  this.fetchQuote();
                }
              },
              error: (err) => {
                this.kycSubmitting = false;
                this.kycError = 'Address proof upload failed: ' + (err?.error?.message || err?.message || 'Unknown');
              }
            });
          },
          error: (err) => {
            this.kycSubmitting = false;
            this.kycError = 'ID proof upload failed: ' + (err?.error?.message || err?.message || 'Unknown');
          }
        });
      },
      error: (err) => {
        this.kycSubmitting = false;
        this.kycError = 'Profile update failed: ' + (err?.error?.message || err?.message || 'Unknown');
      }
    });
  }

  // ── Step 1: Amount & Rate ─────────────────────────────────────

  loadCorridors(): void {
    this.fxService.getCorridors().subscribe({
      next: (corridors: any[]) => {
        this.corridors = corridors.filter((c: any) => c.isActive);
        this.buildReceiveCurrencies();
      },
      error: () => {}
    });
  }

  buildReceiveCurrencies(): void {
    const seen = new Set<string>();
    this.availableReceiveCurrencies = this.corridors
      .filter((c: any) => c.sendCurrency === this.sendCurrency)
      .filter((c: any) => { if (seen.has(c.receiveCurrency)) return false; seen.add(c.receiveCurrency); return true; })
      .map((c: any) => ({ currency: c.receiveCurrency, country: c.receiveCountry }));

    if (!this.receiveCurrency && this.availableReceiveCurrencies.length > 0) {
      this.receiveCurrency = this.availableReceiveCurrencies[0].currency;
    }
    // Track the receive country corresponding to the chosen currency
    const match = this.availableReceiveCurrencies.find(c => c.currency === this.receiveCurrency);
    this.receiveCountry = match ? match.country : '';
    if (this.sendAmount > 0 && this.receiveCurrency) this.fetchQuote();
    this.refreshAvailableDeliveryMethods();
  }

  onCorridorChange(): void {
    // keep receiveCountry in sync with the selected receive currency (drives the flag)
    const sel = this.availableReceiveCurrencies.find(c => c.currency === this.receiveCurrency);
    if (sel) this.receiveCountry = sel.country;
    this.buildReceiveCurrencies();
    this.refreshAvailableDeliveryMethods();
  }

  /** User taps a receive-country flag card → set corridor + requote. */
  selectReceiveCountry(c: { currency: string; country: string }): void {
    this.receiveCurrency = c.currency;
    this.receiveCountry = c.country;
    this.buildReceiveCurrencies();
    this.refreshAvailableDeliveryMethods();
    this.loadBankNames();
    if (this.sendAmount > 0) this.fetchQuote();
  }

  onSendAmountChange(): void {
    this.amountChange$.next();
  }

  fetchQuote(): void {
    if (!this.sendAmount || this.sendAmount <= 0 || !this.sendCurrency || !this.receiveCurrency) return;

    const corridor = this.corridors.find((c: any) =>
      c.sendCurrency === this.sendCurrency && c.receiveCurrency === this.receiveCurrency && c.isActive
    );
    if (!corridor) return;

    const deliveryMap: Record<string, string> = {
      'BANK_TRANSFER': 'BANK_DEPOSIT', 'MOBILE_MONEY': 'MOBILE_WALLET', 'CASH_PICKUP': 'CASH_PICKUP'
    };

    this.loadingQuote = true;
    this.fxService.getQuote({
      sendCurrency: this.sendCurrency,
      receiveCurrency: this.receiveCurrency,
      sendAmount: this.sendAmount,
      deliveryMethod: deliveryMap[this.selectedDeliveryMethod] || 'BANK_DEPOSIT',
      corridorId: corridor.id
    }).subscribe({
      next: (q: any) => { this.quote = q; this.loadingQuote = false; },
      error: () => { this.quote = null; this.loadingQuote = false; }
    });
  }

  goToStep2(): void {
    this.stepError = '';
    if (!this.sendAmount || this.sendAmount <= 0) { this.stepError = 'Enter a valid amount'; return; }
    if (!this.receiveCurrency) { this.stepError = 'Select a destination currency'; return; }
    if (!this.selectedDeliveryMethod) { this.stepError = 'Select a delivery method'; return; }
    if (!this.selectedCollectionType) { this.stepError = 'Select a collection type'; return; }
    this.currentStep = 2;
    this.loadBeneficiaries();
    this.loadBankNames();
  }

  // ── Step 2: Beneficiary ───────────────────────────────────────

  loadBeneficiaries(): void {
    if (!this.selectedCustomer) return;
    this.loadingBeneficiaries = true;
    this.partnerService.getPayinBeneficiariesForCustomer(this.selectedCustomer.customerId).subscribe({
      next: (res: any) => {
        this.beneficiaries = Array.isArray(res) ? res : (res?.data || []);
        this.filterBeneficiaries();
        this.loadingBeneficiaries = false;
      },
      error: () => { this.beneficiaries = []; this.filteredBeneficiaries = []; this.loadingBeneficiaries = false; }
    });
  }

  /** Normalise a delivery method to a coarse group so BANK_TRANSFER/BANK_DEPOSIT etc. match. */
  private deliveryGroup(v: string): string {
    const x = (v || '').toUpperCase();
    if (x === 'BANK_TRANSFER' || x === 'BANK_DEPOSIT') return 'BANK';
    if (x === 'MOBILE_MONEY' || x === 'MOBILE_WALLET') return 'MOBILE';
    if (x === 'CASH_PICKUP' || x === 'CASH_COLLECTION') return 'CASH';
    return x;
  }

  filterBeneficiaries(): void {
    const t = this.beneficiarySearch.toLowerCase();
    const sel = this.deliveryGroup(this.selectedDeliveryMethod);
    // Only show recipients whose delivery method matches the selected one (bank / mobile / cash).
    let list = this.beneficiaries.filter(b => this.deliveryGroup(b.deliveryMethod) === sel);
    if (t) {
      list = list.filter(b => b.name?.toLowerCase().includes(t) || b.bankName?.toLowerCase().includes(t));
    }
    this.filteredBeneficiaries = list;
  }

  selectBeneficiary(b: any): void {
    this.selectedBeneficiary = b;
    this.showAddBeneficiary = false;
    this.newBen = { name: '', bankName: '', accountNumber: '', ifscCode: '' };
  }

  useNewBeneficiary(): void {
    if (!this.newBen.name.trim()) {
      this.stepError = 'Beneficiary name is required';
      return;
    }
    const isBank   = this.selectedDeliveryMethod === 'BANK_DEPOSIT'  || this.selectedDeliveryMethod === 'BANK_TRANSFER';
    const isMobile = this.selectedDeliveryMethod === 'MOBILE_WALLET' || this.selectedDeliveryMethod === 'MOBILE_MONEY';
    if (isBank) {
      if (!this.newBen.bankName.trim() || !this.newBen.accountNumber.trim()) {
        this.stepError = 'Bank name and account number are required';
        return;
      }
      if (this.isUsiIbanCountry() && !this.newBen.iban?.trim()) {
        this.stepError = 'IBAN is required for this country';
        return;
      }
    } else if (isMobile) {
      if (!this.newBen.mobileProvider || !this.newBen.accountNumber.trim()) {
        this.stepError = 'Mobile network and wallet number are required';
        return;
      }
    } else if (this.selectedDeliveryMethod === 'CASH_PICKUP') {
      if (!this.newBen.collectionPointName?.trim() || !this.newBen.collectionPointCode?.trim()) {
        this.stepError = 'Collection point name and code are required';
        return;
      }
    }
    this.selectedBeneficiary = null;
    this.showAddBeneficiary = false;
    this.stepError = '';
  }

  /** Auto-fill cash-collection fields from a USI getCollectionPoints entry. */
  applyUsiCashPoint(code: string): void {
    const pt = this.usiCollectionPoints.find(p => p.code === code);
    if (!pt) return;
    this.newBen.collectionPointName    = pt.name;
    this.newBen.collectionPointCode    = pt.code;
    this.newBen.collectionPointAddress = pt.address;
    this.newBen.collectionPointCity    = pt.city;
  }

  /** Refresh USI collection-point list when delivery becomes CASH_PICKUP on a USI corridor. */
  usiCollectionPoints: any[] = [];
  loadUsiCashPointsIfNeeded(): void {
    this.usiCollectionPoints = [];
    if (this.selectedDeliveryMethod !== 'CASH_PICKUP' || !this.isUsiCountry()) return;
    const fullName = ({ UG:'Uganda', UGA:'Uganda', TR:'Turkey', TUR:'Turkey', EG:'Egypt', EGY:'Egypt',
                        QA:'Qatar', QAT:'Qatar', SA:'Saudi Arabia', SAU:'Saudi Arabia',
                        AE:'United Arab Emirates', ARE:'United Arab Emirates' } as any)
                        [(this.receiveCountry || '').toUpperCase()] || this.receiveCountry;
    this.partnerService.fetchBlob || (this.partnerService as any).http;  // no-op — keep import
    this.usiMoneyService.getCollectionPoints(fullName).subscribe({
      next: (pts) => this.usiCollectionPoints = pts || [],
      error: () => this.usiCollectionPoints = []
    });
  }

  goToStep3(): void {
    this.stepError = '';
    const hasBeneficiary = this.selectedBeneficiary || (this.newBen.name && this.newBen.bankName && this.newBen.accountNumber);
    if (!hasBeneficiary) {
      this.stepError = 'Select an existing beneficiary or add a new one';
      return;
    }
    this.currentStep = 3;
  }

  // ── Step 3: Submit ────────────────────────────────────────────

  submit(): void {
    this.stepError = '';
    this.submitting = true;

    const payload: any = {
      customerId: this.selectedCustomer.customerId,
      amount: this.sendAmount,
      currency: this.sendCurrency,
      receiveCurrency: this.receiveCurrency,
      receiveAmount: this.quote?.receiveAmount,
      deliveryMethod: this.selectedDeliveryMethod,
      paymentMode: this.selectedCollectionType
    };

    if (this.externalReferenceId?.trim()) payload.externalReferenceId = this.externalReferenceId.trim();

    if (this.selectedBeneficiary) {
      payload.beneficiaryId = String(this.selectedBeneficiary.id);
    } else {
      // Pass through every USI-relevant field so the backend persists them on the new
      // BeneficiaryEntity (so the USI Money admin "Create on USI" later finds the
      // complete record without a re-edit).
      payload.beneficiaryDetails = {
        name: this.newBen.name?.trim() || '',
        address: this.newBen.address?.trim() || null,
        mobileNumber: this.newBen.mobileNumber?.trim() || null,

        // Bank
        bankName: this.newBen.bankName?.trim() || null,
        sortCode: this.newBen.sortCode?.trim() || null,          // Branch name (USI bank_branch)
        branchState: this.newBen.branchState?.trim() || null,
        branchCity: this.newBen.branchCity?.trim() || null,
        accountNumber: this.newBen.accountNumber?.trim() || this.newBen.iban?.trim() || null,
        iban: this.newBen.iban?.trim() || null,
        swiftBic: this.newBen.swiftBic?.trim() || null,
        ifscCode: this.newBen.ifscCode?.trim() || null,

        // Mobile wallet
        mobileProvider: this.newBen.mobileProvider || null,

        // Cash collection — packed into bank fields so USI sees them at txn time
        // (the new BeneficiaryEntity gets bank_name=collectionPointName etc.)
        collectionPointName: this.newBen.collectionPointName?.trim() || null,
        collectionPointCode: this.newBen.collectionPointCode?.trim() || null,
        collectionPointAddress: this.newBen.collectionPointAddress?.trim() || null,
        collectionPointCity: this.newBen.collectionPointCity?.trim() || null
      };
    }

    this.partnerService.createPayinTransaction(payload).subscribe({
      next: (res: any) => {
        this.submitting = false;
        if (res?.success) {
          this.receiptTransactionId = res.transactionId || '';
          this.receiptStatus = res.status || 'PENDING';
          this.receiptCustomerSource = res.customerSource || '';
          this.currentStep = 4;
          this.loadReceiptInline();
        } else {
          this.stepError = res?.message || 'Transaction creation failed';
        }
      },
      error: (err: any) => {
        this.submitting = false;
        this.stepError = err?.error?.message || 'An error occurred. Please try again.';
      }
    });
  }

  /** Fetch the branded receipt PDF and embed it inline on the success step. */
  loadReceiptInline(): void {
    if (!this.receiptTransactionId) return;
    this.receiptLoading = true;
    this.receiptError = '';
    this.partnerService.downloadPayinReceipt(this.receiptTransactionId).subscribe({
      next: (blob: Blob) => {
        if (this.receiptObjectUrl) URL.revokeObjectURL(this.receiptObjectUrl);
        this.receiptObjectUrl = URL.createObjectURL(blob);
        // #toolbar=0 hides the browser PDF chrome for a cleaner inline receipt view.
        this.receiptPdfUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.receiptObjectUrl + '#toolbar=0&navpanes=0');
        this.receiptLoading = false;
      },
      error: () => { this.receiptLoading = false; this.receiptError = 'Could not load the receipt.'; }
    });
  }

  /** Download the same receipt to disk (fallback / save). */
  downloadReceipt(): void {
    if (!this.receiptObjectUrl) { this.loadReceiptInline(); return; }
    const a = document.createElement('a');
    a.href = this.receiptObjectUrl;
    a.download = `receipt-${this.receiptTransactionId}.pdf`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  resetForm(): void {
    this.currentStep = 0;
    this.selectedCustomer = null;
    this.customerSearch = '';
    this.sendAmount = 100;
    this.quote = null;
    this.selectedDeliveryMethod = 'BANK_TRANSFER';
    this.selectedCollectionType = 'CASH_COLLECTION';
    this.beneficiaries = [];
    this.filteredBeneficiaries = [];
    this.selectedBeneficiary = null;
    this.newBen = { name: '', bankName: '', accountNumber: '', ifscCode: '' };
    this.externalReferenceId = '';
    this.stepError = '';
    this.receiptTransactionId = '';
    this.receiptStatus = '';
    if (this.receiptObjectUrl) { URL.revokeObjectURL(this.receiptObjectUrl); this.receiptObjectUrl = ''; }
    this.receiptPdfUrl = null;
    this.receiptError = '';
    this.receiptCustomerSource = '';
    this.loadCustomers();
  }

  // ── Helpers ───────────────────────────────────────────────────

  deliveryMethodLabel(v: string): string {
    return this.deliveryMethods.find(m => m.value === v)?.label || v;
  }

  collectionTypeLabel(v: string): string {
    return this.collectionTypes.find(c => c.value === v)?.label || v;
  }
}
