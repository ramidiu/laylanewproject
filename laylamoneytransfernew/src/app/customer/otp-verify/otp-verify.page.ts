import { Component, OnInit, OnDestroy, ViewChildren, QueryList, ElementRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LoadingController, ToastController } from '@ionic/angular';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-otp-verify',
  templateUrl: './otp-verify.page.html',
  styleUrls: ['./otp-verify.page.scss']
})
export class OtpVerifyPage implements OnInit, OnDestroy {
  @ViewChildren('otpInput') otpInputs!: QueryList<ElementRef>;

  email = '';
  flow = 'register'; // 'register' or 'login'
  otpDigits: string[] = ['', '', '', '', '', ''];
  currentYear = new Date().getFullYear();

  timerSeconds = 300; // 5 minutes
  timerDisplay = '5:00';
  timerInterval: any;

  resendCooldown = 0;
  resendInterval: any;

  isVerifying = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private loadingCtrl: LoadingController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.email = this.route.snapshot.queryParams['email'] || '';
    this.flow = this.route.snapshot.queryParams['flow'] || 'register';

    if (!this.email) {
      window.location.href = '/login';
      return;
    }

    this.startTimer();
  }

  ngOnDestroy(): void {
    if (this.timerInterval) clearInterval(this.timerInterval);
    if (this.resendInterval) clearInterval(this.resendInterval);
  }

  trackByIndex(index: number): number { return index; }

  onDigitKeydown(event: KeyboardEvent, index: number): void {
    const inputs = this.otpInputs.toArray();
    const el = inputs[index]?.nativeElement as HTMLInputElement;

    if (event.key === 'Backspace') {
      event.preventDefault();
      if (this.otpDigits[index]) {
        this.otpDigits[index] = '';
        el.value = '';
      } else if (index > 0) {
        this.otpDigits[index - 1] = '';
        const prev = inputs[index - 1]?.nativeElement as HTMLInputElement;
        prev.value = '';
        prev.focus();
      }
      return;
    }

    if (/^\d$/.test(event.key)) {
      event.preventDefault();
      this.otpDigits[index] = event.key;
      el.value = event.key;
      if (index < 5) {
        setTimeout(() => inputs[index + 1]?.nativeElement.focus(), 0);
      }
      return;
    }

    if (event.key === 'ArrowLeft' && index > 0) {
      event.preventDefault();
      inputs[index - 1]?.nativeElement.focus();
      return;
    }
    if (event.key === 'ArrowRight' && index < 5) {
      event.preventDefault();
      inputs[index + 1]?.nativeElement.focus();
      return;
    }

    if (event.key !== 'Tab') {
      event.preventDefault();
    }
  }

  onDigitFocus(event: FocusEvent, index: number): void {
    // Sync native input value with model on focus
    const el = event.target as HTMLInputElement;
    el.value = this.otpDigits[index] || '';
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text')?.trim() || '';
    const digits = pasted.replace(/\D/g, '').substring(0, 6);
    const inputs = this.otpInputs.toArray();

    for (let i = 0; i < 6; i++) {
      this.otpDigits[i] = digits[i] || '';
      const el = inputs[i]?.nativeElement as HTMLInputElement;
      if (el) el.value = this.otpDigits[i];
    }

    const focusIndex = Math.min(digits.length, 5);
    inputs[focusIndex]?.nativeElement.focus();
  }

  get otpValue(): string {
    return this.otpDigits.join('');
  }

  get isOtpComplete(): boolean {
    return this.otpDigits.every(d => d !== '');
  }

  onVerify(): void {
    if (!this.isOtpComplete || this.isVerifying) return;
    this.isVerifying = true;

    this.authService.verifyOtp({ email: this.email, otp: this.otpValue }).subscribe({
      next: (res) => {
        this.isVerifying = false;
        this.showToast('Email verified successfully!', 'success');
        // Small delay to ensure token is stored and auth state is updated
        setTimeout(() => {
          if (this.flow === 'register') {
            window.location.href = '/home/kyc?fromRegistration=true';
          } else {
            window.location.href = this.authService.getHomeRoute();
          }
        }, 500);
      },
      error: (error) => {
        this.isVerifying = false;
        this.showToast(error.error?.message || 'Invalid verification code. Please try again.', 'danger');
        this.otpDigits = ['', '', '', '', '', ''];
        const inputs = this.otpInputs.toArray();
        inputs[0]?.nativeElement.focus();
      }
    });
  }

  onResendOtp(): void {
    if (this.resendCooldown > 0) return;

    this.authService.resendOtp(this.email).subscribe({
      next: () => {
        this.showToast('A new verification code has been sent to your email.', 'success');
        this.startResendCooldown();
        // Reset the expiry timer
        this.timerSeconds = 300;
        this.startTimer();
      },
      error: (error) => {
        this.showToast(error.error?.message || 'Failed to resend code. Please try again.', 'danger');
      }
    });
  }

  private startTimer(): void {
    if (this.timerInterval) clearInterval(this.timerInterval);

    this.updateTimerDisplay();
    this.timerInterval = setInterval(() => {
      this.timerSeconds--;
      this.updateTimerDisplay();
      if (this.timerSeconds <= 0) {
        clearInterval(this.timerInterval);
      }
    }, 1000);
  }

  private updateTimerDisplay(): void {
    const minutes = Math.floor(this.timerSeconds / 60);
    const seconds = this.timerSeconds % 60;
    this.timerDisplay = `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  private startResendCooldown(): void {
    this.resendCooldown = 30;
    if (this.resendInterval) clearInterval(this.resendInterval);

    this.resendInterval = setInterval(() => {
      this.resendCooldown--;
      if (this.resendCooldown <= 0) {
        clearInterval(this.resendInterval);
      }
    }, 1000);
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
