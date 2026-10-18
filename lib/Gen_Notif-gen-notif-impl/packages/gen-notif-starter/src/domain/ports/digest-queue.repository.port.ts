export type DigestStatus = "PENDING" | "PROCESSING" | "SENT" | "FAILED";

export interface DigestQueueRecord {
  id: string;
  tenantId: string;
  userId: string;
  email: string;
  channel: "email";
  scheduledFor: Date;
  status: DigestStatus;
  notificationIds: string[];
  attempts: number;
  lastError: string | null;
  sentAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface IDigestQueueRepo {
  enqueue(tenantId: string, userId: string, email: string, notificationId: string, scheduledFor: Date): Promise<void>;
  claimDue(batchLimit: number, now: Date): Promise<DigestQueueRecord[]>;
  markSent(id: string): Promise<void>;
  markFailed(id: string, lastError: string): Promise<void>;
}
