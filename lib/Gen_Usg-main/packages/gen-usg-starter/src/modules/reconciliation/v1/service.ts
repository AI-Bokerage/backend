import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";
import type { IReconciliationRepo } from "../../../domain/ports/reconciliation.repository.port.ts";
import { listRegisteredMeterCodes, getMeterDefinition } from "../../meters/v1/registry.ts";
import { counterKey, readCurrentValue } from "../../meters/v1/keys.ts";
import { logger } from "../../../common/logger.ts";

export interface ReconciliationSweepResult {
  tenantsProcessed: number;
  driftsCorrected: number;
}

export class ReconciliationService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly meterRepo: IMeterRepo,
    private readonly reconciliationRepo: IReconciliationRepo,
    private readonly driftThresholdPct: number,
  ) {}

  /**
   * "Authoritative" here means the most recent UsageSnapshot row, not true
   * ground truth from each metric's real owning system — same caveat the
   * source carried. dbValue === 0 is treated as "no baseline yet" and never
   * triggers a correction.
   */
  async runSweep(tenantIds: string[]): Promise<ReconciliationSweepResult> {
    let driftsCorrected = 0;
    const now = new Date();
    for (const tenantId of tenantIds) {
      try {
        const snapshot = await this.meterRepo.latestSnapshot(tenantId);
        const dbMetrics = snapshot?.metrics ?? {};
        for (const metric of listRegisteredMeterCodes()) {
          // readCurrentValue routes mode:"resource" meters to their sorted-set
          // sum instead of reading a plain (nonexistent) counter key for them.
          const counterValue = BigInt(Math.trunc(await readCurrentValue(this.counterStore, tenantId, metric)));
          const dbValue = BigInt(Math.trunc(dbMetrics[metric] ?? 0));
          const driftPct = dbValue === 0n ? 0 : Math.abs(Number(counterValue - dbValue) / Number(dbValue)) * 100;

          let corrected = false;
          if (driftPct > this.driftThresholdPct && dbValue !== 0n) {
            // Resource-mode meters are exact-by-construction from zadd/zrem —
            // there's no separate "counter" for them to drift away from, and
            // no single "set the sorted set back to X" operation, so the
            // correction write-back only applies to counter-mode metrics.
            // We still log the drift (with corrected: false) for the audit trail.
            if (getMeterDefinition(metric)?.mode !== "resource") {
              await this.counterStore.setBatch([{ key: counterKey(tenantId, metric), value: dbValue.toString(), ttlSec: 315360000 }]);
              corrected = true;
              driftsCorrected += 1;
            }
          }
          // usage_reconciliation_log.drift_pct is Decimal(6,2) (max 9999.99);
          // clamp what's written so a huge drift doesn't overflow and throw,
          // aborting every remaining metric for this tenant. The correction
          // threshold comparison above still uses the unclamped driftPct.
          const clampedDriftPct = Math.min(driftPct, 9999.99);
          await this.reconciliationRepo.insertLog({ tenantId, metric, counterValue, dbValue, driftPct: clampedDriftPct, corrected, runAt: now });
        }
      } catch (err) {
        logger.error({ err, tenantId }, "[gen-usg] reconciliation.tenant.failed");
      }
    }
    return { tenantsProcessed: tenantIds.length, driftsCorrected };
  }
}
