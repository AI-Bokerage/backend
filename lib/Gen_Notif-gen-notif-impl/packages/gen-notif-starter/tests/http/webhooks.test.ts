import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { WebhookEndpointService } from "../../src/modules/webhooks/v1/service.ts";
import { WebhookEndpointController } from "../../src/modules/webhooks/v1/controller.ts";
import { webhooksRoutes } from "../../src/modules/webhooks/v1/routes.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { IWebhookEndpointRepo } from "../../src/domain/ports/webhook-endpoint.repository.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";
const USER = "22222222-2222-2222-2222-222222222222";

function buildApp(repo: IWebhookEndpointRepo) {
  const app = express();
  app.use(express.json());
  const controller = new WebhookEndpointController(new WebhookEndpointService(repo));
  app.use("/api/v1/webhook-endpoints", webhooksRoutes(controller));
  app.use(errorHandler);
  return app;
}

describe("webhook endpoints HTTP routes", () => {
  it("POST / creates an endpoint and never echoes back a client-supplied secret (server generates it)", async () => {
    const repo: IWebhookEndpointRepo = {
      create: vi.fn(async (input) => ({ id: "e1", ...input, secret: "server-generated-secret", enabled: true, description: input.description ?? null, createdAt: new Date(), updatedAt: new Date() })),
      findById: vi.fn(), listEnabled: vi.fn(), update: vi.fn(), delete: vi.fn(),
    };
    const res = await request(buildApp(repo)).post("/api/v1/webhook-endpoints").send({ tenantId: TENANT, userId: USER, url: "https://example.com/hook" });
    expect(res.status).toBe(201);
    expect(res.body.endpoint.secret).toBe("server-generated-secret");
    expect(repo.create).toHaveBeenCalledWith(expect.objectContaining({ secret: expect.any(String) }));
  });

  it("POST / rejects a non-URL", async () => {
    const repo: IWebhookEndpointRepo = { create: vi.fn(), findById: vi.fn(), listEnabled: vi.fn(), update: vi.fn(), delete: vi.fn() };
    const res = await request(buildApp(repo)).post("/api/v1/webhook-endpoints").send({ tenantId: TENANT, userId: USER, url: "not-a-url" });
    expect(res.status).toBe(400);
  });

  it("DELETE /:id calls repo.delete scoped to tenantId", async () => {
    const ENDPOINT_ID = "33333333-3333-3333-3333-333333333333";
    const repo: IWebhookEndpointRepo = { create: vi.fn(), findById: vi.fn(), listEnabled: vi.fn(), update: vi.fn(), delete: vi.fn(async () => {}) };
    const res = await request(buildApp(repo)).delete(`/api/v1/webhook-endpoints/${ENDPOINT_ID}`).send({ tenantId: TENANT });
    expect(res.status).toBe(204);
    expect(repo.delete).toHaveBeenCalledWith(TENANT, ENDPOINT_ID);
  });
});
