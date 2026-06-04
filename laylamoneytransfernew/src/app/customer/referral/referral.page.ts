import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { ReferralService } from '../../core/services/referral.service';

@Component({
  selector: 'app-referral',
  templateUrl: './referral.page.html',
  styleUrls: ['./referral.page.scss']
})
export class ReferralPage implements OnInit {
  referralCode = '';
  loading = true;
  copied = false;

  constructor(
    private referralService: ReferralService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.referralService.getMyCode().subscribe({
      next: (res: any) => {
        this.referralCode = res?.code || '';
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  copyCode(): void {
    if (!this.referralCode) return;
    navigator.clipboard.writeText(this.referralCode).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2000);
    });
  }

  shareWhatsApp(): void {
    const msg = encodeURIComponent(
      `Join Layla Money Transfer — send money internationally with great rates! Use my referral code *${this.referralCode}* when you sign up and get a rate boost on your first transfer. Download: https://layla.money`
    );
    window.open(`https://web.whatsapp.com/send?text=${msg}`, 'whatsapp_share', 'width=800,height=600');
  }

  shareEmail(): void {
    const subject = encodeURIComponent('Join Layla Money Transfer — Get a rate boost!');
    const body = encodeURIComponent(
      `Hey!\n\nI've been using Layla Money Transfer to send money internationally and it's great. Use my referral code ${this.referralCode} when you sign up and you'll get a rate boost on your first transfer.\n\nSign up: https://layla.money`
    );
    window.location.href = `mailto:?subject=${subject}&body=${body}`;
  }
}
