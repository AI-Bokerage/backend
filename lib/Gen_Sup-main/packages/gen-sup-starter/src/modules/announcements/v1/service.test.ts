import { describe, it, expect, vi } from "vitest";
import { AnnouncementsService } from "./service.ts";
import type { PrismaClient } from "../../../infra/persistence/prisma-client.ts";
import type { EventPublisher } from "../../../domain/ports/event-publisher.port.ts";

function baseRow(overrides: Record<string, unknown> = {}) {
  return {
    id: "a1",
    title: "Scheduled maintenance",
    body: "We will be performing maintenance this weekend.",
    type: "MAINTENANCE",
    targetSegment: "ALL",
    channels: ["EMAIL", "IN_APP"],
    scheduledFor: null,
    sentAt: new Date("2026-01-01T00:00:00Z"),
    createdBy: "11111111-1111-1111-1111-111111111111",
    createdAt: new Date("2026-01-01T00:00:00Z"),
    ...overrides,
  };
}

function fakePrisma(overrides: Record<string, unknown> = {}) {
  return {
    announcement: {
      create: vi.fn(async ({ data }: { data: Record<string, unknown> }) => baseRow(data)),
      findMany: vi.fn(async () => [baseRow()]),
      count: vi.fn(async () => 1),
      update: vi.fn(async () => baseRow()),
      ...overrides,
    },
  } as unknown as PrismaClient;
}

function fakeEventPublisher(overrides: Partial<EventPublisher> = {}): EventPublisher {
  return { publish: vi.fn(async () => undefined), ...overrides };
}

describe("AnnouncementsService", () => {
  describe("create", () => {
    it("stamps sentAt and publishes both CREATED and BROADCAST when there is no scheduledFor", async () => {
      const prisma = fakePrisma();
      const eventPublisher = fakeEventPublisher();
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.create({
        title: "Scheduled maintenance",
        body: "We will be performing maintenance this weekend.",
        type: "MAINTENANCE",
        targetSegment: "ALL",
        channels: ["EMAIL", "IN_APP"],
        createdBy: "11111111-1111-1111-1111-111111111111",
      });

      expect(result.sentAt).not.toBeNull();
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.created", expect.objectContaining({ event_type: "sup.announcement.created" }),
      );
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.broadcast", expect.objectContaining({ event_type: "sup.announcement.broadcast" }),
      );
      expect(eventPublisher.publish).toHaveBeenCalledTimes(2);
    });

    it("leaves sentAt null and publishes only CREATED when scheduledFor is in the future", async () => {
      const prisma = fakePrisma({
        create: vi.fn(async ({ data }: { data: Record<string, unknown> }) => baseRow({ ...data, sentAt: null })),
      });
      const eventPublisher = fakeEventPublisher();
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.create({
        title: "New feature",
        body: "A new feature is coming next month.",
        type: "NEW_FEATURE",
        targetSegment: "ALL",
        channels: ["IN_APP"],
        scheduledFor: "2027-01-01T00:00:00Z",
        createdBy: "11111111-1111-1111-1111-111111111111",
      });

      expect(result.sentAt).toBeNull();
      expect(eventPublisher.publish).toHaveBeenCalledTimes(1);
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.created", expect.anything(),
      );
    });

    it("does not fail the request when audit publishing fails", async () => {
      const prisma = fakePrisma();
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new AnnouncementsService(prisma, eventPublisher);

      await expect(service.create({
        title: "Scheduled maintenance",
        body: "We will be performing maintenance this weekend.",
        type: "MAINTENANCE",
        targetSegment: "ALL",
        channels: ["EMAIL"],
        createdBy: "11111111-1111-1111-1111-111111111111",
      })).resolves.toBeDefined();
    });
  });

  describe("list", () => {
    it("returns paginated announcements with the requested page/pageSize/total", async () => {
      const findMany = vi.fn(async () => [baseRow()]);
      const prisma = fakePrisma({ findMany });
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      const result = await service.list({ page: 2, pageSize: 20 });

      expect(result.announcements).toHaveLength(1);
      expect(result.page).toBe(2);
      expect(result.pageSize).toBe(20);
      expect(result.total).toBe(1);
      expect(findMany).toHaveBeenCalledWith(expect.objectContaining({ skip: 20, take: 20 }));
    });

    it("filters by type when provided", async () => {
      const findMany = vi.fn(async () => [baseRow()]);
      const prisma = fakePrisma({ findMany });
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      await service.list({ type: "CRITICAL", page: 1, pageSize: 20 });

      expect(findMany).toHaveBeenCalledWith(expect.objectContaining({ where: { type: "CRITICAL" } }));
    });

    it("converts Date fields to ISO strings in the returned DTOs", async () => {
      const prisma = fakePrisma();
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      const result = await service.list({ page: 1, pageSize: 20 });

      expect(typeof result.announcements[0]!.sentAt).toBe("string");
      expect(typeof result.announcements[0]!.createdAt).toBe("string");
    });
  });

  describe("dispatchScheduled", () => {
    it("broadcasts and stamps due announcements, returning an accurate dispatched count", async () => {
      const dueRow = baseRow({ id: "a2", scheduledFor: new Date("2026-01-01T00:00:00Z"), sentAt: null });
      const findMany = vi.fn(async () => [dueRow]);
      const update = vi.fn(async () => ({ ...dueRow, sentAt: new Date() }));
      const prisma = fakePrisma({ findMany, update });
      const eventPublisher = fakeEventPublisher();
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.dispatchScheduled();

      expect(result).toEqual({ dispatched: 1, failed: 0 });
      expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
        where: { sentAt: null, scheduledFor: expect.objectContaining({ lte: expect.any(Date) }) },
        orderBy: { scheduledFor: "asc" },
        take: 100,
      }));
      expect(update).toHaveBeenCalledWith({ where: { id: "a2" }, data: { sentAt: expect.any(Date) } });
      expect(eventPublisher.publish).toHaveBeenCalledWith(
        "platform.audit", "sup.announcement.broadcast", expect.objectContaining({ event_type: "sup.announcement.broadcast" }),
      );
    });

    it("returns 0/0 when nothing is due", async () => {
      const prisma = fakePrisma({ findMany: vi.fn(async () => []) });
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      expect(await service.dispatchScheduled()).toEqual({ dispatched: 0, failed: 0 });
    });

    it("counts a failed row without aborting the rest of the sweep", async () => {
      const dueRows = [
        baseRow({ id: "a2", sentAt: null }),
        baseRow({ id: "a3", sentAt: null }),
      ];
      const findMany = vi.fn(async () => dueRows);
      const update = vi.fn(async ({ where }: { where: { id: string } }) => {
        if (where.id === "a2") throw new Error("db write failed");
        return { ...baseRow({ id: where.id }), sentAt: new Date() };
      });
      const prisma = fakePrisma({ findMany, update });
      const service = new AnnouncementsService(prisma, fakeEventPublisher());

      const result = await service.dispatchScheduled();

      expect(result).toEqual({ dispatched: 1, failed: 1 });
      expect(update).toHaveBeenCalledTimes(2);
    });

    it("does not count a failed audit-event publish as a dispatch failure — publishSafely is fire-and-forget", async () => {
      const dueRow = baseRow({ id: "a2", sentAt: null });
      const prisma = fakePrisma({ findMany: vi.fn(async () => [dueRow]) });
      const eventPublisher = fakeEventPublisher({ publish: vi.fn(async () => { throw new Error("broker down"); }) });
      const service = new AnnouncementsService(prisma, eventPublisher);

      const result = await service.dispatchScheduled();

      // The row is still marked sent (the publish failure was swallowed by
      // publishSafely) — "failed" here tracks failure to persist sentAt, not
      // audit-publish hiccups, which are best-effort everywhere else in this
      // codebase too.
      expect(result).toEqual({ dispatched: 1, failed: 0 });
    });
  });
});
