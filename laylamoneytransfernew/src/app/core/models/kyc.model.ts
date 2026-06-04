export interface KycDocumentResponse {
  id: string;
  userId: string;
  documentType: KycDocumentType;
  fileName: string;
  fileUrl: string;
  status: KycDocumentStatus;
  rejectionReason?: string;
  documentNumber?: string;
  issueDate?: string;
  expiryDate?: string;
  filePath?: string;
  uploadedAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  realUpload?: boolean;
}

export interface KycStatusResponse {
  userId: string;
  currentTier: string;
  nextTier?: string;
  documentsRequired: string[];
  documentsSubmitted: string[];
  documentsApproved: string[];
  documentsRejected: string[];
  overallStatus: string;
}

export interface KycReviewRequest {
  status: KycDocumentStatus;
  rejectionReason?: string;
}

export enum KycDocumentType {
  PASSPORT = 'PASSPORT',
  NATIONAL_ID = 'NATIONAL_ID',
  DRIVERS_LICENSE = 'DRIVERS_LICENSE',
  UTILITY_BILL = 'UTILITY_BILL',
  BANK_STATEMENT = 'BANK_STATEMENT',
  SELFIE = 'SELFIE'
}

export enum KycDocumentStatus {
  PENDING = 'PENDING',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED'
}
