import { describe, it, expect, vi } from "vitest";
import { SearchQueryService } from "./service.ts";
import { SearchQueryTooBroadError, SearchInvalidQueryError, SearchIndexUnavailableError } from "../../../common/errors.ts";
import { AllowAllVisibilityFilter } from "../../../domain/ports/visibility-filter.port.ts";
import type { ISearchQueryEngine } from "../../../domain/ports/search-query.port.ts";

function makeEngine(results: { entityType: string; entityId: string; title: string; score: number }[]): ISearchQueryEngine {
  return { search: vi.fn().mockResolvedValue(results) };
}

describe("SearchQueryService", () => {
  it("rejects a wildcard-only query", async () => {
    const service = new SearchQueryService({
      backends: {},
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });
    await expect(service.search("t1", { q: "***", limit: 20, offset: 0 })).rejects.toThrow(SearchQueryTooBroadError);
  });

  it("rejects an unknown entity type filter", async () => {
    const service = new SearchQueryService({
      backends: {},
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });
    await expect(service.search("t1", { q: "acme", type: "ghost", limit: 20, offset: 0 })).rejects.toThrow(SearchInvalidQueryError);
  });

  it("merges results from multiple backends, sorted by score desc, and applies limit", async () => {
    const pgEngine = makeEngine([{ entityType: "lead", entityId: "l1", title: "Acme Lead", score: 0.5 }]);
    const osEngine = makeEngine([{ entityType: "file", entityId: "f1", title: "Acme Contract", score: 0.9 }]);

    const service = new SearchQueryService({
      backends: { pg: pgEngine, opensearch: osEngine },
      entities: { lead: { backend: "pg" }, file: { backend: "opensearch" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    const results = await service.search("t1", { q: "acme", limit: 1, offset: 0 });
    expect(results).toHaveLength(1);
    expect(results[0].entityId).toBe("f1");
  });

  it("drops results the visibility filter rejects", async () => {
    const engine = makeEngine([
      { entityType: "lead", entityId: "visible", title: "A", score: 1 },
      { entityType: "lead", entityId: "hidden", title: "B", score: 0.5 },
    ]);

    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: { isVisible: ({ entityId }) => entityId === "visible" },
      timeoutMs: 1000,
    });

    const results = await service.search("t1", { q: "acme", limit: 20, offset: 0 });
    expect(results.map((r) => r.entityId)).toEqual(["visible"]);
  });

  it("wraps a backend failure in SearchIndexUnavailableError", async () => {
    const engine: ISearchQueryEngine = { search: vi.fn().mockRejectedValue(new Error("connection refused")) };
    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    await expect(service.search("t1", { q: "acme", limit: 20, offset: 0 })).rejects.toThrow(SearchIndexUnavailableError);
  });

  it("skips the first `offset` visible results, sorted by score desc", async () => {
    const engine = makeEngine([
      { entityType: "lead", entityId: "a", title: "A", score: 3 },
      { entityType: "lead", entityId: "b", title: "B", score: 2 },
      { entityType: "lead", entityId: "c", title: "C", score: 1 },
    ]);

    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    const results = await service.search("t1", { q: "acme", limit: 1, offset: 1 });
    expect(results.map((r) => r.entityId)).toEqual(["b"]);
  });

  it("offset counts only visible results, not raw merged results", async () => {
    const engine = makeEngine([
      { entityType: "lead", entityId: "a", title: "A", score: 4 },
      { entityType: "lead", entityId: "hidden", title: "Hidden", score: 3 },
      { entityType: "lead", entityId: "b", title: "B", score: 2 },
    ]);

    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: { isVisible: ({ entityId }) => entityId !== "hidden" },
      timeoutMs: 1000,
    });

    const results = await service.search("t1", { q: "acme", limit: 1, offset: 1 });
    expect(results.map((r) => r.entityId)).toEqual(["b"]);
  });

  it("requests offset + limit rows from each backend", async () => {
    const engine = makeEngine([]);
    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    await service.search("t1", { q: "acme", limit: 10, offset: 40 });
    expect(engine.search).toHaveBeenCalledWith(expect.objectContaining({ limit: 50 }));
  });

  it("deduplicates by (entityType, entityId), keeping the highest score", async () => {
    const engine = makeEngine([
      { entityType: "lead", entityId: "dup", title: "Old", score: 0.4 },
      { entityType: "lead", entityId: "dup", title: "New", score: 0.9 },
    ]);

    const service = new SearchQueryService({
      backends: { pg: engine },
      entities: { lead: { backend: "pg" } },
      visibilityFilter: new AllowAllVisibilityFilter(),
      timeoutMs: 1000,
    });

    const results = await service.search("t1", { q: "acme", limit: 20, offset: 0 });
    expect(results).toHaveLength(1);
    expect(results[0].score).toBe(0.9);
  });
});
