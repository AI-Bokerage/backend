import { describe, it, expect, vi, afterEach } from "vitest";
import { HttpTntClient } from "../../../src/infra/tnt-client/tnt-client.ts";

describe("HttpTntClient", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("notifyTenantSuspended POSTs to the configured base URL", async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new HttpTntClient({ baseUrl: "http://gen-tnt.internal" });
    await client.notifyTenantSuspended("tenant-1");
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gen-tnt.internal/internal/tenants/tenant-1/suspended",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
