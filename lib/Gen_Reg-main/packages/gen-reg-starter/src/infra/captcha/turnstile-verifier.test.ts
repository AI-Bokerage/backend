import { describe, it, expect, vi, afterEach } from "vitest";
import { TurnstileCaptchaVerifier } from "./turnstile-verifier.ts";

function mockFetchOnce(ok: boolean, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok,
      json: async () => body,
    }),
  );
}

describe("TurnstileCaptchaVerifier", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("returns verified true when Cloudflare reports success", async () => {
    mockFetchOnce(true, { success: true, challenge_ts: "2026-01-01T00:00:00Z" });
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("test-token");

    expect(result).toEqual({ verified: true, errorCodes: undefined });
  });

  it("returns verified false with errorCodes when Cloudflare reports failure", async () => {
    mockFetchOnce(true, { success: false, "error-codes": ["invalid-input-response"] });
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("bad-token");

    expect(result).toEqual({ verified: false, errorCodes: ["invalid-input-response"] });
  });

  it("fails closed (verified false) when Cloudflare returns a non-2xx response", async () => {
    mockFetchOnce(false, {});
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("any-token");

    expect(result.verified).toBe(false);
    expect(result.errorCodes).toEqual(["api_error"]);
  });

  it("fails closed (verified false) when fetch throws, without throwing itself", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network down")));
    const verifier = new TurnstileCaptchaVerifier("secret", "https://challenges.cloudflare.com/turnstile/v0/siteverify");

    const result = await verifier.verify("any-token");

    expect(result.verified).toBe(false);
    expect(result.errorCodes).toEqual(["verification_failed"]);
  });

  it("posts the token and secret to the configured verify URL", async () => {
    mockFetchOnce(true, { success: true });
    const verifier = new TurnstileCaptchaVerifier("my-secret", "https://example.com/verify");

    await verifier.verify("the-token");

    expect(fetch).toHaveBeenCalledWith(
      "https://example.com/verify",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ response: "the-token", secret: "my-secret" }),
      }),
    );
  });
});
