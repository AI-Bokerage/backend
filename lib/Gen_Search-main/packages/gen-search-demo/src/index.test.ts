import { describe, it, expect } from "vitest";
import request from "supertest";
import { createGenSearch } from "@gen-ms/gen-search-starter";

describe("gen-search-demo wiring", () => {
  it("createGenSearch mounts a working /health endpoint with minimal config", () => {
    process.env.DATABASE_URL ??= "postgresql://user:pass@localhost:5442/gensearch";
    process.env.GEN_SEARCH_INTERNAL_SECRET ??= "demo-secret";
    process.env.VALKEY_URL ??= "redis://localhost:6386";

    const { app } = createGenSearch({
      entities: { lead: { backend: "pg" } },
      reindexSource: async function* () {},
      modules: { worker: false },
    });
    return request(app).get("/health").expect(200, { status: "ok" });
  });
});
