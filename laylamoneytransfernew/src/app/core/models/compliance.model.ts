export interface ComplianceAlertResponse {
  id: number;
  userId?: number;
  transactionId?: number;
  severity: AlertSeverity;
  status: AlertStatus;
  description: string;
  details?: string;
  assignedTo?: number;
  resolvedBy?: number;
  resolvedAt?: string;
  resolutionNotes?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ComplianceAlertDetail {
  id: number;
  userId?: number;
  userName?: string;
  userEmail?: string;
  userCountry?: string;
  transactionId?: number;
  transactionReference?: string;
  severity: AlertSeverity;
  status: AlertStatus;
  description: string;
  details?: Record<string, any>;
  assignedTo?: number;
  resolvedBy?: number;
  resolvedAt?: string;
  resolutionNotes?: string;
  createdAt: string;

  listEntryId?: number;
  listEntryExternalId?: string;
  listEntryName?: string;
  listEntrySource?: string;
  listEntryListType?: string;
  listEntryCountry?: string;
  listEntryAliases?: string;
  listEntryTopics?: string;
  listEntryDateOfBirth?: string;
}

export interface AlertDispositionRequest {
  action: 'FALSE_POSITIVE' | 'CONFIRMED_MATCH' | 'ESCALATE';
  reason?: string;
}

export interface ComplianceMetrics {
  openedToday: number;
  pendingReview: number;
  closedLast30Days: number;
  closedFalsePositiveLast30Days: number;
  closedConfirmedMatchLast30Days: number;
  meanMinutesToDisposition30d: number;
  falsePositiveRate30d: number;
}

export interface ComplianceCaseResponse {
  id: number;
  caseReference: string;
  userId: number;
  status: string;
  priority: string;
  summary?: string;
  findings?: string;
  outcome?: string;
  createdAt: string;
  updatedAt?: string;
  closedAt?: string;
}

export enum AlertSeverity {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL'
}

export interface ComplianceAuditEntry {
  alertId: number;
  customerId: number;
  customerName?: string;
  customerEmail?: string;
  severity: string;
  status: string;
  listType?: string;
  matchedName?: string;
  source?: string;
  description: string;
  reviewerId?: number;
  reviewerName?: string;
  reviewerEmail?: string;
  reason?: string;
  resolvedAt?: string;
  createdAt: string;
}

export enum AlertStatus {
  OPEN = 'OPEN',
  UNDER_REVIEW = 'UNDER_REVIEW',
  ESCALATED = 'ESCALATED',
  CLOSED_NO_ACTION = 'CLOSED_NO_ACTION',
  CLOSED_SAR_FILED = 'CLOSED_SAR_FILED',
  CLOSED_FALSE_POSITIVE = 'CLOSED_FALSE_POSITIVE'
}
