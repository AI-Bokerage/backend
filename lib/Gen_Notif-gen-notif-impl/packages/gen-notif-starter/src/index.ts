export { createGenNotif } from "./create-gen-notif.ts";
export type { GenNotifConfig, GenNotifModulesConfig, GenNotifInstance } from "./create-gen-notif.ts";

export type { NotifyInput, NotifyResult, NotifyRecipient } from "./modules/notify/v1/service.ts";
export type { DigestSweepResult } from "./modules/digest/v1/service.ts";
export { registerTemplate, renderTemplate, listRegisteredEventTypes } from "./modules/templates/v1/registry.ts";
export type { NotificationContent, TemplateBuilder } from "./modules/templates/v1/registry.ts";
export { verifyWebhookSignature } from "./modules/webhooks/v1/hmac.ts";

export type { ITenantPreferenceRepo, PreferenceRecord, NotifChannel } from "./domain/ports/tenant-preference.repository.port.ts";
export type { INotificationLogRepo, NotificationLogRecord, NotificationStatus } from "./domain/ports/notification-log.repository.port.ts";
export type { IDigestQueueRepo, DigestQueueRecord } from "./domain/ports/digest-queue.repository.port.ts";
export type { IWebhookEndpointRepo, WebhookEndpointRecord } from "./domain/ports/webhook-endpoint.repository.port.ts";
export type { IEmailSender, SendEmailInput } from "./domain/ports/email-sender.port.ts";
export type { ISmsSender, SendSmsInput } from "./domain/ports/sms-sender.port.ts";
export type { IRealtimeGateway, InAppPayload } from "./domain/ports/realtime-gateway.port.ts";
export type { IJwtVerifier, VerifiedClaims } from "./domain/ports/jwt-verifier.port.ts";

export { PrismaTenantPreferenceRepo } from "./modules/preferences/v1/repo.ts";
export { PrismaNotificationLogRepo } from "./modules/notifications/v1/repo.ts";
export { PrismaDigestQueueRepo } from "./modules/digest/v1/repo.ts";
export { PrismaWebhookEndpointRepo } from "./modules/webhooks/v1/repo.ts";
export { NodemailerEmailSender } from "./infra/email/nodemailer-email-sender.ts";
export { UnimplementedSmsSender } from "./infra/sms/unimplemented-sms-sender.ts";
export { SocketIoRedisGateway } from "./infra/realtime/socket-io-redis-gateway.ts";
export { JwksJwtVerifier } from "./infra/auth/jwks-jwt-verifier.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { AppError, GenNotifConfigError } from "./common/errors.ts";
