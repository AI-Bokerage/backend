import type { IDigestQueueRepo, DigestQueueRecord } from "../../../domain/ports/digest-queue.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";
import { getPrismaClient } from "../../../infra/persistence/prisma-client.ts";

export class PrismaDigestQueueRepo implements IDigestQueueRepo {
  async enqueue(
    tenantId: string,
    userId: string,
    email: string,
    notificationId: string,
    scheduledFor: Date,
  ): Promise<void> {
    await withTenant(tenantId, async (tx) => {
      const existing = await tx.digestQueueEntry.findFirst({
        where: { tenantId, userId, email, channel: "email", status: "PENDING" },
      });
      if (existing) {
        await tx.digestQueueEntry.update({
          where: { id: existing.id },
          data: { notificationIds: { push: notificationId } },
        });
      } else {
        await tx.digestQueueEntry.create({
          data: {
            tenantId, userId, email, channel: "email", scheduledFor,
            status: "PENDING", notificationIds: [notificationId],
          },
        });
      }
    });
  }

  async claimDue(batchLimit: number, now: Date): Promise<DigestQueueRecord[]> {
    const prisma = getPrismaClient();
    // Atomic claim: only rows still PENDING get flipped, so two concurrent
    // sweep callers never both process the same entry.
    const due = await prisma.digestQueueEntry.findMany({
      where: { status: "PENDING", scheduledFor: { lte: now } },
      orderBy: { createdAt: "asc" },
      take: batchLimit,
    });
    const claimed: DigestQueueRecord[] = [];
    for (const entry of due) {
      const result = await prisma.digestQueueEntry.updateMany({
        where: { id: entry.id, status: "PENDING" },
        data: { status: "PROCESSING" },
      });
      // ponytail: Prisma's generated `channel` type is the full NotifChannel
      // enum (the column is shared/typed broadly at the schema level) but the
      // port narrows DigestQueueRecord.channel to the literal "email" (digests
      // are email-only) — the cast is safe because enqueue() always writes
      // channel: "email" for every row this query can select.
      if (result.count === 1) claimed.push({ ...entry, status: "PROCESSING" } as DigestQueueRecord);
    }
    return claimed;
  }

  async markSent(id: string): Promise<void> {
    await getPrismaClient().digestQueueEntry.update({
      where: { id },
      data: { status: "SENT", sentAt: new Date() },
    });
  }

  async markFailed(id: string, lastError: string): Promise<void> {
    await getPrismaClient().digestQueueEntry.update({
      where: { id },
      data: { status: "FAILED", lastError, attempts: { increment: 1 } },
    });
  }
}
