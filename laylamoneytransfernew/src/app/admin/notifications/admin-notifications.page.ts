import { Component, OnInit } from '@angular/core';
import { NotificationService } from '../../core/services/notification.service';
import { UserService } from '../../core/services/user.service';
import { UserResponse } from '../../core/models/user.model';
import {
  AdminNotificationTarget,
  AdminSendNotificationRequest,
  NotificationType
} from '../../core/models/notification.model';

interface RecipientOption {
  id: number;
  label: string;
  email: string;
}

@Component({
  selector: 'app-admin-notifications',
  templateUrl: './admin-notifications.page.html',
  styleUrls: ['./admin-notifications.page.scss']
})
export class AdminNotificationsPage implements OnInit {
  title = '';
  message = '';
  type: string = NotificationType.SYSTEM;
  targetType: AdminNotificationTarget = 'ALL';

  searchTerm = '';
  searchResults: RecipientOption[] = [];
  selectedRecipients: RecipientOption[] = [];
  searching = false;

  sending = false;
  successMessage = '';
  errorMessage = '';

  logEntries: any[] = [];
  logLoading = false;

  types = [
    { value: NotificationType.SYSTEM, label: 'System' },
    { value: NotificationType.RATE_ALERT, label: 'Rate Alert' },
    { value: NotificationType.PROMOTIONAL, label: 'Promotional' },
    { value: NotificationType.TRANSACTION_UPDATE, label: 'Transaction Update' },
    { value: NotificationType.SECURITY_ALERT, label: 'Security Alert' },
    { value: NotificationType.KYC_UPDATE, label: 'KYC Update' },
    { value: NotificationType.COMPLIANCE_ALERT, label: 'Compliance Alert' }
  ];

  constructor(
    private notificationService: NotificationService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.loadLog();
  }

  onTargetChange(target: AdminNotificationTarget): void {
    this.targetType = target;
    this.successMessage = '';
    this.errorMessage = '';
  }

  searchUsers(): void {
    const term = this.searchTerm.trim();
    if (term.length < 2) {
      this.searchResults = [];
      return;
    }
    this.searching = true;
    this.userService.listUsers({ page: 0, size: 10, search: term }).subscribe({
      next: (res: any) => {
        const content: UserResponse[] = res?.content || [];
        this.searchResults = content.map(u => ({
          id: u.id,
          label: `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email,
          email: u.email
        }));
        this.searching = false;
      },
      error: () => {
        this.searchResults = [];
        this.searching = false;
      }
    });
  }

  addRecipient(option: RecipientOption): void {
    if (!this.selectedRecipients.find(r => r.id === option.id)) {
      this.selectedRecipients.push(option);
    }
    this.searchTerm = '';
    this.searchResults = [];
  }

  removeRecipient(id: number): void {
    this.selectedRecipients = this.selectedRecipients.filter(r => r.id !== id);
  }

  canSend(): boolean {
    if (this.sending) return false;
    if (!this.title.trim() || !this.message.trim()) return false;
    if (this.targetType === 'USERS' && this.selectedRecipients.length === 0) return false;
    return true;
  }

  send(): void {
    if (!this.canSend()) return;

    this.sending = true;
    this.successMessage = '';
    this.errorMessage = '';

    const request: AdminSendNotificationRequest = {
      title: this.title.trim(),
      message: this.message.trim(),
      type: this.type,
      targetType: this.targetType,
      userIds: this.targetType === 'USERS' ? this.selectedRecipients.map(r => r.id) : undefined
    };

    this.notificationService.sendAdminNotification(request).subscribe({
      next: (res) => {
        this.successMessage = `Delivered to ${res.delivered} user(s).`;
        this.sending = false;
        this.resetForm();
        this.loadLog();
      },
      error: (err) => {
        this.errorMessage = err?.error?.message || 'Failed to send notification.';
        this.sending = false;
      }
    });
  }

  private resetForm(): void {
    this.title = '';
    this.message = '';
    this.type = NotificationType.SYSTEM;
    this.selectedRecipients = [];
  }

  loadLog(): void {
    this.logLoading = true;
    this.notificationService.getRecentInAppNotifications(0, 20).subscribe({
      next: (res: any) => {
        this.logEntries = res?.content || [];
        this.logLoading = false;
      },
      error: () => {
        this.logEntries = [];
        this.logLoading = false;
      }
    });
  }
}
