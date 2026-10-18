import { describe, it, expect, vi } from "vitest";
import { MetricsService } from "./service.ts";
import type { IMetricsRepo } from "../../../domain/ports/metrics-repo.port.ts";

function fakeRepo(overrides: Partial<IMetricsRepo> = {}): IMetricsRepo {
  return {
    getSummary: vi.fn(async () => ({ ACTIVE: 1, WARNING: 0, BREACHED: 0, RESOLVED: 0, CANCELLED: 0 })),
    getComplianceRates: vi.fn(async () => [{ entityType: "TICKET", slaType: "FIRST_RESPONSE", total: 10, breached: 2, resolved: 8 }]),
    getRecentBreaches: vi.fn(async () => []),
    getTrends: vi.fn(async () => []),
    ...overrides,
  };
}

describe("MetricsService", () => {
  it("getSummary passes through repo counts", async () => {
    const service = new MetricsService(fakeRepo());
    const result = await service.getSummary("tenant-1", {});
    expect(result.totalActive).toBe(1);
  });

  it("getComplianceRates computes compliancePct as (total-breached)/total * 100", async () => {
    const service = new MetricsService(fakeRepo());
    const [rate] = await service.getComplianceRates("tenant-1", {});
    expect(rate.compliancePct).toBe(80);
  });

  it("getComplianceRates defaults compliancePct to 100 when total is 0", async () => {
    const repo = fakeRepo({ getComplianceRates: vi.fn(async () => [{ entityType: "TICKET", slaType: "X", total: 0, breached: 0, resolved: 0 }]) });
    const service = new MetricsService(repo);
    const [rate] = await service.getComplianceRates("tenant-1", {});
    expect(rate.compliancePct).toBe(100);
  });
});
