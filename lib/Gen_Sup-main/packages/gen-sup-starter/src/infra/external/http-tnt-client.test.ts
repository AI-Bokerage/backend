import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpTntClient, TntHttpError } from "./http-tnt-client.ts";
import { logger } from "../../common/logger.ts";

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

describe("HttpTntClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("createTenant returns the tenant on 202", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "PROVISIONING", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: "j1", createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(202, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    const result = await client.createTenant({ name: "Acme", slug: "acme", region: "us-east-1", primaryOwnerUserId: "u1" });
    expect(result).toEqual(tenant);
  });

  it("createTenant throws TntHttpError with status 409 on a duplicate slug", async () => {
    mockFetchOnce(409, { error: "duplicate_slug" });
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(
      client.createTenant({ name: "Acme", slug: "acme", region: "us-east-1", primaryOwnerUserId: "u1" }),
    ).rejects.toMatchObject({ status: 409 });
  });

  it("getTenant returns null on 404", async () => {
    mockFetchOnce(404, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.getTenant("missing")).toBeNull();
  });

  it("getTenant returns the tenant on 200", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "ACTIVE", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: null, createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.getTenant("t1")).toEqual(tenant);
  });

  it("getTenantBySlug returns null on 404", async () => {
    mockFetchOnce(404, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.getTenantBySlug("free-slug")).toBeNull();
  });

  it("suspendTenant returns the tenant on 200", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "SUSPENDED", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: null, createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.suspendTenant("t1")).toEqual(tenant);
  });

  it("suspendTenant throws TntHttpError with status 404 when the tenant doesn't exist", async () => {
    mockFetchOnce(404, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(client.suspendTenant("missing")).rejects.toMatchObject({ status: 404 });
  });

  it("suspendTenant throws TntHttpError with status 409 on an illegal transition", async () => {
    mockFetchOnce(409, {});
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(client.suspendTenant("t1")).rejects.toMatchObject({ status: 409 });
  });

  it("reactivateTenant returns the tenant on 200", async () => {
    const tenant = { id: "t1", slug: "acme", name: "Acme", status: "ACTIVE", region: "us-east-1", primaryOwnerUserId: "u1", provisioningJobId: null, createdAt: "2026-01-01T00:00:00Z" };
    mockFetchOnce(200, tenant);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    expect(await client.reactivateTenant("t1")).toEqual(tenant);
  });

  it("logs the actual response body text on failure (not swallowed to empty)", async () => {
    mockFetchOnce(500, { error: "boom" });
    const warnSpy = vi.spyOn(logger, "warn").mockImplementation(() => undefined as never);
    const client = new HttpTntClient("http://gen-tnt", "secret");
    await expect(client.getTenant("t1")).rejects.toThrow(TntHttpError);
    expect(warnSpy).toHaveBeenCalledWith(
      expect.objectContaining({ status: 500, body: JSON.stringify({ error: "boom" }) }),
      expect.any(String),
    );
    warnSpy.mockRestore();
  });

  it("sends X-Internal-Secret on every request", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({}), text: async () => "{}" });
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpTntClient("http://gen-tnt", "s3cret");
    await client.getTenant("t1");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gen-tnt/api/v1/tenants/t1",
      expect.objectContaining({ headers: expect.objectContaining({ "X-Internal-Secret": "s3cret" }) }),
    );
  });
});
