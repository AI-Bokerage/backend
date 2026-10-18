import type { INotificationLogRepo, NotificationLogRecord } from "../../../domain/ports/notification-log.repository.port.ts";
import { NotificationNotFoundError, NotificationForbiddenError } from "../../../common/errors.ts";

export class NotificationService {
  constructor(private readonly repo: INotificationLogRepo) {}

  listUnread(tenantId: string, userId: string, limit: number, before?: Date): Promise<NotificationLogRecord[]> {
    return this.repo.findUnread(tenantId, userId, { limit, before });
  }

  async markRead(tenantId: string, userId: string, id: string): Promise<NotificationLogRecord> {
    const existing = await this.repo.findById(tenantId, id);
    if (!existing) throw new NotificationNotFoundError(id);
    if (existing.userId !== userId) throw new NotificationForbiddenError();
    const updated = await this.repo.markRead(tenantId, userId, id);
    if (!updated) throw new NotificationNotFoundError(id);
    return updated;
  }
}
