import { pipelineCounts, reassignAll } from './lead.service';
import { getTenantPeople, type TenantPerson } from './modauth.client';
import { aiUsageReport } from './reports.service';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';
import * as exportService from '../platform/export.service';

export interface TenantProfileInput {
  displayName?: string;
  supportEmail?: string;
  phone?: string;
  address?: string;
}

/** Brokerage profile — Tenant Admin's own settings screen (Flow 2). */
export async function getProfile(tenantId: string) {
  return (
    (await getPrismaClient().tenantProfile.findUnique({ where: { tenantId } })) ?? {
      tenantId,
      displayName: null,
      supportEmail: null,
      phone: null,
      address: null,
      updatedAt: null,
    }
  );
}

/**
 * Flow 0 "Tenant Admin → Complete the brokerage profile, required before
 * anything else": name, support email and phone (address is optional).
 */
export async function isProfileComplete(tenantId: string) {
  const profile = await getProfile(tenantId);
  return Boolean(profile.displayName && profile.supportEmail && profile.phone);
}

export async function updateProfile(tenantId: string, input: TenantProfileInput) {
  return getPrismaClient().tenantProfile.upsert({
    where: { tenantId },
    create: { tenantId, ...input },
    update: input,
  });
}

export interface BookingSettingsInput {
  slug?: string;
  enabled?: boolean;
  bufferMinutes?: number;
  defaultBrokerUserId?: string | null;
}

const BOOK_HOLDERS = ['BROKER', 'TEAM_LEAD'];

function requireBookHolder(people: TenantPerson[], userId: string) {
  const person = people.find((p) => p.userId === userId);
  if (!person || !BOOK_HOLDERS.includes(person.role)) {
    throw new ApiError('Pick a Broker or Team Lead in this brokerage', 400);
  }
  return person;
}

/**
 * Booking page setup — settings/config only (per the agreed scope): a
 * slug, on/off, and a buffer. No public scheduling/availability engine.
 */
export async function getBookingSettings(tenantId: string) {
  return (
    (await getPrismaClient().bookingPageSettings.findUnique({ where: { tenantId } })) ?? {
      tenantId,
      slug: null,
      enabled: false,
      bufferMinutes: 15,
      defaultBrokerUserId: null,
      updatedAt: null,
    }
  );
}

export async function updateBookingSettings(
  tenantId: string,
  input: BookingSettingsInput,
  callerBearerToken = '',
) {
  if (input.defaultBrokerUserId) {
    requireBookHolder(await getTenantPeople(callerBearerToken), input.defaultBrokerUserId);
  }
  if (input.slug) {
    const existing = await getPrismaClient().bookingPageSettings.findUnique({
      where: { slug: input.slug },
    });
    if (existing && existing.tenantId !== tenantId) {
      throw new ApiError('That booking page slug is already taken', 409);
    }
  }
  const current = await getPrismaClient().bookingPageSettings.findUnique({ where: { tenantId } });
  if (!current && !input.slug) {
    throw new ApiError('A slug is required to set up the booking page', 400);
  }
  return getPrismaClient().bookingPageSettings.upsert({
    where: { tenantId },
    create: {
      tenantId,
      slug: input.slug!,
      enabled: input.enabled ?? false,
      bufferMinutes: input.bufferMinutes ?? 15,
      defaultBrokerUserId: input.defaultBrokerUserId ?? null,
    },
    update: input,
  });
}

/** "Every lead and client across the brokerage" — Tenant Admin only, no team filter. */
export async function tenantWideBook(tenantId: string, callerBearerToken: string) {
  const people = await getTenantPeople(callerBearerToken);
  const brokerIds = people.map((p) => p.userId);
  if (brokerIds.length === 0) return [];
  return getPrismaClient().lead.findMany({
    where: { tenantId, brokerUserId: { in: brokerIds } },
    orderBy: { createdAt: 'desc' },
  });
}

/** "Export all our data" — Tenant Admin's self-service counterpart of the Super Admin's cancellation export. */
export async function startSelfServiceExport(tenantId: string) {
  return exportService.startSelfServiceExport(tenantId);
}

export async function getExportStatus(tenantId: string) {
  return exportService.getLatestExportForTenant(tenantId);
}

export async function getExportDownloadPath(tenantId: string, jobId: string) {
  const latest = await getExportStatus(tenantId);
  if (!latest || latest.id !== jobId) {
    throw new ApiError("Not your brokerage's export", 403);
  }
  return exportService.getDownloadPath(jobId);
}

/** Flow 2 "Brokerage dashboard" — the Tenant Admin's landing screen. */
export async function brokerageDashboard(tenantId: string, callerBearerToken: string) {
  const people = await getTenantPeople(callerBearerToken);
  const brokerIds = people.filter((p) => BOOK_HOLDERS.includes(p.role)).map((p) => p.userId);
  const in90Days = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000);
  const [pipeline, renewalsIn90Days, profileComplete, aiUsage] = await Promise.all([
    pipelineCounts(tenantId, brokerIds),
    getPrismaClient().mortgage.count({
      where: { lead: { tenantId }, maturityDate: { gte: new Date(), lte: in90Days } },
    }),
    isProfileComplete(tenantId),
    aiUsageReport({ tenantId, brokerIds, from: new Date(), to: new Date() }),
  ]);
  const peopleByRole = people.reduce<Record<string, number>>((acc, p) => {
    acc[p.role] = (acc[p.role] ?? 0) + 1;
    return acc;
  }, {});
  return { profileComplete, peopleByRole, pipeline, renewalsIn90Days, aiUsage };
}

/** Flow 2 "Blocked — reassign their clients first → Pick a receiving broker and reassign". */
export async function reassignAllClients(
  tenantId: string,
  callerBearerToken: string,
  fromUserId: string,
  toBrokerUserId: string,
  actorId: string,
) {
  const people = await getTenantPeople(callerBearerToken);
  if (!people.some((p) => p.userId === fromUserId)) {
    throw new ApiError("That person isn't in this brokerage", 404);
  }
  requireBookHolder(people, toBrokerUserId);
  return reassignAll(tenantId, fromUserId, toBrokerUserId, actorId);
}
