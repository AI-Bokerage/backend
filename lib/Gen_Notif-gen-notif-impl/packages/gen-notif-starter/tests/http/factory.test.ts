import { describe, it, expect, afterEach } from "vitest";
import request from "supertest";
import { createGenNotif } from "../../src/create-gen-notif.ts";
import { GenNotifConfigError } from "../../src/common/errors.ts";
import type { ITenantPreferenceRepo } from "../../src/domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "../../src/domain/ports/notification-log.repository.port.ts";
import type { IDigestQueueRepo } from "../../src/domain/ports/digest-queue.repository.port.ts";
import type { IWebhookEndpointRepo } from "../../src/domain/ports/webhook-endpoint.repository.port.ts";
import type { IEmailSender } from "../../src/domain/ports/email-sender.port.ts";
import type { IRealtimeGateway } from "../../src/domain/ports/realtime-gateway.port.ts";
import type { IJwtVerifier } from "../../src/domain/ports/jwt-verifier.port.ts";

const noopPreferenceRepo: ITenantPreferenceRepo = { findAll: async () => [], findByEventType: async () => [], upsert: async (i) => ({ id: "p1", ...i, createdAt: new Date(), updatedAt: new Date() }) };
const noopNotificationRepo: INotificationLogRepo = { create: async (i) => ({ id: "n1", ...i, readAt: null, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(), entityRefType: i.entityRefType ?? null, entityRefId: i.entityRefId ?? null }), findById: async () => null, findUnread: async () => [], markRead: async () => null, markStatus: async () => {} };
const noopDigestRepo: IDigestQueueRepo = { enqueue: async () => {}, claimDue: async () => [], markSent: async () => {}, markFailed: async () => {} };
const noopWebhookRepo: IWebhookEndpointRepo = { create: async (i) => ({ id: "w1", ...i, secret: "s", enabled: true, description: i.description ?? null, createdAt: new Date(), updatedAt: new Date() }), findById: async () => null, listEnabled: async () => [], update: async (_t, id) => ({ id, tenantId: "t", userId: "u", url: "https://x", secret: "s", enabled: true, description: null, createdAt: new Date(), updatedAt: new Date() }), delete: async () => {} };
const noopEmailSender: IEmailSender = { send: async () => {} };
const noopRealtimeGateway: IRealtimeGateway = { attach: () => {}, publishInApp: async () => {} };
const noopJwtVerifier: IJwtVerifier = { verify: async () => ({ sub: "u", tenantId: "t", roles: [] }) };

const fullOverrides = {
  preferenceRepo: noopPreferenceRepo, notificationRepo: noopNotificationRepo, digestRepo: noopDigestRepo,
  webhookRepo: noopWebhookRepo, emailSender: noopEmailSender, realtimeGateway: noopRealtimeGateway, jwtVerifier: noopJwtVerifier,
  internalSecret: "test-secret",
};

describe("createGenNotif", () => {
  afterEach(() => { delete process.env.DATABASE_URL; });

  it("boots cleanly with every port overridden and no env set", () => {
    expect(() => createGenNotif(fullOverrides)).not.toThrow();
  });

  it("throws GenNotifConfigError when a Prisma-backed repo has no DATABASE_URL", () => {
    expect(() => createGenNotif({ ...fullOverrides, preferenceRepo: undefined })).toThrow(GenNotifConfigError);
  });

  it("GET /health returns ok without any auth", async () => {
    const instance = createGenNotif(fullOverrides);
    const res = await request(instance.app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: "ok" });
  });

  it("a disabled module's routes genuinely 404", async () => {
    const instance = createGenNotif({ ...fullOverrides, modules: { preferences: false } });
    const res = await request(instance.app).get("/api/v1/preferences").query({ tenantId: "t", userId: "u" });
    expect(res.status).toBe(404);
  });

  it("POST /internal/notify without the internal secret is 401", async () => {
    const instance = createGenNotif(fullOverrides);
    const res = await request(instance.app).post("/internal/notify").send({ tenantId: "t", recipients: [], eventType: "x", data: {} });
    expect(res.status).toBe(401);
  });

  it("modules.realtime = false omits attachRealtime", () => {
    const instance = createGenNotif({ ...fullOverrides, modules: { realtime: false } });
    expect(instance.attachRealtime).toBeUndefined();
  });

  it("GET /docs.json returns the OpenAPI spec", async () => {
    const instance = createGenNotif(fullOverrides);
    const res = await request(instance.app).get("/docs.json");
    expect(res.status).toBe(200);
    expect(res.body.openapi).toBe("3.0.3");
    expect(res.body.paths["/internal/notify"]).toBeDefined();
  });

  it("GET /docs serves the Swagger UI page", async () => {
    const instance = createGenNotif(fullOverrides);
    const res = await request(instance.app).get("/docs/");
    expect(res.status).toBe(200);
    expect(res.text).toContain("swagger-ui");
  });
});
