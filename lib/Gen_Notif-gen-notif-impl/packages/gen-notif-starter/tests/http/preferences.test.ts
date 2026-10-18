import { describe, it, expect, vi, afterEach } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { PreferenceService } from "../../src/modules/preferences/v1/service.ts";
import { PreferenceController } from "../../src/modules/preferences/v1/controller.ts";
import { preferencesRoutes } from "../../src/modules/preferences/v1/routes.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import { registerTemplate, __resetRegistryForTests } from "../../src/modules/templates/v1/registry.ts";
import type { ITenantPreferenceRepo } from "../../src/domain/ports/tenant-preference.repository.port.ts";

function buildApp(repo: ITenantPreferenceRepo) {
  const app = express();
  app.use(express.json());
  const service = new PreferenceService(repo);
  const controller = new PreferenceController(service);
  app.use("/api/v1/preferences", preferencesRoutes(controller));
  app.use(errorHandler);
  return app;
}

describe("preferences HTTP routes", () => {
  afterEach(() => __resetRegistryForTests());

  it("GET / lists preferences for a tenant/user", async () => {
    const repo: ITenantPreferenceRepo = {
      findAll: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
      findByEventType: vi.fn(),
      upsert: vi.fn(),
    };
    const res = await request(buildApp(repo)).get("/api/v1/preferences").query({ tenantId: "11111111-1111-1111-1111-111111111111", userId: "22222222-2222-2222-2222-222222222222" });
    expect(res.status).toBe(200);
    expect(res.body.preferences).toHaveLength(1);
  });

  it("PATCH / rejects an invalid channel with 400", async () => {
    const repo: ITenantPreferenceRepo = { findAll: vi.fn(), findByEventType: vi.fn(), upsert: vi.fn() };
    const res = await request(buildApp(repo)).patch("/api/v1/preferences").send({
      tenantId: "11111111-1111-1111-1111-111111111111", userId: "22222222-2222-2222-2222-222222222222",
      eventType: "doc.uploaded", channel: "carrier-pigeon", enabled: true, digestMode: false,
    });
    expect(res.status).toBe(400);
  });

  it("GET /event-types reflects live-registered templates", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "t", body: "b" }));
    const repo: ITenantPreferenceRepo = { findAll: vi.fn(), findByEventType: vi.fn(), upsert: vi.fn() };
    const res = await request(buildApp(repo)).get("/api/v1/preferences/event-types");
    expect(res.body.eventTypes).toEqual(["doc.uploaded"]);
  });
});
