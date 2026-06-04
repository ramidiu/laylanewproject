import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { NotificationService } from '../../core/services/notification.service';
import { InAppNotificationResponse } from '../../core/models/notification.model';
import { PageResponse } from '../../core/models/common.model';

@Component({
  selector: 'app-customer-notifications',
  templateUrl: './notifications.page.html',
  styleUrls: ['./notifications.page.scss']
})
export class NotificationsPage implements OnInit {
  notifications: InAppNotificationResponse[] = [];
  loading = true;
  page = 0;
  pageSize = 50;
  totalPages = 0;
  totalElements = 0;
  markingAll = false;

  constructor(
    private notificationService: NotificationService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.loading = true;
    this.notificationService.getNotifications(this.page, this.pageSize).subscribe({
      next: (res: PageResponse<InAppNotificationResponse>) => {
        this.notifications = res.content || [];
        this.totalPages = res.totalPages || 0;
        this.totalElements = res.totalElements || 0;
        this.loading = false;
      },
      error: () => {
        this.notifications = [];
        this.loading = false;
      }
    });
  }

  onNotificationClick(notification: InAppNotificationResponse): void {
    if (notification.isRead) return;
    this.notificationService.markAsRead(notification.id).subscribe({
      next: () => {
        notification.isRead = true;
        this.notificationService.refreshUnreadCount();
      },
      error: () => {}
    });
  }

  markAllAsRead(): void {
    if (this.markingAll) return;
    this.markingAll = true;
    this.notificationService.markAllAsRead().subscribe({
      next: () => {
        this.markingAll = false;
        this.notifications.forEach(n => (n.isRead = true));
        this.notificationService.refreshUnreadCount();
        this.showToast('All notifications marked as read', 'success');
      },
      error: () => {
        this.markingAll = false;
        this.showToast('Failed to mark all as read', 'danger');
      }
    });
  }

  nextPage(): void {
    if (this.page + 1 < this.totalPages) {
      this.page++;
      this.loadNotifications();
    }
  }

  prevPage(): void {
    if (this.page > 0) {
      this.page--;
      this.loadNotifications();
    }
  }

  typeIcon(type: string): string {
    switch (type) {
      case 'TRANSACTION_UPDATE': return 'swap-horizontal-outline';
      case 'KYC_UPDATE': return 'document-text-outline';
      case 'COMPLIANCE_ALERT': return 'shield-checkmark-outline';
      case 'SYSTEM': return 'information-circle-outline';
      case 'PROMOTIONAL': return 'gift-outline';
      default: return 'notifications-outline';
    }
  }

  typeColor(type: string): string {
    switch (type) {
      case 'TRANSACTION_UPDATE': return 'primary';
      case 'KYC_UPDATE': return 'warning';
      case 'COMPLIANCE_ALERT': return 'danger';
      case 'SYSTEM': return 'medium';
      case 'PROMOTIONAL': return 'success';
      default: return 'medium';
    }
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({
      message, duration: 3000, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await toast.present();
  }
}
