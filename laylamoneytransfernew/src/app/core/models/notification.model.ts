export interface InAppNotificationResponse {
  id: string;
  userId: string;
  title: string;
  message: string;
  type: NotificationType;
  referenceId?: string;
  referenceType?: string;
  isRead: boolean;
  createdAt: string;
}

export enum NotificationType {
  TRANSACTION_UPDATE = 'TRANSACTION_UPDATE',
  KYC_UPDATE = 'KYC_UPDATE',
  COMPLIANCE_ALERT = 'COMPLIANCE_ALERT',
  SYSTEM = 'SYSTEM',
  PROMOTIONAL = 'PROMOTIONAL',
  RATE_ALERT = 'RATE_ALERT',
  SECURITY_ALERT = 'SECURITY_ALERT'
}

export interface NotificationPreferences {
  userId?: number;
  rateAlerts: boolean;
  promotional: boolean;
  transactionUpdates: boolean;
  securityAlerts: boolean;
  kycUpdates: boolean;
  complianceAlerts: boolean;
  systemNotifications: boolean;
  emailEnabled: boolean;
}

export type AdminNotificationTarget = 'ALL' | 'USERS';

export interface AdminSendNotificationRequest {
  title: string;
  message: string;
  type?: NotificationType | string;
  targetType: AdminNotificationTarget;
  userIds?: number[];
}

export interface AdminSendNotificationResponse {
  delivered: number;
  targetType: string;
}
