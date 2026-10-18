import { describe, it, expect, vi, afterEach } from "vitest";
import { NotifService } from "./service.ts";
import { registerTemplate, __resetRegistryForTests } from "../../templates/v1/registry.ts";
import type { ITenantPreferenceRepo } from "../../../domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";
import type { IRealtimeGateway } from "../../../domain/ports/realtime-gateway.port.ts";
import type { WebhookEndpointRecord } from "../../../domain/ports/webhook-endpoint.repository.port.ts";

function makeService(
  overrides: {
    prefs?: ITenantPreferenceRepo["findByEventType"];
    noGateway?: boolean;
    webhookListEnabled?: () => Promise<WebhookEndpointRecord[]>;
    createThrowsForUserId?: string;
  } = {},
) {
  const created: unknown[] = [];
  const statusUpdates: unknown[] = [];
  const notificationRepo: INotificationLogRepo = {
    create: vi.fn(async (input) => {
      if (overrides.createThrowsForUserId && input.userId === overrides.createThrowsForUserId) {
        throw new Error("db timeout on create");
      }
      created.push(input);
      return { id: "log-1", ...input, readAt: null, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date() } as never;
    }),
    findById: vi.fn(),
    findUnread: vi.fn(),
    markRead: vi.fn(),
    markStatus: vi.fn(async (...args) => { statusUpdates.push(args); }),
  };
  const preferenceRepo: ITenantPreferenceRepo = {
    findAll: vi.fn(),
    findByEventType: overrides.prefs ?? vi.fn(async () => []),
    upsert: vi.fn(),
  };
  const digestRepo = { enqueue: vi.fn(), claimDue: vi.fn(), markSent: vi.fn(), markFailed: vi.fn() };
  const webhookRepo = {
    create: vi.fn(),
    findById: vi.fn(),
    listEnabled: vi.fn(overrides.webhookListEnabled ?? (async () => [])),
    update: vi.fn(),
    delete: vi.fn(),
  };
  const emailSender = { send: vi.fn(async () => {}) };
  const smsSender = { send: vi.fn(async () => { throw new Error("no SMS provider"); }) };
  const realtimeGateway = { attach: vi.fn(), publishInApp: vi.fn(async () => {}) };
  const gateway: IRealtimeGateway | undefined = overrides.noGateway ? undefined : realtimeGateway;

  const service = new NotifService(preferenceRepo, notificationRepo, digestRepo, webhookRepo, emailSender, smsSender, gateway);
  return { service, created, statusUpdates, notificationRepo, preferenceRepo, webhookRepo, emailSender, realtimeGateway };
}

describe("NotifService.notify()", () => {
  afterEach(() => __resetRegistryForTests());

  it("defaults to in-app when no preference rows exist", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service, realtimeGateway } = makeService();
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([{ channel: "inapp", status: "SENT" }]);
    expect(realtimeGateway.publishInApp).toHaveBeenCalledOnce();
  });

  it("dispatches email when an enabled email preference exists", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service, emailSender } = makeService({
      prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
    });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1", email: "a@example.com" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([{ channel: "email", status: "SENT" }]);
    expect(emailSender.send).toHaveBeenCalledWith(expect.objectContaining({ to: "a@example.com", subject: "New doc" }));
  });

  it("queues for digest instead of sending immediately when digestMode is set", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service, emailSender } = makeService({
      prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "email", enabled: true, digestMode: true, createdAt: new Date(), updatedAt: new Date() }]),
    });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1", email: "a@example.com" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([{ channel: "email", status: "QUEUED_FOR_DIGEST" }]);
    expect(emailSender.send).not.toHaveBeenCalled();
  });

  it("SMS with no configured provider dispatches FAILED, not a silent success", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service } = makeService({
      prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "sms", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
    });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1", phone: "+15551234567" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels[0]!.status).toBe("FAILED");
  });

  it("one recipient's channel failure does not abort another recipient", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    // u1's log create() genuinely throws (simulated DB timeout); u2 is unaffected.
    // If notify() ever regressed from Promise.allSettled to Promise.all, this
    // throw would reject the whole batch and u2 would never appear in the result.
    const { service } = makeService({ createThrowsForUserId: "u1" });
    const result = await service.notify({
      tenantId: "t1",
      recipients: [{ userId: "u1" }, { userId: "u2" }],
      eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients).toHaveLength(2);
    expect(result.recipients.find((r) => r.userId === "u1")!.channels).toEqual([
      { channel: "inapp", status: "FAILED", lastError: "db timeout on create" },
    ]);
    expect(result.recipients.find((r) => r.userId === "u2")!.channels).toEqual([
      { channel: "inapp", status: "SENT" },
    ]);
  });

  it("in-app dispatch fails cleanly (not a throw) when no realtime gateway is configured", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service } = makeService({ noGateway: true });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([
      { channel: "inapp", status: "FAILED", lastError: expect.stringContaining("no realtime gateway configured") },
    ]);
  });

  it("dispatches webhook successfully when enabled endpoints exist", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const endpoint: WebhookEndpointRecord = {
      id: "wh-1", tenantId: "t1", userId: "u1", url: "https://example.com/hook", secret: "s3cr3t",
      enabled: true, description: null, createdAt: new Date(), updatedAt: new Date(),
    };
    // Stub global fetch so dispatchWebhook's real HTTP call resolves without a network.
    const fetchMock = vi.fn(async () => new Response(null, { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    try {
      const { service } = makeService({
        prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "webhook", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
        webhookListEnabled: async () => [endpoint],
      });
      const result = await service.notify({
        tenantId: "t1", recipients: [{ userId: "u1" }], eventType: "doc.uploaded", data: {},
      });
      expect(result.recipients[0]!.channels).toEqual([{ channel: "webhook", status: "SENT" }]);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("corrects the log to FAILED when webhookRepo.listEnabled throws after the log row was already created", async () => {
    registerTemplate("doc.uploaded", () => ({ title: "New doc", body: "body text" }));
    const { service, statusUpdates } = makeService({
      prefs: vi.fn(async () => [{ id: "p1", tenantId: "t1", userId: "u1", eventType: "doc.uploaded", channel: "webhook", enabled: true, digestMode: false, createdAt: new Date(), updatedAt: new Date() }]),
      webhookListEnabled: async () => { throw new Error("webhook db connection error"); },
    });
    const result = await service.notify({
      tenantId: "t1", recipients: [{ userId: "u1" }], eventType: "doc.uploaded", data: {},
    });
    expect(result.recipients[0]!.channels).toEqual([
      { channel: "webhook", status: "FAILED", lastError: "webhook db connection error" },
    ]);
    // The log was optimistically created as SENT, then must be corrected to FAILED —
    // proving the create()-then-throw invariant gap (Critical bug) is fixed.
    expect(statusUpdates).toContainEqual(["t1", "log-1", "FAILED", "webhook db connection error"]);
  });
});
