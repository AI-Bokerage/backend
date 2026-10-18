import type { PrismaClient, Announcement } from "../../../infra/persistence/prisma-client.ts";
import type { EventPublisher, EventEnvelope } from "../../../domain/ports/event-publisher.port.ts";
import { PLATFORM_AUDIT_EXCHANGE, ANNOUNCEMENT_ROUTING_KEYS } from "../../../config/constants.ts";
import { logger } from "../../../common/logger.ts";
import type {
  AnnouncementDto,
  CreateAnnouncementInput,
  DispatchScheduledResult,
  ListAnnouncementsQuery,
  ListAnnouncementsResult,
} from "./types.ts";

// Announcements aren't scoped to a single tenant — targetSegment is a coarse
// string nobody in this family resolves into real tenants/users yet — but
// EventEnvelope.tenant_id is a required field. This sentinel marks
// platform-level (non-tenant) audit events, same pattern as the feature-flags
// module's flag-update event.
const PLATFORM_TENANT_ID = "platform";

function toDto(row: Announcement): AnnouncementDto {
  return {
    id: row.id,
    title: row.title,
    body: row.body,
    type: row.type,
    targetSegment: row.targetSegment,
    channels: row.channels,
    scheduledFor: row.scheduledFor ? row.scheduledFor.toISOString() : null,
    sentAt: row.sentAt ? row.sentAt.toISOString() : null,
    createdBy: row.createdBy,
    createdAt: row.createdAt.toISOString(),
  };
}

export class AnnouncementsService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly eventPublisher: EventPublisher,
  ) {}

  private async publishSafely(routingKey: string, data: Record<string, unknown>): Promise<void> {
    const envelope: EventEnvelope = {
      event_type: routingKey,
      occurred_at: new Date().toISOString(),
      tenant_id: PLATFORM_TENANT_ID,
      data,
    };
    try {
      await this.eventPublisher.publish(PLATFORM_AUDIT_EXCHANGE, routingKey, envelope);
    } catch (err) {
      logger.warn({ err, routingKey }, "[Announcements] Audit event publish failed — continuing");
    }
  }

  async create(input: CreateAnnouncementInput): Promise<AnnouncementDto> {
    const immediate = !input.scheduledFor;
    const row = await this.prisma.announcement.create({
      data: {
        title: input.title,
        body: input.body,
        type: input.type,
        targetSegment: input.targetSegment,
        channels: input.channels,
        scheduledFor: input.scheduledFor ? new Date(input.scheduledFor) : null,
        sentAt: immediate ? new Date() : null,
        createdBy: input.createdBy,
      },
    });

    await this.publishSafely(ANNOUNCEMENT_ROUTING_KEYS.CREATED, {
      announcement_id: row.id,
      title: row.title,
      type: row.type,
      target_segment: row.targetSegment,
    });

    if (immediate) {
      await this.publishSafely(ANNOUNCEMENT_ROUTING_KEYS.BROADCAST, {
        announcement_id: row.id,
        title: row.title,
        body: row.body,
        type: row.type,
        target_segment: row.targetSegment,
        channels: row.channels,
      });
    }

    return toDto(row);
  }

  async list(query: ListAnnouncementsQuery): Promise<ListAnnouncementsResult> {
    const where = query.type ? { type: query.type } : {};
    const [rows, total] = await Promise.all([
      this.prisma.announcement.findMany({
        where,
        orderBy: { createdAt: "desc" },
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
      }),
      this.prisma.announcement.count({ where }),
    ]);
    return {
      announcements: rows.map(toDto),
      page: query.page,
      pageSize: query.pageSize,
      total,
    };
  }

  async dispatchScheduled(): Promise<DispatchScheduledResult> {
    // Unbounded here would let a long sweep outage (or rows that keep
    // failing to persist sentAt) load an ever-growing backlog in one call.
    // Capped + oldest-first makes each call self-draining across repeated
    // sweeps instead of trying to process everything at once.
    const due = await this.prisma.announcement.findMany({
      where: { sentAt: null, scheduledFor: { lte: new Date() } },
      orderBy: { scheduledFor: "asc" },
      take: 100,
    });

    let dispatched = 0;
    let failed = 0;
    for (const row of due) {
      try {
        // publishSafely never throws — a broker outage here is logged and
        // swallowed, matching the fire-and-forget audit pattern used
        // everywhere else in this codebase (tenants/feature-flags). It does
        // NOT count toward `failed` below; only a failure to persist
        // `sentAt` does, since that's what actually needs a retry on the
        // next sweep.
        //
        // Publish happens before the sentAt persist below, so if the update
        // fails after a successful publish, the next sweep re-broadcasts
        // this row — at-least-once, not exactly-once, delivery. Acceptable
        // today since nothing consumes this event yet; revisit before a
        // real consumer is added.
        await this.publishSafely(ANNOUNCEMENT_ROUTING_KEYS.BROADCAST, {
          announcement_id: row.id,
          title: row.title,
          body: row.body,
          type: row.type,
          target_segment: row.targetSegment,
          channels: row.channels,
        });
        await this.prisma.announcement.update({ where: { id: row.id }, data: { sentAt: new Date() } });
        dispatched++;
      } catch (err) {
        failed++;
        logger.error({ err, announcementId: row.id }, "[Announcements] Failed to mark scheduled announcement as sent");
      }
    }

    return { dispatched, failed };
  }
}
