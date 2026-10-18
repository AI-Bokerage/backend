import "dotenv/config";
import { z } from "zod";
import { GenSlaConfigError } from "../common/errors.ts";

const EnvSchema = z.object({
  PORT: z.coerce.number().default(3202),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),

  TIMER_POLL_INTERVAL_MS: z.coerce.number().default(30_000),
  TIMER_BATCH_SIZE: z.coerce.number().default(100),
  TIMER_LOCK_TTL_S: z.coerce.number().default(60),

  OUTBOX_POLL_INTERVAL_MS: z.coerce.number().default(5_000),
  OUTBOX_BATCH_SIZE: z.coerce.number().default(50),
  OUTBOX_MAX_RETRIES: z.coerce.number().default(3),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it. DATABASE_URL, VALKEY_URL, RABBITMQ_URL, and
// GEN_SLA_INTERNAL_SECRET are intentionally NOT in EnvSchema — they're read
// through this function instead, only when the module/adapter that needs them
// is enabled and no override was supplied (see create-gen-sla.ts).
export function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new GenSlaConfigError(`Missing required environment variable "${key}"`);
  }
  return value;
}
