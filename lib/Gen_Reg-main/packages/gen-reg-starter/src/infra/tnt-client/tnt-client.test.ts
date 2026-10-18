// src/infra/tnt-client/tnt-client.test.ts
import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpTntClient } from "./tnt-client.ts";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      status,
      ok: status >= 200 && status < 300,
      json: async () => body,
      text: async () => JSON.stringify(body),
    }),
  );
}

describe("TntClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("isSlugTaken returns false on 404", async () => {
    mockFetchOnce(404, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.isSlugTaken("free-slug")).toBe(false);
  });

  it("isSlugTaken returns true on 200", async () => {
    mockFetchOnce(200, { id: "t1" });
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.isSlugTaken("taken-slug")).toBe(true);
  });

  it("createTenant returns the tenant on 202", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "PROVISIONING", region: null, primaryOwnerUserId: "u1", provisioningJobId: "j1", createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(202, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    const result = await client.createTenant({ name: "Acme", slug: "acme", primaryOwnerUserId: "u1", idempotencyKey: "s1" });
    expect(result).toEqual(tenant);
  });

  it("createTenant throws GenTntProvisioningError on a non-202 response", async () => {
    mockFetchOnce(409, { error: "duplicate_slug" });
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(
      client.createTenant({ name: "Acme", slug: "acme", primaryOwnerUserId: "u1", idempotencyKey: "s1" }),
    ).rejects.toThrow(/Gen_TNT createTenant failed/);
  });

  it("getJob returns the job on 200", async () => {
    const job = { id: "j1", tenantId: "t1", status: "COMPLETED", retryCount: 0, lastError: null, startedAt: null, completedAt: null, expiresAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, job);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.getJob("j1")).toEqual(job);
  });
});
