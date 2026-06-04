import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ToastController } from '@ionic/angular';
import { AuthService } from '../../core/services/auth.service';

interface CountryOption {
  code: string;
  name: string;
  flag: string;
  dialCode: string;
}

@Component({
  selector: 'app-demo-access',
  templateUrl: './demo-access.page.html',
  styleUrls: ['./demo-access.page.scss']
})
export class DemoAccessPage {
  form: FormGroup;
  submitting = false;
  success = false;
  successEmail = '';
  successRole = '';
  expiresAt = '';

  countries: CountryOption[] = [
    { code: 'GB', name: 'United Kingdom', flag: '\u{1F1EC}\u{1F1E7}', dialCode: '+44' },
    { code: 'US', name: 'United States', flag: '\u{1F1FA}\u{1F1F8}', dialCode: '+1' },
    { code: 'IN', name: 'India', flag: '\u{1F1EE}\u{1F1F3}', dialCode: '+91' },
    { code: 'AE', name: 'United Arab Emirates', flag: '\u{1F1E6}\u{1F1EA}', dialCode: '+971' },
    { code: 'AU', name: 'Australia', flag: '\u{1F1E6}\u{1F1FA}', dialCode: '+61' },
    { code: 'CA', name: 'Canada', flag: '\u{1F1E8}\u{1F1E6}', dialCode: '+1' },
    { code: 'DE', name: 'Germany', flag: '\u{1F1E9}\u{1F1EA}', dialCode: '+49' },
    { code: 'FR', name: 'France', flag: '\u{1F1EB}\u{1F1F7}', dialCode: '+33' },
    { code: 'NG', name: 'Nigeria', flag: '\u{1F1F3}\u{1F1EC}', dialCode: '+234' },
    { code: 'KE', name: 'Kenya', flag: '\u{1F1F0}\u{1F1EA}', dialCode: '+254' },
    { code: 'GH', name: 'Ghana', flag: '\u{1F1EC}\u{1F1ED}', dialCode: '+233' },
    { code: 'ZA', name: 'South Africa', flag: '\u{1F1FF}\u{1F1E6}', dialCode: '+27' },
    { code: 'PK', name: 'Pakistan', flag: '\u{1F1F5}\u{1F1F0}', dialCode: '+92' },
    { code: 'BD', name: 'Bangladesh', flag: '\u{1F1E7}\u{1F1E9}', dialCode: '+880' },
    { code: 'PH', name: 'Philippines', flag: '\u{1F1F5}\u{1F1ED}', dialCode: '+63' },
    { code: 'AF', name: 'Afghanistan', flag: '\u{1F1E6}\u{1F1EB}', dialCode: '+93' },
    { code: 'AL', name: 'Albania', flag: '\u{1F1E6}\u{1F1F1}', dialCode: '+355' },
    { code: 'DZ', name: 'Algeria', flag: '\u{1F1E9}\u{1F1FF}', dialCode: '+213' },
    { code: 'AR', name: 'Argentina', flag: '\u{1F1E6}\u{1F1F7}', dialCode: '+54' },
    { code: 'AT', name: 'Austria', flag: '\u{1F1E6}\u{1F1F9}', dialCode: '+43' },
    { code: 'BH', name: 'Bahrain', flag: '\u{1F1E7}\u{1F1ED}', dialCode: '+973' },
    { code: 'BE', name: 'Belgium', flag: '\u{1F1E7}\u{1F1EA}', dialCode: '+32' },
    { code: 'BR', name: 'Brazil', flag: '\u{1F1E7}\u{1F1F7}', dialCode: '+55' },
    { code: 'BG', name: 'Bulgaria', flag: '\u{1F1E7}\u{1F1EC}', dialCode: '+359' },
    { code: 'KH', name: 'Cambodia', flag: '\u{1F1F0}\u{1F1ED}', dialCode: '+855' },
    { code: 'CM', name: 'Cameroon', flag: '\u{1F1E8}\u{1F1F2}', dialCode: '+237' },
    { code: 'CL', name: 'Chile', flag: '\u{1F1E8}\u{1F1F1}', dialCode: '+56' },
    { code: 'CN', name: 'China', flag: '\u{1F1E8}\u{1F1F3}', dialCode: '+86' },
    { code: 'CO', name: 'Colombia', flag: '\u{1F1E8}\u{1F1F4}', dialCode: '+57' },
    { code: 'HR', name: 'Croatia', flag: '\u{1F1ED}\u{1F1F7}', dialCode: '+385' },
    { code: 'CY', name: 'Cyprus', flag: '\u{1F1E8}\u{1F1FE}', dialCode: '+357' },
    { code: 'CZ', name: 'Czech Republic', flag: '\u{1F1E8}\u{1F1FF}', dialCode: '+420' },
    { code: 'DK', name: 'Denmark', flag: '\u{1F1E9}\u{1F1F0}', dialCode: '+45' },
    { code: 'EG', name: 'Egypt', flag: '\u{1F1EA}\u{1F1EC}', dialCode: '+20' },
    { code: 'ET', name: 'Ethiopia', flag: '\u{1F1EA}\u{1F1F9}', dialCode: '+251' },
    { code: 'FI', name: 'Finland', flag: '\u{1F1EB}\u{1F1EE}', dialCode: '+358' },
    { code: 'GR', name: 'Greece', flag: '\u{1F1EC}\u{1F1F7}', dialCode: '+30' },
    { code: 'HK', name: 'Hong Kong', flag: '\u{1F1ED}\u{1F1F0}', dialCode: '+852' },
    { code: 'HU', name: 'Hungary', flag: '\u{1F1ED}\u{1F1FA}', dialCode: '+36' },
    { code: 'ID', name: 'Indonesia', flag: '\u{1F1EE}\u{1F1E9}', dialCode: '+62' },
    { code: 'IE', name: 'Ireland', flag: '\u{1F1EE}\u{1F1EA}', dialCode: '+353' },
    { code: 'IL', name: 'Israel', flag: '\u{1F1EE}\u{1F1F1}', dialCode: '+972' },
    { code: 'IT', name: 'Italy', flag: '\u{1F1EE}\u{1F1F9}', dialCode: '+39' },
    { code: 'JM', name: 'Jamaica', flag: '\u{1F1EF}\u{1F1F2}', dialCode: '+1876' },
    { code: 'JP', name: 'Japan', flag: '\u{1F1EF}\u{1F1F5}', dialCode: '+81' },
    { code: 'JO', name: 'Jordan', flag: '\u{1F1EF}\u{1F1F4}', dialCode: '+962' },
    { code: 'KW', name: 'Kuwait', flag: '\u{1F1F0}\u{1F1FC}', dialCode: '+965' },
    { code: 'LB', name: 'Lebanon', flag: '\u{1F1F1}\u{1F1E7}', dialCode: '+961' },
    { code: 'MY', name: 'Malaysia', flag: '\u{1F1F2}\u{1F1FE}', dialCode: '+60' },
    { code: 'MX', name: 'Mexico', flag: '\u{1F1F2}\u{1F1FD}', dialCode: '+52' },
    { code: 'MA', name: 'Morocco', flag: '\u{1F1F2}\u{1F1E6}', dialCode: '+212' },
    { code: 'MZ', name: 'Mozambique', flag: '\u{1F1F2}\u{1F1FF}', dialCode: '+258' },
    { code: 'NP', name: 'Nepal', flag: '\u{1F1F3}\u{1F1F5}', dialCode: '+977' },
    { code: 'NL', name: 'Netherlands', flag: '\u{1F1F3}\u{1F1F1}', dialCode: '+31' },
    { code: 'NZ', name: 'New Zealand', flag: '\u{1F1F3}\u{1F1FF}', dialCode: '+64' },
    { code: 'NO', name: 'Norway', flag: '\u{1F1F3}\u{1F1F4}', dialCode: '+47' },
    { code: 'OM', name: 'Oman', flag: '\u{1F1F4}\u{1F1F2}', dialCode: '+968' },
    { code: 'PE', name: 'Peru', flag: '\u{1F1F5}\u{1F1EA}', dialCode: '+51' },
    { code: 'PL', name: 'Poland', flag: '\u{1F1F5}\u{1F1F1}', dialCode: '+48' },
    { code: 'PT', name: 'Portugal', flag: '\u{1F1F5}\u{1F1F9}', dialCode: '+351' },
    { code: 'QA', name: 'Qatar', flag: '\u{1F1F6}\u{1F1E6}', dialCode: '+974' },
    { code: 'RO', name: 'Romania', flag: '\u{1F1F7}\u{1F1F4}', dialCode: '+40' },
    { code: 'RW', name: 'Rwanda', flag: '\u{1F1F7}\u{1F1FC}', dialCode: '+250' },
    { code: 'SA', name: 'Saudi Arabia', flag: '\u{1F1F8}\u{1F1E6}', dialCode: '+966' },
    { code: 'SN', name: 'Senegal', flag: '\u{1F1F8}\u{1F1F3}', dialCode: '+221' },
    { code: 'SG', name: 'Singapore', flag: '\u{1F1F8}\u{1F1EC}', dialCode: '+65' },
    { code: 'LK', name: 'Sri Lanka', flag: '\u{1F1F1}\u{1F1F0}', dialCode: '+94' },
    { code: 'SE', name: 'Sweden', flag: '\u{1F1F8}\u{1F1EA}', dialCode: '+46' },
    { code: 'CH', name: 'Switzerland', flag: '\u{1F1E8}\u{1F1ED}', dialCode: '+41' },
    { code: 'TZ', name: 'Tanzania', flag: '\u{1F1F9}\u{1F1FF}', dialCode: '+255' },
    { code: 'TH', name: 'Thailand', flag: '\u{1F1F9}\u{1F1ED}', dialCode: '+66' },
    { code: 'TR', name: 'Turkey', flag: '\u{1F1F9}\u{1F1F7}', dialCode: '+90' },
    { code: 'UG', name: 'Uganda', flag: '\u{1F1FA}\u{1F1EC}', dialCode: '+256' },
    { code: 'VN', name: 'Vietnam', flag: '\u{1F1FB}\u{1F1F3}', dialCode: '+84' },
    { code: 'ZM', name: 'Zambia', flag: '\u{1F1FF}\u{1F1F2}', dialCode: '+260' },
    { code: 'ZW', name: 'Zimbabwe', flag: '\u{1F1FF}\u{1F1FC}', dialCode: '+263' }
  ];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private toastCtrl: ToastController
  ) {
    this.form = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(2)]],
      country: ['GB', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      phone: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  get selectedDialCode(): string {
    const code = this.form?.get('country')?.value;
    const country = this.countries.find(c => c.code === code);
    return country?.dialCode || '+44';
  }

  onSubmit(): void {
    if (this.form.invalid || this.submitting) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    const v = this.form.value;

    // Prepend dial code if user typed just the local number
    const country = this.countries.find(c => c.code === v.country);
    let phone = v.phone.trim().replace(/\s+/g, '');
    if (country && phone && !phone.startsWith('+')) {
      // Strip leading 0 if present (common in local formats)
      if (phone.startsWith('0')) phone = phone.substring(1);
      phone = country.dialCode + phone;
    }

    const payload = {
      fullName: v.fullName.trim(),
      country: v.country,
      email: v.email.trim().toLowerCase(),
      phone: phone,
      role: 'ADMIN'
    };

    this.authService.requestDemoAccess(payload).subscribe({
      next: (res) => {
        this.submitting = false;
        this.success = true;
        this.successEmail = res.email;
        this.successRole = res.role;
        this.expiresAt = res.expiresAt;
      },
      error: (err) => {
        this.submitting = false;
        const msg = err?.error?.message || 'Could not generate demo credentials. Please try again.';
        this.showToast(msg, 'danger');
      }
    });
  }

  resetForm(): void {
    this.success = false;
    this.form.reset({
      country: 'GB',
      role: 'CUSTOMER'
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 4500, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
