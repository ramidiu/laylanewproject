import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AlertController, ToastController } from '@ionic/angular';
import { Subject, debounceTime, takeUntil, switchMap, of } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import { KycService } from '../../core/services/kyc.service';
import { NotificationService } from '../../core/services/notification.service';
import { AddressService, AddressSuggestion } from '../../core/services/address.service';
import { NotificationPreferences } from '../../core/models/notification.model';
import { UserResponse, KycTier } from '../../core/models/user.model';
import { KycStatusResponse } from '../../core/models/kyc.model';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.page.html',
  styleUrls: ['./profile.page.scss']
})
export class ProfilePage implements OnInit, OnDestroy {
  user: UserResponse | null = null;
  kycStatus: KycStatusResponse | null = null;
  loading = true;
  showEditModal = false;
  editForm!: FormGroup;
  saving = false;

  showNotificationPrefs = false;
  notifPrefs: NotificationPreferences = {
    rateAlerts: true,
    promotional: true,
    transactionUpdates: true,
    securityAlerts: true,
    kycUpdates: true,
    complianceAlerts: true,
    systemNotifications: true,
    emailEnabled: true
  };
  savingNotifPrefs = false;

  // Address autocomplete
  addrSearchQuery = '';
  addrSuggestions: AddressSuggestion[] = [];
  addrLoading = false;
  addrShowList = false;
  private addrSearch$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  showPasswordModal = false;
  passwordForm!: FormGroup;
  savingPassword = false;
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;
  passwordStrength = 0;
  passwordStrengthLabel = '';

  private countryMap: Record<string, { name: string; flag: string }> = {
    'GB': { name: 'United Kingdom', flag: '\u{1F1EC}\u{1F1E7}' },
    'US': { name: 'United States', flag: '\u{1F1FA}\u{1F1F8}' },
    'AU': { name: 'Australia', flag: '\u{1F1E6}\u{1F1FA}' },
    'AE': { name: 'UAE', flag: '\u{1F1E6}\u{1F1EA}' },
    'DE': { name: 'Germany', flag: '\u{1F1E9}\u{1F1EA}' },
    'FR': { name: 'France', flag: '\u{1F1EB}\u{1F1F7}' },
    'IN': { name: 'India', flag: '\u{1F1EE}\u{1F1F3}' },
    'NG': { name: 'Nigeria', flag: '\u{1F1F3}\u{1F1EC}' },
    'PK': { name: 'Pakistan', flag: '\u{1F1F5}\u{1F1F0}' },
    'PH': { name: 'Philippines', flag: '\u{1F1F5}\u{1F1ED}' },
    'KE': { name: 'Kenya', flag: '\u{1F1F0}\u{1F1EA}' },
    'GH': { name: 'Ghana', flag: '\u{1F1EC}\u{1F1ED}' },
    'ZA': { name: 'South Africa', flag: '\u{1F1FF}\u{1F1E6}' },
    'BD': { name: 'Bangladesh', flag: '\u{1F1E7}\u{1F1E9}' },
    'LK': { name: 'Sri Lanka', flag: '\u{1F1F1}\u{1F1F0}' },
    'NP': { name: 'Nepal', flag: '\u{1F1F3}\u{1F1F5}' },
    'CA': { name: 'Canada', flag: '\u{1F1E8}\u{1F1E6}' },
    'SG': { name: 'Singapore', flag: '\u{1F1F8}\u{1F1EC}' },
    'MY': { name: 'Malaysia', flag: '\u{1F1F2}\u{1F1FE}' },
    'JP': { name: 'Japan', flag: '\u{1F1EF}\u{1F1F5}' },
    'SA': { name: 'Saudi Arabia', flag: '\u{1F1F8}\u{1F1E6}' },
    'QA': { name: 'Qatar', flag: '\u{1F1F6}\u{1F1E6}' },
    'KW': { name: 'Kuwait', flag: '\u{1F1F0}\u{1F1FC}' },
  };

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private userService: UserService,
    private kycService: KycService,
    private notificationService: NotificationService,
    private addressService: AddressService,
    public router: Router,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {
    this.editForm = this.fb.group({
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required, Validators.minLength(2)]],
      phone: ['', [Validators.required]],
      addressLine1: [''],
      addressLine2: [''],
      city: [''],
      postcode: ['']
    });

    this.passwordForm = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.loadProfile();
    this.loadNotifPrefs();
    this.setupAddrSearch();

    // Forced first-login password change (default-password accounts): auto-open the
    // change-password modal and tell the user they must change it.
    if (sessionStorage.getItem('fb_force_change_password') === '1') {
      setTimeout(() => {
        this.openChangePassword();
        this.showToast('You are using a default password. Please set a new password to continue.', 'warning');
      }, 400);
    }
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
    this.addrShowList = false;
    this.addrSearchQuery = suggestion.addressText;
    this.addrLoading = true;
    this.addressService.retrieve(suggestion.addressId, 'GB').subscribe({
      next: (detail) => {
        this.addrLoading = false;
        this.editForm.patchValue({
          addressLine1: detail.street || suggestion.addressText,
          addressLine2: detail.address2 || '',
          city: detail.city || '',
          postcode: detail.postcode || ''
        });
      },
      error: () => {
        this.addrLoading = false;
        this.editForm.patchValue({ addressLine1: suggestion.addressText });
      }
    });
  }

  clearAddr(): void {
    this.addrSearchQuery = '';
    this.addrSuggestions = [];
    this.addrShowList = false;
    this.editForm.patchValue({ addressLine1: '', addressLine2: '', city: '', postcode: '' });
  }

  loadProfile(): void {
    this.loading = true;
    this.userService.getProfile().subscribe({
      next: (user) => {
        this.user = user;
        if (user?.uuid) {
          this.loadKycStatus(user.uuid);
        }
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  loadKycStatus(userId: string): void {
    this.kycService.getStatus(userId).subscribe({
      next: (status) => this.kycStatus = status,
      error: () => {}
    });
  }

  get initials(): string {
    if (!this.user) return 'FB';
    return (this.user.firstName[0] + this.user.lastName[0]).toUpperCase();
  }

  get countryName(): string {
    if (!this.user) return '';
    const code = this.user.countryCode || '';
    const info = this.countryMap[code];
    return info ? info.name : (this.user.country || code);
  }

  get countryFlag(): string {
    if (!this.user) return '';
    const code = this.user.countryCode || '';
    const info = this.countryMap[code];
    return info ? info.flag : '';
  }

  get kycTierLabel(): string {
    if (!this.user) return '';
    const labels: Record<string, string> = {
      NONE: 'Not Verified',
      TIER_1: 'Basic',
      TIER_2: 'Standard',
      TIER_3: 'Premium'
    };
    return labels[this.user.kycTier] || this.user.kycTier;
  }

  get kycTierColor(): string {
    if (!this.user) return 'medium';
    const colors: Record<string, string> = {
      NONE: 'medium',
      TIER_1: 'warning',
      TIER_2: 'secondary',
      TIER_3: 'success'
    };
    return colors[this.user.kycTier] || 'medium';
  }

  openEdit(): void {
    if (!this.user) return;
    this.addrSearchQuery = '';
    this.addrSuggestions = [];
    this.addrShowList = false;
    this.editForm.patchValue({
      firstName: this.user.firstName || '',
      lastName: this.user.lastName || '',
      phone: this.user.phone || '',
      addressLine1: this.user.addressLine1 || '',
      addressLine2: this.user.addressLine2 || '',
      city: this.user.city || '',
      postcode: this.user.postcode || ''
    });
    this.showEditModal = true;
  }

  closeEdit(): void {
    this.showEditModal = false;
  }

  async onSaveProfile(): Promise<void> {
    if (this.editForm.invalid || this.saving) return;
    this.saving = true;

    const val = this.editForm.value;
    const request: any = {
      firstName: val.firstName,
      lastName: val.lastName,
      phone: val.phone,
      addressLine1: val.addressLine1 || '',
      addressLine2: val.addressLine2 || '',
      city: val.city || '',
      postcode: val.postcode || ''
    };

    this.userService.updateProfile(request).subscribe({
      next: () => {
        this.saving = false;
        this.showEditModal = false;
        this.showToast('Profile saved. Your account is now pending admin review before you can send money.', 'warning');
        this.loadProfile();
      },
      error: (err) => {
        this.saving = false;
        this.showToast(err.error?.message || 'Failed to update profile', 'danger');
      }
    });
  }

  // Document management is now handled by the dedicated KYC page (/home/kyc)

  // --- Notification Preferences ---

  loadNotifPrefs(): void {
    this.notificationService.getPreferences().subscribe({
      next: (p) => {
        this.notifPrefs = {
          userId: p.userId,
          rateAlerts: p.rateAlerts ?? true,
          promotional: p.promotional ?? true,
          transactionUpdates: p.transactionUpdates ?? true,
          securityAlerts: p.securityAlerts ?? true,
          kycUpdates: p.kycUpdates ?? true,
          complianceAlerts: p.complianceAlerts ?? true,
          systemNotifications: p.systemNotifications ?? true,
          emailEnabled: p.emailEnabled ?? true
        };
      },
      error: () => {
        // keep the defaults already set in the class initializer
      }
    });
  }

  saveNotifPrefs(): void {
    if (this.savingNotifPrefs) return;
    this.savingNotifPrefs = true;
    this.notificationService.updatePreferences(this.notifPrefs).subscribe({
      next: () => {
        this.savingNotifPrefs = false;
        this.showToast('Notification preferences saved', 'success');
      },
      error: (err) => {
        this.savingNotifPrefs = false;
        this.showToast(err?.error?.message || 'Failed to save preferences', 'danger');
      }
    });
  }

  // --- Change Password ---

  openChangePassword(): void {
    this.passwordForm.reset();
    this.showCurrentPassword = false;
    this.showNewPassword = false;
    this.showConfirmPassword = false;
    this.passwordStrength = 0;
    this.passwordStrengthLabel = '';
    this.showPasswordModal = true;
  }

  closeChangePassword(): void {
    this.showPasswordModal = false;
  }

  onNewPasswordInput(): void {
    const pw = this.passwordForm.get('newPassword')?.value || '';
    let score = 0;
    if (pw.length >= 8) score++;
    if (/[a-z]/.test(pw)) score++;
    if (/[A-Z]/.test(pw)) score++;
    if (/\d/.test(pw)) score++;
    if (/[@$!%*?&#+\-_]/.test(pw)) score++;
    this.passwordStrength = score;
    const labels: Record<number, string> = { 0: '', 1: 'Very Weak', 2: 'Weak', 3: 'Fair', 4: 'Strong', 5: 'Very Strong' };
    this.passwordStrengthLabel = labels[score] || '';
  }

  get passwordsMatch(): boolean {
    return this.passwordForm.get('newPassword')?.value === this.passwordForm.get('confirmPassword')?.value;
  }

  async onChangePassword(): Promise<void> {
    if (this.passwordForm.invalid || this.savingPassword || !this.passwordsMatch) return;

    this.savingPassword = true;
    const { currentPassword, newPassword } = this.passwordForm.value;

    this.authService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.savingPassword = false;
        this.showPasswordModal = false;
        sessionStorage.removeItem('fb_force_change_password');   // requirement satisfied
        this.showToast('Password changed successfully', 'success');
      },
      error: (err) => {
        this.savingPassword = false;
        this.showToast(err.error?.message || 'Failed to change password', 'danger');
      }
    });
  }

  async onLogout(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Sign Out',
      message: 'Are you sure you want to sign out?',
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Sign Out',
          handler: () => this.authService.logout()
        }
      ]
    });
    await alert.present();
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
