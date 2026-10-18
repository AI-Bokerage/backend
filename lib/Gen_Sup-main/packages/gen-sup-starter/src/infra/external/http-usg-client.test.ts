import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpUsgClient, UsgHttpError } from "./http-usg-client.ts";
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

describe("HttpUsgClient", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("getSummary returns the summary on 200", async () => {
    const summary = {
      tenantId: "t1",
      meters: [{ metric: "api_calls", unit: "count", current: 500, limit: 10000, pct: 5 }],
      trend: [{ snapshotAt: "2026-08-01T00:00:00Z", period: "daily", metrics: { api_calls: 500 } }],
    };
    mockFetchOnce(200, summary);
    const client = new HttpUsgClient("http://gen-usg");
    expect(await client.getSummary("t1")).toEqual(summary);
  });

  it("getSummary throws UsgHttpError on a non-2xx status", async () => {
    mockFetchOnce(404, { error: "not found" });
    const client = new HttpUsgClient("http://gen-usg");
    await expect(client.getSummary("missing")).rejects.toMatchObject({ status: 404 });
  });

  it("getSummary throws UsgHttpError on a 5xx status", async () => {
    mockFetchOnce(500, { error: "boom" });
    const client = new HttpUsgClient("http://gen-usg");
    await expect(client.getSummary("t1")).rejects.toMatchObject({ status: 500 });
  });

  it("does not send an X-Internal-Secret header (Gen_USG's summary route is unauthenticated)", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 200, ok: true, json: async () => ({ tenantId: "t1", meters: [], trend: [] }), text: async () => "{}" });
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpUsgClient("http://gen-usg");
    await client.getSummary("t1");
    const [, opts] = fetchMock.mock.calls[0];
    expect(opts?.headers).toBeUndefined();
  });

  it("logs the actual response body text on failure (not swallowed to empty)", async () => {
    mockFetchOnce(500, { error: "boom" });
    const warnSpy = vi.spyOn(logger, "warn").mockImplementation(() => undefined as never);
    const client = new HttpUsgClient("http://gen-usg");
    await expect(client.getSummary("t1")).rejects.toThrow(UsgHttpError);
    expect(warnSpy).toHaveBeenCalledWith(
      expect.objectContaining({ status: 500, body: JSON.stringify({ error: "boom" }) }),
      expect.any(String),
    );
    warnSpy.mockRestore();
  });
});
