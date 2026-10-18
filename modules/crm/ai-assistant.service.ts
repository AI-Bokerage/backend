import type { DocumentType, Prisma } from '@prisma/client';
import { StatusCodes } from 'http-status-codes';
import { randomUUID } from 'node:crypto';

import { DOCUMENT_LABELS } from './ai-email.service';
import { PRIORITIES } from './ai-notes.service';
import { aiCall, type AiCaller } from './ai.client';
import { missingDocuments } from './document-processing';
import { getLeadWithAccess } from './lead.service';
import { getModAuthPerson } from './modauth.client';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';

/**
 * Flow 4.10 AI Assistant. assistant-svc answers the question; to do it, it
 * reads CRM records back through GET /ai-data/v1 (ai-data.routes.ts) as the
 * asking user. "It only reads": that API has no write route.
 */

// ─── Who may see what, while their question is being answered ───

/**
 * A Team Lead's or Assistant's team is resolved with their own sign-in (see
 * leadAccessIds), which assistant-svc doesn't have. So the chat call registers
 * the asker's access under a one-off request id; assistant-svc sends that id
 * back with every data read it makes for the question.
 */
const grants = new Map<string, { tenantId: string; userId: string; accessIds: string[]; expires: number }>();
const GRANT_MS = 90_000; // longer than aiCall's 60 s timeout

export async function askAssistant(
  caller: AiCaller & { userId: string },
  accessIds: string[],
  input: { question: string; history?: { role: string; content: string }[]; clientId?: string },
) {
  const requestId = randomUUID();
  grants.set(requestId, { tenantId: caller.tenantId, userId: caller.userId, accessIds, expires: Date.now() + GRANT_MS });
  try {
    return await aiCall<{ status: string; [k: string]: unknown }>('assistant', 'POST', '/v1/chat', {
      caller,
      json: input,
      requestId,
    });
  } finally {
    grants.delete(requestId);
  }
}

/**
 * A recommendation run is a background job (no live question), so starting
 * one lends the user's access for the run's lifetime instead.
 * shortcut: in-memory, so a backend restart mid-run fails a team member's run (they refresh again); move to the DB if the backend runs on several instances.
 */
const runGrants = new Map<string, { accessIds: string[]; expires: number }>();
const RUN_GRANT_MS = 30 * 60_000;

export interface DataScope {
  tenantId: string;
  accessIds: string[];
}

/** The asking user's lead access for an /ai-data/v1 read. Never wider than leadAccessIds. */
export async function dataScope(h: { tenantId?: string; userId?: string; requestId?: string }): Promise<DataScope> {
  if (!h.tenantId || !h.userId) throw new ApiError('x-brokerage-id and x-user-id are required', StatusCodes.BAD_REQUEST);
  const grant = h.requestId ? grants.get(h.requestId) : undefined;
  if (grant && grant.expires > Date.now() && grant.tenantId === h.tenantId && grant.userId === h.userId) {
    return { tenantId: grant.tenantId, accessIds: grant.accessIds };
  }
  const run = runGrants.get(`${h.tenantId}:${h.userId}`);
  if (run && run.expires > Date.now()) return { tenantId: h.tenantId, accessIds: run.accessIds };
  // Nothing lent: only a one-person book can be resolved without a sign-in.
  const person = await getModAuthPerson(h.userId);
  if (!person || !person.active || person.tenantSuspended || person.tenantId !== h.tenantId) {
    throw new ApiError("You don't have access to that", StatusCodes.FORBIDDEN);
  }
  if (person.role !== 'BROKER' && person.role !== 'TENANT_ADMIN') {
    throw new ApiError("A team's records can only be read while its user is asking", StatusCodes.FORBIDDEN);
  }
  return { tenantId: person.tenantId, accessIds: [h.userId] };
}

// ─── The records (the contract: ai service/apps/assistant-svc/README.md) ───

type Field = string | number | boolean | null;
interface AiRecord {
  type: 'client' | 'mortgage' | 'task' | 'document' | 'note';
  id: string;
  clientId: string;
  fields: Record<string, Field>;
}

// Derived day counts are in the brokerage's time zone, as the contract asks.
const TZ = 'America/Toronto';
const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(new Date());
// Due and maturity dates are stored as dates (UTC midnight).
const day = (d: Date) => d.toISOString().slice(0, 10);
const daysFrom = (from: string, to: string) => Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000);
const startOf = (ymd: string) => new Date(`${ymd}T00:00:00.000Z`);
const nextDay = (ymd: string) => new Date(Date.parse(ymd) + 86_400_000);

const inScope = (s: DataScope): Prisma.LeadWhereInput => ({ tenantId: s.tenantId, brokerUserId: { in: s.accessIds } });

const mortgageRecord = (
  m: { id: string; leadId: string; lender: string; balance: number; interestRate: number; maturityDate: Date },
  lead: { name: string; propertyAddress: string | null },
): AiRecord => ({
  type: 'mortgage',
  id: m.id,
  clientId: m.leadId,
  fields: {
    clientName: lead.name,
    lender: m.lender,
    balance: m.balance,
    rate: m.interestRate,
    propertyAddress: lead.propertyAddress,
    maturityDate: day(m.maturityDate),
    // maturityDate reaches the AI masked; the month alone identifies no one and lets it answer "renewals in March".
    maturityMonth: m.maturityDate.toLocaleString('en-CA', { month: 'long', year: 'numeric', timeZone: 'UTC' }),
    daysToMaturity: daysFrom(today(), day(m.maturityDate)),
  },
});

export async function renewals(s: DataScope, from: string, to: string, limit: number) {
  const where = { lead: inScope(s), maturityDate: { gte: startOf(from), lt: nextDay(to) } };
  const db = getPrismaClient();
  const [rows, total] = await Promise.all([
    db.mortgage.findMany({ where, include: { lead: true }, orderBy: { maturityDate: 'asc' }, take: limit }),
    db.mortgage.count({ where }),
  ]);
  return { records: rows.map((m) => mortgageRecord(m, m.lead)), total };
}

// What a question may call a document ("tax documents", "pay stub", "NOA").
const DOCUMENT_WORDS: Record<DocumentType, string> = {
  ID: 'id identification photo licence passport',
  T4: 't4 slip tax income',
  NOA: 'noa notice of assessment tax income',
  PAY_STUB: 'pay stub paystub payslip income',
  EMPLOYMENT_LETTER: 'employment letter job',
  BANK_STATEMENT: 'bank statement',
  PURCHASE_AGREEMENT: 'purchase agreement offer',
  MORTGAGE_STATEMENT: 'mortgage statement',
  PROPERTY_TAX: 'property tax bill',
  OTHER: 'other',
};
const IGNORED_WORDS = new Set(['document', 'documents', 'doc', 'docs', 'the', 'a', 'an', 'their', 'any', 'missing']);

/** The document types a free-text name means; every meaningful word must match. */
export function documentTypesMatching(name: string): DocumentType[] {
  const words = name.toLowerCase().split(/[^a-z0-9]+/).filter((w) => w && !IGNORED_WORDS.has(w));
  return (Object.keys(DOCUMENT_WORDS) as DocumentType[]).filter((t) => {
    const known = ` ${DOCUMENT_WORDS[t]} `;
    return words.every((w) => known.includes(` ${w.replace(/s$/, '')}`));
  });
}

export async function clientsMissingDocuments(s: DataScope, documentType: string | undefined, limit: number) {
  const wanted = documentType ? documentTypesMatching(documentType) : null;
  const leads = await getPrismaClient().lead.findMany({
    where: { ...inScope(s), requiredDocuments: { isEmpty: false } },
    include: { documents: { select: { type: true } } },
    orderBy: { name: 'asc' },
  });
  const matches = leads
    .map((l) => ({ l, missing: missingDocuments(l.requiredDocuments, l.documents).filter((t) => !wanted || wanted.includes(t)) }))
    .filter((m) => m.missing.length);
  const records: AiRecord[] = matches.slice(0, limit).map(({ l, missing }) => ({
    type: 'client',
    id: l.id,
    clientId: l.id,
    fields: { clientName: l.name, email: l.email, phone: l.phone, missingDocuments: missing.map((t) => DOCUMENT_LABELS[t]).join(', ') },
  }));
  return { records, total: matches.length };
}

const taskRecord = (
  t: { id: string; leadId: string | null; title: string; dueDate: Date | null; priority: string; completed: boolean },
  clientName: string | null,
): AiRecord => ({
  type: 'task',
  id: t.id,
  clientId: t.leadId!,
  fields: {
    clientName,
    title: t.title,
    dueDate: t.dueDate && day(t.dueDate),
    status: t.completed ? 'done' : 'open',
    priority: t.priority,
    daysOverdue: t.dueDate && !t.completed ? Math.max(0, daysFrom(day(t.dueDate), today())) : 0,
  },
});

/** Past due and not done, oldest first. Only tasks about a person: a record needs its client. */
export async function overdueTasks(s: DataScope, limit: number) {
  const where: Prisma.TaskWhereInput = {
    tenantId: s.tenantId,
    lead: inScope(s),
    completed: false,
    suggested: false,
    dueDate: { lt: startOf(today()) },
  };
  const db = getPrismaClient();
  const [rows, total] = await Promise.all([
    db.task.findMany({ where, include: { lead: { select: { name: true } } }, orderBy: { dueDate: 'asc' }, take: limit }),
    db.task.count({ where }),
  ]);
  return { records: rows.map((t) => taskRecord(t, t.lead?.name ?? null)), total };
}

const NOTE_MAX = 2000;

/** The person with their mortgages, open tasks, documents and notes. 404 for not found and not visible alike. */
export async function fileSummary(s: DataScope, leadId: string) {
  const lead = await getPrismaClient().lead.findFirst({
    where: { ...inScope(s), id: leadId },
    include: {
      mortgages: { orderBy: { maturityDate: 'asc' } },
      tasks: { where: { suggested: false }, orderBy: { dueDate: 'asc' }, take: 20 },
      documents: { orderBy: { createdAt: 'desc' } },
    },
  });
  if (!lead) throw new ApiError('Client not found', StatusCodes.NOT_FOUND);

  const records: AiRecord[] = [
    {
      type: 'client',
      id: lead.id,
      clientId: lead.id,
      fields: {
        clientName: lead.name,
        email: lead.email,
        phone: lead.phone,
        city: lead.city,
        stage: lead.stage,
        fileStatus: lead.fileStatus,
        income: lead.income,
        downPayment: lead.downPayment,
        propertyAddress: lead.propertyAddress,
        propertyValue: lead.propertyValue,
        missingDocuments: missingDocuments(lead.requiredDocuments, lead.documents).map((t) => DOCUMENT_LABELS[t]).join(', ') || null,
      },
    },
    ...lead.mortgages.map((m) => mortgageRecord(m, lead)),
    ...lead.tasks.map((t) => taskRecord(t, lead.name)),
    ...lead.documents.map(
      (d): AiRecord => ({
        type: 'document',
        id: d.id,
        clientId: lead.id,
        fields: { clientName: lead.name, title: DOCUMENT_LABELS[d.type], fileName: d.fileName, uploadedOn: day(d.createdAt), readStatus: d.readStatus },
      }),
    ),
    ...(
      [
        ['original', lead.notes],
        ['broker', lead.brokerNotes],
      ] as const
    )
      .filter(([, text]) => text)
      .map(
        ([kind, text]): AiRecord => ({
          type: 'note',
          id: `${lead.id}-${kind}-notes`,
          clientId: lead.id,
          fields: { clientName: lead.name, title: kind === 'original' ? 'Original notes' : 'Broker notes', text: text!.slice(0, NOTE_MAX) },
        }),
      ),
  ];
  return { records, total: records.length };
}

const plain = (v: string) => v.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');

/**
 * Names containing q, ignoring case and punctuation — or named in full inside
 * q, since the AI sometimes passes more than the name ("Summarize Maria Gonzalez's").
 */
export async function searchClients(s: DataScope, q: string, limit: number) {
  const needle = plain(q);
  // shortcut: filters the user's whole book in memory, upgrade to a normalized-name column if books grow past a few thousand.
  const leads = await getPrismaClient().lead.findMany({
    where: inScope(s),
    select: { id: true, name: true, email: true, phone: true, stage: true, city: true },
    orderBy: { name: 'asc' },
  });
  const matches = needle
    ? leads.filter((l) => {
        const name = plain(l.name);
        return name.includes(needle) || (name.length > 3 && needle.includes(name));
      })
    : [];
  const records: AiRecord[] = matches.slice(0, limit).map((l) => ({
    type: 'client',
    id: l.id,
    clientId: l.id,
    fields: { clientName: l.name, email: l.email, phone: l.phone, stage: l.stage, city: l.city },
  }));
  return { records, total: matches.length };
}

// ─── Dashboard "AI suggestions" (assistant-svc recommendations) ───

const RENEWAL_LOOKAHEAD_DAYS = 120;
const STALE_CONTACT_DAYS = 30;

/**
 * Last contact = the latest past appointment or follow-up with the person,
 * else when they were added.
 * shortcut: emails sent from the CRM don't count yet, add them once sent emails are stored.
 */
async function followUpCandidates(s: DataScope, limit: number) {
  const now = new Date();
  const leads = await getPrismaClient().lead.findMany({
    where: { ...inScope(s), stage: { not: 'LOST' } },
    select: {
      id: true,
      name: true,
      email: true,
      phone: true,
      stage: true,
      createdAt: true,
      appointments: { where: { startTime: { lte: now } }, orderBy: { startTime: 'desc' }, take: 1, select: { startTime: true } },
      followUps: { where: { scheduledAt: { lte: now } }, orderBy: { scheduledAt: 'desc' }, take: 1, select: { scheduledAt: true } },
      tasks: { where: { completed: false, suggested: false, dueDate: { lt: startOf(today()) } }, select: { id: true } },
    },
  });
  const rows = leads
    .map((l) => {
      const last = [l.createdAt, l.appointments[0]?.startTime, l.followUps[0]?.scheduledAt]
        .filter((d): d is Date => !!d)
        .reduce((a, b) => (b > a ? b : a));
      const lastContactDate = new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(last);
      return { l, lastContactDate, daysSinceLastContact: daysFrom(lastContactDate, today()), overdueTasks: l.tasks.length };
    })
    .filter((r) => r.overdueTasks > 0 || r.daysSinceLastContact >= STALE_CONTACT_DAYS)
    .sort((a, b) => b.daysSinceLastContact - a.daysSinceLastContact);
  const records: AiRecord[] = rows.slice(0, limit).map(({ l, ...derived }) => ({
    type: 'client',
    id: l.id,
    clientId: l.id,
    fields: { clientName: l.name, email: l.email, phone: l.phone, stage: l.stage, ...derived },
  }));
  return { records, total: rows.length };
}

/** What assistant-svc ranks for the dashboard: only the records each type is about. */
export function recommendationCandidates(s: DataScope, type: string, limit: number) {
  if (type === 'renewal') {
    const from = today();
    return renewals(s, from, day(new Date(Date.parse(from) + RENEWAL_LOOKAHEAD_DAYS * 86_400_000)), limit);
  }
  if (type === 'follow_up') return followUpCandidates(s, limit);
  if (type === 'missing_documents') return clientsMissingDocuments(s, undefined, limit);
  throw new ApiError('type must be renewal, follow_up or missing_documents', StatusCodes.BAD_REQUEST);
}

/** "Refresh suggestions": one AI pass over the user's book. Poll latestRecommendationRun until completed. */
export function refreshRecommendations(caller: AiCaller & { userId: string }, accessIds: string[]) {
  runGrants.set(`${caller.tenantId}:${caller.userId}`, { accessIds, expires: Date.now() + RUN_GRANT_MS });
  return aiCall('assistant', 'POST', '/v1/recommendation-runs', { caller, json: {} });
}

export const latestRecommendationRun = (caller: AiCaller) =>
  aiCall('assistant', 'GET', '/v1/recommendation-runs/latest', { caller });

/** The card: the user's current suggestions, high priority first (each one is only ever the user's own). */
export const listRecommendations = (caller: AiCaller) =>
  aiCall('assistant', 'GET', '/v1/recommendations', { caller, query: { status: 'new', limit: 25 } });

export const dismissRecommendation = (caller: AiCaller, id: string) =>
  aiCall('assistant', 'POST', `/v1/recommendations/${encodeURIComponent(id)}/dismiss`, { caller });

interface TaskSuggestionView {
  id: string;
  task: { title: string; notes: string; dueDate: string | null; priority: string } | null;
}

/**
 * "Add as task": the suggestion's drafted task (with the broker's edits, if
 * any) becomes a real task on the person, and assistant-svc is told which
 * task it became. Asking again returns the same task.
 */
export async function addRecommendationAsTask(
  caller: AiCaller & { userId: string },
  accessIds: string[],
  id: string,
  edits: { title?: string; dueDate?: string | null; priority?: 'low' | 'medium' | 'high' | 'urgent' },
) {
  const recId = encodeURIComponent(id);
  const rec = await aiCall<{ clientId: string }>('assistant', 'GET', `/v1/recommendations/${recId}`, { caller });
  const lead = await getLeadWithAccess(caller.tenantId, rec.clientId, accessIds);

  const suggestion = await aiCall<TaskSuggestionView>('assistant', 'POST', `/v1/recommendations/${recId}/create-task`, { caller });
  const decided = await aiCall<TaskSuggestionView>('assistant', 'POST', `/v1/task-suggestions/${suggestion.id}/decision`, {
    caller,
    json: Object.keys(edits).length ? { decision: 'edited', edits } : { decision: 'approved' },
  });
  const content = decided.task!;

  const db = getPrismaClient();
  const task =
    (await db.task.findUnique({ where: { aiSuggestionId: suggestion.id } })) ??
    (await db.task.create({
      data: {
        tenantId: caller.tenantId,
        brokerUserId: lead.brokerUserId,
        leadId: lead.id,
        title: content.title,
        dueDate: content.dueDate ? new Date(content.dueDate) : null,
        priority: PRIORITIES[content.priority] ?? 'MEDIUM',
        aiSuggestionId: suggestion.id,
      },
    }));
  await aiCall('assistant', 'POST', `/v1/task-suggestions/${suggestion.id}/handed-off`, { caller, json: { crmTaskId: task.id } });
  await aiCall('assistant', 'POST', `/v1/recommendations/${recId}/accept`, { caller });
  return task;
}
