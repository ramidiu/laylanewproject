import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular';
import { HttpClient } from '@angular/common/http';
import { Subject, debounceTime, takeUntil, switchMap, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AddressService, AddressSuggestion } from '../../core/services/address.service';

interface DocType { label: string; value: string; hasFront: boolean; hasBack: boolean; hasNumber: boolean; hasExpiry: boolean; }

@Component({
  selector: 'app-create-customer',
  template: `
    <div class="cc-page">

      <!-- Header -->
      <div class="cc-header">
        <button class="cc-back-btn" (click)="router.navigate(['/payin-partner/customers'])">
          <ion-icon name="arrow-back-outline"></ion-icon>
        </button>
        <div>
          <h1 class="cc-title">Create Customer</h1>
          <p class="cc-sub">Complete all sections to register a new PayIn customer</p>
        </div>
      </div>

      <!-- Step indicator -->
      <div class="cc-steps">
        <div class="cc-step" *ngFor="let s of steps; let i = index"
             [class.cc-step--active]="step === i"
             [class.cc-step--done]="step > i">
          <div class="cc-step__dot">
            <ion-icon *ngIf="step > i" name="checkmark"></ion-icon>
            <span *ngIf="step <= i">{{ i + 1 }}</span>
          </div>
          <span class="cc-step__label">{{ s }}</span>
          <div class="cc-step__line" *ngIf="i < steps.length - 1" [class.cc-step__line--done]="step > i"></div>
        </div>
      </div>

      <!-- ── STEP 1: Identity Details ──────────────────────────── -->
      <div class="cc-card" *ngIf="step === 0">
        <div class="cc-card-header">
          <div class="cc-card-icon" style="background:#EFF6FF;color:#1B3571;">
            <ion-icon name="person-outline"></ion-icon>
          </div>
          <div>
            <h2 class="cc-card-title">Identity Details</h2>
            <p class="cc-card-sub">Customer's personal information</p>
          </div>
        </div>

        <form [formGroup]="identityForm">
          <div class="cc-grid">
            <div class="cc-field">
              <label>First Name <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(identityForm, 'firstName')">
                <ion-icon name="person-outline" class="cc-ico"></ion-icon>
                <input formControlName="firstName" placeholder="John" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(identityForm, 'firstName')">Required</span>
            </div>

            <div class="cc-field">
              <label>Last Name <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(identityForm, 'lastName')">
                <ion-icon name="person-outline" class="cc-ico"></ion-icon>
                <input formControlName="lastName" placeholder="Doe" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(identityForm, 'lastName')">Required</span>
            </div>

            <div class="cc-field">
              <label>Email Address <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(identityForm, 'email')">
                <ion-icon name="mail-outline" class="cc-ico"></ion-icon>
                <input formControlName="email" type="email" placeholder="john@example.com" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(identityForm, 'email')">Valid email required</span>
            </div>

            <div class="cc-field">
              <label>Phone Number <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(identityForm, 'phone')">
                <ion-icon name="call-outline" class="cc-ico"></ion-icon>
                <input formControlName="phone" placeholder="+44 7700 000000" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(identityForm, 'phone')">Required</span>
            </div>

            <div class="cc-field">
              <label>Date of Birth <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(identityForm, 'dob')">
                <ion-icon name="calendar-outline" class="cc-ico"></ion-icon>
                <input formControlName="dob" type="date" [max]="maxDob" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(identityForm, 'dob')">Required (must be 18+)</span>
            </div>

            <div class="cc-field">
              <label>Nationality <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(identityForm, 'nationality')">
                <ion-icon name="flag-outline" class="cc-ico"></ion-icon>
                <input formControlName="nationality" placeholder="e.g. British, Indian" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(identityForm, 'nationality')">Required</span>
            </div>
          </div>
        </form>

        <div class="cc-nav">
          <button class="cc-btn cc-btn--outline" (click)="router.navigate(['/payin-partner/customers'])">Cancel</button>
          <button class="cc-btn cc-btn--primary" (click)="nextStep(0)">Next <ion-icon name="arrow-forward-outline"></ion-icon></button>
        </div>
      </div>

      <!-- ── STEP 2: Address Details ───────────────────────────── -->
      <div class="cc-card" *ngIf="step === 1">
        <div class="cc-card-header">
          <div class="cc-card-icon" style="background:#F0FDF4;color:#00B894;">
            <ion-icon name="location-outline"></ion-icon>
          </div>
          <div>
            <h2 class="cc-card-title">Address Details</h2>
            <p class="cc-card-sub">Customer's residential address</p>
          </div>
        </div>

        <!-- Country selector first so Smarty only shows for GB -->
        <form [formGroup]="addressForm">

          <div class="cc-field" style="margin-bottom:18px;">
            <label>Country <span class="cc-req">*</span></label>
            <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(addressForm, 'country')">
              <ion-icon name="earth-outline" class="cc-ico"></ion-icon>
              <select formControlName="country">
                <option value="">Select country</option>
                <option value="GB">🇬🇧 United Kingdom</option>
                <option value="US">🇺🇸 United States</option>
                <option value="IN">🇮🇳 India</option>
                <option value="PK">🇵🇰 Pakistan</option>
                <option value="NG">🇳🇬 Nigeria</option>
                <option value="GH">🇬🇭 Ghana</option>
                <option value="KE">🇰🇪 Kenya</option>
                <option value="BD">🇧🇩 Bangladesh</option>
                <option value="PH">🇵🇭 Philippines</option>
                <option value="NP">🇳🇵 Nepal</option>
                <option value="LK">🇱🇰 Sri Lanka</option>
                <option value="AE">🇦🇪 UAE</option>
                <option value="AU">🇦🇺 Australia</option>
                <option value="CA">🇨🇦 Canada</option>
                <option value="DE">🇩🇪 Germany</option>
                <option value="FR">🇫🇷 France</option>
                <option value="ZA">🇿🇦 South Africa</option>
                <option value="UG">🇺🇬 Uganda</option>
                <option value="TZ">🇹🇿 Tanzania</option>
                <option value="EG">🇪🇬 Egypt</option>
                <option value="SD">🇸🇩 Sudan</option>
              </select>
            </div>
            <span class="cc-err" *ngIf="isInvalid(addressForm, 'country')">Required</span>
          </div>

          <!-- Smarty address lookup — shown only for UK -->
          <div class="cc-addr-lookup" *ngIf="addressForm.get('country')?.value === 'GB'">
            <div class="cc-addr-lookup__header">
              <ion-icon name="location-outline"></ion-icon>
              Find UK address
            </div>

            <!-- Search input -->
            <div class="cc-field" style="margin-bottom:6px;">
              <label>Search address</label>
              <div class="cc-addr-search-wrap">
                <ion-icon name="search-outline" class="cc-addr-search-ico"></ion-icon>
                <input
                  class="cc-addr-search-input"
                  type="text"
                  [value]="addrSearchQuery"
                  (input)="onAddrInput($event)"
                  placeholder="Type postcode or street…"
                  autocomplete="off"
                />
                <ion-spinner *ngIf="addrLoading" name="crescent" class="cc-addr-spinner"></ion-spinner>
                <ion-icon *ngIf="addrSearchQuery && !addrLoading" name="close-circle-outline"
                  class="cc-addr-clear" (click)="clearAddr()"></ion-icon>
              </div>
            </div>

            <!-- Suggestions — inline list -->
            <div class="cc-addr-suggestions" *ngIf="addrShowList && addrSuggestions.length > 0">
              <div class="cc-addr-suggestion" *ngFor="let s of addrSuggestions" (click)="pickAddr(s)">
                <ion-icon name="location-outline"></ion-icon>
                <span class="cc-addr-suggestion__text">{{ s.addressText }}</span>
                <span class="cc-addr-suggestion__badge" *ngIf="s.entries > 1">{{ s.entries }}</span>
              </div>
            </div>
          </div>

          <div class="cc-grid">
            <div class="cc-field cc-field--full">
              <label>Address Line 1 <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(addressForm, 'addressLine1')">
                <ion-icon name="home-outline" class="cc-ico"></ion-icon>
                <input formControlName="addressLine1" placeholder="10 Downing Street" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(addressForm, 'addressLine1')">Required</span>
            </div>

            <div class="cc-field">
              <label>City <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(addressForm, 'city')">
                <ion-icon name="business-outline" class="cc-ico"></ion-icon>
                <input formControlName="city" placeholder="London" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(addressForm, 'city')">Required</span>
            </div>

            <div class="cc-field">
              <label>Postal / ZIP Code <span class="cc-req">*</span></label>
              <div class="cc-input-wrap" [class.cc-input-wrap--error]="isInvalid(addressForm, 'postalCode')">
                <ion-icon name="map-outline" class="cc-ico"></ion-icon>
                <input formControlName="postalCode" placeholder="SW1A 2AA" />
              </div>
              <span class="cc-err" *ngIf="isInvalid(addressForm, 'postalCode')">Required</span>
            </div>
          </div>
        </form>

        <div class="cc-nav">
          <button class="cc-btn cc-btn--outline" (click)="step = 0">Back</button>
          <button class="cc-btn cc-btn--primary" (click)="nextStep(1)">Next <ion-icon name="arrow-forward-outline"></ion-icon></button>
        </div>
      </div>

      <!-- ── STEP 3: Identity Document ─────────────────────────── -->
      <div class="cc-card" *ngIf="step === 2">
        <div class="cc-card-header">
          <div class="cc-card-icon" style="background:#FFF7ED;color:#f59e0b;">
            <ion-icon name="id-card-outline"></ion-icon>
          </div>
          <div>
            <h2 class="cc-card-title">Identity Document</h2>
            <p class="cc-card-sub">Upload a government-issued photo ID</p>
          </div>
        </div>

        <!-- Document type selector -->
        <div class="cc-field cc-field--full" style="margin-bottom:20px;">
          <label>Document Type <span class="cc-req">*</span></label>
          <div class="cc-doc-type-grid">
            <button
              *ngFor="let dt of identityDocTypes"
              type="button"
              class="cc-doc-type-btn"
              [class.cc-doc-type-btn--active]="selectedIdentityDocType?.value === dt.value"
              (click)="selectIdentityDocType(dt)"
            >
              <ion-icon name="id-card-outline"></ion-icon>
              {{ dt.label }}
            </button>
          </div>
        </div>

        <div *ngIf="selectedIdentityDocType">
          <div class="cc-grid" style="margin-bottom:20px;">
            <div class="cc-field" *ngIf="selectedIdentityDocType.hasNumber">
              <label>Document Number <span class="cc-req">*</span></label>
              <div class="cc-input-wrap">
                <ion-icon name="barcode-outline" class="cc-ico"></ion-icon>
                <input [(ngModel)]="identityDocNumber" placeholder="e.g. P12345678" />
              </div>
            </div>
            <div class="cc-field">
              <label>Issue Date</label>
              <div class="cc-input-wrap">
                <ion-icon name="calendar-outline" class="cc-ico"></ion-icon>
                <input type="date" [(ngModel)]="identityIssueDate" />
              </div>
            </div>
            <div class="cc-field" *ngIf="selectedIdentityDocType.hasExpiry">
              <label>Expiry Date <span class="cc-req">*</span></label>
              <div class="cc-input-wrap">
                <ion-icon name="calendar-outline" class="cc-ico"></ion-icon>
                <input type="date" [(ngModel)]="identityExpiryDate" />
              </div>
            </div>
          </div>

          <!-- Front upload -->
          <div class="cc-upload-zone" [class.cc-upload-zone--has-file]="identityFrontFile" (click)="triggerFileInput('identityFront')">
            <input type="file" id="identityFront" accept=".jpg,.jpeg,.png,.pdf" (change)="onFileSelected($event, 'identityFront')" style="display:none" />
            <div *ngIf="!identityFrontPreview" class="cc-upload-empty">
              <ion-icon name="cloud-upload-outline"></ion-icon>
              <strong>Front of {{ selectedIdentityDocType.label }}</strong>
              <span>Click to upload or drag &amp; drop</span>
              <span class="cc-upload-hint">JPG, PNG or PDF · Max 10 MB</span>
            </div>
            <div *ngIf="identityFrontPreview" class="cc-upload-preview">
              <img *ngIf="!identityFrontFile?.name?.endsWith('.pdf')" [src]="identityFrontPreview" alt="Front" />
              <div *ngIf="identityFrontFile?.name?.endsWith('.pdf')" class="cc-pdf-badge">
                <ion-icon name="document-outline"></ion-icon> {{ identityFrontFile?.name }}
              </div>
              <button type="button" class="cc-upload-remove" (click)="clearFile('identityFront', $event)">
                <ion-icon name="close-circle"></ion-icon>
              </button>
            </div>
          </div>

          <!-- Back upload — only for documents that have a back (not Passport) -->
          <div *ngIf="selectedIdentityDocType?.hasBack" class="cc-upload-zone cc-upload-zone--secondary" style="margin-top:14px;" [class.cc-upload-zone--has-file]="identityBackFile" (click)="triggerFileInput('identityBack')">
            <input type="file" id="identityBack" accept=".jpg,.jpeg,.png,.pdf" (change)="onFileSelected($event, 'identityBack')" style="display:none" />
            <div *ngIf="!identityBackPreview" class="cc-upload-empty">
              <ion-icon name="cloud-upload-outline"></ion-icon>
              <strong>Back of {{ selectedIdentityDocType.label }} <span class="cc-optional">(optional)</span></strong>
              <span>Click to upload if your document has a back</span>
            </div>
            <div *ngIf="identityBackPreview" class="cc-upload-preview">
              <img *ngIf="!identityBackFile?.name?.endsWith('.pdf')" [src]="identityBackPreview" alt="Back" />
              <div *ngIf="identityBackFile?.name?.endsWith('.pdf')" class="cc-pdf-badge">
                <ion-icon name="document-outline"></ion-icon> {{ identityBackFile?.name }}
              </div>
              <button type="button" class="cc-upload-remove" (click)="clearFile('identityBack', $event)">
                <ion-icon name="close-circle"></ion-icon>
              </button>
            </div>
          </div>
        </div>

        <div class="cc-err-box" *ngIf="stepError">{{ stepError }}</div>
        <div class="cc-nav">
          <button class="cc-btn cc-btn--outline" (click)="step = 1; stepError = ''">Back</button>
          <button class="cc-btn cc-btn--primary" (click)="nextStep(2)">Next <ion-icon name="arrow-forward-outline"></ion-icon></button>
        </div>
      </div>

      <!-- ── STEP 4: Address Proof ──────────────────────────────── -->
      <div class="cc-card" *ngIf="step === 3">
        <div class="cc-card-header">
          <div class="cc-card-icon" style="background:#F5F3FF;color:#7c3aed;">
            <ion-icon name="document-text-outline"></ion-icon>
          </div>
          <div>
            <h2 class="cc-card-title">Address Proof</h2>
            <p class="cc-card-sub">Upload a document confirming the customer's address</p>
          </div>
        </div>

        <!-- Address doc type selector -->
        <div class="cc-field cc-field--full" style="margin-bottom:20px;">
          <label>Document Type <span class="cc-req">*</span></label>
          <div class="cc-doc-type-grid">
            <button
              *ngFor="let dt of addressDocTypes"
              type="button"
              class="cc-doc-type-btn"
              [class.cc-doc-type-btn--active]="selectedAddressDocType?.value === dt.value"
              (click)="selectedAddressDocType = dt"
            >
              <ion-icon name="document-outline"></ion-icon>
              {{ dt.label }}
            </button>
          </div>
        </div>

        <div class="cc-info-box" *ngIf="selectedAddressDocType">
          <ion-icon name="information-circle-outline"></ion-icon>
          Document must clearly show the customer's name and full address. Must be dated within the last 3 months.
        </div>

        <div class="cc-upload-zone" style="margin-top:16px;" [class.cc-upload-zone--has-file]="addressFile" (click)="triggerFileInput('addressProof')">
          <input type="file" id="addressProof" accept=".jpg,.jpeg,.png,.pdf" (change)="onFileSelected($event, 'addressProof')" style="display:none" />
          <div *ngIf="!addressPreview" class="cc-upload-empty">
            <ion-icon name="cloud-upload-outline"></ion-icon>
            <strong>{{ selectedAddressDocType ? selectedAddressDocType.label : 'Address Proof Document' }}</strong>
            <span>Click to upload or drag &amp; drop</span>
            <span class="cc-upload-hint">JPG, PNG or PDF · Max 10 MB</span>
          </div>
          <div *ngIf="addressPreview" class="cc-upload-preview">
            <img *ngIf="!addressFile?.name?.endsWith('.pdf')" [src]="addressPreview" alt="Address Proof" />
            <div *ngIf="addressFile?.name?.endsWith('.pdf')" class="cc-pdf-badge">
              <ion-icon name="document-outline"></ion-icon> {{ addressFile?.name }}
            </div>
            <button type="button" class="cc-upload-remove" (click)="clearFile('addressProof', $event)">
              <ion-icon name="close-circle"></ion-icon>
            </button>
          </div>
        </div>

        <div class="cc-err-box" *ngIf="stepError">{{ stepError }}</div>
        <div class="cc-submit-progress" *ngIf="submitting">
          <div class="cc-progress-step" [class.cc-progress-step--done]="submitProgress >= 1" [class.cc-progress-step--active]="submitProgress === 0">
            <ion-icon [name]="submitProgress >= 1 ? 'checkmark-circle' : 'ellipsis-horizontal-circle'"></ion-icon>
            Creating customer record
          </div>
          <div class="cc-progress-step" [class.cc-progress-step--done]="submitProgress >= 2" [class.cc-progress-step--active]="submitProgress === 1">
            <ion-icon [name]="submitProgress >= 2 ? 'checkmark-circle' : 'ellipsis-horizontal-circle'"></ion-icon>
            Uploading identity document
          </div>
          <div class="cc-progress-step" [class.cc-progress-step--done]="submitProgress >= 3" [class.cc-progress-step--active]="submitProgress === 2">
            <ion-icon [name]="submitProgress >= 3 ? 'checkmark-circle' : 'ellipsis-horizontal-circle'"></ion-icon>
            Uploading address proof
          </div>
        </div>

        <div class="cc-nav">
          <button class="cc-btn cc-btn--outline" [disabled]="submitting" (click)="step = 2; stepError = ''">Back</button>
          <button class="cc-btn cc-btn--primary" (click)="submit()" [disabled]="submitting">
            <ion-spinner *ngIf="submitting" name="crescent" style="width:18px;height:18px;"></ion-spinner>
            <ion-icon *ngIf="!submitting" name="checkmark-outline"></ion-icon>
            {{ submitting ? 'Submitting...' : 'Create Customer' }}
          </button>
        </div>
      </div>

    </div>
  `,
  styles: [`
    .cc-page { padding: 24px; max-width: 780px; }

    /* Header */
    .cc-header { display:flex; align-items:flex-start; gap:14px; margin-bottom:28px; }
    .cc-back-btn { background:none; border:none; cursor:pointer; color:#1B3571; font-size:1.4rem; padding:4px 0; flex-shrink:0; }
    .cc-title { font-size:1.5rem; font-weight:700; margin:0 0 4px; color:#111827; }
    .cc-sub { color:#6b7280; margin:0; font-size:0.875rem; }

    /* Steps */
    .cc-steps { display:flex; align-items:center; gap:0; margin-bottom:28px; overflow-x:auto; padding-bottom:4px; }
    .cc-step { display:flex; align-items:center; gap:8px; flex-shrink:0; position:relative; }
    .cc-step__dot { width:30px; height:30px; border-radius:50%; border:2px solid #e5e7eb; display:flex; align-items:center; justify-content:center; font-size:0.8rem; font-weight:600; color:#9ca3af; flex-shrink:0; transition:all .2s; }
    .cc-step--active .cc-step__dot { border-color:#1B3571; background:#1B3571; color:#fff; }
    .cc-step--done .cc-step__dot { border-color:#00B894; background:#00B894; color:#fff; }
    .cc-step__label { font-size:0.75rem; color:#9ca3af; white-space:nowrap; }
    .cc-step--active .cc-step__label { color:#1B3571; font-weight:600; }
    .cc-step--done .cc-step__label { color:#00B894; }
    .cc-step__line { width:40px; height:2px; background:#e5e7eb; margin:0 4px; flex-shrink:0; }
    .cc-step__line--done { background:#00B894; }

    /* Card */
    .cc-card { background:#fff; border:1px solid #e5e7eb; border-radius:12px; padding:24px; box-shadow:0 1px 6px rgba(0,0,0,.06); }
    .cc-card-header { display:flex; align-items:flex-start; gap:14px; margin-bottom:24px; padding-bottom:16px; border-bottom:1px solid #f3f4f6; }
    .cc-card-icon { width:46px; height:46px; border-radius:10px; display:flex; align-items:center; justify-content:center; font-size:1.3rem; flex-shrink:0; }
    .cc-card-title { font-size:1.05rem; font-weight:700; color:#111827; margin:0 0 3px; }
    .cc-card-sub { font-size:0.82rem; color:#6b7280; margin:0; }

    /* Form grid */
    .cc-grid { display:grid; grid-template-columns:1fr 1fr; gap:18px; }
    .cc-field { display:flex; flex-direction:column; gap:5px; }
    .cc-field--full { grid-column:1/-1; }
    .cc-field label { font-size:0.82rem; font-weight:600; color:#374151; }
    .cc-req { color:#ef4444; }
    .cc-optional { font-weight:400; color:#9ca3af; font-size:0.78rem; }

    .cc-input-wrap { display:flex; align-items:center; border:1.5px solid #e5e7eb; border-radius:8px; overflow:hidden; transition:border-color .15s, box-shadow .15s; background:#fff; }
    .cc-input-wrap:focus-within { border-color:#1B3571; box-shadow:0 0 0 3px rgba(27,53,113,.08); }
    .cc-input-wrap--error { border-color:#ef4444 !important; }
    .cc-ico { padding:0 10px; color:#9ca3af; font-size:1rem; flex-shrink:0; }
    .cc-input-wrap input, .cc-input-wrap select { flex:1; padding:11px 12px 11px 0; border:none; outline:none; font-size:0.875rem; background:transparent; min-width:0; }
    .cc-input-wrap select { cursor:pointer; }
    .cc-err { font-size:0.75rem; color:#ef4444; }

    /* Doc type buttons */
    .cc-doc-type-grid { display:flex; flex-wrap:wrap; gap:10px; margin-top:6px; }
    .cc-doc-type-btn { display:flex; align-items:center; gap:8px; padding:10px 18px; border:1.5px solid #e5e7eb; border-radius:8px; background:#fff; cursor:pointer; font-size:0.875rem; font-weight:500; color:#374151; transition:all .15s; }
    .cc-doc-type-btn:hover { border-color:#1B3571; color:#1B3571; background:#f8faff; }
    .cc-doc-type-btn--active { border-color:#1B3571; background:#1B3571; color:#fff; }

    /* Upload zone */
    .cc-upload-zone { border:2px dashed #e5e7eb; border-radius:12px; padding:28px 20px; cursor:pointer; text-align:center; transition:all .2s; background:#fafafa; }
    .cc-upload-zone:hover { border-color:#1B3571; background:#f8faff; }
    .cc-upload-zone--has-file { border-color:#00B894; background:#f0fdf8; border-style:solid; }
    .cc-upload-zone--secondary { background:#fff; }
    .cc-upload-empty { display:flex; flex-direction:column; align-items:center; gap:6px; color:#6b7280; }
    .cc-upload-empty ion-icon { font-size:2.2rem; color:#9ca3af; }
    .cc-upload-empty strong { font-size:0.9rem; color:#374151; }
    .cc-upload-empty span { font-size:0.8rem; }
    .cc-upload-hint { color:#9ca3af !important; font-size:0.75rem !important; }
    .cc-upload-preview { position:relative; display:flex; justify-content:center; align-items:center; }
    .cc-upload-preview img { max-height:200px; max-width:100%; border-radius:8px; }
    .cc-upload-remove { position:absolute; top:-10px; right:-10px; background:none; border:none; cursor:pointer; color:#ef4444; font-size:1.6rem; }
    .cc-pdf-badge { display:flex; align-items:center; gap:8px; padding:16px 24px; background:#fff; border-radius:8px; border:1px solid #e5e7eb; font-size:0.875rem; }

    /* Info box */
    .cc-info-box { display:flex; align-items:flex-start; gap:10px; background:#eff6ff; border:1px solid #bfdbfe; border-radius:8px; padding:12px 16px; font-size:0.82rem; color:#1e40af; }

    /* Error box */
    .cc-err-box { color:#ef4444; font-size:0.875rem; padding:10px 14px; background:#fef2f2; border:1px solid #fecaca; border-radius:8px; margin-top:16px; }

    /* Submit progress */
    .cc-submit-progress { margin-top:16px; display:flex; flex-direction:column; gap:8px; padding:16px; background:#f8faff; border-radius:8px; border:1px solid #e0e7ff; }
    .cc-progress-step { display:flex; align-items:center; gap:10px; font-size:0.875rem; color:#6b7280; }
    .cc-progress-step--active { color:#1B3571; font-weight:600; }
    .cc-progress-step--done { color:#00B894; }
    .cc-progress-step ion-icon { font-size:1.2rem; }

    /* Nav */
    .cc-nav { display:flex; justify-content:space-between; align-items:center; margin-top:24px; padding-top:16px; border-top:1px solid #f3f4f6; }
    .cc-btn { display:flex; align-items:center; gap:8px; padding:11px 28px; border-radius:8px; font-size:0.9rem; font-weight:600; cursor:pointer; border:none; transition:all .15s; }
    .cc-btn--outline { background:#fff; border:1.5px solid #d1d5db; color:#374151; }
    .cc-btn--outline:hover:not(:disabled) { border-color:#1B3571; color:#1B3571; }
    .cc-btn--primary { background:#1B3571; color:#fff; }
    .cc-btn--primary:hover:not(:disabled) { background:#122550; }
    .cc-btn:disabled { opacity:.5; cursor:not-allowed; }

    /* Address lookup */
    .cc-addr-lookup { background:#f0f4ff; border:1px solid #c7d4f0; border-radius:10px; padding:14px 14px 10px; margin-bottom:18px; }
    .cc-addr-lookup__header { display:flex; align-items:center; gap:6px; font-size:11px; font-weight:700; color:#1B3571; text-transform:uppercase; letter-spacing:.05em; margin-bottom:10px; }
    .cc-addr-lookup__header ion-icon { font-size:14px; }
    .cc-addr-search-wrap { position:relative; display:flex; align-items:center; }
    .cc-addr-search-ico { position:absolute; left:10px; font-size:15px; color:#6b7280; pointer-events:none; }
    .cc-addr-search-input { width:100%; padding:10px 36px; border:1.5px solid #e5e7eb; border-radius:8px; font-size:0.875rem; outline:none; background:#fff; transition:border-color .15s; }
    .cc-addr-search-input:focus { border-color:#1B3571; box-shadow:0 0 0 3px rgba(27,53,113,.08); }
    .cc-addr-spinner { position:absolute; right:10px; width:16px; height:16px; --color:#1B3571; }
    .cc-addr-clear { position:absolute; right:10px; font-size:17px; color:#9ca3af; cursor:pointer; }
    .cc-addr-clear:hover { color:#374151; }
    .cc-addr-suggestions { border:1px solid #c7d4f0; border-radius:8px; overflow:hidden; margin-top:4px; margin-bottom:8px; background:#fff; box-shadow:0 4px 12px rgba(0,0,0,.08); }
    .cc-addr-suggestion { display:flex; align-items:center; gap:8px; padding:10px 12px; font-size:13px; color:#374151; cursor:pointer; border-bottom:1px solid #f3f4f6; }
    .cc-addr-suggestion:last-child { border-bottom:none; }
    .cc-addr-suggestion:hover { background:#eef3ff; }
    .cc-addr-suggestion ion-icon { flex-shrink:0; font-size:14px; color:#1B3571; }
    .cc-addr-suggestion__text { flex:1; }
    .cc-addr-suggestion__badge { font-size:10px; color:#6b7280; background:#f3f4f6; border-radius:4px; padding:1px 5px; flex-shrink:0; }

    @media (max-width:600px) {
      .cc-grid { grid-template-columns:1fr; }
      .cc-doc-type-grid { flex-direction:column; }
    }
  `]
})
export class CreateCustomerPage implements OnInit, OnDestroy {
  steps = ['Identity', 'Address', 'ID Document', 'Address Proof'];
  step = 0;
  stepError = '';
  submitting = false;
  submitProgress = 0;

  // Smarty address search
  addrSearchQuery = '';
  addrSuggestions: AddressSuggestion[] = [];
  addrLoading = false;
  addrShowList = false;
  private addrSearch$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  identityForm!: FormGroup;
  addressForm!: FormGroup;

  maxDob = '';

  identityDocTypes: DocType[] = [
    { label: 'Passport', value: 'PASSPORT', hasFront: true, hasBack: false, hasNumber: true, hasExpiry: true },
    { label: 'Driving Licence', value: 'DRIVING_LICENSE', hasFront: true, hasBack: true, hasNumber: true, hasExpiry: true },
    { label: 'National ID', value: 'NATIONAL_ID', hasFront: true, hasBack: true, hasNumber: true, hasExpiry: false },
    { label: 'BRP Card', value: 'BRP', hasFront: true, hasBack: true, hasNumber: true, hasExpiry: true }
  ];

  addressDocTypes: DocType[] = [
    { label: 'Utility Bill', value: 'UTILITY_BILL', hasFront: true, hasBack: false, hasNumber: false, hasExpiry: false },
    { label: 'Bank Statement', value: 'BANK_STATEMENT', hasFront: true, hasBack: false, hasNumber: false, hasExpiry: false },
    { label: 'Council Tax Bill', value: 'COUNCIL_TAX_BILL', hasFront: true, hasBack: false, hasNumber: false, hasExpiry: false },
    { label: 'Tenancy Agreement', value: 'TENANCY_AGREEMENT', hasFront: true, hasBack: false, hasNumber: false, hasExpiry: false }
  ];

  selectedIdentityDocType: DocType | null = null;
  identityDocNumber = '';
  identityIssueDate = '';
  identityExpiryDate = '';
  identityFrontFile: File | null = null;
  identityFrontPreview: string | null = null;
  identityBackFile: File | null = null;
  identityBackPreview: string | null = null;

  selectedAddressDocType: DocType | null = null;
  addressFile: File | null = null;
  addressPreview: string | null = null;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    public router: Router,
    private toastCtrl: ToastController,
    private addressService: AddressService
  ) {}

  ngOnInit(): void {
    const d = new Date();
    d.setFullYear(d.getFullYear() - 18);
    this.maxDob = d.toISOString().split('T')[0];

    this.identityForm = this.fb.group({
      firstName:   ['', Validators.required],
      lastName:    ['', Validators.required],
      email:       ['', [Validators.required, Validators.email]],
      phone:       ['', Validators.required],
      dob:         ['', Validators.required],
      nationality: ['', Validators.required]
    });

    this.addressForm = this.fb.group({
      addressLine1: ['', Validators.required],
      city:         ['', Validators.required],
      country:      ['', Validators.required],
      postalCode:   ['', Validators.required]
    });

    // Smarty address search with debounce
    this.addrSearch$.pipe(
      takeUntil(this.destroy$),
      debounceTime(400),
      switchMap(query => {
        if (!query || query.length < 3) {
          this.addrSuggestions = [];
          this.addrShowList = false;
          this.addrLoading = false;
          return of([]);
        }
        this.addrLoading = true;
        return this.addressService.lookup(query, 'GB');
      })
    ).subscribe({
      next: (suggestions: AddressSuggestion[]) => {
        this.addrSuggestions = suggestions;
        this.addrLoading = false;
        this.addrShowList = suggestions.length > 0;
      },
      error: () => { this.addrLoading = false; this.addrShowList = false; }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onAddrInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.addrSearchQuery = value;
    this.addrSearch$.next(value);
  }

  pickAddr(s: AddressSuggestion): void {
    // Multi-entry: expand to show individual units
    if (s.entries > 1) {
      this.addrLoading = true;
      this.addrShowList = false;
      this.addressService.lookup(s.addressText, 'GB', s.addressId).subscribe({
        next: (subs) => {
          this.addrLoading = false;
          this.addrSuggestions = subs;
          this.addrShowList = subs.length > 0;
        },
        error: () => { this.addrLoading = false; }
      });
      return;
    }

    // Single entry: retrieve and auto-fill
    this.addrShowList = false;
    this.addrSearchQuery = s.addressText;
    this.addrLoading = true;
    this.addressService.retrieve(s.addressId, 'GB').subscribe({
      next: (detail) => {
        this.addrLoading = false;
        this.addressForm.patchValue({
          addressLine1: detail.street || s.addressText,
          city:         detail.city   || '',
          postalCode:   detail.postcode || ''
        });
      },
      error: () => {
        this.addrLoading = false;
        this.addressForm.patchValue({ addressLine1: s.addressText });
      }
    });
  }

  clearAddr(): void {
    this.addrSearchQuery = '';
    this.addrSuggestions = [];
    this.addrShowList = false;
    this.addressForm.patchValue({ addressLine1: '', city: '', postalCode: '' });
  }

  selectIdentityDocType(dt: DocType): void {
    this.selectedIdentityDocType = dt;
    this.identityFrontFile = null; this.identityFrontPreview = null;
    this.identityBackFile = null; this.identityBackPreview = null;
  }

  nextStep(current: number): void {
    this.stepError = '';
    if (current === 0) {
      this.identityForm.markAllAsTouched();
      if (this.identityForm.invalid) { this.stepError = 'Please fill in all required fields'; return; }
    }
    if (current === 1) {
      this.addressForm.markAllAsTouched();
      if (this.addressForm.invalid) { this.stepError = 'Please fill in all required fields'; return; }
    }
    if (current === 2) {
      if (!this.selectedIdentityDocType) { this.stepError = 'Select a document type'; return; }
      if (!this.identityFrontFile) { this.stepError = 'Upload the front of your identity document'; return; }
    }
    this.step = current + 1;
  }

  triggerFileInput(inputId: string): void {
    document.getElementById(inputId)?.click();
  }

  onFileSelected(event: Event, type: 'identityFront' | 'identityBack' | 'addressProof'): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) { this.stepError = 'File must be under 10 MB'; return; }

    const reader = new FileReader();
    reader.onload = (e) => {
      const result = e.target?.result as string;
      if (type === 'identityFront') { this.identityFrontFile = file; this.identityFrontPreview = result; }
      else if (type === 'identityBack') { this.identityBackFile = file; this.identityBackPreview = result; }
      else { this.addressFile = file; this.addressPreview = result; }
    };
    reader.readAsDataURL(file);
  }

  clearFile(type: string, event: Event): void {
    event.stopPropagation();
    if (type === 'identityFront') { this.identityFrontFile = null; this.identityFrontPreview = null; }
    else if (type === 'identityBack') { this.identityBackFile = null; this.identityBackPreview = null; }
    else { this.addressFile = null; this.addressPreview = null; }
  }

  async submit(): Promise<void> {
    this.stepError = '';
    if (!this.selectedAddressDocType) { this.stepError = 'Select an address document type'; return; }
    if (!this.addressFile) { this.stepError = 'Upload your address proof document'; return; }

    this.submitting = true;
    this.submitProgress = 0;

    try {
      // Step 1: Create customer
      const customerPayload = { ...this.identityForm.value, ...this.addressForm.value, createdSource: 'BACKEND' };
      const customerRes: any = await this.http.post<any>(`${environment.apiUrl}/payin/customer/create`, customerPayload).toPromise();

      if (!customerRes?.success) {
        this.stepError = customerRes?.message || 'Failed to create customer';
        this.submitting = false; return;
      }

      const customerId = customerRes.customerId;
      this.submitProgress = 1;

      // Step 2: Upload identity front
      const frontFd = new FormData();
      frontFd.append('file', this.identityFrontFile!);
      frontFd.append('docSide', 'IDENTITY_FRONT');
      frontFd.append('docCategory', this.selectedIdentityDocType!.value);
      if (this.identityDocNumber) frontFd.append('documentNumber', this.identityDocNumber);
      if (this.identityIssueDate) frontFd.append('issueDate', this.identityIssueDate);
      if (this.identityExpiryDate) frontFd.append('expiryDate', this.identityExpiryDate);

      await this.http.post<any>(`${environment.apiUrl}/payin/customer/${customerId}/document`, frontFd).toPromise();

      // Step 2b: Upload identity back if provided
      if (this.identityBackFile) {
        const backFd = new FormData();
        backFd.append('file', this.identityBackFile);
        backFd.append('docSide', 'IDENTITY_BACK');
        backFd.append('docCategory', this.selectedIdentityDocType!.value);
        await this.http.post<any>(`${environment.apiUrl}/payin/customer/${customerId}/document`, backFd).toPromise();
      }

      this.submitProgress = 2;

      // Step 3: Upload address proof
      const addrFd = new FormData();
      addrFd.append('file', this.addressFile!);
      addrFd.append('docSide', 'ADDRESS_PROOF');
      addrFd.append('docCategory', this.selectedAddressDocType!.value);

      await this.http.post<any>(`${environment.apiUrl}/payin/customer/${customerId}/document`, addrFd).toPromise();
      this.submitProgress = 3;

      this.submitting = false;
      const toast = await this.toastCtrl.create({
        message: `Customer created successfully — ID: ${customerId}`,
        duration: 5000, position: 'top', color: 'success',
        buttons: [{ icon: 'close-outline', role: 'cancel' }]
      });
      await toast.present();
      this.router.navigate(['/payin-partner/customers']);

    } catch (err: any) {
      this.submitting = false;
      this.submitProgress = 0;
      this.stepError = err?.error?.message || 'An error occurred. Please try again.';
    }
  }

  isInvalid(form: FormGroup, field: string): boolean {
    const c = form.get(field);
    return !!(c?.invalid && c?.touched);
  }
}
