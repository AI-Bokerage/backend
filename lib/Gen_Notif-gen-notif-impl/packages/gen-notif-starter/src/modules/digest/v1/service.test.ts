import { describe, it, expect, vi } from "vitest";
import { DigestService } from "./service.ts";
import type { IDigestQueueRepo } from "../../../domain/ports/digest-queue.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";

describe("DigestService.runSweep()", () => {
  it("sends a digest email listing each notification's real title/body, not just a count", async () => {
    const digestRepo: IDigestQueueRepo = {
      enqueue: vi.fn(),
      claimDue: vi.fn(async () => [{
        id: "d1", tenantId: "t1", userId: "u1", email: "a@example.com", channel: "email",
        scheduledFor: new Date(), status: "PROCESSING", notificationIds: ["n1", "n2"],
        attempts: 0, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(),
      }]),
      markSent: vi.fn(),
      markFailed: vi.fn(),
    };
    const notificationRepo: INotificationLogRepo = {
      create: vi.fn(), findUnread: vi.fn(), markRead: vi.fn(), markStatus: vi.fn(),
      findById: vi.fn(async (_tenantId, id) => ({
        id, tenantId: "t1", userId: "u1", channel: "email", eventType: "doc.uploaded",
        title: id === "n1" ? "Invoice uploaded" : "Report uploaded", body: `body for ${id}`,
        entityRefType: null, entityRefId: null, status: "QUEUED_FOR_DIGEST", readAt: null,
        attempts: 1, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(),
      })),
    };
    const emailSender = { send: vi.fn(async () => {}) };

    const service = new DigestService(digestRepo, notificationRepo, emailSender);
    const result = await service.runSweep();

    expect(result).toEqual({ processed: 1, sent: 1, failed: 0 });
    expect(emailSender.send).toHaveBeenCalledWith(expect.objectContaining({
      to: "a@example.com",
      text: expect.stringContaining("Invoice uploaded"),
    }));
    expect(digestRepo.markSent).toHaveBeenCalledWith("d1");
    expect(notificationRepo.markStatus).toHaveBeenCalledWith("t1", "n1", "SENT");
    expect(notificationRepo.markStatus).toHaveBeenCalledWith("t1", "n2", "SENT");
  });

  it("marks the entry FAILED (not SENT) when the email send throws", async () => {
    const digestRepo: IDigestQueueRepo = {
      enqueue: vi.fn(),
      claimDue: vi.fn(async () => [{
        id: "d1", tenantId: "t1", userId: "u1", email: "a@example.com", channel: "email",
        scheduledFor: new Date(), status: "PROCESSING", notificationIds: [],
        attempts: 0, lastError: null, sentAt: null, createdAt: new Date(), updatedAt: new Date(),
      }]),
      markSent: vi.fn(), markFailed: vi.fn(),
    };
    const notificationRepo: INotificationLogRepo = { create: vi.fn(), findUnread: vi.fn(), markRead: vi.fn(), markStatus: vi.fn(), findById: vi.fn() };
    const emailSender = { send: vi.fn(async () => { throw new Error("SMTP down"); }) };

    const service = new DigestService(digestRepo, notificationRepo, emailSender);
    const result = await service.runSweep();

    expect(result).toEqual({ processed: 1, sent: 0, failed: 1 });
    expect(digestRepo.markFailed).toHaveBeenCalledWith("d1", "SMTP down");
    expect(digestRepo.markSent).not.toHaveBeenCalled();
  });
});
