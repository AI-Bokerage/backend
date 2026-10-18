import { describe, it, expect, vi } from "vitest";
import { GraceOverageService } from "./service.ts";
import type { IGraceOverageRepo, GraceOverageRecord } from "../../../domain/ports/grace-overage.repository.port.ts";

function makeRepo(overrides: Partial<IGraceOverageRepo> = {}): IGraceOverageRepo {
  return {
    findOpen: vi.fn().mockResolvedValue(null),
    open: vi.fn(),
    bump: vi.fn(),
    listOpenForTenant: vi.fn().mockResolvedValue([]),
    listExpiredOpen: vi.fn().mockResolvedValue([]),
    close: vi.fn(),
    ...overrides,
  };
}

describe("GraceOverageService", () => {
  it("opens a fresh window when none exists", async () => {
    const now = new Date("2026-01-01T00:00:00Z");
    const opened: GraceOverageRecord = {
      id: "g1", tenantId: "t1", metric: "seats", graceStartedAt: now,
      graceExpiresAt: new Date("2026-01-08T00:00:00Z"), overageCount: 1,
      status: "OPEN", billedAt: null, createdAt: now, updatedAt: now,
    };
    const repo = makeRepo({ open: vi.fn().mockResolvedValue(opened) });
    const service = new GraceOverageService(repo);
    const result = await service.getOrOpenGraceWindow("t1", "seats", 7, now);
    expect(result.daysLeft).toBe(7);
    expect(repo.open).toHaveBeenCalledWith("t1", "seats", now, new Date("2026-01-08T00:00:00Z"));
  });

  it("bumps overageCount on an existing OPEN window instead of opening a new one", async () => {
    const now = new Date("2026-01-05T00:00:00Z");
    const existing: GraceOverageRecord = {
      id: "g1", tenantId: "t1", metric: "seats", graceStartedAt: new Date("2026-01-01T00:00:00Z"),
      graceExpiresAt: new Date("2026-01-08T00:00:00Z"), overageCount: 2,
      status: "OPEN", billedAt: null, createdAt: now, updatedAt: now,
    };
    const repo = makeRepo({ findOpen: vi.fn().mockResolvedValue(existing) });
    const service = new GraceOverageService(repo);
    const result = await service.getOrOpenGraceWindow("t1", "seats", 7, now);
    expect(repo.bump).toHaveBeenCalledWith("t1", "g1");
    expect(repo.open).not.toHaveBeenCalled();
    expect(result.daysLeft).toBe(3);
  });

  it("closeExpiredGraceWindows closes every expired row (per tenant) and returns the closed list", async () => {
    const now = new Date("2026-01-10T00:00:00Z");
    const expired: GraceOverageRecord = {
      id: "g2", tenantId: "t2", metric: "api_calls", graceStartedAt: new Date("2026-01-01T00:00:00Z"),
      graceExpiresAt: new Date("2026-01-08T00:00:00Z"), overageCount: 9,
      status: "OPEN", billedAt: null, createdAt: now, updatedAt: now,
    };
    const repo = makeRepo({
      listExpiredOpen: vi.fn().mockImplementation(async (tenantId: string) => (tenantId === "t2" ? [expired] : [])),
    });
    const service = new GraceOverageService(repo);
    const closed = await service.closeExpiredGraceWindows(["t1", "t2"], now);
    expect(repo.listExpiredOpen).toHaveBeenCalledWith("t1", now);
    expect(repo.listExpiredOpen).toHaveBeenCalledWith("t2", now);
    expect(repo.close).toHaveBeenCalledWith("t2", "g2");
    expect(closed).toEqual([{ tenantId: "t2", metric: "api_calls", overageCount: 9 }]);
  });

  it("closeExpiredGraceWindows returns an empty list when nothing expired", async () => {
    const repo = makeRepo();
    const service = new GraceOverageService(repo);
    expect(await service.closeExpiredGraceWindows(["t1"], new Date())).toEqual([]);
  });
});
