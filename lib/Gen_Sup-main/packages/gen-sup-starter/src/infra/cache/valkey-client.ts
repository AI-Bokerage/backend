import { Redis } from "ioredis";
import { logger } from "../../common/logger.ts";

export function createValkeyClient(url: string): Redis {
  const client = new Redis(url, {
    // Don't crash the whole process if Valkey is briefly unreachable —
    // DashboardService treats cache failures as non-fatal (computes live instead).
    maxRetriesPerRequest: 1,
    retryStrategy: (times: number) => Math.min(times * 200, 2000),
  });

  // Without a listener, ioredis emits unhandled "error" events straight to
  // stderr (bypassing the structured logger) on every failed reconnect.
  client.on("error", (err) => logger.warn({ err }, "[Valkey] connection error"));

  return client;
}
