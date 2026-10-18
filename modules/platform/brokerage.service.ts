import { randomUUID } from 'node:crypto';

import { activityLogEventPublisher, recordActivity } from './activity-log';
import {
  createBrokerageOwnerInvitation,
  emailAlreadyHasAccount,
  setTenantAccess,
} from './auth-service.client';
import { purgeTenantCrmData, startExport } from './export.service';
import type {
  BrokerageExportJobDto,
  BrokerageSummary,
  CreateBrokerageInput,
  TenantRecord,
} from './platform.types';
import { tenantServiceGaps } from './tenant-service.client';
import { getPrismaClient } from '../../config/database';
import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

// gen-sup-starter is a pure-ESM package (its own package.json says
// "type": "module", built from source with .ts-extension imports) — this app
// is CommonJS (tsconfig "module": "commonjs"), so it's loaded via a dynamic
// import() rather than a static one. Node's CJS->ESM interop handles this
// fine; a static import here would fail to compile under this tsconfig.
function importGenSup() {
  return import('@gen-ms/gen-sup-starter');
}
type GenSup = Awaited<ReturnType<typeof importGenSup>>;

let genSupPromise: Promise<GenSup> | null = null;
function loadGenSup(): Promise<GenSup> {
  genSupPromise ??= importGenSup();
  return genSupPromise;
}

type TenantsService = InstanceType<GenSup['TenantsService']>;
let tenantsServicePromise: Promise<TenantsService> | null = null;
async function getTenantsService(): Promise<TenantsService> {
  if (!tenantsServicePromise) {
    tenantsServicePromise = loadGenSup().then((genSup) => {
      const tntClient = new genSup.HttpTntClient(
        env.platform.tenantServiceBaseUrl,
        env.platform.internalSecret,
      );
      return new genSup.TenantsService(tntClient, activityLogEventPublisher);
    });
  }
  return tenantsServicePromise;
}

function slugify(name: string): string {
  const base = name
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return base.length >= 3 ? base : `${base}-brokerage`;
}

async function createTenantWithUniqueSlug(
  tenantsService: TenantsService,
  genSup: GenSup,
  input: CreateBrokerageInput,
): Promise<Awaited<ReturnType<TenantsService['create']>>> {
  const baseSlug = slugify(input.name);
  for (let attempt = 0; attempt < 5; attempt += 1) {
    const slug = attempt === 0 ? baseSlug : `${baseSlug}-${randomUUID().slice(0, 6)}`;
    try {
      return await tenantsService.create({
        name: input.name,
        slug,
        region: input.region,
        ownerEmail: input.ownerEmail,
        ownerFirstName: input.ownerName,
        idempotencyKey: randomUUID(),
      });
    } catch (err) {
      if (!(err instanceof genSup.TenantSlugTakenError) || attempt === 4) {
        throw err;
      }
    }
  }
  throw new ApiError('Could not allocate a unique brokerage slug', 500);
}

async function attachOnboardingStatus(tenants: TenantRecord[]): Promise<BrokerageSummary[]> {
  if (tenants.length === 0) return [];
  const rows = await getPrismaClient().brokerageOnboarding.findMany({
    where: { tenantId: { in: tenants.map((t) => t.id) } },
  });
  const byTenant = new Map(rows.map((r) => [r.tenantId, r]));
  return tenants.map((tenant) => {
    const row = byTenant.get(tenant.id);
    return {
      ...tenant,
      onboardingStatus: (row?.status as 'PENDING' | 'ACTIVE') ?? 'ACTIVE',
      invitationId: row?.invitationId ?? '',
    };
  });
}

export async function createBrokerage(input: CreateBrokerageInput): Promise<BrokerageSummary> {
  if (await emailAlreadyHasAccount(input.ownerEmail)) {
    throw new ApiError('That email already belongs to another account', 409);
  }

  const genSup = await loadGenSup();
  const tenantsService = await getTenantsService();
  const created = await createTenantWithUniqueSlug(tenantsService, genSup, input);

  const invitation = await createBrokerageOwnerInvitation({
    name: input.ownerName,
    email: input.ownerEmail,
    tenantId: created.id,
    preAllocatedUserId: created.primaryOwnerUserId,
  });

  await getPrismaClient().brokerageOnboarding.create({
    data: { tenantId: created.id, invitationId: invitation.id, status: 'PENDING' },
  });

  return { ...created, onboardingStatus: 'PENDING', invitationId: invitation.id };
}

export async function listBrokerages(
  page: number,
  size: number,
): Promise<{ items: BrokerageSummary[]; total: number }> {
  const result = await tenantServiceGaps.list(page, size);
  return { items: await attachOnboardingStatus(result.content), total: result.totalElements };
}

export async function getBrokerage(id: string): Promise<BrokerageSummary> {
  const tenantsService = await getTenantsService();
  const tenant = await tenantsService.getById(id);
  const [summary] = await attachOnboardingStatus([tenant]);
  return summary!;
}

// Sign-in is blocked before the status changes and restored only after it
// does — a failure half-way always leaves people locked out, never let in.
export async function suspendBrokerage(id: string, reason: string): Promise<TenantRecord> {
  const tenantsService = await getTenantsService();
  await setTenantAccess(id, true, 'SUSPENDED');
  return tenantsService.suspend(id, reason);
}

export async function reactivateBrokerage(id: string, note?: string): Promise<TenantRecord> {
  const tenantsService = await getTenantsService();
  const tenant = await tenantsService.reactivate(id, note);
  await setTenantAccess(id, false, 'ACTIVE');
  return tenant;
}

export async function startBrokerageCancellation(
  id: string,
): Promise<{ tenant: TenantRecord; exportJob: BrokerageExportJobDto }> {
  await setTenantAccess(id, true, 'CANCELLED');
  const tenant = await tenantServiceGaps.cancel(id);
  // Starts the retention clock for purgeCancelledBrokerages.
  await getPrismaClient().brokerageOnboarding.update({
    where: { tenantId: id },
    data: { cancelledAt: new Date() },
  });
  await recordActivity('platform.brokerage.cancellation_started', {
    targetType: 'tenant',
    targetId: id,
  });
  const exportJob = await startExport(tenant);
  return { tenant, exportJob };
}

/** Called by modules/platform's own webhook route after modules/auth confirms a TENANT_ADMIN invite was accepted. */
export async function markBrokerageOwnerAccepted(tenantId: string): Promise<void> {
  await getPrismaClient().brokerageOnboarding.update({
    where: { tenantId },
    data: { status: 'ACTIVE' },
  });
  await recordActivity('platform.brokerage.owner_accepted', {
    targetType: 'tenant',
    targetId: tenantId,
  });
}

/**
 * Flow 1 "Download available for the agreed window → permanently deleted
 * after the retention period". Runs hourly from server.ts: every brokerage
 * cancelled longer ago than BROKERAGE_RETENTION_DAYS loses its CRM data and
 * stored documents, and its tenant record is purged. Its people stay
 * blocked from sign-in (set at cancellation).
 */
export async function purgeCancelledBrokerages(now = new Date()): Promise<number> {
  const cutoff = new Date(now.getTime() - env.platform.retentionDays * 24 * 60 * 60 * 1000);
  const due = await getPrismaClient().brokerageOnboarding.findMany({
    where: { cancelledAt: { lt: cutoff }, purgedAt: null },
  });
  for (const row of due) {
    await purgeTenantCrmData(row.tenantId);
    await tenantServiceGaps.purge(row.tenantId);
    await getPrismaClient().brokerageOnboarding.update({
      where: { tenantId: row.tenantId },
      data: { purgedAt: now },
    });
    await recordActivity('platform.brokerage.purged', {
      targetType: 'tenant',
      targetId: row.tenantId,
    });
  }
  return due.length;
}

/** Flow 1 activity log — sign-in and security events forwarded by modules/auth. */
export async function recordAuthEvent(event: {
  action: string;
  tenantId?: string | null;
  userId?: string | null;
  ip?: string | null;
  details?: string | null;
}): Promise<void> {
  await recordActivity(event.action, {
    actorId: event.userId ?? undefined,
    targetType: event.tenantId ? 'tenant' : undefined,
    targetId: event.tenantId ?? undefined,
    metadata: { ip: event.ip ?? null, details: event.details ?? null },
  });
}
