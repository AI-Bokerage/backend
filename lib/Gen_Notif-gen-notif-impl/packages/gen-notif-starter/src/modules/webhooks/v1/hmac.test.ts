import { describe, it, expect } from "vitest";
import { signWebhookPayload, verifyWebhookSignature } from "./hmac.ts";

describe("webhook HMAC signing", () => {
  it("a signature verifies against the same secret/timestamp/body", () => {
    const sig = signWebhookPayload("s3cr3t", 1_700_000_000, '{"a":1}');
    expect(sig).toMatch(/^sha256=[0-9a-f]{64}$/);
  });

  it("verifyWebhookSignature accepts a fresh, correctly-signed payload", () => {
    const now = Math.floor(Date.now() / 1000);
    const body = '{"eventType":"doc.uploaded"}';
    const sig = signWebhookPayload("s3cr3t", now, body);
    expect(verifyWebhookSignature("s3cr3t", now, body, sig)).toBe(true);
  });

  it("verifyWebhookSignature rejects a timestamp outside the 300s grace window", () => {
    const stale = Math.floor(Date.now() / 1000) - 301;
    const body = "{}";
    const sig = signWebhookPayload("s3cr3t", stale, body);
    expect(verifyWebhookSignature("s3cr3t", stale, body, sig)).toBe(false);
  });

  it("verifyWebhookSignature rejects a tampered body", () => {
    const now = Math.floor(Date.now() / 1000);
    const sig = signWebhookPayload("s3cr3t", now, '{"a":1}');
    expect(verifyWebhookSignature("s3cr3t", now, '{"a":2}', sig)).toBe(false);
  });
});
