
import type { EmploymentType, Lead, Prisma, TaskPriority } from '@prisma/client';
import { StatusCodes } from 'http-status-codes';
import { createHash, randomUUID } from 'node:crypto';

import { aiCall, type AiCaller } from './ai.client';
import { createLead, getLeadWithAccess, maskLead, type CreateLeadInput } from './lead.service';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';
import { recordActivity } from '../platform/activity-log';

/**
 * Flow 4.1 voice note (voice-svc) and "Analyse notes" → 4.2 AI review (notes-svc),
 * also the 4.3 AI tools tab's review of an existing person. Both services work
 * as jobs: the frontend polls, the broker decides each suggestion, and nothing
 * reaches the CRM until Save, which applies only what was approved.
 */

const notFound = (what: string) => new ApiError(`${what} not found`, StatusCodes.NOT_FOUND);

// ─── 4.1 Voice note ──────────────────────────────────────────────

interface VoiceNoteView {
  id: string;
  userId: string;
  status: 'queued' | 'processing' | 'completed' | 'failed';
  transcript: string | null;
  aiLabel: string | null;
  durationSec: number | null;
  error: { code: string; message: string; retryable: boolean } | null;
  createdAt: string;
}

const voiceView = ({ id, status, transcript, aiLabel, durationSec, error, createdAt }: VoiceNoteView) => ({
  id,
  status,
  transcript,
  aiLabel,
  durationSec,
  error,
  createdAt,
});

/** "Voice note added?" — voice-svc keeps the recording (7 days), so a failed transcription can be retried. */
export async function uploadVoiceNote(caller: AiCaller, audio: Buffer, mimeType: string) {
  if (audio.length === 0) throw new ApiError('No audio received', StatusCodes.BAD_REQUEST);
  const form = new FormData();
  form.append('file', new Blob([audio], { type: mimeType }), 'voice-note');
  return voiceView(await aiCall<VoiceNoteView>('voice', 'POST', '/v1/voice-notes', { caller, form }));
}

// voice-svc scopes by brokerage only; a recording is its uploader's alone.
async function ownVoiceNote(caller: AiCaller, id: string) {
  const note = await aiCall<VoiceNoteView>('voice', 'GET', `/v1/voice-notes/${id}`, { caller });
  if (note.userId !== caller.userId) throw notFound('Voice note');
  return note;
}

/** "Did it transcribe?" — poll until completed (transcript) or failed (retry, or type the notes). */
export async function getVoiceNote(caller: AiCaller, id: string) {
  return voiceView(await ownVoiceNote(caller, id));
}

export async function retryVoiceNote(caller: AiCaller, id: string) {
  await ownVoiceNote(caller, id);
  return voiceView(await aiCall<VoiceNoteView>('voice', 'POST', `/v1/voice-notes/${id}/retry`, { caller }));
}

// ─── 4.1 "Analyse notes" → 4.2 AI review ─────────────────────────

export interface Run {
  id: string;
  userId: string;
  clientId: string | null;
  status: string;
  reviewStatus: string;
  [key: string]: unknown;
}

interface TaskContent {
  title: string;
  notes: string;
  dueDate: string | null;
  priority: string;
}
interface ApprovedValue {
  key: string;
  label: string;
  instance: number;
  target: string | null;
  value: string | number | null;
  display: string;
  replaceExisting: boolean;
}
interface ApprovedRun {
  values: ApprovedValue[] | null;
  tasks: { suggestionId: string; task: TaskContent }[];
  emailDraft: { email: { subject: string; body: string } } | null;
  aiLabel: string;
}

/** What the form already holds — the review never silently overwrites it. */
export interface KnownFields {
  name?: string;
  email?: string | null;
  phone?: string | null;
  income?: number | null;
  downPayment?: number | null;
  dateOfBirth?: string | Date | null;
}

/** notes-svc's context: values to mask before the AI sees them, and what the CRM already holds (by field key). */
function runContext(fields: KnownFields) {
  const knownEntities = [
    { type: 'PERSON', value: fields.name },
    { type: 'EMAIL', value: fields.email },
    { type: 'PHONE', value: fields.phone },
  ].filter((e): e is { type: string; value: string } => !!e.value);
  const dob = fields.dateOfBirth ? new Date(fields.dateOfBirth).toISOString().slice(0, 10) : null;
  const existing = {
    email: fields.email,
    phone: fields.phone,
    annualIncome: fields.income?.toString(),
    downPayment: fields.downPayment?.toString(),
    dateOfBirth: dob,
  };
  return {
    knownEntities,
    existingValues: Object.fromEntries(Object.entries(existing).filter(([, v]) => v)),
  };
}

/**
 * Saves the notes in notes-svc as a sealed original ("Original notes locked
 * forever") and starts one AI reading of them. The idempotency key makes the
 * same notes for the same person return the same run instead of a new charge.
 */
async function startRun(caller: AiCaller, notes: string, fields: KnownFields, clientId: string | null) {
  const digest = createHash('sha256').update(notes).digest('hex');
  const note = await aiCall<{ id: string }>('notes', 'POST', '/v1/notes', {
    caller,
    json: { text: notes, clientId, source: 'typed' },
    idempotencyKey: `${caller.userId}:${clientId ?? 'draft'}:${digest}`,
  });
  return aiCall<Run>('notes', 'POST', '/v1/runs', {
    caller,
    json: { noteIds: [note.id], clientId, context: runContext(fields) },
  });
}

/** "Analyse notes" on the new-client form — nothing exists in the CRM yet, so Discard creates nothing. */
export function analyseDraftNotes(caller: AiCaller, input: { notes: string; fields?: KnownFields }) {
  return startRun(caller, input.notes, input.fields ?? {}, null);
}

/** AI tools tab (4.3) — the same reading over an existing person's notes. */
export async function analyseLeadNotes(caller: AiCaller, leadId: string, accessIds: string[]) {
  const lead = await getLeadWithAccess(caller.tenantId, leadId, accessIds);
  const notes = [lead.notes, lead.brokerNotes].filter(Boolean).join('\n\n');
  if (!notes) throw new ApiError('This person has no notes to analyse yet', StatusCodes.BAD_REQUEST);
  return startRun(caller, notes, lead, lead.id);
}

/** A run is visible to whoever may see its person; a run with no person yet, to its author only. */
async function accessibleRun(caller: AiCaller, runId: string, accessIds: string[]) {
  const run = await aiCall<Run>('notes', 'GET', `/v1/runs/${runId}`, { caller });
  if (run.clientId) {
    await getLeadWithAccess(caller.tenantId, run.clientId, accessIds).catch(() => {
      throw notFound('Review');
    });
  } else if (run.userId !== caller.userId) {
    throw notFound('Review');
  }
  return run;
}

/** Poll until `processed`/`processed_with_errors` (review open) or `failed`. */
export const getRun = accessibleRun;

/** "Accept, edit or reject each item" — passed through as notes-svc decisions. */
export async function decideRun(caller: AiCaller, runId: string, accessIds: string[], decisions: unknown[]) {
  await accessibleRun(caller, runId, accessIds);
  return aiCall<Run>('notes', 'POST', `/v1/runs/${runId}/decisions`, { caller, json: { decisions } });
}

export async function retryRun(caller: AiCaller, runId: string, accessIds: string[]) {
  await accessibleRun(caller, runId, accessIds);
  return aiCall<Run>('notes', 'POST', `/v1/runs/${runId}/retry`, { caller });
}

// ─── 4.2 Save: apply what the broker approved ────────────────────

// Approved value target → Lead column. Anything else is reported back as notApplied.
const LEAD_TARGETS = {
  'contact.email': 'email',
  'contact.phone': 'phone',
  'income.annualAmount': 'income',
  'request.downPayment': 'downPayment',
  'properties[].value': 'propertyValue',
  'applicant.dateOfBirth': 'dateOfBirth',
} as const;
type LeadColumn = (typeof LEAD_TARGETS)[keyof typeof LEAD_TARGETS];
type LeadValues = Partial<Pick<Lead, LeadColumn>>;

/** An approved value in its column's type (notes-svc already validated it as money/date/email/phone). */
function columnValue(column: LeadColumn, value: string | number): LeadValues[LeadColumn] {
  if (column === 'dateOfBirth') return new Date(String(value));
  if (column === 'email' || column === 'phone') return String(value);
  return Number(value);
}

export const PRIORITIES: Record<string, TaskPriority> = { low: 'LOW', medium: 'MEDIUM', high: 'HIGH', urgent: 'HIGH' };

const isBlank = (v: unknown) => v === null || v === undefined || v === '';

/**
 * Turns notes-svc's approved run into CRM writes. "Would it overwrite something
 * already filled in? → Left alone, marked as already filled" — unless the
 * broker explicitly chose to replace it (replaceExisting).
 */
export function planApply(
  approved: ApprovedRun,
  current: Partial<Record<LeadColumn, unknown>> & { name?: string },
) {
  const lead: Record<string, LeadValues[LeadColumn]> = {};
  const applied: string[] = [];
  const skipped: string[] = [];
  const notApplied: { label: string; display: string }[] = [];
  const person: Record<string, string> = {};

  for (const v of approved.values ?? []) {
    if (v.value === null) continue;
    const column = LEAD_TARGETS[v.target as keyof typeof LEAD_TARGETS];
    if (column && v.instance === 0) {
      if (!isBlank(current[column]) && !v.replaceExisting) {
        skipped.push(v.label);
        continue;
      }
      lead[column] = columnValue(column, v.value);
      applied.push(v.label);
    } else if (v.target?.startsWith('applicant.') || v.target?.startsWith('employment.')) {
      person[v.target] = String(v.value);
      applied.push(v.label);
    } else {
      notApplied.push({ label: v.label, display: v.display });
    }
  }

  // The main applicant is the person themself. The AI masks names, so it rarely
  // suggests one: fall back to the person's own name for approved job details.
  const [ownFirst = '', ...ownRest] = (current.name ?? '').trim().split(/\s+/);
  const firstName = person['applicant.firstName'] ?? ownFirst;
  const lastName = person['applicant.lastName'] ?? ownRest.join(' ');
  const employer = person['employment.employerName'];
  const applicant: Prisma.ApplicantCreateWithoutLeadInput | null =
    Object.keys(person).length && (firstName || lastName)
      ? {
          firstName,
          lastName,
          employments:
            employer || person['employment.jobTitle']
              ? {
                  create: {
                    type: (person['employment.type'] === 'self_employed'
                      ? 'SELF_EMPLOYED'
                      : 'EMPLOYED') as EmploymentType,
                    employer,
                    position: person['employment.jobTitle'],
                  },
                }
              : undefined,
        }
      : null;

  const tasks = approved.tasks.map(({ suggestionId, task }) => ({
    aiSuggestionId: suggestionId,
    title: task.title.slice(0, 255),
    dueDate: task.dueDate ? new Date(task.dueDate) : null,
    priority: PRIORITIES[task.priority] ?? 'MEDIUM',
  }));
  const email = approved.emailDraft?.email ?? null;

  return { lead: lead as LeadValues, applicant, tasks, email, applied, skipped, notApplied };
}

/** finalize closes the profile review; undecided values count as rejected ("nothing is saved until the broker says so"). */
const finalize = (caller: AiCaller, runId: string) =>
  aiCall<ApprovedRun>('notes', 'POST', `/v1/runs/${runId}/finalize`, {
    caller,
    json: { discardPending: true },
  });

/**
 * 4.2 "Save → Lead created at stage New": the run is linked to the new person
 * first (so "delete with the client" reaches the AI's copy), then finalized,
 * then the lead, the accepted tasks and the draft email are saved together.
 */
export async function createLeadFromReview(
  caller: AiCaller & { userId: string },
  accessIds: string[],
  input: CreateLeadInput & { aiRunId: string },
) {
  const { aiRunId, ...form } = input;
  const run = await accessibleRun(caller, aiRunId, accessIds);
  if (run.clientId) throw new ApiError('This review already belongs to a person', StatusCodes.CONFLICT);

  const leadId = randomUUID();
  await aiCall('notes', 'PUT', `/v1/runs/${aiRunId}/client`, { caller, json: { clientId: leadId } });
  const plan = planApply(await finalize(caller, aiRunId), form);

  const lead = await createLead(caller.tenantId, caller.userId, { ...form, ...plan.lead } as CreateLeadInput, {
    id: leadId,
    applicants: plan.applicant ? { create: plan.applicant } : undefined,
    tasks: plan.tasks.length
      ? { create: plan.tasks.map((t) => ({ ...t, tenantId: caller.tenantId, brokerUserId: caller.userId })) }
      : undefined,
    emailDrafts: plan.email
      ? {
          create: {
            tenantId: caller.tenantId,
            brokerUserId: caller.userId,
            intent: 'DOCUMENTS',
            ...plan.email,
            aiAssisted: true,
          },
        }
      : undefined,
  });
  return { lead: maskLead(lead), applied: plan.applied, skipped: plan.skipped, notApplied: plan.notApplied };
}

/** AI tools tab (4.3): apply an approved review to the existing person — blank fields only, same rules as Save. */
export async function applyReviewToLead(
  caller: AiCaller & { userId: string },
  accessIds: string[],
  leadId: string,
  aiRunId: string,
) {
  const lead = await getLeadWithAccess(caller.tenantId, leadId, accessIds);
  const run = await accessibleRun(caller, aiRunId, accessIds);
  if (run.clientId !== lead.id) throw notFound('Review');
  const plan = planApply(await finalize(caller, aiRunId), lead as Lead);
  // Already on the task list as a Suggested task (4.9 "Suggest tasks"): don't add it twice.
  const existing = await getPrismaClient().task.findMany({
    where: { aiSuggestionId: { in: plan.tasks.map((t) => t.aiSuggestionId) } },
    select: { aiSuggestionId: true },
  });
  plan.tasks = plan.tasks.filter((t) => !existing.some((e) => e.aiSuggestionId === t.aiSuggestionId));

  const updated = await getPrismaClient().lead.update({
    where: { id: lead.id },
    data: {
      ...plan.lead,
      applicants: plan.applicant ? { create: plan.applicant } : undefined,
      tasks: plan.tasks.length
        ? { create: plan.tasks.map((t) => ({ ...t, tenantId: caller.tenantId, brokerUserId: lead.brokerUserId })) }
        : undefined,
      emailDrafts: plan.email
        ? {
            create: {
              tenantId: caller.tenantId,
              brokerUserId: lead.brokerUserId,
              intent: 'DOCUMENTS',
              ...plan.email,
              aiAssisted: true,
            },
          }
        : undefined,
    },
  });
  if (plan.applied.length) {
    await recordActivity('crm.lead.ai_fields_applied', {
      actorId: caller.userId,
      targetType: 'lead',
      targetId: lead.id,
      metadata: { applied: plan.applied },
    });
  }
  return { lead: maskLead(updated), applied: plan.applied, skipped: plan.skipped, notApplied: plan.notApplied };
}

// ─── 4.9 Suggested tasks ─────────────────────────────────────────

interface TaskSuggestion {
  id: string;
  title: string | null;
  dueDate: string | null;
  priority: string | null;
  decision: string;
}

/**
 * "Suggested by AI → Shown as Suggested": notes-svc reads the person's notes
 * and each follow-up task it proposes is added to the task list as Suggested,
 * for the person's broker to approve (becomes a real task) or dismiss.
 * Reading takes a few seconds: call again with the same notes until `ready`
 * (the same notes are the same run, so no second charge).
 */
export async function suggestTasks(caller: AiCaller, leadId: string, accessIds: string[]) {
  const lead = await getLeadWithAccess(caller.tenantId, leadId, accessIds);
  const notes = [lead.notes, lead.brokerNotes].filter(Boolean).join('\n\n');
  if (!notes) throw new ApiError('This person has no notes to suggest tasks from yet', StatusCodes.BAD_REQUEST);
  const run = await startRun(caller, notes, lead, lead.id);
  if (run.status !== 'processed' && run.status !== 'processed_with_errors') {
    // queued / processing: call again shortly. failed: POST /crm/ai/runs/{id}/retry.
    return { ready: false as const, runId: run.id, status: run.status, error: run.error ?? null, tasks: [] };
  }

  // Starting a run answers without the suggestions; read them from the run itself.
  const { tasks: suggested } = await aiCall<Run>('notes', 'GET', `/v1/runs/${run.id}`, { caller });
  const proposed = ((suggested as TaskSuggestion[] | null) ?? []).filter((t) => t.title && t.decision !== 'rejected');
  const db = getPrismaClient();
  await db.task.createMany({
    data: proposed.map((t) => ({
      tenantId: caller.tenantId,
      brokerUserId: lead.brokerUserId,
      leadId: lead.id,
      title: t.title!.slice(0, 255),
      dueDate: t.dueDate ? new Date(t.dueDate) : null,
      priority: PRIORITIES[t.priority ?? ''] ?? 'MEDIUM',
      suggested: true,
      aiSuggestionId: t.id,
      aiRunId: run.id,
    })),
    skipDuplicates: true,
  });
  // Only the ones still awaiting a decision: approved or dismissed ones are already handled.
  const tasks = await db.task.findMany({
    where: { aiSuggestionId: { in: proposed.map((t) => t.id) }, suggested: true },
    orderBy: { dueDate: 'asc' },
  });
  return { ready: true as const, runId: run.id, status: run.status, error: null, tasks };
}
