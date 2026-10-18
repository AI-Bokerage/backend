import type { IGraceOverageRepo } from "../../../domain/ports/grace-overage.repository.port.ts";
import { logger } from "../../../common/logger.ts";

export interface GraceWindow {
  expiresAt: Date;
  daysLeft: number;
}

export interface ClosedGraceWindow {
  tenantId: string;
  metric: string;
  overageCount: number;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

function daysLeftUntil(expiresAt: Date, now: Date): number {
  return Math.max(0, Math.ceil((expiresAt.getTime() - now.getTime()) / MS_PER_DAY));
}

export class GraceOverageService {
  constructor(private readonly repo: IGraceOverageRepo) {}

  /**
   * Caller must have already confirmed the metric is grace-eligible
   * (via the meter registry) before calling this.
   */
  async getOrOpenGraceWindow(tenantId: string, metric: string, graceWindowDays: number, now: Date): Promise<GraceWindow> {
    const existing = await this.repo.findOpen(tenantId, metric);
    if (existing) {
      await this.repo.bump(tenantId, existing.id);
      return { expiresAt: existing.graceExpiresAt, daysLeft: daysLeftUntil(existing.graceExpiresAt, now) };
    }
    const graceExpiresAt = new Date(now.getTime() + graceWindowDays * MS_PER_DAY);
    const opened = await this.repo.open(tenantId, metric, now, graceExpiresAt);
    return { expiresAt: opened.graceExpiresAt, daysLeft: daysLeftUntil(opened.graceExpiresAt, now) };
  }

  async closeExpiredGraceWindows(tenantIds: string[], now: Date): Promise<ClosedGraceWindow[]> {
    const closed: ClosedGraceWindow[] = [];
    for (const tenantId of tenantIds) {
      try {
        const expired = await this.repo.listExpiredOpen(tenantId, now);
        for (const window of expired) {
          await this.repo.close(tenantId, window.id);
          closed.push({ tenantId: window.tenantId, metric: window.metric, overageCount: window.overageCount });
        }
      } catch (err) {
        logger.error({ err, tenantId }, "[gen-usg] grace-overage.tenant.failed");
      }
    }
    return closed;
  }
}
