import type { Prisma } from '@prisma/client';

import { deleteClientsAiData } from './ai-deletion.service';
import { missingDocuments } from './document-processing';
import { removeStoredFiles } from './document-storage';
import { decryptToken, encryptToken } from './encryption';
import { notify } from './notification.service';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';
import { listActivity, recordActivity } from '../platform/activity-log';

export type LeadStage =
  | 'NEW'
  | 'QUALIFIED'
  | 'APPLICATION'
  | 'UNDERWRITING'
  | 'APPROVED'
  | 'FUNDED'
  | 'LOST';

export interface CreateLeadInput {
  name: string;
  email?: string;
  phone?: string;
  income?: number;
  monthlyDebts?: number;
  downPayment?: number;
  propertyAddress?: string;
  propertyValue?: number;
  notes?: string;
  tags?: string[];
  dateOfBirth?: string;
  sin?: string;
  bankDetails?: string;
}

type SensitiveField = 'sin' | 'bankDetails' | 'dateOfBirth';

/**
 * "New client" (Flow 4.1) — applicants/income/debts/properties/down-payment/
 * notes, single-applicant scope (no co-applicant modeling). After an AI review,
 * ai-notes.service.ts passes the approved applicant, tasks and draft email as
 * `nested`, so they are saved with the lead or not at all. SIN and bank details
 * (Flow 4.3's "hidden values") are encrypted at rest and never returned by
 * the normal read paths — see revealSensitiveField.
 */
export async function createLead(
  tenantId: string,
  brokerUserId: string,
  input: CreateLeadInput,
  nested: Pick<Prisma.LeadCreateInput, 'id' | 'applicants' | 'tasks' | 'emailDrafts'> = {},
) {
  const { dateOfBirth, sin, bankDetails, ...rest } = input;
  return getPrismaClient().lead.create({
    data: {
      ...nested,
      tenantId,
      brokerUserId,
      ...rest,
      dateOfBirth: dateOfBirth ? new Date(dateOfBirth) : undefined,
      sin: sin ? encryptToken(sin) : undefined,
      bankDetails: bankDetails ? encryptToken(bankDetails) : undefined,
    },
  });
}

/** SIN/bank details never leave the server as ciphertext either — just whether one is on file. See revealSensitiveField for the only path that decrypts them. */
export function maskLead<T extends { sin: string | null; bankDetails: string | null }>(lead: T) {
  const { sin, bankDetails, ...rest } = lead;
  return { ...rest, hasSin: sin !== null, hasBankDetails: bankDetails !== null };
}

const DAY = 24 * 60 * 60 * 1000;

/** Flow 4 dashboard "hot leads" — shared with the leads-list filter so card and list agree. */
export const HOT_STAGES: LeadStage[] = ['APPLICATION', 'UNDERWRITING', 'APPROVED'];

/** Midnight UTC today — the dashboard and the list filters count days from here. */
export function utcStartOfToday(now = new Date()) {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
}

/** Days until this date-of-birth's next anniversary (0 = today), in UTC. */
export function daysUntilBirthday(dob: Date, today: Date) {
  const next = new Date(Date.UTC(today.getUTCFullYear(), dob.getUTCMonth(), dob.getUTCDate()));
  if (next < today) next.setUTCFullYear(next.getUTCFullYear() + 1);
  return Math.round((next.getTime() - today.getTime()) / DAY);
}

/** Follow-ups due on or before the end of day N from today (overdue ones included). */
export function followUpCutoff(withinDays: number, today = utcStartOfToday()) {
  return new Date(today.getTime() + (withinDays + 1) * DAY);
}

/**
 * Flow 4 "Every card is clickable and lands on a filtered list" — the
 * filters behind each dashboard card (see brokerDashboard's links).
 * stages: explicit stages, or ['ALL'] for every stage; omitted = the
 * list's own default (active pipeline for Leads, Funded for Clients).
 */
export interface LeadListFilter {
  stages?: (LeadStage | 'ALL')[];
  hot?: boolean;
  renewalWithinDays?: number;
  followUpWithinDays?: number;
  birthdayWithinDays?: number;
  missingDocuments?: boolean;
}

async function listBook(
  tenantId: string,
  brokerIds: string[],
  defaultStage: Prisma.LeadWhereInput['stage'],
  filter: LeadListFilter,
  includeMortgages: boolean,
) {
  const today = utcStartOfToday();
  const and: Prisma.LeadWhereInput[] = [];
  if (!filter.stages?.length) and.push({ stage: defaultStage });
  else if (!filter.stages.includes('ALL'))
    and.push({ stage: { in: filter.stages as LeadStage[] } });
  if (filter.hot) and.push({ stage: { in: HOT_STAGES } });
  if (filter.renewalWithinDays !== undefined) {
    const until = new Date(Date.now() + filter.renewalWithinDays * DAY);
    and.push({ mortgages: { some: { maturityDate: { gte: new Date(), lte: until } } } });
  }
  if (filter.followUpWithinDays !== undefined) {
    and.push({
      followUps: {
        some: { scheduledAt: { lt: followUpCutoff(filter.followUpWithinDays, today) } },
      },
    });
  }
  if (filter.birthdayWithinDays !== undefined) and.push({ dateOfBirth: { not: null } });
  if (filter.missingDocuments) and.push({ NOT: { requiredDocuments: { isEmpty: true } } });

  const rows = await getPrismaClient().lead.findMany({
    where: { tenantId, brokerUserId: { in: brokerIds }, AND: and },
    orderBy: { createdAt: 'desc' },
    include: {
      mortgages: includeMortgages,
      documents: filter.missingDocuments ? { select: { type: true } } : false,
    },
  });
  // The two filters Postgres can't express through Prisma: day-of-year and set difference.
  return (
    rows
      .filter(
        (l) =>
          filter.birthdayWithinDays === undefined ||
          daysUntilBirthday(l.dateOfBirth!, today) <= filter.birthdayWithinDays,
      )
      .filter(
        (l) =>
          !filter.missingDocuments || missingDocuments(l.requiredDocuments, l.documents).length > 0,
      )
      // documents was only fetched for the missing-documents check.
      .map((l) => maskLead({ ...l, documents: undefined }))
  );
}

/** "Leads" — by default everything not yet funded and not lost (the active pipeline). */
export async function listMyLeads(
  tenantId: string,
  brokerIds: string[],
  filter: LeadListFilter = {},
) {
  return listBook(tenantId, brokerIds, { notIn: ['FUNDED', 'LOST'] }, filter, false);
}

/** "Clients" — funded leads, each with their mortgage(s). */
export async function listMyClients(
  tenantId: string,
  brokerIds: string[],
  filter: Omit<LeadListFilter, 'stages' | 'hot'> = {},
) {
  return listBook(tenantId, brokerIds, 'FUNDED', filter, true);
}

/** Throws 404 outside the tenant, 403 outside the given access set — used for both "own book" and "team book" access. */
export async function getLeadWithAccess(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
) {
  const lead = await getPrismaClient().lead.findUnique({ where: { id: leadId } });
  if (!lead || lead.tenantId !== tenantId) {
    throw new ApiError('Lead not found', 404);
  }
  if (!allowedBrokerIds.includes(lead.brokerUserId)) {
    throw new ApiError("You don't have access to this record", 403);
  }
  return lead;
}

export async function updateStage(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  stage: LeadStage,
  lostReason?: string,
  lostNote?: string,
) {
  if (stage === 'FUNDED') {
    // "This step cannot be skipped" — Funded only happens through fundLead,
    // which requires a mortgage in the same transaction.
    throw new ApiError('Funding a lead requires mortgage details — use the fund endpoint', 400);
  }
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  if (stage === 'LOST' && !lostReason) {
    throw new ApiError('A reason is required to mark a lead Lost', 400);
  }
  const updated = await getPrismaClient().lead.update({
    where: { id: lead.id },
    data: {
      stage,
      lostReason: stage === 'LOST' ? lostReason : null,
      lostNote: stage === 'LOST' ? (lostNote ?? null) : null,
    },
  });
  return maskLead(updated);
}

export interface MortgageInput {
  lender: string;
  interestRate: number;
  balance: number;
  monthlyPayment: number;
  maturityDate: string;
}

/**
 * "Change the stage to Funded" (Flow 4.6) — creates the mortgage and moves
 * the stage to FUNDED in one transaction; if either fails, "the stage
 * change is cancelled, the person stays where they were." Renewal reminders
 * aren't a separate scheduled job — see workload.service.ts's
 * renewalsWithin90Days, which reads maturityDate directly.
 */
export async function fundLead(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  mortgage: MortgageInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const [, updated] = await getPrismaClient().$transaction([
    getPrismaClient().mortgage.create({
      data: { leadId: lead.id, ...mortgage, maturityDate: new Date(mortgage.maturityDate) },
    }),
    getPrismaClient().lead.update({
      where: { id: lead.id },
      data: { stage: 'FUNDED', lostReason: null },
    }),
  ]);
  return maskLead(updated);
}

/** A client can hold more than one mortgage, each with its own lender and dates. */
export async function addMortgage(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  mortgage: MortgageInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  if (lead.stage !== 'FUNDED') {
    throw new ApiError('Only a funded client can hold a mortgage', 400);
  }
  return getPrismaClient().mortgage.create({
    data: { leadId: lead.id, ...mortgage, maturityDate: new Date(mortgage.maturityDate) },
  });
}

/** "Team book" — every lead/client belonging to any broker on the team. */
export async function listForBrokers(tenantId: string, brokerIds: string[]) {
  if (brokerIds.length === 0) return [];
  const leads = await getPrismaClient().lead.findMany({
    where: { tenantId, brokerUserId: { in: brokerIds } },
    orderBy: { createdAt: 'desc' },
  });
  return leads.map(maskLead);
}

/** "Reassign to another broker" — caller must already have verified toBrokerUserId is inside the team. */
export async function reassign(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  toBrokerUserId: string,
  actingUserId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  if (!allowedBrokerIds.includes(toBrokerUserId)) {
    throw new ApiError('That broker is not on this team', 400);
  }
  const updated = await getPrismaClient().lead.update({
    where: { id: lead.id },
    data: { brokerUserId: toBrokerUserId },
  });
  await announceReassignment(tenantId, [lead], lead.brokerUserId, toBrokerUserId, actingUserId);
  return maskLead(updated);
}

/**
 * Flow 2 "Blocked — reassign their 14 clients first → Pick a receiving
 * broker and reassign": every lead the person holds, in one go. The caller
 * checks both people are in the brokerage.
 */
export async function reassignAll(
  tenantId: string,
  fromBrokerUserId: string,
  toBrokerUserId: string,
  actingUserId: string,
) {
  if (fromBrokerUserId === toBrokerUserId) {
    throw new ApiError('Pick a different receiving broker', 400);
  }
  const leads = await getPrismaClient().lead.findMany({
    where: { tenantId, brokerUserId: fromBrokerUserId },
    select: { id: true, name: true },
  });
  await getPrismaClient().lead.updateMany({
    where: { tenantId, brokerUserId: fromBrokerUserId },
    data: { brokerUserId: toBrokerUserId },
  });
  await announceReassignment(tenantId, leads, fromBrokerUserId, toBrokerUserId, actingUserId);
  return { reassigned: leads.length };
}

/** Flow 3 "Reassigned — both brokers notified and it's recorded". */
async function announceReassignment(
  tenantId: string,
  leads: { id: string; name: string }[],
  fromBrokerUserId: string,
  toBrokerUserId: string,
  actingUserId: string,
) {
  if (leads.length === 0) return;
  const label = leads.length === 1 ? leads[0]!.name : `${leads.length} clients`;
  const leadId = leads.length === 1 ? leads[0]!.id : undefined;
  await Promise.all([
    notify({
      tenantId,
      userId: fromBrokerUserId,
      type: 'lead.reassigned_away',
      title: `${label} reassigned to another broker`,
      leadId,
    }),
    notify({
      tenantId,
      userId: toBrokerUserId,
      type: 'lead.reassigned_to_you',
      title: `${label} reassigned to you`,
      leadId,
    }),
    ...leads.map((l) =>
      recordActivity('crm.lead.reassigned', {
        actorId: actingUserId,
        targetType: 'lead',
        targetId: l.id,
        metadata: { fromBrokerUserId, toBrokerUserId },
      }),
    ),
  ]);
}

/**
 * Flow 4.11 "Type their name to confirm → Deleted permanently, recorded in
 * the activity log". Everything about the person goes with them (cascade);
 * their tasks stay, flagged unlinked (Flow 4.9), and appointments keep their
 * calendar slot without the link.
 */
export async function deleteLead(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  confirmName: string,
  actorId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  if (confirmName.trim() !== lead.name.trim()) {
    throw new ApiError("Type the person's name exactly to confirm", 400);
  }
  const documents = await getPrismaClient().leadDocument.findMany({
    where: { leadId: lead.id },
    select: { storagePath: true },
  });
  await getPrismaClient().$transaction([
    getPrismaClient().task.updateMany({ where: { leadId: lead.id }, data: { unlinked: true } }),
    getPrismaClient().lead.delete({ where: { id: lead.id } }),
  ]);
  await removeStoredFiles(documents.map((d) => d.storagePath));
  // After the CRM delete, so a running AI job can't read the person back in (runbook order).
  await deleteClientsAiData(tenantId, [lead.id]);
  await recordActivity('crm.lead.deleted', {
    actorId,
    targetType: 'lead',
    targetId: lead.id,
    metadata: { tenantId, name: lead.name },
  });
}

/** "Team pipeline" — stage counts across every broker on the team. */
export async function pipelineCounts(
  tenantId: string,
  brokerIds: string[],
): Promise<Record<LeadStage, number>> {
  const base: Record<LeadStage, number> = {
    NEW: 0,
    QUALIFIED: 0,
    APPLICATION: 0,
    UNDERWRITING: 0,
    APPROVED: 0,
    FUNDED: 0,
    LOST: 0,
  };
  if (brokerIds.length === 0) return base;
  const grouped = await getPrismaClient().lead.groupBy({
    by: ['stage'],
    where: { tenantId, brokerUserId: { in: brokerIds } },
    _count: true,
  });
  for (const row of grouped) base[row.stage as LeadStage] = row._count;
  return base;
}

/** Open leads per broker — for the workload view; brokers with none stay at 0 (see workload.service.ts). */
export async function openLeadCountByBroker(
  tenantId: string,
  brokerIds: string[],
): Promise<Record<string, number>> {
  const counts: Record<string, number> = Object.fromEntries(brokerIds.map((id) => [id, 0]));
  if (brokerIds.length === 0) return counts;
  const grouped = await getPrismaClient().lead.groupBy({
    by: ['brokerUserId'],
    where: { tenantId, brokerUserId: { in: brokerIds }, stage: { notIn: ['FUNDED', 'LOST'] } },
    _count: true,
  });
  for (const row of grouped) counts[row.brokerUserId] = row._count;
  return counts;
}

/**
 * "Do they still have clients assigned?" (Flow 2 / SOW 2.11) — every lead/
 * client still on this broker's book, Lost excluded since a dead deal needs
 * no reassignment. Called by modauth's internal deactivation check (see
 * docs/swagger/crm.yaml's internal path), not by any JWT-authenticated route.
 */
export async function activeAssignedLeadCount(
  tenantId: string,
  brokerUserId: string,
): Promise<number> {
  return getPrismaClient().lead.count({
    where: { tenantId, brokerUserId, stage: { not: 'LOST' } },
  });
}

/** Renewals due within 90 days, per broker — the workload view's real figure (see Mortgage.maturityDate). */
export async function renewalCountByBroker(
  tenantId: string,
  brokerIds: string[],
): Promise<Record<string, number>> {
  const counts: Record<string, number> = Object.fromEntries(brokerIds.map((id) => [id, 0]));
  if (brokerIds.length === 0) return counts;
  const in90Days = new Date();
  in90Days.setDate(in90Days.getDate() + 90);
  const mortgages = await getPrismaClient().mortgage.findMany({
    where: {
      maturityDate: { gte: new Date(), lte: in90Days },
      lead: { tenantId, brokerUserId: { in: brokerIds } },
    },
    select: { lead: { select: { brokerUserId: true } } },
  });
  for (const m of mortgages) counts[m.lead.brokerUserId] = (counts[m.lead.brokerUserId] ?? 0) + 1;
  return counts;
}

// ── Person panel (Flow 4.3) ──────────────────────────────────────────────────

/** "Your notes - editable" — "Original notes" (the `notes` field) is locked forever and never touched here. Each edit is appended to the activity log as its own history entry. */
export async function updateNotes(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  brokerNotes: string,
  actorId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const updated = await getPrismaClient().lead.update({
    where: { id: lead.id },
    data: { brokerNotes },
  });
  await recordActivity('crm.notes.updated', {
    actorId,
    targetType: 'lead',
    targetId: lead.id,
    metadata: { previous: lead.brokerNotes, next: brokerNotes },
  });
  return maskLead(updated);
}

export async function updateTags(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  tags: string[],
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const updated = await getPrismaClient().lead.update({ where: { id: lead.id }, data: { tags } });
  return maskLead(updated);
}

export interface UpdateProfileInput {
  name?: string;
  email?: string;
  phone?: string;
  city?: string;
  fileStatus?: 'ACTIVE' | 'INACTIVE';
  propertyAddress?: string;
  propertyValue?: number;
  income?: number;
  monthlyDebts?: number;
  downPayment?: number;
}

/**
 * "Inline profile edit with Save and Cancel" (SOW 6.9) — one call for the
 * header/profile-tab fields that don't already have their own dedicated
 * endpoint (notes, tags, stage and the sensitive fields are all edited
 * elsewhere, deliberately not folded in here). "Cancel" is just not
 * calling this — there's nothing to undo server-side.
 */
export async function updateLeadProfile(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  input: UpdateProfileInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const updated = await getPrismaClient().lead.update({ where: { id: lead.id }, data: input });
  return maskLead(updated);
}

/** "Click a hidden value... Reveal — the reveal is recorded" — re-hiding on navigate-away is a frontend concern; this just decrypts and logs. */
export async function revealSensitiveField(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  field: SensitiveField,
  actorId: string,
): Promise<{ field: SensitiveField; value: string | null }> {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  await recordActivity('crm.lead.sensitive_field_revealed', {
    actorId,
    targetType: 'lead',
    targetId: lead.id,
    metadata: { field },
  });

  if (field === 'dateOfBirth') {
    return { field, value: lead.dateOfBirth ? lead.dateOfBirth.toISOString() : null };
  }
  const encrypted = lead[field];
  return { field, value: encrypted ? decryptToken(encrypted) : null };
}

/**
 * "The person panel... same panel everywhere" — one aggregated read for the
 * Profile/Affordability/Mortgages/Documents/Notes/Activity tabs. AI Tools
 * has its own endpoints (ai-review.controller.ts).
 */
export async function getPersonPanel(tenantId: string, leadId: string, allowedBrokerIds: string[]) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const [mortgages, history, recentActivity, documents] = await Promise.all([
    getPrismaClient().mortgage.findMany({
      where: { leadId: lead.id },
      orderBy: { createdAt: 'desc' },
    }),
    listActivity({ targetId: lead.id, action: 'crm.notes.updated', size: 100 }),
    listActivity({ targetId: lead.id, size: 50 }),
    getPrismaClient().leadDocument.findMany({
      where: { leadId: lead.id },
      orderBy: { createdAt: 'desc' },
      omit: { storagePath: true },
    }),
  ]);

  return {
    profile: maskLead(lead),
    mortgages,
    notes: {
      original: lead.notes,
      yours: lead.brokerNotes,
      history: history.items,
    },
    activity: recentActivity.items,
    documents: {
      required: lead.requiredDocuments,
      items: documents,
      missing: missingDocuments(lead.requiredDocuments, documents),
    },
  };
}
