import { Injectable } from '@angular/core';
import { ToastController } from '@ionic/angular';

@Injectable({ providedIn: 'root' })
export class ToastService {
  constructor(private toastCtrl: ToastController) {}

  async show(message: string, color: 'success' | 'danger' | 'warning' | 'primary' = 'primary', duration = 4000): Promise<void> {
    // Dismiss any existing toast first
    try { await this.toastCtrl.dismiss(); } catch {}

    const toast = await this.toastCtrl.create({
      message,
      duration,
      position: 'top',
      color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }

  success(message: string, duration = 4000): Promise<void> {
    return this.show(message, 'success', duration);
  }

  error(message: string, duration = 5000): Promise<void> {
    return this.show(message, 'danger', duration);
  }

  warning(message: string, duration = 5000): Promise<void> {
    return this.show(message, 'warning', duration);
  }

  info(message: string, duration = 4000): Promise<void> {
    return this.show(message, 'primary', duration);
  }
}
