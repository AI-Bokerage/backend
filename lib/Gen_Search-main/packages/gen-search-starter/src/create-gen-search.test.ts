import { describe, it, expect, afterAll, beforeAll } from "vitest";
import request from "supertest";
import { createGenSearch } from "./create-gen-search.ts";
import { GenSearchConfigError } from "./common/errors.ts";
import { PgFtsAdapter } from "./infra/search/pg-fts.adapter.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { startTestPostgres } from "../tests/support/postgres-container.ts";
import type { StartedPostgreSqlContainer } from "@testcontainers/postgresql";

describe("createGenSearch", () => {
  it("throws GenSearchConfigError when entities is empty", () => {
    expect(() => createGenSearch({ entities: {}, internalSecret: "test-secret" })).toThrow(GenSearchConfigError);
  });

  it("throws GenSearchConfigError when internalSecret is missing and GEN_SEARCH_INTERNAL_SECRET is unset", () => {
    const original = process.env.GEN_SEARCH_INTERNAL_SECRET;
    delete process.env.GEN_SEARCH_INTERNAL_SECRET;
    process.env.DATABASE_URL = "postgresql://user:pass@localhost:5442/gensearch";
    expect(() => createGenSearch({ entities: { lead: { backend: "pg" } }, modules: { reindex: false, worker: false } })).toThrow(GenSearchConfigError);
    if (original !== undefined) process.env.GEN_SEARCH_INTERNAL_SECRET = original;
  });

  it("throws GenSearchConfigError when modules.reindex is enabled without a reindexSource", () => {
    process.env.DATABASE_URL = "postgresql://user:pass@localhost:5442/gensearch";
    process.env.VALKEY_URL = "redis://localhost:6386";
    expect(() => createGenSearch({ entities: { lead: { backend: "pg" } }, internalSecret: "test-secret", modules: { worker: false } })).toThrow(GenSearchConfigError);
  });

  describe("with a real database", () => {
    let container: StartedPostgreSqlContainer;

    beforeAll(async () => {
      const started = await startTestPostgres();
      container = started.container;
      process.env.DATABASE_URL = started.databaseUrl;
    }, 60_000);

    afterAll(async () => {
      await container.stop();
    });

    it("mounts /search behind the internal-secret + tenantId guards and returns indexed results", async () => {
      const { app } = createGenSearch({
        entities: { lead: { backend: "pg" } },
        internalSecret: "test-secret",
        modules: { reindex: false, erasure: false, worker: false },
      });
      const tenantId = "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11";

      await request(app).get(`/api/v1/search/${tenantId}`).query({ q: "acme" }).expect(401);

      const emptyRes = await request(app).get(`/api/v1/search/${tenantId}`).query({ q: "acme" }).set("X-Internal-Secret", "test-secret").expect(200);
      expect(emptyRes.body.data).toEqual([]);

      const adapter = new PgFtsAdapter(getPrismaClient());
      await adapter.upsert({ tenantId, entityType: "lead", entityId: "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a12", title: "Acme Corp" });

      const foundRes = await request(app).get(`/api/v1/search/${tenantId}`).query({ q: "acme" }).set("X-Internal-Secret", "test-secret").expect(200);
      expect(foundRes.body.data).toHaveLength(1);
      expect(foundRes.body.data[0].title).toBe("Acme Corp");
    });

    it("erasure removes documents by owner across the pg backend", async () => {
      const { app } = createGenSearch({
        entities: { lead: { backend: "pg" } },
        internalSecret: "test-secret",
        modules: { query: false, reindex: false, worker: false },
      });
      const tenantId = "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a13";
      const ownerId = "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a14";

      const adapter = new PgFtsAdapter(getPrismaClient());
      await adapter.upsert({ tenantId, entityType: "lead", entityId: "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a15", title: "Owned Lead", ownerId });

      const res = await request(app)
        .post(`/api/v1/search/${tenantId}/erasure`)
        .set("X-Internal-Secret", "test-secret")
        .send({ ownerId })
        .expect(200);

      expect(res.body.removed).toBe(1);
    });
  });
});
