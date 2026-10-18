import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpFmmClient, FmmHttpError } from "./http-fmm-client.ts";
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

describe("HttpFmmClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("listFlags returns the flags array on 200", async () => {
    const flags = [{ key: "f1", moduleCode: null, defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 100, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z" }];
    mockFetchOnce(200, { flags });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.listFlags()).toEqual(flags);
  });

  it("listFlags throws FmmHttpError on a non-2xx status", async () => {
    mockFetchOnce(500, { error: "boom" });
    const client = new HttpFmmClient("http://gen-fmm");
    await expect(client.listFlags()).rejects.toMatchObject({ status: 500 });
  });

  it("updateFlag returns the updated flag on 200", async () => {
    const flag = { key: "f1", moduleCode: "billing", defaultEnabled: true, isGradualRollout: false, rolloutPercentage: 100, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-02T00:00:00Z" };
    mockFetchOnce(200, { flag });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.updateFlag("f1", { moduleCode: "billing" })).toEqual(flag);
  });

  it("updateFlag throws FmmHttpError with status 404 when the flag doesn't exist", async () => {
    mockFetchOnce(404, {});
    const client = new HttpFmmClient("http://gen-fmm");
    await expect(client.updateFlag("missing", { defaultEnabled: true })).rejects.toMatchObject({ status: 404 });
  });

  it("setOverride returns the override on 200", async () => {
    const override = { tenantId: "t1", flagKey: "f1", enabled: true, config: {}, reason: "beta", expiresAt: null, createdBy: null };
    mockFetchOnce(200, { override });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.setOverride({ tenantId: "t1", flagKey: "f1", enabled: true, reason: "beta" })).toEqual(override);
  });

  it("listOverridesForTenant returns the overrides array on 200", async () => {
    const overrides = [{ tenantId: "t1", flagKey: "f1", enabled: true, config: {}, reason: null, expiresAt: null, createdBy: null }];
    mockFetchOnce(200, { overrides });
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.listOverridesForTenant("t1")).toEqual(overrides);
  });

  it("clearOverride returns false on 404 (no override existed)", async () => {
    mockFetchOnce(404, {});
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.clearOverride("t1", "f1")).toBe(false);
  });

  it("clearOverride returns true on success", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ status: 204, ok: true, text: async () => "" }));
    const client = new HttpFmmClient("http://gen-fmm");
    expect(await client.clearOverride("t1", "f1")).toBe(true);
  });

  it("does not send an X-Internal-Secret header (Gen_FMM's catalog/override routes are unauthenticated)", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({ flags: [] }), text: async () => "{}" });
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpFmmClient("http://gen-fmm");
    await client.listFlags();
    const [, opts] = fetchMock.mock.calls[0];
    expect(opts?.headers).toBeUndefined();
  });

  it("logs the actual response body text on failure (not swallowed to empty)", async () => {
    mockFetchOnce(500, { error: "boom" });
    const warnSpy = vi.spyOn(logger, "warn").mockImplementation(() => undefined as never);
    const client = new HttpFmmClient("http://gen-fmm");
    await expect(client.listFlags()).rejects.toThrow(FmmHttpError);
    expect(warnSpy).toHaveBeenCalledWith(
      expect.objectContaining({ status: 500, body: JSON.stringify({ error: "boom" }) }),
      expect.any(String),
    );
    warnSpy.mockRestore();
  });
});
