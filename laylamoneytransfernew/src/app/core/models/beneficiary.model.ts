export interface AddBeneficiaryRequest {
  fullName: string;
  country: string;
  deliveryMethod: string;
  bankName?: string | null;
  accountNumber?: string | null;
  iban?: string | null;
  swiftBic?: string | null;
  sortCode?: string | null;
  mobileNumber?: string | null;
  mobileProvider?: string | null;
  idNumber?: string | null;
  idType?: string | null;
  address?: string | null;
  relationship?: string | null;
}

export interface UpdateBeneficiaryRequest {
  fullName?: string;
  country?: string;
  deliveryMethod?: string;
  bankName?: string;
  accountNumber?: string;
  iban?: string;
  swiftBic?: string;
  sortCode?: string;
  mobileNumber?: string;
  mobileProvider?: string;
  idNumber?: string;
  idType?: string;
  address?: string;
  relationship?: string;
  isFavourite?: boolean;
}

export interface BeneficiaryResponse {
  id: string;
  fullName: string;
  country: string;
  deliveryMethod: string;
  bankName?: string;
  accountNumber?: string;
  iban?: string;
  swiftBic?: string;
  sortCode?: string;
  mobileNumber?: string;
  mobileProvider?: string;
  idType?: string;
  idNumber?: string;
  address?: string;
  relationship?: string;
  isFavourite: boolean;
  createdAt: string;
}
