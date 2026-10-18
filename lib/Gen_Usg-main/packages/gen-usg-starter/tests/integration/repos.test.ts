import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { randomUUID } from "node:crypto";
import { startPostgresContainer, type TestPostgres } from "../support/postgres-container.ts";
import { PrismaGraceOverageRepo } from "../../src/modules/grace-overage/v1/repo.ts";
import { PrismaMeterRepo } from "../../src/modules/rollup/v1/repo.ts";
import { GraceOverageService } from "../../src/modules/grace-overage/v1/service.ts";
import { withTenant } from "../../src/infra/persistence/with-tenant.ts";

describe("Prisma repos against real Postgres", () => {
  let db: TestPostgres;
  beforeAll(async () => { db = await startPostgresContainer(); }, 60_000);
  afterAll(async () => { await db.stop(); });

  it("open() then a second open() with the same (tenantId, metric, status) re-reads instead of throwing", async () => {
    const repo = new PrismaGraceOverageRepo();
    const tenantId = randomUUID();
    const started = new Date();
    const expires = new Date(started.getTime() + 7 * 24 * 60 * 60 * 1000);
    const first = await repo.open(tenantId, "active_users", started, expires);
    const second = await repo.open(tenantId, "active_users", started, expires);
    expect(second.id).toBe(first.id);
  });

  it("enforces RLS: a query without app.tenant_id set sees zero rows via a raw query", async () => {
    const repo = new PrismaMeterRepo();
    const tenantId = randomUUID();
    await repo.insertSnapshot(tenantId, new Date(), "daily", { seats: 3 });
    const rawRows: unknown[] = await db.prisma.$queryRawUnsafe(`SELECT * FROM "usage_snapshot" WHERE tenant_id = '${tenantId}'`);
    // Outside withTenant(), app.tenant_id is unset -> RLS (FORCE + USING) hides the row even from a direct query.
    expect(rawRows.length).toBe(0);
  });

  // Final-review regression coverage: bump()/close()/listExpiredOpen()/
  // latestSnapshot() used to bypass withTenant() entirely, so under FORCE RLS
  // (live in this Testcontainers harness via the genusg_app role) they ran
  // with app.tenant_id unset — the RLS policy hid every row, and Prisma's
  // update-on-zero-matched-rows raised P2025 "record not found" instead of
  // updating anything. These three tests prove the withTenant() wrapping
  // fixed that.

  it("a second grace-overage on the same tenant+metric bumps overageCount instead of 500ing", async () => {
    const repo = new PrismaGraceOverageRepo();
    const service = new GraceOverageService(repo);
    const tenantId = randomUUID();
    const now = new Date();

    const first = await service.getOrOpenGraceWindow(tenantId, "api_calls", 7, now);
    expect(first.daysLeft).toBe(7);

    // Second occurrence for the same (tenantId, metric): findOpen() sees the
    // still-OPEN window and calls repo.bump(tenantId, id) — this is the call
    // that used to run outside withTenant() and 500 via a Prisma P2025 once
    // RLS was actually enforced.
    const second = await service.getOrOpenGraceWindow(tenantId, "api_calls", 7, now);
    expect(second.expiresAt).toEqual(first.expiresAt);

    const stillOpen = await repo.findOpen(tenantId, "api_calls");
    expect(stillOpen?.overageCount).toBe(2);
  });

  it("closeExpiredGraceWindows returns an actually-expired window and its row becomes CLOSED in the DB", async () => {
    const repo = new PrismaGraceOverageRepo();
    const service = new GraceOverageService(repo);
    const tenantId = randomUUID();
    const past = new Date(Date.now() - 8 * 24 * 60 * 60 * 1000);
    const alreadyExpired = new Date(Date.now() - 1000);

    const opened = await repo.open(tenantId, "api_calls", past, alreadyExpired);

    const closed = await service.closeExpiredGraceWindows([tenantId], new Date());
    expect(closed).toEqual([{ tenantId, metric: "api_calls", overageCount: opened.overageCount }]);

    const afterClose = await repo.findOpen(tenantId, "api_calls");
    expect(afterClose).toBeNull();
    // A plain db.prisma raw query is itself subject to RLS (app.tenant_id
    // unset outside withTenant()), so it must be run through withTenant()
    // to actually see the row and confirm its status in the DB.
    const rawRows: Array<{ status: string }> = await withTenant(tenantId, (tx) =>
      tx.$queryRawUnsafe(`SELECT status FROM "usage_grace_overage" WHERE id = '${opened.id}'`),
    );
    expect(rawRows[0]?.status).toBe("CLOSED");
  });

  it("latestSnapshot round-trips a snapshot under RLS via the genusg_app role", async () => {
    const repo = new PrismaMeterRepo();
    const tenantId = randomUUID();
    const snapshotAt = new Date();
    await repo.insertSnapshot(tenantId, snapshotAt, "daily", { seats: 3, api_calls: 42 });

    const latest = await repo.latestSnapshot(tenantId);
    expect(latest).not.toBeNull();
    expect(latest?.tenantId).toBe(tenantId);
    expect(latest?.metrics).toEqual({ seats: 3, api_calls: 42 });
  });
});
