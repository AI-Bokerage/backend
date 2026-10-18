import { createHmac, timingSafeEqual } from "node:crypto";

const TIMESTAMP_GRACE_S = 300;

export function signWebhookPayload(secret: string, timestamp: number, body: string): string {
  const data = `${timestamp}.${body}`;
  const hex = createHmac("sha256", secret).update(data).digest("hex");
  return `sha256=${hex}`;
}

export function verifyWebhookSignature(
  secret: string,
  timestamp: number,
  rawBody: string,
  signature: string,
): boolean {
  if (Math.abs(Date.now() / 1000 - timestamp) > TIMESTAMP_GRACE_S) return false;
  const expected = signWebhookPayload(secret, timestamp, rawBody);
  const expectedBuf = Buffer.from(expected);
  const actualBuf = Buffer.from(signature);
  if (expectedBuf.length !== actualBuf.length) return false;
  return timingSafeEqual(expectedBuf, actualBuf);
}
