import "dotenv/config";
import { z } from "zod";
import { GenSupConfigError } from "../common/errors.ts";

const EnvSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),
  DASHBOARD_CACHE_TTL_SEC: z.coerce.number().default(300),
  ANALYTICS_CACHE_TTL_SEC: z.coerce.number().default(300),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it. VALKEY_URL and GEN_SUP_INTERNAL_SECRET are intentionally
// NOT in EnvSchema — they're read through this function instead, only when
// no override was supplied to createGenSup() (see create-gen-sup.ts).
export function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new GenSupConfigError(`Missing required environment variable "${key}"`);
  }
  return value;
}
