import type { PrismaClient } from "../../../__generated__/prisma/index.js";
import type { IEventPublisher, EventEnvelope } from "../../domain/ports/event-publisher.port.ts";
import type { IDistributedLock } from "../../domain/ports/distributed-lock.port.ts";
import { logger } from "../../common/logger.ts";

export interface OutboxDispatcherConfig {
  pollIntervalMs: number;
  batchSize: number;
  maxRetries: number;
}

const OUTBOX_LOCK_KEY = "gen-sla:lock:outbox-dispatcher";

export function startOutboxDispatcher(
  prisma: PrismaClient,
  publisher: IEventPublisher,
  lock: IDistributedLock,
  config: OutboxDispatcherConfig,
): { close: () => void } {
  let running = true;
  const lockTtlS = Math.ceil(config.pollIntervalMs / 1000) + 5;

  async function pollOnce(): Promise<void> {
    if (!running) return;

    // ponytail: one coarse lock for the whole batch rather than per-event — the timer
    // worker locks per-instance because instances are independent; outbox rows are a
    // single ordered queue, so one replica owning the whole poll tick is simpler and correct.
    const acquired = await lock.acquire(OUTBOX_LOCK_KEY, lockTtlS);
    if (!acquired) return;

    const events = await prisma.slaOutboxEvent.findMany({
      where: { status: "PENDING" },
      orderBy: { createdAt: "asc" },
      take: config.batchSize,
    });

    for (const event of events) {
      if (!running) break;

      if (event.retryCount >= config.maxRetries) {
        await prisma.slaOutboxEvent.update({ where: { id: event.id }, data: { status: "FAILED", updatedAt: new Date() } });
        logger.error({ eventId: event.id, eventType: event.eventType }, "[outbox] permanent failure — max retries exceeded");
        continue;
      }

      try {
        await publisher.publish(event.exchange, event.routingKey, event.payload as unknown as EventEnvelope);
        await prisma.slaOutboxEvent.update({ where: { id: event.id }, data: { status: "PUBLISHED", updatedAt: new Date() } });
      } catch (err) {
        const nextRetry = event.retryCount + 1;
        const newStatus = nextRetry >= config.maxRetries ? "FAILED" : "PENDING";
        await prisma.slaOutboxEvent.update({ where: { id: event.id }, data: { retryCount: nextRetry, status: newStatus, updatedAt: new Date() } });
        logger.warn({ eventId: event.id, eventType: event.eventType, attempt: nextRetry, err }, "[outbox] publish failure");
      }
    }
  }

  const intervalId = setInterval(() => {
    pollOnce().catch((err) => logger.error({ err }, "[outbox] unhandled error in poll"));
  }, config.pollIntervalMs);

  logger.info("[outbox] dispatcher started");

  return {
    close: () => {
      running = false;
      clearInterval(intervalId);
      logger.info("[outbox] dispatcher stopped");
    },
  };
}
