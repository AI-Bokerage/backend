import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { randomUUID } from "node:crypto";
import { startPostgresContainer, type TestPostgres } from "../support/postgres-container.ts";
import { PrismaTenantPreferenceRepo } from "../../src/modules/preferences/v1/repo.ts";
import { PrismaWebhookEndpointRepo } from "../../src/modules/webhooks/v1/repo.ts";

describe("Prisma repos against real Postgres", () => {
  let db: TestPostgres;
  beforeAll(async () => { db = await startPostgresContainer(); }, 60_000);
  afterAll(async () => { await db.stop(); });

  it("upserts a preference and enforces the tenant-scoped unique key", async () => {
    const repo = new PrismaTenantPreferenceRepo();
    const tenantId = randomUUID();
    const userId = randomUUID();
    const first = await repo.upsert({
      tenantId, userId, eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: false,
    });
    const second = await repo.upsert({
      tenantId, userId, eventType: "doc.uploaded", channel: "email", enabled: false, digestMode: true,
    });
    expect(second.id).toBe(first.id);
    expect(second.enabled).toBe(false);
    expect(second.digestMode).toBe(true);
  });

  it("enforces RLS: a query without app.tenant_id set sees zero rows via the public client", async () => {
    const repo = new PrismaWebhookEndpointRepo();
    const tenantId = randomUUID();
    await repo.create({ tenantId, userId: randomUUID(), url: "https://example.com/hook", secret: "s3cr3t" });
    const rawRows: unknown[] = await db.prisma.$queryRawUnsafe(`SELECT * FROM "webhook_endpoint" WHERE tenant_id = '${tenantId}'`);
    // Outside withTenant(), app.tenant_id is unset -> RLS (FORCE + USING) hides the row even from a direct query.
    expect(rawRows.length).toBe(0);
  });
});
