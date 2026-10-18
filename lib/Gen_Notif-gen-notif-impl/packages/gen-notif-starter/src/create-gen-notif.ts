import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import swaggerUi from "swagger-ui-express";
import { openApiSpec } from "./docs/openapi.ts";
import type { NextFunction, Request, Response } from "express";
import type { Server as HttpServer } from "node:http";
import type { Server as SocketIoServer } from "socket.io";
import { Server as SocketIoServerImpl } from "socket.io";

import { requireEnv, optionalEnv } from "./config/env.ts";
import { logger } from "./common/logger.ts";

import type { ITenantPreferenceRepo } from "./domain/ports/tenant-preference.repository.port.ts";
import type { INotificationLogRepo } from "./domain/ports/notification-log.repository.port.ts";
import type { IDigestQueueRepo } from "./domain/ports/digest-queue.repository.port.ts";
import type { IWebhookEndpointRepo } from "./domain/ports/webhook-endpoint.repository.port.ts";
import type { IEmailSender } from "./domain/ports/email-sender.port.ts";
import type { ISmsSender } from "./domain/ports/sms-sender.port.ts";
import type { IRealtimeGateway } from "./domain/ports/realtime-gateway.port.ts";
import type { IJwtVerifier } from "./domain/ports/jwt-verifier.port.ts";

import { PrismaTenantPreferenceRepo } from "./modules/preferences/v1/repo.ts";
import { PrismaNotificationLogRepo } from "./modules/notifications/v1/repo.ts";
import { PrismaDigestQueueRepo } from "./modules/digest/v1/repo.ts";
import { PrismaWebhookEndpointRepo } from "./modules/webhooks/v1/repo.ts";
import { NodemailerEmailSender } from "./infra/email/nodemailer-email-sender.ts";
import { UnimplementedSmsSender } from "./infra/sms/unimplemented-sms-sender.ts";
import { SocketIoRedisGateway } from "./infra/realtime/socket-io-redis-gateway.ts";
import { JwksJwtVerifier } from "./infra/auth/jwks-jwt-verifier.ts";

import { NotifService, type NotifyInput, type NotifyResult } from "./modules/notify/v1/service.ts";
import { PreferenceService } from "./modules/preferences/v1/service.ts";
import { PreferenceController } from "./modules/preferences/v1/controller.ts";
import { preferencesRoutes } from "./modules/preferences/v1/routes.ts";
import { NotificationService } from "./modules/notifications/v1/service.ts";
import { NotificationController } from "./modules/notifications/v1/controller.ts";
import { notificationsRoutes } from "./modules/notifications/v1/routes.ts";
import { WebhookEndpointService } from "./modules/webhooks/v1/service.ts";
import { WebhookEndpointController } from "./modules/webhooks/v1/controller.ts";
import { webhooksRoutes } from "./modules/webhooks/v1/routes.ts";
import { DigestService, type DigestSweepResult } from "./modules/digest/v1/service.ts";
import { DigestController } from "./modules/digest/v1/controller.ts";
import { digestRoutes } from "./modules/digest/v1/routes.ts";

import { errorHandler } from "./middleware/error-handler.ts";
import { internalSecretMiddleware } from "./middleware/internal-secret.ts";

export interface GenNotifModulesConfig {
  preferences?: boolean;
  notifications?: boolean;
  webhookEndpoints?: boolean;
  realtime?: boolean;
}

export interface GenNotifConfig {
  preferenceRepo?: ITenantPreferenceRepo;
  notificationRepo?: INotificationLogRepo;
  digestRepo?: IDigestQueueRepo;
  webhookRepo?: IWebhookEndpointRepo;
  emailSender?: IEmailSender;
  smsSender?: ISmsSender;
  realtimeGateway?: IRealtimeGateway;
  jwtVerifier?: IJwtVerifier;
  channelGate?: (tenantId: string, channel: string) => boolean | Promise<boolean>;
  modules?: GenNotifModulesConfig;
  internalSecret?: string;
}

export interface GenNotifInstance {
  app: Express;
  attachRealtime?: (httpServer: HttpServer) => SocketIoServer;
  notify(input: NotifyInput): Promise<NotifyResult>;
  runDigestSweep(): Promise<DigestSweepResult>;
}

function resolvePreferenceRepo(override: ITenantPreferenceRepo | undefined): ITenantPreferenceRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantPreferenceRepo();
}

function resolveNotificationRepo(override: INotificationLogRepo | undefined): INotificationLogRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaNotificationLogRepo();
}

function resolveDigestRepo(override: IDigestQueueRepo | undefined): IDigestQueueRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaDigestQueueRepo();
}

function resolveWebhookRepo(override: IWebhookEndpointRepo | undefined): IWebhookEndpointRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaWebhookEndpointRepo();
}

function resolveEmailSender(override: IEmailSender | undefined): IEmailSender {
  if (override) return override;
  return new NodemailerEmailSender({
    host: requireEnv("SMTP_HOST"),
    port: Number(optionalEnv("SMTP_PORT", "587")),
    secure: optionalEnv("SMTP_SECURE", "false") === "true",
    user: process.env.SMTP_USER || undefined,
    pass: process.env.SMTP_PASS || undefined,
    from: optionalEnv("SMTP_FROM", "no-reply@example.com"),
  });
}

function resolveSmsSender(override: ISmsSender | undefined): ISmsSender {
  if (override) return override;
  return new UnimplementedSmsSender();
}

function resolveJwtVerifier(override: IJwtVerifier | undefined): IJwtVerifier {
  if (override) return override;
  return new JwksJwtVerifier(requireEnv("JWKS_URL"), requireEnv("JWT_ISSUER"));
}

function resolveRealtimeGateway(override: IRealtimeGateway | undefined, jwtVerifier: IJwtVerifier): IRealtimeGateway {
  if (override) return override;
  return new SocketIoRedisGateway(requireEnv("REDIS_URL"), jwtVerifier);
}

export function createGenNotif(config: GenNotifConfig): GenNotifInstance {
  const modules: Required<GenNotifModulesConfig> = {
    preferences: config.modules?.preferences ?? true,
    notifications: config.modules?.notifications ?? true,
    webhookEndpoints: config.modules?.webhookEndpoints ?? true,
    realtime: config.modules?.realtime ?? true,
  };

  const preferenceRepo = resolvePreferenceRepo(config.preferenceRepo);
  const notificationRepo = resolveNotificationRepo(config.notificationRepo);
  const digestRepo = resolveDigestRepo(config.digestRepo);
  const webhookRepo = resolveWebhookRepo(config.webhookRepo);
  const emailSender = resolveEmailSender(config.emailSender);
  const smsSender = resolveSmsSender(config.smsSender);
  const channelGate = config.channelGate ?? (() => true);

  // realtimeGateway is optional by construction: if modules.realtime is
  // disabled and no override is supplied, notify()'s "inapp" channel
  // dispatch fails per-recipient at request time (a normal FAILED log
  // entry, per NotifService's dispatchImmediate) rather than createGenNotif()
  // refusing to boot over config (JWKS_URL/REDIS_URL) a host that only
  // wants email/webhook channels never needed to supply.
  let realtimeGateway: IRealtimeGateway | undefined = config.realtimeGateway;
  if (!realtimeGateway && modules.realtime) {
    const jwtVerifier = resolveJwtVerifier(config.jwtVerifier);
    realtimeGateway = resolveRealtimeGateway(undefined, jwtVerifier);
  }

  const internalSecret = config.internalSecret ?? optionalEnv("GEN_NOTIF_INTERNAL_SECRET", "");

  const notifService = new NotifService(
    preferenceRepo, notificationRepo, digestRepo, webhookRepo,
    emailSender, smsSender, realtimeGateway, channelGate,
  );
  const digestService = new DigestService(digestRepo, notificationRepo, emailSender);

  const app = express();
  app.use(helmet());
  app.use(cors({ origin: optionalEnv("ALLOWED_ORIGINS", "*").split(",") }));
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  app.get("/docs.json", (_req, res) => res.json(openApiSpec));
  app.use(
    "/docs",
    // Swagger UI's HTML bundle relies on an inline <script> to boot —
    // helmet's default CSP blocks that, so scope the relaxation to /docs only.
    (_req: Request, res: Response, next: NextFunction) => { res.removeHeader("Content-Security-Policy"); next(); },
    swaggerUi.serve,
    swaggerUi.setup(openApiSpec),
  );

  const gate = internalSecretMiddleware(internalSecret);

  app.post("/internal/notify", gate, async (req, res) => {
    const result = await notifService.notify(req.body as NotifyInput);
    res.json(result);
  });
  app.use("/internal/digest", gate, digestRoutes(new DigestController(digestService)));

  if (modules.preferences) {
    const controller = new PreferenceController(new PreferenceService(preferenceRepo));
    app.use("/api/v1/preferences", preferencesRoutes(controller));
  }
  if (modules.notifications) {
    const controller = new NotificationController(new NotificationService(notificationRepo));
    app.use("/api/v1/notifications", notificationsRoutes(controller));
  }
  if (modules.webhookEndpoints) {
    const controller = new WebhookEndpointController(new WebhookEndpointService(webhookRepo));
    app.use("/api/v1/webhook-endpoints", webhooksRoutes(controller));
  }

  app.use(errorHandler);

  const instance: GenNotifInstance = {
    app,
    notify: (input) => notifService.notify(input),
    runDigestSweep: () => digestService.runSweep(),
  };

  if (modules.realtime) {
    // realtimeGateway is guaranteed set here — the resolution block above
    // constructs it whenever modules.realtime is true and no override was given.
    const gateway = realtimeGateway!;
    instance.attachRealtime = (httpServer: HttpServer): SocketIoServer => {
      const io = new SocketIoServerImpl(httpServer, {
        path: "/gen-notif/ws",
        cors: { origin: optionalEnv("ALLOWED_ORIGINS", "*").split(",") },
      });
      gateway.attach(io);
      logger.info("[gen-notif] realtime gateway attached");
      return io;
    };
  }

  return instance;
}
