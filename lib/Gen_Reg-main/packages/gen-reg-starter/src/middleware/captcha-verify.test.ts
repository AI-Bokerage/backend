import { describe, it, expect, vi } from "vitest";
import type { Request, Response, NextFunction } from "express";
import { createCaptchaMiddleware } from "./captcha-verify.ts";
import { CaptchaTokenMissingError, CaptchaVerificationFailedError } from "../common/errors.ts";
import type { ICaptchaVerifier } from "../domain/ports/captcha-verifier.port.ts";

function fakeVerifier(verified: boolean): ICaptchaVerifier {
  return { verify: vi.fn(async () => ({ verified })) };
}

function fakeReqRes(body: Record<string, unknown>) {
  const req = { body } as Request;
  const res = {} as Response;
  const next = vi.fn() as unknown as NextFunction;
  return { req, res, next };
}

describe("createCaptchaMiddleware", () => {
  it("throws CaptchaTokenMissingError when captchaToken is missing, without calling the verifier", async () => {
    const verifier = fakeVerifier(true);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({});

    await expect(middleware(req, res, next)).rejects.toThrow(CaptchaTokenMissingError);
    expect(verifier.verify).not.toHaveBeenCalled();
  });

  it("throws CaptchaTokenMissingError when captchaToken is not a string", async () => {
    const verifier = fakeVerifier(true);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({ captchaToken: 12345 });

    await expect(middleware(req, res, next)).rejects.toThrow(CaptchaTokenMissingError);
    expect(verifier.verify).not.toHaveBeenCalled();
  });

  it("throws CaptchaVerificationFailedError when the verifier reports not verified", async () => {
    const verifier = fakeVerifier(false);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({ captchaToken: "a-token" });

    await expect(middleware(req, res, next)).rejects.toThrow(CaptchaVerificationFailedError);
    expect(verifier.verify).toHaveBeenCalledWith("a-token");
  });

  it("calls next() when the verifier reports verified", async () => {
    const verifier = fakeVerifier(true);
    const middleware = createCaptchaMiddleware(verifier);
    const { req, res, next } = fakeReqRes({ captchaToken: "a-token" });

    await middleware(req, res, next);

    expect(next).toHaveBeenCalledOnce();
  });
});
