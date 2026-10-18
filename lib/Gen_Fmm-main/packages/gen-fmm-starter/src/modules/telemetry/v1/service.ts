import type { ITelemetryRepo, UsageEventInput, TelemetryQueryFilter } from "../../../domain/ports/telemetry.repository.port.ts";
import { logger } from "../../../common/logger.ts";

export class TelemetryService {
  private buffer: UsageEventInput[] = [];

  constructor(
    private readonly repo: ITelemetryRepo,
    private readonly bufferMax: number,
  ) {}

  record(tenantId: string, flagKey: string, enabled: boolean, reason: string, planCode: string | null): void {
    if (this.buffer.length >= this.bufferMax) {
      this.buffer.shift();
    }
    this.buffer.push({ tenantId, flagKey, enabled, reason, planCode, occurredAt: new Date() });
  }

  async flush(): Promise<number> {
    if (this.buffer.length === 0) return 0;
    const events = this.buffer.splice(0, this.buffer.length);
    try {
      return await this.repo.insertMany(events);
    } catch (err) {
      logger.error({ err }, "[gen-fmm] telemetry flush failed");
      return 0;
    }
  }

  query(tenantId: string, filter: TelemetryQueryFilter) {
    return this.repo.query(tenantId, filter);
  }

  bufferSize(): number {
    return this.buffer.length;
  }
}
