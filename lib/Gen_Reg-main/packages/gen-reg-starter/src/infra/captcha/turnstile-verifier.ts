import type { CaptchaVerifyResult, ICaptchaVerifier } from "../../domain/ports/captcha-verifier.port.ts";

export class TurnstileCaptchaVerifier implements ICaptchaVerifier {
  constructor(
    private readonly secretKey: string,
    private readonly verifyUrl: string,
  ) {}

  async verify(token: string): Promise<CaptchaVerifyResult> {
    try {
      const response = await fetch(this.verifyUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ response: token, secret: this.secretKey }),
      });

      if (!response.ok) {
        return { verified: false, errorCodes: ["api_error"] };
      }

      const data = (await response.json()) as {
        success: boolean;
        "error-codes"?: string[];
      };

      return { verified: data.success, errorCodes: data["error-codes"] };
    } catch {
      return { verified: false, errorCodes: ["verification_failed"] };
    }
  }
}
