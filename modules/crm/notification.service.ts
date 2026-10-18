import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';

export interface NotifyInput {
  tenantId: string;
  userId: string;
  type: string;
  title: string;
  body?: string;
  leadId?: string;
}

/**
 * In-app notifications — every "broker notified" in the flows, reminders and
 * the 9am digest. shortcut: in-app only; email/SMS delivery comes with
 * Gen_Notif (scope.md Phase 9).
 */
export async function notify(input: NotifyInput) {
  return getPrismaClient().notification.create({ data: input });
}

export async function listMyNotifications(userId: string) {
  return getPrismaClient().notification.findMany({
    where: { userId },
    orderBy: { createdAt: 'desc' },
    take: 100,
  });
}

export async function markRead(userId: string, notificationId: string) {
  const row = await getPrismaClient().notification.findUnique({ where: { id: notificationId } });
  if (!row || row.userId !== userId) {
    throw new ApiError('Notification not found', 404);
  }
  return getPrismaClient().notification.update({
    where: { id: row.id },
    data: { readAt: row.readAt ?? new Date() },
  });
}
