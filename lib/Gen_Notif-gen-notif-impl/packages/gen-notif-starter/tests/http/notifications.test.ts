import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { NotificationService } from "../../src/modules/notifications/v1/service.ts";
import { NotificationController } from "../../src/modules/notifications/v1/controller.ts";
import { notificationsRoutes } from "../../src/modules/notifications/v1/routes.ts";
import { errorHandler } from "../../src/middleware/error-handler.ts";
import type { INotificationLogRepo } from "../../src/domain/ports/notification-log.repository.port.ts";

const TENANT = "11111111-1111-1111-1111-111111111111";
const OWNER = "22222222-2222-2222-2222-222222222222";
const OTHER = "33333333-3333-3333-3333-333333333333";
const NOTIF_ID = "44444444-4444-4444-4444-444444444444";
const UNKNOWN_ID = "55555555-5555-5555-5555-555555555555";

function buildApp(repo: INotificationLogRepo) {
  const app = express();
  app.use(express.json());
  const service = new NotificationService(repo);
  const controller = new NotificationController(service);
  app.use("/api/v1/notifications", notificationsRoutes(controller));
  app.use(errorHandler);
  return app;
}

function baseRecord(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: NOTIF_ID, tenantId: TENANT, userId: OWNER, channel: "inapp", eventType: "doc.uploaded",
    title: "New doc", body: "body", entityRefType: null, entityRefId: null, status: "SENT",
    readAt: null, attempts: 1, lastError: null, sentAt: new Date(), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

describe("notifications HTTP routes", () => {
  it("GET /unread returns the repo's unread list", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findById: vi.fn(), markStatus: vi.fn(),
      findUnread: vi.fn(async () => [baseRecord()]),
      markRead: vi.fn(),
    };
    const res = await request(buildApp(repo)).get("/api/v1/notifications/unread").query({ tenantId: TENANT, userId: OWNER });
    expect(res.status).toBe(200);
    expect(res.body.notifications).toHaveLength(1);
  });

  it("PATCH /:id/read marks it read for the owning user", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markStatus: vi.fn(),
      findById: vi.fn(async () => baseRecord()),
      markRead: vi.fn(async () => baseRecord({ status: "READ", readAt: new Date() })),
    };
    const res = await request(buildApp(repo)).patch(`/api/v1/notifications/${NOTIF_ID}/read`).send({ tenantId: TENANT, userId: OWNER });
    expect(res.status).toBe(200);
    expect(res.body.notification.status).toBe("READ");
  });

  it("PATCH /:id/read for a different user's notification is 403", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markStatus: vi.fn(), markRead: vi.fn(),
      findById: vi.fn(async () => baseRecord()),
    };
    const res = await request(buildApp(repo)).patch(`/api/v1/notifications/${NOTIF_ID}/read`).send({ tenantId: TENANT, userId: OTHER });
    expect(res.status).toBe(403);
  });

  it("PATCH /:id/read for an unknown id is 404", async () => {
    const repo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markStatus: vi.fn(), markRead: vi.fn(),
      findById: vi.fn(async () => null),
    };
    const res = await request(buildApp(repo)).patch(`/api/v1/notifications/${UNKNOWN_ID}/read`).send({ tenantId: TENANT, userId: OWNER });
    expect(res.status).toBe(404);
  });
});
