export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken?: string;
  refreshToken?: string;
  mfaRequired: boolean;
  mfaToken?: string;
  mfaSetupRequired?: boolean;
  mfaSetupSecret?: string;
  mfaSetupQrCodeUri?: string;
  userUuid?: string;
  tokenType?: string;
  expiresIn?: number;
  emailVerified?: boolean;
  email?: string;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
  countryCode: string;
  countryOfResidence: string;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  message: string;
  emailVerified?: boolean;
}

export interface OtpVerifyRequest {
  email: string;
  otp: string;
}

export interface OtpVerifyResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  message: string;
}

export interface MfaVerifyRequest {
  mfaToken: string;
  totpCode: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface JwtPayload {
  sub: string;
  email: string;
  roles: string[];
  permissions: string[];
  kycTier: string;
  mfaVerified: boolean;
  country: string;
  countryOfResidence: string;
  exp: number;
  iat: number;
}
