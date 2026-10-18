import { describe, it, expect, vi } from "vitest";
import { ErasureService } from "./service.ts";

describe("ErasureService", () => {
  it("sums removal counts across every configured backend", async () => {
    const service = new ErasureService({
      pg: { upsert: vi.fn(), bulkUpsert: vi.fn(), remove: vi.fn(), removeByOwner: vi.fn().mockResolvedValue(3), removeByEntityType: vi.fn() },
      opensearch: { upsert: vi.fn(), bulkUpsert: vi.fn(), remove: vi.fn(), removeByOwner: vi.fn().mockResolvedValue(2), removeByEntityType: vi.fn() },
    });

    const result = await service.removeUserDocuments("t1", "owner-1");
    expect(result.removed).toBe(5);
  });
});
