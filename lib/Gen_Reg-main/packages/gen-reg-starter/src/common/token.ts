// src/common/token.ts
import { randomBytes, createHash } from "node:crypto";

export function generateToken(bytes = 32): string {
  return randomBytes(bytes).toString("hex");
}

// sha256, not bcrypt: these are lookup tokens with 256 bits of their own
// entropy (not low-entropy secrets like passwords), so a fast one-way
// digest is the correct tool — bcrypt's deliberate slowness buys nothing
// here and would needlessly slow down every verify-email/resume request.
export async function hashToken(token: string): Promise<string> {
  return createHash("sha256").update(token).digest("hex");
}
