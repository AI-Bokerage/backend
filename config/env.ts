import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config();

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().min(1).max(65535).default(4000),
  APP_NAME: z.string().default('Enterprise Backend Template'),
  API_PREFIX: z.string().default('/api/v1'),
  ALLOWED_ORIGINS: z.string().default('http://localhost:3000'),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),
  JWT_ACCESS_SECRET: z.string().min(16),
  JWT_REFRESH_SECRET: z.string().min(16),
  JWT_ACCESS_EXPIRES_IN: z.string().default('15m'),
  JWT_REFRESH_EXPIRES_IN: z.string().default('7d'),
  BCRYPT_SALT_ROUNDS: z.coerce.number().int().min(4).max(15).default(12),
  DB_CLIENT: z.enum(['prisma', 'mongoose', 'both']).default('prisma'),
  DATABASE_URL: z.string().url(),
  MONGODB_URI: z.string().min(10),

  // ── modules/platform (Super Admin console) ────────────────────────────────
  AUTH_SERVICE_BASE_URL: z.string().url().default('http://localhost:8102'),
  TENANT_SERVICE_BASE_URL: z.string().url().default('http://localhost:8103'),
  // Same shared secret every Gen_MS service in this stack gates /internal/**
  // with — modules/auth, modules/tenant, and this app's own webhook receiver.
  INTERNAL_SERVICE_SECRET: z.string().min(8),
  // PEM contents (not a path) so the same env var works identically whether
  // this app runs on the host or in a container.
  AUTH_JWT_PUBLIC_KEY: z.string().min(1),
  AUTH_JWT_ISSUER: z.string().default('https://auth.example.com'),
  AUTH_JWT_AUDIENCE: z.string().default('example-api'),
  BROKERAGE_EXPORT_DOWNLOAD_DAYS: z.coerce.number().int().min(1).default(7),
  // Flow 1 "permanently deleted after the retention period" — days after cancellation.
  BROKERAGE_RETENTION_DAYS: z.coerce.number().int().min(1).default(90),
  BROKERAGE_EXPORT_RETENTION_DAYS: z.coerce.number().int().min(1).default(30),

  // ── modules/platform — AI cost (Flow 1) ──────────────────────────────────
  // "Allow extra for today" raises a brokerage's daily AI cost cap to this (CAD).
  AI_COST_OVERRIDE_LIMIT: z.coerce.number().positive().default(100),

  // ── modules/crm — Google OAuth (Connect email/calendar, Flow 4.7/4.8) ──────
  // Empty by default so the app still boots without this feature configured —
  // google-oauth.client.ts throws a clear error at call time instead, same
  // pattern as modauth's optional JavaMailSender bean.
  GOOGLE_CLIENT_ID: z.string().default(''),
  GOOGLE_CLIENT_SECRET: z.string().default(''),
  GOOGLE_REDIRECT_URI: z
    .string()
    .default('http://localhost:3000/api/v1/crm/integrations/google/callback'),
  // 32-byte hex key for AES-256-GCM token-at-rest encryption — this dev
  // default is fine for local/test; override it in any real deployment.
  TOKEN_ENCRYPTION_KEY: z
    .string()
    .default('eed03caf5b9fad03e8fb6243a466ed9d2eda77b629fa50899c4eff234b68f8cc'),

  // ── modules/crm — documents, borrower links, reminders (Flow 4.5/4.8) ─────
  // Where the borrower-facing upload page lives; request links point here.
  CRM_FRONTEND_URL: z.string().url().default('http://localhost:3000'),
  // Local-disk document store. shortcut: local disk only, swap for Canadian-region object storage at deploy.
  DOCUMENT_STORAGE_DIR: z.string().default('storage/documents'),
  DOCUMENT_MAX_BYTES: z.coerce
    .number()
    .int()
    .positive()
    .default(20 * 1024 * 1024),
  // ── AI services (ai service/ — gateway + the feature services) ─────────────
  // Each key is one of that service's API_KEYS (its apps/<svc>/.env). Empty =
  // the feature answers 503 "not configured" at call time; the app still boots.
  AI_GATEWAY_URL: z.string().url().default('http://localhost:4201'),
  AI_GATEWAY_KEY: z.string().default(''),
  AI_GATEWAY_ADMIN_KEY: z.string().default(''),
  AI_VOICE_URL: z.string().url().default('http://localhost:4202'),
  AI_VOICE_KEY: z.string().default(''),
  AI_DOCREAD_URL: z.string().url().default('http://localhost:4203'),
  AI_DOCREAD_KEY: z.string().default(''),
  AI_NOTES_URL: z.string().url().default('http://localhost:4204'),
  AI_NOTES_KEY: z.string().default(''),
  AI_EMAIL_URL: z.string().url().default('http://localhost:4205'),
  AI_EMAIL_KEY: z.string().default(''),
  AI_ASSISTANT_URL: z.string().url().default('http://localhost:4206'),
  AI_ASSISTANT_KEY: z.string().default(''),
  AI_SEARCH_URL: z.string().url().default('http://localhost:4207'),
  AI_SEARCH_KEY: z.string().default(''),
  // Where the AI services can reach this API for their signed job callbacks, as seen from
  // their containers (e.g. http://host.docker.internal:4000). Empty = no callbacks.
  AI_CALLBACK_BASE_URL: z.string().default(''),
  // Each service's WEBHOOK_SECRET (its apps/<svc>/.env); signs the callbacks it sends.
  AI_DOCREAD_WEBHOOK_SECRET: z.string().default(''),
  // The key assistant-svc presents when it reads /ai-data/v1 (its CRM_API_KEY).
  AI_DATA_API_KEY: z.string().default(''),

  // Off in tests; on in the server so reminders/digest/calendar retries run hourly.
  // Flow 4.5 virus check: clamd's TCP address (docker-compose's `clamav` service).
  CLAMAV_HOST: z.string().default('localhost'),
  CLAMAV_PORT: z.coerce.number().int().positive().default(3310),
  CRM_REMINDERS_ENABLED: z
    .enum(['true', 'false'])
    .default('true')
    .transform((v) => v === 'true'),
});

const parsed = envSchema.safeParse(process.env);
if (!parsed.success) {
  // Fail fast in startup for invalid env.
  throw new Error(
    `Invalid environment variables: ${JSON.stringify(parsed.error.flatten().fieldErrors)}`,
  );
}

const e = parsed.data;

export const env = {
  nodeEnv: e.NODE_ENV,
  port: e.PORT,
  appName: e.APP_NAME,
  apiPrefix: e.API_PREFIX,
  allowedOrigins: e.ALLOWED_ORIGINS.split(',').map((origin) => origin.trim()),
  logLevel: e.LOG_LEVEL,
  bcryptSaltRounds: e.BCRYPT_SALT_ROUNDS,
  dbClient: e.DB_CLIENT,
  databaseUrl: e.DATABASE_URL,
  mongodbUri: e.MONGODB_URI,
  jwt: {
    accessSecret: e.JWT_ACCESS_SECRET,
    refreshSecret: e.JWT_REFRESH_SECRET,
    accessExpiresIn: e.JWT_ACCESS_EXPIRES_IN,
    refreshExpiresIn: e.JWT_REFRESH_EXPIRES_IN,
  },
  platform: {
    authServiceBaseUrl: e.AUTH_SERVICE_BASE_URL,
    tenantServiceBaseUrl: e.TENANT_SERVICE_BASE_URL,
    internalSecret: e.INTERNAL_SERVICE_SECRET,
    authJwtPublicKey: e.AUTH_JWT_PUBLIC_KEY.replace(/\\n/g, '\n'),
    authJwtIssuer: e.AUTH_JWT_ISSUER,
    authJwtAudience: e.AUTH_JWT_AUDIENCE,
    exportDownloadDays: e.BROKERAGE_EXPORT_DOWNLOAD_DAYS,
    retentionDays: e.BROKERAGE_RETENTION_DAYS,
    exportRetentionDays: e.BROKERAGE_EXPORT_RETENTION_DAYS,
    aiCostOverrideLimit: e.AI_COST_OVERRIDE_LIMIT,
  },
  google: {
    clientId: e.GOOGLE_CLIENT_ID,
    clientSecret: e.GOOGLE_CLIENT_SECRET,
    redirectUri: e.GOOGLE_REDIRECT_URI,
    tokenEncryptionKey: e.TOKEN_ENCRYPTION_KEY,
  },
  ai: {
    gateway: { url: e.AI_GATEWAY_URL, key: e.AI_GATEWAY_KEY, adminKey: e.AI_GATEWAY_ADMIN_KEY },
    voice: { url: e.AI_VOICE_URL, key: e.AI_VOICE_KEY },
    docread: {
      url: e.AI_DOCREAD_URL,
      key: e.AI_DOCREAD_KEY,
      webhookSecret: e.AI_DOCREAD_WEBHOOK_SECRET,
    },
    notes: { url: e.AI_NOTES_URL, key: e.AI_NOTES_KEY },
    email: { url: e.AI_EMAIL_URL, key: e.AI_EMAIL_KEY },
    assistant: { url: e.AI_ASSISTANT_URL, key: e.AI_ASSISTANT_KEY },
    search: { url: e.AI_SEARCH_URL, key: e.AI_SEARCH_KEY },
    dataApiKey: e.AI_DATA_API_KEY,
    callbackBaseUrl: e.AI_CALLBACK_BASE_URL,
  },
  crm: {
    frontendUrl: e.CRM_FRONTEND_URL,
    documentStorageDir: e.DOCUMENT_STORAGE_DIR,
    documentMaxBytes: e.DOCUMENT_MAX_BYTES,
    remindersEnabled: e.CRM_REMINDERS_ENABLED,
    clamav: { host: e.CLAMAV_HOST, port: e.CLAMAV_PORT },
  },
} as const;
