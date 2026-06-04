export interface CreateTransactionRequest {
  quoteId?: string;
  beneficiaryId: any;
  corridorId?: any;
  deliveryMethod: string;
  sendAmount: number;
  sendCurrency: string;
  paymentMethodType?: string;
  notes?: string;
  idempotencyKey?: string;
  referralCode?: string;
  useWallet?: boolean;
  walletAmountToUse?: number;
}

export interface TransactionResponse {
  id: string;
  referenceNumber: string;
  payoutReference?: string;
  senderId: string;
  senderName: string;
  beneficiaryId: string;
  beneficiaryName: string;
  sendCurrency: string;
  receiveCurrency: string;
  sendAmount: number;
  receiveAmount: number;
  exchangeRate: number;
  appliedRate: number;
  feeAmount: number;
  totalDebitAmount: number;
  status: string;
  deliveryMethod: string;
  paymentMethodType: string;
  createdAt: string;
  updatedAt: string;
  walletAmountUsed?: number;
  referralCodeUsed?: string;
  rateBoostApplied?: number;
  riskScore?: number;
  riskFactors?: string;
  visitorId?: string;
}

export interface StatusHistoryResponse {
  fromStatus: string;
  toStatus: string;
  actorType: string;
  reason?: string;
  createdAt: string;
}

export interface TransactionFilterParams {
  status?: string;
  startDate?: string;
  endDate?: string;
  corridorId?: number;
  search?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
}
