export interface UserResponse {
  id: number;
  uuid: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  countryCode: string;
  country: string;
  dateOfBirth: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  postcode: string;
  preferredLanguage: string;
  kycTier: KycTier;
  status: UserStatus;
  accountStatus?: 'ACTIVE' | 'DELETE_REQUESTED' | 'DELETED';
  deleteRequestedAt?: string;
  roles: string[];
  mfaEnabled: boolean;
  emailVerified: boolean;
  phoneVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  address?: AddressRequest;
}

export interface AddressRequest {
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export enum KycTier {
  NONE = 'NONE',
  TIER_1 = 'TIER_1',
  TIER_2 = 'TIER_2',
  TIER_3 = 'TIER_3'
}

export enum UserStatus {
  ACTIVE = 'ACTIVE',
  SUSPENDED = 'SUSPENDED',
  PENDING_VERIFICATION = 'PENDING_VERIFICATION',
  DEACTIVATED = 'DEACTIVATED'
}
