// src/config/env.ts
import "dotenv/config";
import { z } from "zod";
import { GenRegConfigError } from "../common/errors.ts";

const EnvSchema = z.object({
  PORT: z.coerce.number().default(3200),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),
  EMAIL_VERIFICATION_TTL_SECS: z.coerce.number().default(86400),
  RESUME_TOKEN_TTL_SECS: z.coerce.number().default(604800),
  PROVISIONING_POLL_INTERVAL_MS: z.coerce.number().default(5000),
  ABANDON_SWEEP_INTERVAL_MS: z.coerce.number().default(300000),
  TURNSTILE_VERIFY_URL: z.string().url().default("https://challenges.cloudflare.com/turnstile/v0/siteverify"),
  TURNSTILE_CDN_URL: z.string().url().default("https://challenges.cloudflare.com/turnstile/v0/api.js"),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it — as opposed to EnvSchema above, which eagerly validates
// only the fields every consumer needs regardless of which modules/adapters
// they enable. DATABASE_URL, GEN_TNT_BASE_URL, GEN_TNT_INTERNAL_SECRET,
// GEN_AUTH_BASE_URL, STRIPE_*/RAZORPAY_*/VALKEY_URL, and TURNSTILE_SECRET_KEY/
// TURNSTILE_SITE_KEY are intentionally NOT in EnvSchema — they're read through
// this function instead, only when the module that needs them is enabled and
// no override was supplied (see create-gen-reg.ts's resolver functions).
export function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new GenRegConfigError(`Missing required environment variable "${key}"`);
  }
  return value;
}
