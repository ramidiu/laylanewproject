import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AlertController, ToastController } from '@ionic/angular';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-sa-demo-users',
  templateUrl: './sa-demo-users.page.html',
  styleUrls: ['./sa-demo-users.page.scss']
})
export class SADemoUsersPage implements OnInit {
  demoUsers: any[] = [];
  loading = true;

  constructor(
    private http: HttpClient,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.http.get<any>(`${environment.apiUrl}/auth/admin/demo-users`).subscribe({
      next: (res) => {
        this.demoUsers = Array.isArray(res) ? res : res?.data || [];
        this.loading = false;
      },
      error: () => {
        this.demoUsers = [];
        this.loading = false;
        this.toast('Failed to load demo users', 'danger');
      }
    });
  }

  async extend(user: any): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Extend Demo Access',
      message: `Extend demo access for ${user.email} by 24 hours?`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Extend 24h',
          handler: () => {
            this.http.post<any>(
              `${environment.apiUrl}/auth/admin/demo-users/${user.id}/extend?hours=24`, {}
            ).subscribe({
              next: () => {
                this.toast(`Extended access for ${user.email}`, 'success');
                this.load();
              },
              error: () => this.toast('Failed to extend access', 'danger')
            });
          }
        }
      ]
    });
    await alert.present();
  }

  get expiredCount(): number {
    return this.demoUsers.filter(u => this.isExpired(u)).length;
  }

  isExpired(user: any): boolean {
    return user.expired === true;
  }

  timeLeft(user: any): string {
    if (!user.demoAccessExpiresAt) return '—';
    const exp = new Date(user.demoAccessExpiresAt);
    const now = new Date();
    const diff = exp.getTime() - now.getTime();
    if (diff <= 0) return 'Expired';
    const hours = Math.floor(diff / 3600000);
    const mins = Math.floor((diff % 3600000) / 60000);
    return `${hours}h ${mins}m remaining`;
  }

  private async toast(message: string, color: string): Promise<void> {
    const t = await this.toastCtrl.create({
      message, duration: 3000, position: 'top', color,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await t.present();
  }
}
