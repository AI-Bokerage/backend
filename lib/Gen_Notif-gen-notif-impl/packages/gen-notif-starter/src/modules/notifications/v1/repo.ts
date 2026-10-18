import type {
  INotificationLogRepo,
  NotificationLogRecord,
  CreateNotificationLogInput,
  FindUnreadOptions,
  NotificationStatus,
} from "../../../domain/ports/notification-log.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaNotificationLogRepo implements INotificationLogRepo {
  async create(input: CreateNotificationLogInput): Promise<NotificationLogRecord> {
    return withTenant(input.tenantId, (tx) => tx.notificationLog.create({ data: input }));
  }

  async findById(tenantId: string, id: string): Promise<NotificationLogRecord | null> {
    return withTenant(tenantId, (tx) => tx.notificationLog.findFirst({ where: { id, tenantId } }));
  }

  async findUnread(tenantId: string, userId: string, opts: FindUnreadOptions): Promise<NotificationLogRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.notificationLog.findMany({
        where: {
          tenantId,
          userId,
          status: { in: ["SENT", "QUEUED_FOR_DIGEST"] },
          ...(opts.before ? { createdAt: { lt: opts.before } } : {}),
        },
        orderBy: { createdAt: "desc" },
        take: opts.limit,
      }),
    );
  }

  async markRead(tenantId: string, userId: string, id: string): Promise<NotificationLogRecord | null> {
    return withTenant(tenantId, async (tx) => {
      const result = await tx.notificationLog.updateMany({
        where: { id, tenantId, userId },
        data: { status: "READ", readAt: new Date() },
      });
      if (result.count === 0) return null;
      return tx.notificationLog.findUnique({ where: { id } });
    });
  }

  async markStatus(tenantId: string, id: string, status: NotificationStatus, lastError?: string): Promise<void> {
    // Must run inside withTenant(): with FORCE ROW LEVEL SECURITY + a USING/WITH CHECK
    // policy keyed on current_setting('app.tenant_id'), an UPDATE issued with
    // app.tenant_id unset matches zero rows and silently no-ops.
    await withTenant(tenantId, (tx) =>
      tx.notificationLog.update({
        where: { id },
        data: { status, lastError, sentAt: status === "SENT" ? new Date() : undefined },
      }),
    );
  }
}
