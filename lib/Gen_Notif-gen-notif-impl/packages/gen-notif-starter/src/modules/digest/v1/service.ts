import type { IDigestQueueRepo } from "../../../domain/ports/digest-queue.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";
import type { IEmailSender } from "../../../domain/ports/email-sender.port.ts";
import { logger } from "../../../common/logger.ts";

const DEFAULT_BATCH_LIMIT = 50;

export interface DigestSweepResult {
  processed: number;
  sent: number;
  failed: number;
}

export class DigestService {
  constructor(
    private readonly digestRepo: IDigestQueueRepo,
    private readonly notificationRepo: INotificationLogRepo,
    private readonly emailSender: IEmailSender,
    private readonly batchLimit: number = DEFAULT_BATCH_LIMIT,
  ) {}

  async runSweep(now: Date = new Date()): Promise<DigestSweepResult> {
    const due = await this.digestRepo.claimDue(this.batchLimit, now);
    let sent = 0;
    let failed = 0;

    for (const entry of due) {
      try {
        const items = await Promise.all(
          entry.notificationIds.map((id) => this.notificationRepo.findById(entry.tenantId, id)),
        );
        const realItems = items.filter((i): i is NonNullable<typeof i> => i !== null);

        const text = realItems.length > 0
          ? realItems.map((i) => `- ${i.title}: ${i.body}`).join("\n")
          : "You have new notifications.";
        const html = realItems.length > 0
          ? `<ul>${realItems.map((i) => `<li><strong>${escapeHtml(i.title)}</strong>: ${escapeHtml(i.body)}</li>`).join("")}</ul>`
          : undefined;

        await this.emailSender.send({
          to: entry.email,
          subject: `You have ${realItems.length} notification${realItems.length === 1 ? "" : "s"}`,
          text,
          html,
        });

        for (const id of entry.notificationIds) {
          await this.notificationRepo.markStatus(entry.tenantId, id, "SENT");
        }
        await this.digestRepo.markSent(entry.id);
        sent++;
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        logger.error({ err, digestEntryId: entry.id }, "[gen-notif] digest sweep entry failed");
        await this.digestRepo.markFailed(entry.id, message);
        failed++;
      }
    }

    return { processed: due.length, sent, failed };
  }
}

function escapeHtml(input: string): string {
  return input
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
