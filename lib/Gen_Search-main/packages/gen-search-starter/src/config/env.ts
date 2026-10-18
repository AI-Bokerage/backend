import "dotenv/config";
import { z } from "zod";
import { GenSearchConfigError } from "../common/errors.ts";

const EnvSchema = z.object({
  PORT: z.coerce.number().default(3800),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.string().default("info"),

  SEARCH_TIMEOUT_MS: z.coerce.number().default(1500),
  MIN_QUERY_LENGTH: z.coerce.number().default(1),
  MAX_QUERY_LENGTH: z.coerce.number().default(500),
  DEFAULT_RESULTS: z.coerce.number().default(20),
  MAX_RESULTS: z.coerce.number().default(100),
  MAX_SEARCH_OFFSET: z.coerce.number().default(1_000),

  INDEXER_BATCH_SIZE: z.coerce.number().default(100),
  INDEXER_FLUSH_INTERVAL_MS: z.coerce.number().default(2000),

  OPENSEARCH_INDEX_PREFIX: z.string().default("gensearch-"),
});

export const env = EnvSchema.parse(process.env);
export type Env = z.infer<typeof EnvSchema>;

// Lazily validates a single required env var at the point a default adapter
// actually needs it. DATABASE_URL, OPENSEARCH_URL, OPENSEARCH_USERNAME,
// OPENSEARCH_PASSWORD, VALKEY_URL, RABBITMQ_URL, and
// GEN_SEARCH_INTERNAL_SECRET are intentionally NOT in EnvSchema — they're
// read through this function instead, only when the module/adapter that
// needs them is enabled and no override was supplied (see create-gen-search.ts).
export function requireEnv(key: string): string {
  const value = process.env[key];
  if (!value) {
    throw new GenSearchConfigError(`Missing required environment variable "${key}"`);
  }
  return value;
}
