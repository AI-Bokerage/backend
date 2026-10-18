import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo, RollupPeriod } from "../../../domain/ports/meter.repository.port.ts";
import { listRegisteredMeterCodes } from "../../meters/v1/registry.ts";
import { readCurrentValue } from "../../meters/v1/keys.ts";
import { logger } from "../../../common/logger.ts";

export interface RollupResult {
  period: RollupPeriod;
  tenantsProcessed: number;
  failures: number;
}

export class RollupService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly meterRepo: IMeterRepo,
  ) {}

  async runRollup(tenantIds: string[], period: RollupPeriod): Promise<RollupResult> {
    const metricCodes = listRegisteredMeterCodes();
    const snapshotAt = new Date();
    let failures = 0;
    for (const tenantId of tenantIds) {
      try {
        // Per-metric reads (not a plain mget across counter keys) because
        // mode:"resource" meters store their real value in a sorted set at
        // a different key — readCurrentValue knows how to route each metric.
        const values = await Promise.all(
          metricCodes.map((code) => readCurrentValue(this.counterStore, tenantId, code)),
        );
        const metrics: Record<string, number> = {};
        metricCodes.forEach((code, i) => { metrics[code] = values[i]; });
        await this.meterRepo.insertSnapshot(tenantId, snapshotAt, period, metrics);
      } catch (err) {
        failures += 1;
        logger.error({ err, tenantId }, "[gen-usg] rollup.tenant.failed");
      }
    }
    return { period, tenantsProcessed: tenantIds.length, failures };
  }
}
