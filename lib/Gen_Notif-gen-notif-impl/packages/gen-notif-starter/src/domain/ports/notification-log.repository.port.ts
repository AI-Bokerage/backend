import type { NotifChannel } from "./tenant-preference.repository.port.ts";

export type NotificationStatus = "SENT" | "FAILED" | "QUEUED_FOR_DIGEST" | "READ";

export interface NotificationLogRecord {
  id: string;
  tenantId: string;
  userId: string;
  channel: NotifChannel;
  eventType: string;
  title: string;
  body: string;
  entityRefType: string | null;
  entityRefId: string | null;
  status: NotificationStatus;
  readAt: Date | null;
  attempts: number;
  lastError: string | null;
  sentAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateNotificationLogInput {
  tenantId: string;
  userId: string;
  channel: NotifChannel;
  eventType: string;
  title: string;
  body: string;
  entityRefType?: string;
  entityRefId?: string;
  status: NotificationStatus;
  attempts?: number;
  lastError?: string;
  sentAt?: Date;
}

export interface FindUnreadOptions {
  limit: number;
  before?: Date;
}

export interface INotificationLogRepo {
  create(input: CreateNotificationLogInput): Promise<NotificationLogRecord>;
  findById(tenantId: string, id: string): Promise<NotificationLogRecord | null>;
  findUnread(tenantId: string, userId: string, opts: FindUnreadOptions): Promise<NotificationLogRecord[]>;
  markRead(tenantId: string, userId: string, id: string): Promise<NotificationLogRecord | null>;
  markStatus(tenantId: string, id: string, status: NotificationStatus, lastError?: string): Promise<void>;
}
