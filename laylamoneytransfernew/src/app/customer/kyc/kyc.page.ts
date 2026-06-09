import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { LoadingController, ToastController } from '@ionic/angular';
import { environment } from '../../../environments/environment';
import { KycService } from '../../core/services/kyc.service';
// Code added by Naresh: Veriff module removed — VeriffService import deleted
import { AuthService } from '../../core/services/auth.service';
import { Subject, debounceTime, takeUntil, switchMap, of } from 'rxjs';
import { AddressService, AddressSuggestion } from '../../core/services/address.service';

interface DocumentType {
  id: string;
  name: string;
  code: string;
  sides: number;
  hasIdNumber: boolean;
  idNumberLabel?: string;
  idNumberFormat?: string;
  hasIssueDate: boolean;
  hasExpiry: boolean;
}

@Component({
  selector: 'app-kyc',
  templateUrl: './kyc.page.html',
  styleUrls: ['./kyc.page.scss']
})
export class KycPage implements OnInit, OnDestroy {
  personalForm!: FormGroup;
  addressForm!: FormGroup;
  fromRegistration = false;
  submitting = false;

  // KYC status tracking
  kycStatus: string = ''; // NOT_SUBMITTED, PENDING, VERIFIED, REJECTED
  existingDocuments: any[] = [];
  loadingStatus = true;
  uploadOnly = false; // true = show only document upload sections (no personal/address forms)
  private readonly MAX_FILE_BYTES = 10 * 1024 * 1024; // 10MB — must match backend multipart cap
  // Code added by Naresh: Veriff removed — flag kept as constant-false for backward compat of existing guards
  readonly veriffAvailable = false;

  // Identity document
  identityDocTypes: DocumentType[] = [];
  selectedIdentityDocType: DocumentType | null = null;
  identityFrontFile: File | null = null;
  identityFrontFileName = '';
  identityBackFile: File | null = null;
  identityBackFileName = '';
  identityDocNumber = '';
  identityIssueDate = '';
  identityExpiryDate = '';

  // Address document
  addressDocTypes: DocumentType[] = [];
  selectedAddressDocType: DocumentType | null = null;
  addressFile: File | null = null;
  addressFileName = '';

  occupationOptions = [
    'Employed',
    'Self-Employed',
    'Business Owner',
    'Student',
    'Retired',
    'Homemaker',
    'Other'
  ];

  genderOptions = [
    { value: 'MALE', label: 'Male' },
    { value: 'FEMALE', label: 'Female' },
    { value: 'OTHER', label: 'Other' }
  ];

  todayDate = '';
  maxDobDate = '';
  formSubmitAttempted = false;

  // Address autocomplete (UK users)
  addrSearchQuery = '';
  addrSuggestions: AddressSuggestion[] = [];
  addrLoading = false;
  addrShowList = false;
  private addrSearch$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  // Validation errors for non-form fields
  identityErrors: Record<string, string> = {};

  private readonly apiUrl = environment.apiUrl;

  get isUKUser(): boolean {
    return true;
  }

  // Map document display names to backend enum codes
  readonly docTypeNameToEnum: Record<string, string> = {
    'Passport': 'PASSPORT', 'Driving Licence': 'DRIVING_LICENCE', "Driver's Licence": 'DRIVING_LICENCE',
    "Driver's License": 'DRIVING_LICENCE', 'National ID': 'NATIONAL_ID', 'National ID Card': 'NATIONAL_ID',
    'Aadhaar Card': 'NATIONAL_ID', 'PAN Card': 'NATIONAL_ID', 'Voter ID': 'NATIONAL_ID',
    'BRP (Biometric Residence Permit)': 'NATIONAL_ID', 'Ghana Card': 'NATIONAL_ID',
    'Utility Bill': 'PROOF_OF_ADDRESS', 'Bank Statement': 'PROOF_OF_ADDRESS',
    'Council Tax Bill': 'PROOF_OF_ADDRESS', 'Lease/Rental Agreement': 'PROOF_OF_ADDRESS',
    'Utility Bill (within 3 months)': 'PROOF_OF_ADDRESS', 'Bank Statement (within 3 months)': 'PROOF_OF_ADDRESS',
    'Government Letter (within 3 months)': 'PROOF_OF_ADDRESS'
  };

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient,
    private loadingCtrl: LoadingController,
    private toastCtrl: ToastController,
    private kycService: KycService,
    private authService: AuthService,
    private addressService: AddressService
  ) {}

  ngOnInit(): void {
    this.fromRegistration = this.route.snapshot.queryParams['fromRegistration'] === 'true';

    const today = new Date().toISOString().split('T')[0];
    this.todayDate = today;
    // Max DOB = 18 years ago
    const maxDob = new Date();
    maxDob.setFullYear(maxDob.getFullYear() - 18);
    this.maxDobDate = maxDob.toISOString().split('T')[0];

    this.personalForm = this.fb.group({
      dateOfBirth: ['', [Validators.required, this.minAgeValidator(18), this.notFutureDateValidator]],
      gender: ['', Validators.required],
      nationality: ['', [Validators.required, Validators.minLength(2)]],
      occupation: ['', Validators.required]
    });

    this.addressForm = this.fb.group({
      addressLine1: ['', [Validators.required, Validators.minLength(3)]],
      addressLine2: [''],
      city: ['', [Validators.required, Validators.minLength(2)]],
      postcode: ['', Validators.required]
    });

    this.checkExistingKyc();
    this.loadDocumentTypes();
    this.setupAddrSearch();
  }

  private checkExistingKyc(): void {
    const user = this.authService.getCurrentUser();
    const userId = user?.sub || '';
    if (!userId) {
      this.loadingStatus = false;
      return;
    }

    this.kycService.getStatus(userId).subscribe({
      next: (status: any) => {
        // PARTIAL (legacy/imported, no real submission) is treated like NOT_SUBMITTED so the
        // customer is taken straight to the document-upload + address KYC flow after login.
        const os = (status?.overallStatus || 'NOT_SUBMITTED').toUpperCase();
        this.kycStatus = (os === 'PARTIAL') ? 'NOT_SUBMITTED' : os;
        if (this.kycStatus !== 'NOT_SUBMITTED') {
          this.loadExistingDocuments(userId);
        } else {
          this.loadingStatus = false;
        }
      },
      error: () => {
        this.kycStatus = 'NOT_SUBMITTED';
        this.loadingStatus = false;
      }
    });
  }

  private loadExistingDocuments(userId: string): void {
    this.kycService.getDocuments(userId).subscribe({
      next: (docs: any[]) => {
        this.existingDocuments = docs || [];
        this.loadingStatus = false;
      },
      error: () => {
        this.existingDocuments = [];
        this.loadingStatus = false;
      }
    });
  }

  getDocTypeLabel(type: string): string {
    return (type || '').replace(/_/g, ' ');
  }

  getStatusColor(status: string): string {
    switch ((status || '').toUpperCase()) {
      case 'PENDING': return 'warning';
      case 'APPROVED': case 'VERIFIED': return 'success';
      case 'REJECTED': return 'danger';
      default: return 'medium';
    }
  }

  getDocPreviewUrl(doc: any): string {
    const user = this.authService.getCurrentUser();
    return `${environment.apiUrl}/users/${user?.sub}/kyc/documents/${doc.id}/file`;
  }

  private loadDocumentTypes(): void {
    const user = this.authService.getCurrentUser();
    // Use 2-letter country code from JWT
    let country = user?.country || '';
    // If not a 2-3 letter code, try to get from countryOfResidence
    if (!country || country.length > 3) {
      country = user?.countryOfResidence || '';
    }
    // If still a full name, clear it - API will use fallback
    if (country.length > 3) {
      country = '';
    }

    // Normalize ISO-3 → ISO-2 (kyc_document_types table uses ISO-2 codes)
    const iso3ToIso2: Record<string, string> = {
      GBR: 'GB', IND: 'IN', PAK: 'PK', NGA: 'NG', GHA: 'GH', PHL: 'PH',
      KEN: 'KE', NPL: 'NP', BGD: 'BD', AUS: 'AU', USA: 'US', ARE: 'AE',
      DEU: 'DE', ZAF: 'ZA', UGA: 'UG', TZA: 'TZ', LKA: 'LK', EGY: 'EG',
      MAR: 'MA', SGP: 'SG', MYS: 'MY', CAN: 'CA', FRA: 'FR', ITA: 'IT', ESP: 'ES'
    };
    if (country.length === 3 && iso3ToIso2[country]) {
      country = iso3ToIso2[country];
    }

    // Map document names to backend enum values
    const nameToEnumMap: Record<string, string> = {
      'passport': 'PASSPORT',
      'driving licence': 'DRIVING_LICENCE',
      'driver\'s licence': 'DRIVING_LICENCE',
      'driver\'s license': 'DRIVING_LICENCE',
      'national id': 'NATIONAL_ID',
      'national id card': 'NATIONAL_ID',
      'nin (national id)': 'NATIONAL_ID',
      'nid (national id)': 'NATIONAL_ID',
      'philid (national id)': 'NATIONAL_ID',
      'smart id card': 'NATIONAL_ID',
      'ghana card': 'NATIONAL_ID',
      'cnic (national id)': 'NATIONAL_ID',
      'brp (biometric residence permit)': 'NATIONAL_ID',
      'aadhaar card': 'NATIONAL_ID',
      'pan card': 'NATIONAL_ID',
      'voter id': 'NATIONAL_ID',
      'voter\'s card': 'NATIONAL_ID',
      'state id': 'NATIONAL_ID',
      'green card': 'NATIONAL_ID',
      'medicare card': 'NATIONAL_ID',
      'utility bill': 'PROOF_OF_ADDRESS',
      'utility bill (within 3 months)': 'PROOF_OF_ADDRESS',
      'bank statement': 'PROOF_OF_ADDRESS',
      'bank statement (within 3 months)': 'PROOF_OF_ADDRESS',
      'council tax bill': 'PROOF_OF_ADDRESS',
      'lease/rental agreement': 'PROOF_OF_ADDRESS',
      'government letter (within 3 months)': 'PROOF_OF_ADDRESS',
      'credit card statement (within 3 months)': 'PROOF_OF_ADDRESS'
    };

    const resolveEnumCode = (docName: string, fallback: string): string => {
      return nameToEnumMap[(docName || '').toLowerCase()] || fallback;
    };

    // Load identity document types
    this.http.get<any>(`${this.apiUrl}/users/kyc/document-types`, {
      params: { country, category: 'IDENTITY' }
    }).subscribe({
      next: (res) => {
        const raw = res.data || res || [];
        this.identityDocTypes = (Array.isArray(raw) ? raw : []).map((d: any) => ({
          id: d.id?.toString() || '',
          name: d.documentName || d.name || '',
          code: resolveEnumCode(d.documentName || d.name || '', 'NATIONAL_ID'),
          sides: d.sides || 1,
          hasIdNumber: d.hasIdNumber || false,
          idNumberLabel: d.idNumberLabel || 'Document Number',
          idNumberFormat: d.idNumberFormat || '',
          hasIssueDate: d.hasIssueDate || false,
          hasExpiry: d.hasExpiry || false
        }));
      },
      error: () => {
        this.identityDocTypes = [
          { id: '1', name: 'Passport', code: 'PASSPORT', sides: 1, hasIdNumber: true, idNumberLabel: 'Passport Number', idNumberFormat: 'e.g., AB1234567', hasIssueDate: true, hasExpiry: true },
          { id: '2', name: 'National ID Card', code: 'NATIONAL_ID', sides: 1, hasIdNumber: true, idNumberLabel: 'ID Number', idNumberFormat: 'e.g., 1234567890', hasIssueDate: true, hasExpiry: true },
          { id: '3', name: 'Driving Licence', code: 'DRIVING_LICENCE', sides: 2, hasIdNumber: true, idNumberLabel: 'Licence Number', idNumberFormat: 'e.g., SMITH901010AB1CD', hasIssueDate: true, hasExpiry: true }
        ];
      }
    });

    // Load address document types
    this.http.get<any>(`${this.apiUrl}/users/kyc/document-types`, {
      params: { category: 'ADDRESS' }
    }).subscribe({
      next: (res) => {
        const raw = res.data || res || [];
        this.addressDocTypes = (Array.isArray(raw) ? raw : []).map((d: any) => ({
          id: d.id?.toString() || '',
          name: d.documentName || d.name || '',
          code: resolveEnumCode(d.documentName || d.name || '', 'PROOF_OF_ADDRESS'),
          sides: d.sides || 1,
          hasIdNumber: d.hasIdNumber || false,
          idNumberLabel: d.idNumberLabel || '',
          idNumberFormat: d.idNumberFormat || '',
          hasIssueDate: d.hasIssueDate || false,
          hasExpiry: d.hasExpiry || false
        }));
      },
      error: () => {
        this.addressDocTypes = [
          { id: '10', name: 'Utility Bill', code: 'PROOF_OF_ADDRESS', sides: 1, hasIdNumber: false, hasIssueDate: false, hasExpiry: false },
          { id: '11', name: 'Bank Statement', code: 'PROOF_OF_ADDRESS', sides: 1, hasIdNumber: false, hasIssueDate: false, hasExpiry: false },
          { id: '12', name: 'Council Tax Bill', code: 'PROOF_OF_ADDRESS', sides: 1, hasIdNumber: false, hasIssueDate: false, hasExpiry: false }
        ];
      }
    });
  }

  onIdentityDocTypeChange(event: any): void {
    const id = event.target.value;
    this.selectedIdentityDocType = this.identityDocTypes.find(d => d.id === id) || null;
    // Reset files when doc type changes
    this.identityFrontFile = null;
    this.identityFrontFileName = '';
    this.identityBackFile = null;
    this.identityBackFileName = '';
    this.identityDocNumber = '';
    this.identityIssueDate = '';
    this.identityExpiryDate = '';
  }

  onAddressDocTypeChange(event: any): void {
    const id = event.target.value;
    this.selectedAddressDocType = this.addressDocTypes.find(d => d.id === id) || null;
    this.addressFile = null;
    this.addressFileName = '';
  }

  onIdentityFrontSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      this.identityFrontFile = file;
      this.identityFrontFileName = file.name;
    }
  }

  onIdentityBackSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      this.identityBackFile = file;
      this.identityBackFileName = file.name;
    }
  }

  onAddressFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      this.addressFile = file;
      this.addressFileName = file.name;
    }
  }

  private minAgeValidator(minAge: number) {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const dob = new Date(control.value);
      const today = new Date();
      let age = today.getFullYear() - dob.getFullYear();
      const monthDiff = today.getMonth() - dob.getMonth();
      if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
        age--;
      }
      return age < minAge ? { minAge: { required: minAge, actual: age } } : null;
    };
  }

  private notFutureDateValidator(control: AbstractControl): ValidationErrors | null {
    if (!control.value) return null;
    const date = new Date(control.value);
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    return date > today ? { futureDate: true } : null;
  }

  validateIdentityDates(): void {
    this.identityErrors = {};
    if (this.selectedIdentityDocType?.hasIssueDate && this.identityIssueDate) {
      const issue = new Date(this.identityIssueDate);
      const today = new Date();
      if (issue > today) {
        this.identityErrors['issueDate'] = 'Issue date cannot be in the future';
      }
    }
    if (this.selectedIdentityDocType?.hasExpiry && this.identityExpiryDate) {
      const expiry = new Date(this.identityExpiryDate);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (expiry < today) {
        this.identityErrors['expiryDate'] = 'Document has expired. Please use a valid document';
      }
    }
  }

  get isDocUploadValid(): boolean {
    if (this.uploadOnly) {
      const identitySelected = !!this.selectedIdentityDocType;
      const hasIdentityFile = this.veriffAvailable || !!this.identityFrontFile;
      const hasIdentity = identitySelected && hasIdentityFile;
      const hasAddress = this.selectedAddressDocType && this.addressFile;
      if (!hasIdentity && !hasAddress) return false;
      if (hasIdentity && identitySelected) {
        if (!this.veriffAvailable && this.selectedIdentityDocType!.sides === 2 && !this.identityBackFile) return false;
        if (this.selectedIdentityDocType!.hasIdNumber && !this.identityDocNumber.trim()) return false;
        if (Object.keys(this.identityErrors).length > 0) return false;
      }
      return true;
    }
    return this.isFormValid;
  }

  cancelUpload(): void {
    this.uploadOnly = false;
    this.checkExistingKyc();
  }

  async onUploadDocumentsOnly(): Promise<void> {
    this.formSubmitAttempted = true;
    this.validateIdentityDates();

    if (!this.isDocUploadValid) {
      this.showToast('Please fill in at least one document section', 'warning');
      return;
    }

    this.submitting = true;
    const user = this.authService.getCurrentUser();
    if (!user) { this.submitting = false; return; }

    let uploaded = 0;
    let failed = 0;

    // Upload identity document if filled (front, then back for 2-sided docs)
    if (this.selectedIdentityDocType && this.identityFrontFile) {
      const docTypeEnum = this.docTypeNameToEnum[this.selectedIdentityDocType.name] || 'NATIONAL_ID';
      try {
        await this.kycService.uploadDocument(
          user.sub, docTypeEnum, this.identityFrontFile,
          this.identityDocNumber || undefined,
          this.identityIssueDate || undefined,
          this.identityExpiryDate || undefined
        ).toPromise();
        uploaded++;
      } catch { failed++; }

      // Back side for 2-sided documents (e.g. driving licence) — was previously missing here.
      if (this.selectedIdentityDocType.sides === 2 && this.identityBackFile) {
        try {
          await this.kycService.uploadDocument(
            user.sub, docTypeEnum, this.identityBackFile,
            this.identityDocNumber || undefined,
            this.identityIssueDate || undefined,
            this.identityExpiryDate || undefined
          ).toPromise();
          uploaded++;
        } catch { failed++; }
      }
    }

    // Upload address proof if filled
    if (this.selectedAddressDocType && this.addressFile) {
      try {
        const docTypeEnum = this.docTypeNameToEnum[this.selectedAddressDocType.name] || 'PROOF_OF_ADDRESS';
        await this.kycService.uploadDocument(user.sub, docTypeEnum, this.addressFile).toPromise();
        uploaded++;
      } catch { failed++; }
    }

    this.submitting = false;

    if (uploaded > 0) {
      this.showToast(`${uploaded} document(s) uploaded successfully. They will be reviewed shortly.`, 'success');
      this.uploadOnly = false;
      this.checkExistingKyc();
    } else {
      this.showToast('Failed to upload documents. Please try again.', 'danger');
    }
  }

  get isFormValid(): boolean {
    if (this.personalForm.invalid) return false;
    if (this.addressForm.invalid) return false;
    if (!this.selectedIdentityDocType) return false;
    // Identity images only required when Veriff is not available
    if (!this.veriffAvailable) {
      if (!this.identityFrontFile) return false;
      if (this.selectedIdentityDocType.sides === 2 && !this.identityBackFile) return false;
    }
    if (this.selectedIdentityDocType.hasIdNumber && !this.identityDocNumber.trim()) return false;
    if (Object.keys(this.identityErrors).length > 0) return false;
    if (!this.selectedAddressDocType || !this.addressFile) return false;
    return true;
  }

  /** Label of the first selected file that exceeds the 10MB cap, or null if all are OK. */
  private firstOversizedFile(): string | null {
    const tooBig = (f: File | null) => !!f && f.size > this.MAX_FILE_BYTES;
    if (tooBig(this.identityFrontFile)) return 'Your identity document (front)';
    if (tooBig(this.identityBackFile)) return 'Your identity document (back)';
    if (tooBig(this.addressFile)) return 'Your proof of address';
    return null;
  }

  async onSubmitKyc(): Promise<void> {
    this.formSubmitAttempted = true;
    this.validateIdentityDates();

    // Mark all personal and address form fields touched
    Object.keys(this.personalForm.controls).forEach(key => {
      this.personalForm.get(key)?.markAsTouched();
    });
    Object.keys(this.addressForm.controls).forEach(key => {
      this.addressForm.get(key)?.markAsTouched();
    });

    if (!this.isFormValid) {
      this.showToast('Please fill in all required fields correctly', 'warning');
      return;
    }

    // Reject oversized files BEFORE uploading anything — an over-cap file is the most common
    // reason an upload fails halfway and leaves a partial submission.
    const oversized = this.firstOversizedFile();
    if (oversized) {
      this.showToast(`${oversized} is too large. Each document must be 10MB or less.`, 'warning');
      return;
    }

    const loading = await this.loadingCtrl.create({
      message: 'Submitting your KYC documents...',
      cssClass: 'fb-loading'
    });
    await loading.present();

    const user = this.authService.getCurrentUser();
    const userId = user?.sub || '';

    try {
      // Step 1: Update user profile with personal details
      if (this.personalForm.valid) {
        const formVal = this.personalForm.value;
        const addrVal = this.addressForm.value;
        const profilePayload: any = {
          dateOfBirth: formVal.dateOfBirth,
          gender: formVal.gender,
          nationality: formVal.nationality,
          occupation: formVal.occupation,
          addressLine1: addrVal.addressLine1,
          addressLine2: addrVal.addressLine2 || null,
          city: addrVal.city,
          postcode: addrVal.postcode
        };
        await this.http.put(`${this.apiUrl}/users/${userId}`, profilePayload).toPromise();
      }

      // Steps 2 & 3: upload documents with ROLLBACK. Documents are uploaded as separate
      // requests, so if a later one fails (e.g. proof-of-address) we delete the ones already
      // saved — the customer is never left half-submitted (identity saved, address missing).
      const uploadedDocIds: (string | number)[] = [];
      try {
        // Identity images only when Veriff is not available (Veriff captures them itself).
        if (!this.veriffAvailable && this.selectedIdentityDocType && this.identityFrontFile) {
          const front = await this.kycService.uploadDocument(
            userId,
            this.selectedIdentityDocType.code,
            this.identityFrontFile,
            this.identityDocNumber,
            this.identityIssueDate,
            this.identityExpiryDate
          ).toPromise();
          if (front?.id) uploadedDocIds.push(front.id);

          if (this.selectedIdentityDocType.sides === 2 && this.identityBackFile) {
            const back = await this.kycService.uploadDocument(
              userId,
              this.selectedIdentityDocType.code,
              this.identityBackFile,
              this.identityDocNumber,
              this.identityIssueDate,
              this.identityExpiryDate
            ).toPromise();
            if (back?.id) uploadedDocIds.push(back.id);
          }
        }

        // Proof of address.
        if (this.addressFile && this.selectedAddressDocType) {
          const addr = await this.kycService.uploadDocument(
            userId,
            this.selectedAddressDocType.code,
            this.addressFile,
            undefined,
            undefined,
            undefined
          ).toPromise();
          if (addr?.id) uploadedDocIds.push(addr.id);
        }
      } catch (uploadErr) {
        // Roll back every document saved during this submission so nothing partial remains.
        for (const id of uploadedDocIds) {
          try { await this.kycService.deleteDocument(userId, id).toPromise(); } catch { /* best-effort */ }
        }
        throw uploadErr; // handled by the outer catch: shows an error and stays on the page (no navigation)
      }

      // Code added by Naresh: Veriff module removed — KYC flow is document-upload only.
      loading.dismiss();
      this.submitting = false;
      this.showToast('KYC documents submitted successfully! Our team will review them shortly.', 'success');
      this.router.navigate(['/home']);

    } catch (error: any) {
      loading.dismiss();
      this.submitting = false;
      this.showToast(error.error?.message || 'Failed to submit KYC. Please try again.', 'danger');
    }
  }

  showUploadForm(): void {
    this.uploadOnly = true;
    this.kycStatus = 'NOT_SUBMITTED';
    this.formSubmitAttempted = false;
    this.identityFrontFile = null;
    this.identityFrontFileName = '';
    this.identityBackFile = null;
    this.identityBackFileName = '';
    this.addressFile = null;
    this.addressFileName = '';
    this.identityDocNumber = '';
    this.identityIssueDate = '';
    this.identityExpiryDate = '';
    this.selectedIdentityDocType = null;
    this.selectedAddressDocType = null;
  }

  onSkip(): void {
    this.router.navigate(['/home/dashboard']);
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private setupAddrSearch(): void {
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
      error: () => {
        this.addrLoading = false;
        this.addrSuggestions = [];
        this.addrShowList = false;
      }
    });
  }

  onAddrInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.addrSearchQuery = value;
    this.addrSearch$.next(value);
  }

  pickAddr(suggestion: AddressSuggestion): void {
    // Multi-entry: expand the container to show individual units
    if (suggestion.entries > 1) {
      this.addrLoading = true;
      this.addrShowList = false;
      this.addressService.lookup(suggestion.addressText, 'GB', suggestion.addressId).subscribe({
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
    this.addrSearchQuery = suggestion.addressText;
    this.addrLoading = true;
    this.addressService.retrieve(suggestion.addressId, 'GB').subscribe({
      next: (detail) => {
        this.addrLoading = false;
        this.addressForm.patchValue({
          addressLine1: detail.street || suggestion.addressText,
          addressLine2: detail.address2 || '',
          city: detail.city || '',
          postcode: detail.postcode || ''
        });
      },
      error: () => {
        this.addrLoading = false;
        this.addressForm.patchValue({ addressLine1: suggestion.addressText });
      }
    });
  }

  clearAddr(): void {
    this.addrSearchQuery = '';
    this.addrSuggestions = [];
    this.addrShowList = false;
    this.addressForm.patchValue({ addressLine1: '', addressLine2: '', city: '', postcode: '' });
  }
}
