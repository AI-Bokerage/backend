import type { ITenantPreferenceRepo } from "../../../domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "../../../domain/ports/notification-log.repository.port.ts";
import type { IDigestQueueRepo } from "../../../domain/ports/digest-queue.repository.port.ts";
import type { IWebhookEndpointRepo } from "../../../domain/ports/webhook-endpoint.repository.port.ts";
import type { IEmailSender } from "../../../domain/ports/email-sender.port.ts";
import type { ISmsSender } from "../../../domain/ports/sms-sender.port.ts";
import type { IRealtimeGateway } from "../../../domain/ports/realtime-gateway.port.ts";
import { renderTemplate } from "../../templates/v1/registry.ts";
import { resolveChannels } from "./channel-router.ts";
import { dispatchEmail } from "./channels/email.ts";
import { dispatchInApp } from "./channels/inapp.ts";
import { dispatchSms } from "./channels/sms.ts";
import { dispatchWebhook } from "../../webhooks/v1/dispatch.ts";
import { logger } from "../../../common/logger.ts";

export interface NotifyRecipient {
  userId: string;
  email?: string;
  phone?: string;
}

export interface NotifyInput {
  tenantId: string;
  recipients: NotifyRecipient[];
  eventType: string;
  data: Record<string, unknown>;
  entityRefType?: string;
  entityRefId?: string;
}

export interface NotifyRecipientResult {
  userId: string;
  channels: Array<{ channel: string; status: "SENT" | "FAILED" | "QUEUED_FOR_DIGEST"; lastError?: string }>;
}

export interface NotifyResult {
  recipients: NotifyRecipientResult[];
}

function nextDigestBoundary(): Date {
  const now = new Date();
  const next = new Date(now);
  next.setMinutes(0, 0, 0);
  next.setHours(next.getHours() + 1);
  return next;
}

export class NotifService {
  constructor(
    private readonly preferenceRepo: ITenantPreferenceRepo,
    private readonly notificationRepo: INotificationLogRepo,
    private readonly digestRepo: IDigestQueueRepo,
    private readonly webhookRepo: IWebhookEndpointRepo,
    private readonly emailSender: IEmailSender,
    private readonly smsSender: ISmsSender,
    private readonly realtimeGateway: IRealtimeGateway | undefined,
    private readonly channelGate: (tenantId: string, channel: string) => boolean | Promise<boolean> = () => true,
  ) {}

  async notify(input: NotifyInput): Promise<NotifyResult> {
    const settled = await Promise.allSettled(input.recipients.map((r) => this.notifyOne(input, r)));
    return {
      recipients: settled.map((s, i) =>
        s.status === "fulfilled"
          ? s.value
          : { userId: input.recipients[i]!.userId, channels: [] },
      ),
    };
  }

  private async notifyOne(input: NotifyInput, recipient: NotifyRecipient): Promise<NotifyRecipientResult> {
    const prefs = await this.preferenceRepo.findByEventType(input.tenantId, recipient.userId, input.eventType);
    const resolved = resolveChannels(prefs);
    const content = renderTemplate(input.eventType, input.data);
    const channelResults: NotifyRecipientResult["channels"] = [];

    for (const { channel, digestMode } of resolved) {
      let log: Awaited<ReturnType<typeof this.notificationRepo.create>> | undefined;
      try {
        if (!(await this.channelGate(input.tenantId, channel))) continue;

        if (channel === "email" && digestMode) {
          if (!recipient.email) {
            channelResults.push({ channel, status: "FAILED", lastError: "recipient has no email address" });
            continue;
          }
          log = await this.notificationRepo.create({
            tenantId: input.tenantId, userId: recipient.userId, channel: "email",
            eventType: input.eventType, title: content.title, body: content.body,
            entityRefType: input.entityRefType, entityRefId: input.entityRefId,
            status: "QUEUED_FOR_DIGEST",
          });
          await this.digestRepo.enqueue(input.tenantId, recipient.userId, recipient.email, log.id, nextDigestBoundary());
          channelResults.push({ channel, status: "QUEUED_FOR_DIGEST" });
          continue;
        }

        log = await this.notificationRepo.create({
          tenantId: input.tenantId, userId: recipient.userId, channel,
          eventType: input.eventType, title: content.title, body: content.body,
          entityRefType: input.entityRefType, entityRefId: input.entityRefId,
          status: "SENT",
        });

        const outcome = await this.dispatchImmediate(channel, input, recipient, content, log.id);
        await this.notificationRepo.markStatus(input.tenantId, log.id, outcome.status, outcome.lastError);
        channelResults.push({ channel, status: outcome.status, lastError: outcome.lastError });
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        logger.warn({ err, channel, userId: recipient.userId }, "[gen-notif] channel dispatch error — continuing");
        if (log) {
          // create() already persisted a row (SENT or QUEUED_FOR_DIGEST) before this
          // threw — correct it to FAILED so the log never lies about an outcome that
          // never actually happened. Best-effort: swallow a second failure here, the
          // original error is still reported to the caller via channelResults below.
          await this.notificationRepo.markStatus(input.tenantId, log.id, "FAILED", message).catch(() => {});
        }
        channelResults.push({ channel, status: "FAILED", lastError: message });
      }
    }

    return { userId: recipient.userId, channels: channelResults };
  }

  private async dispatchImmediate(
    channel: string,
    input: NotifyInput,
    recipient: NotifyRecipient,
    content: ReturnType<typeof renderTemplate>,
    notifId: string,
  ): Promise<{ status: "SENT" | "FAILED"; lastError?: string }> {
    switch (channel) {
      case "email":
        if (!recipient.email) return { status: "FAILED", lastError: "recipient has no email address" };
        return dispatchEmail(this.emailSender, recipient.email, content);
      case "inapp":
        if (!this.realtimeGateway) return { status: "FAILED", lastError: "no realtime gateway configured (modules.realtime is disabled and no override was supplied)" };
        return dispatchInApp(this.realtimeGateway, input.tenantId, recipient.userId, notifId, input.eventType, content, input.entityRefType, input.entityRefId);
      case "sms":
        if (!recipient.phone) return { status: "FAILED", lastError: "recipient has no phone number" };
        return dispatchSms(this.smsSender, recipient.phone, content);
      case "webhook": {
        const endpoints = await this.webhookRepo.listEnabled(input.tenantId, recipient.userId);
        if (endpoints.length === 0) return { status: "FAILED", lastError: "no enabled webhook endpoints" };
        const results = await dispatchWebhook(endpoints, input.eventType, content);
        const anySent = results.some((r) => r.result.status === "SENT");
        return anySent
          ? { status: "SENT" }
          : { status: "FAILED", lastError: results[0]?.result.lastError ?? "all webhook endpoints failed" };
      }
      default:
        return { status: "FAILED", lastError: `unknown channel: ${channel}` };
    }
  }
}
