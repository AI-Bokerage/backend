import type { Request, Response, NextFunction, RequestHandler } from "express";
import type { ICaptchaVerifier } from "../domain/ports/captcha-verifier.port.ts";
import { CaptchaTokenMissingError, CaptchaVerificationFailedError } from "../common/errors.ts";

export function createCaptchaMiddleware(verifier: ICaptchaVerifier): RequestHandler {
  return async (req: Request, _res: Response, next: NextFunction): Promise<void> => {
    const token = req.body?.captchaToken;

    if (typeof token !== "string" || token.length === 0) {
      throw new CaptchaTokenMissingError();
    }

    const result = await verifier.verify(token);

    if (!result.verified) {
      throw new CaptchaVerificationFailedError();
    }

    next();
  };
}
