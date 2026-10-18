import { randomUUID } from "node:crypto";
import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IIdempotencyRepo } from "../../../domain/ports/idempotency.repository.port.ts";
import { getMeterDefinition } from "../../meters/v1/registry.ts";
import { counterKey, resourceKey } from "../../meters/v1/keys.ts";
import { logger } from "../../../common/logger.ts";
import {
  MeterNotRegisteredError,
  IncrementDeltaInvalidError,
  UsageEventOutOfWindowError,
  ResourceIdRequiredError,
} from "../../../common/errors.ts";

export interface IncrementInput {
  tenantId: string;
  metric: string;
  delta: number;
  eventId?: string;
  resourceId?: string;
  idempotencyKey?: string;
  occurredAt?: Date;
}

export interface IncrementResult {
  newValue: number;
  metric: string;
  replayed: boolean;
}

export interface IncrementServiceOptions {
  backdateDays: number;
  dedupTtlSec: number;
}

export class IncrementService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly idempotencyRepo: IIdempotencyRepo,
    private readonly options: IncrementServiceOptions,
  ) {}

  async increment(input: IncrementInput): Promise<IncrementResult> {
    const def = getMeterDefinition(input.metric);
    if (!def) throw new MeterNotRegisteredError(input.metric);
    if (input.delta === 0) throw new IncrementDeltaInvalidError();

    const occurredAt = input.occurredAt ?? new Date();
    const backdateMs = this.options.backdateDays * 24 * 60 * 60 * 1000;
    if (Date.now() - occurredAt.getTime() > backdateMs) {
      throw new UsageEventOutOfWindowError(this.options.backdateDays);
    }

    const eventId = input.eventId ?? randomUUID();

    const dedupAcquired = await this.counterStore.setNX(`genusg:dedup:${eventId}`, "1", this.options.dedupTtlSec);
    if (!dedupAcquired) {
      return { newValue: 0, metric: input.metric, replayed: true };
    }

    const idempotencyKey = input.idempotencyKey ?? eventId;
    const idempotencyAcquired = await this.counterStore.setNX(`genusg:dedup:inc:${idempotencyKey}`, "1", this.options.dedupTtlSec);
    if (!idempotencyAcquired) {
      return { newValue: 0, metric: input.metric, replayed: true };
    }

    const alreadyProcessed = await this.idempotencyRepo.exists(eventId);
    if (alreadyProcessed) {
      return { newValue: 0, metric: input.metric, replayed: true };
    }

    let newValue: number;
    if (def.mode === "resource") {
      if (!input.resourceId) throw new ResourceIdRequiredError(input.metric);
      const key = resourceKey(input.tenantId, input.metric);
      if (input.delta > 0) {
        await this.counterStore.zadd(key, input.delta, input.resourceId);
      } else {
        await this.counterStore.zrem(key, input.resourceId);
      }
      newValue = await this.counterStore.zsumScores(key);
    } else {
      const key = counterKey(input.tenantId, input.metric);
      const raw = await this.counterStore.incrBy(key, input.delta);
      if (raw < 0) {
        // Bring the counter back to exactly 0 with a second incrBy (by the
        // positive inverse of the negative result) rather than a set-style
        // call — ICounterStore has no plain "set to value" method, only
        // setBatch(which always requires a positive TTL, wrong fit here).
        // ponytail: known ceiling — these two incrBy calls aren't atomic, so
        // a concurrent increment landing between them could be lost; add a
        // Lua/MULTI compare-and-set if that race ever matters in practice.
        await this.counterStore.incrBy(key, -raw);
        newValue = 0;
        logger.warn({ tenantId: input.tenantId, metric: input.metric }, "[gen-usg] counter.negative.clamped");
      } else {
        newValue = raw;
      }
    }

    this.idempotencyRepo.insert(eventId).catch((err: unknown) => {
      logger.warn({ err, eventId }, "[gen-usg] idempotency ledger write failed");
    });

    return { newValue, metric: input.metric, replayed: false };
  }
}
