import { describe, it, expect, afterAll, beforeAll } from "vitest";
import request from "supertest";
import { createGenSla } from "./create-gen-sla.ts";
import { GenSlaConfigError } from "./common/errors.ts";
import { startTestPostgres } from "../tests/support/postgres-container.ts";
import type { StartedPostgreSqlContainer } from "@testcontainers/postgresql";

describe("createGenSla", () => {
  it("throws GenSlaConfigError when DATABASE_URL is missing and no repo override is supplied", () => {
    const original = process.env.DATABASE_URL;
    delete process.env.DATABASE_URL;
    expect(() => createGenSla({ internalSecret: "test-secret" })).toThrow(GenSlaConfigError);
    if (original !== undefined) process.env.DATABASE_URL = original;
  });

  it("throws GenSlaConfigError when internalSecret is missing and GEN_SLA_INTERNAL_SECRET is unset", () => {
    const original = process.env.GEN_SLA_INTERNAL_SECRET;
    delete process.env.GEN_SLA_INTERNAL_SECRET;
    process.env.DATABASE_URL = "postgresql://user:pass@localhost:5440/gensla";
    expect(() => createGenSla({})).toThrow(GenSlaConfigError);
    if (original !== undefined) process.env.GEN_SLA_INTERNAL_SECRET = original;
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

    it("mounts policies/instances/metrics behind the internal-secret + tenantId guards", async () => {
      const { app } = createGenSla({ internalSecret: "test-secret", modules: { worker: false } });
      const tenantId = "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11";

      await request(app).get(`/api/v1/sla/${tenantId}/policies`).expect(401);

      const listRes = await request(app)
        .get(`/api/v1/sla/${tenantId}/policies`)
        .set("X-Internal-Secret", "test-secret")
        .expect(200);
      expect(listRes.body.data).toEqual([]);

      const createRes = await request(app)
        .post(`/api/v1/sla/${tenantId}/policies`)
        .set("X-Internal-Secret", "test-secret")
        .send({ name: "First Response", entityType: "TICKET", slaType: "FIRST_RESPONSE", durationMins: 60, warningMins: 45 })
        .expect(201);
      expect(createRes.body.entityType).toBe("TICKET");
    });

    it("404s the worker-only routes when modules.instances is disabled", async () => {
      const { app } = createGenSla({ internalSecret: "test-secret", modules: { instances: false, worker: false } });
      const tenantId = "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11";
      await request(app).get(`/api/v1/sla/${tenantId}/instances`).set("X-Internal-Secret", "test-secret").expect(404);
    });
  });
});
