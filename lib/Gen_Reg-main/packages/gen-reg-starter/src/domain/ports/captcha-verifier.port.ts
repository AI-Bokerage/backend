export interface CaptchaVerifyResult {
  verified: boolean;
  errorCodes?: string[];
}

export interface ICaptchaVerifier {
  verify(token: string): Promise<CaptchaVerifyResult>;
}
