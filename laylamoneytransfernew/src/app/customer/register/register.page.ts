import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { LoadingController, ToastController } from '@ionic/angular';

import { AuthService } from '../../core/services/auth.service';

interface Country {
  name: string;
  code: string;
  dialCode: string;
  flag: string;
}

@Component({
  selector: 'app-register',
  templateUrl: './register.page.html',
  styleUrls: ['./register.page.scss']
})
export class RegisterPage implements OnInit {
  registerForm!: FormGroup;
  showPassword = false;
  showConfirmPassword = false;
  submitting = false;
  passwordStrength = 0;
  passwordStrengthLabel = '';

  phoneDigits = 10;
  phoneDigitsLabel = '10 digits';

  private countryMeta: Record<string, { dialCode: string; flag: string; phoneDigits: number }> = {
    'GB': { dialCode: '+44', flag: '\u{1F1EC}\u{1F1E7}', phoneDigits: 10 },
    'US': { dialCode: '+1', flag: '\u{1F1FA}\u{1F1F8}', phoneDigits: 10 },
    'CA': { dialCode: '+1', flag: '\u{1F1E8}\u{1F1E6}', phoneDigits: 10 },
    'AU': { dialCode: '+61', flag: '\u{1F1E6}\u{1F1FA}', phoneDigits: 9 },
    'AE': { dialCode: '+971', flag: '\u{1F1E6}\u{1F1EA}', phoneDigits: 9 },
    'DE': { dialCode: '+49', flag: '\u{1F1E9}\u{1F1EA}', phoneDigits: 11 },
    'FR': { dialCode: '+33', flag: '\u{1F1EB}\u{1F1F7}', phoneDigits: 9 },
    'IN': { dialCode: '+91', flag: '\u{1F1EE}\u{1F1F3}', phoneDigits: 10 },
    'NG': { dialCode: '+234', flag: '\u{1F1F3}\u{1F1EC}', phoneDigits: 10 },
    'PK': { dialCode: '+92', flag: '\u{1F1F5}\u{1F1F0}', phoneDigits: 10 },
    'PH': { dialCode: '+63', flag: '\u{1F1F5}\u{1F1ED}', phoneDigits: 10 },
    'KE': { dialCode: '+254', flag: '\u{1F1F0}\u{1F1EA}', phoneDigits: 9 },
    'GH': { dialCode: '+233', flag: '\u{1F1EC}\u{1F1ED}', phoneDigits: 9 },
    'ZA': { dialCode: '+27', flag: '\u{1F1FF}\u{1F1E6}', phoneDigits: 9 },
    'BD': { dialCode: '+880', flag: '\u{1F1E7}\u{1F1E9}', phoneDigits: 10 },
    'LK': { dialCode: '+94', flag: '\u{1F1F1}\u{1F1F0}', phoneDigits: 9 },
    'NP': { dialCode: '+977', flag: '\u{1F1F3}\u{1F1F5}', phoneDigits: 10 },
    'MY': { dialCode: '+60', flag: '\u{1F1F2}\u{1F1FE}', phoneDigits: 10 },
    'SG': { dialCode: '+65', flag: '\u{1F1F8}\u{1F1EC}', phoneDigits: 8 },
    'JP': { dialCode: '+81', flag: '\u{1F1EF}\u{1F1F5}', phoneDigits: 10 },
    'SA': { dialCode: '+966', flag: '\u{1F1F8}\u{1F1E6}', phoneDigits: 9 },
    'QA': { dialCode: '+974', flag: '\u{1F1F6}\u{1F1E6}', phoneDigits: 8 },
    'KW': { dialCode: '+965', flag: '\u{1F1F0}\u{1F1FC}', phoneDigits: 8 },
  };

  countries: Country[] = [];
  selectedCountry: Country = { name: 'United Kingdom', code: 'GB', dialCode: '+44', flag: '\u{1F1EC}\u{1F1E7}' };

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private loadingCtrl: LoadingController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.registerForm = this.fb.group({
      firstName: ['', [Validators.required, Validators.minLength(2)]],
      lastName: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      countryOfResidence: ['GB', [Validators.required]],
      phone: ['', [Validators.required, Validators.pattern(/^[0-9]{10}$/)]],
      password: ['', [Validators.required, Validators.minLength(8), this.passwordStrengthValidator]],
      confirmPassword: ['', [Validators.required]],
      agreeTerms: [false]
    }, { validators: this.passwordMatchValidator });

    this.phoneDigits = 10;
    this.phoneDigitsLabel = '10 digits';

    this.registerForm.get('password')?.valueChanges.subscribe(val => {
      this.calculatePasswordStrength(val);
    });
  }


passwordStrengthValidator(control: AbstractControl): ValidationErrors | null {
    const value = control.value;
    if (!value) return null;
    const hasUpperCase = /[A-Z]/.test(value);
    const hasNumber = /\d/.test(value);
    const hasSpecial = /[^a-zA-Z0-9]/.test(value);
    if (!hasUpperCase || !hasNumber || !hasSpecial) {
      return { weakPassword: true };
    }
    return null;
  }

  passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
    const password = control.get('password');
    const confirmPassword = control.get('confirmPassword');
    if (password && confirmPassword && password.value !== confirmPassword.value) {
      confirmPassword.setErrors({ passwordMismatch: true });
      return { passwordMismatch: true };
    }
    return null;
  }

  calculatePasswordStrength(password: string): void {
    let strength = 0;
    if (password.length >= 8) strength++;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) strength++;
    if (/\d/.test(password)) strength++;
    if (/[^a-zA-Z0-9]/.test(password)) strength++;

    this.passwordStrength = strength;
    const labels = ['', 'Weak', 'Fair', 'Good', 'Strong'];
    this.passwordStrengthLabel = labels[strength] || '';
  }

  onRegister(): void {
    if (this.registerForm.invalid) {
      Object.keys(this.registerForm.controls).forEach(key => {
        this.registerForm.get(key)?.markAsTouched();
      });
      this.showToast('Please fill in all required fields correctly', 'warning');
      return;
    }
    this.submitting = true;

    const formValue = this.registerForm.value;
    const country = this.countries.find(c => c.code === formValue.countryOfResidence);

    const request: any = {
      firstName: formValue.firstName,
      lastName: formValue.lastName,
      email: formValue.email,
      countryOfResidence: formValue.countryOfResidence,
      countryCode: formValue.countryOfResidence,
      phone: (country?.dialCode || '') + formValue.phone.replace(/^0+/, ''),
      password: formValue.password
    };

    this.authService.register(request).subscribe({
      next: (res: any) => {
        this.submitting = false;
        this.showToast('Account created! Please check your email for the verification code.', 'success');
        setTimeout(() => {
          window.location.href = `/otp-verify?email=${encodeURIComponent(formValue.email)}&flow=register`;
        }, 500);
      },
      error: (error: any) => {
        this.submitting = false;
        const status = error?.status;
        const msg = (error?.error?.message || error?.message || '').toLowerCase();
        if (status === 409 || (msg.includes('log in') || msg.includes('login'))) {
          this.showToast('This email is already registered. Redirecting to login...', 'warning');
          setTimeout(() => {
            window.location.href = `/login?email=${encodeURIComponent(formValue.email)}`;
          }, 1500);
        } else {
          this.showToast(error?.error?.message || error?.message || 'Registration failed. Please try again.', 'danger');
        }
      }
    });
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
