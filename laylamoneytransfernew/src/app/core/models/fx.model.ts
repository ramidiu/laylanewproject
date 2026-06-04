export interface QuoteRequest {
  sendCurrency: string;
  receiveCurrency: string;
  sendAmount?: number;
  receiveAmount?: number;
  deliveryMethod: string;
  corridorId?: number;
}

export interface QuoteResponse {
  quoteId: string;
  sendAmount: number;
  receiveAmount: number;
  exchangeRate: number;
  appliedRate: number;
  marginApplied: number;
  fee: number;
  totalCost: number;
  rateLockedUntil: string;
  expiresInSeconds: number;
  _baseRate?: number;
  _baseReceiveAmount?: number;
}

export interface FxRateResponse {
  baseCurrency: string;
  targetCurrency: string;
  rate: number;
  source: string;
  fetchedAt: string;
}

export interface CorridorResponse {
  id: number;
  sendCountry: string;
  receiveCountry: string;
  sendCurrency: string;
  receiveCurrency: string;
  deliveryMethods: string[];
  isActive: boolean;
  minAmount: number;
  maxAmount: number;
  requiredKycTier: string;
  riskLevel: string;
}

export interface DeliveryMethodResponse {
  id: number;
  deliveryMethod: string;
  isActive: boolean;
  processingTimeMinutes: number;
}
