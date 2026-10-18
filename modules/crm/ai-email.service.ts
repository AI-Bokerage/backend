import type { DocumentType } from '@prisma/client';
import { StatusCodes } from 'http-status-codes';

import type { EmailIntent } from './ai-review.service';
import { aiCall, type AiCaller } from './ai.client';
import { missingDocuments } from './document-processing';
import { getLeadWithAccess } from './lead.service';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';

/**
 * Flow 4.7 "Pick the purpose → Draft appears, marked as AI-assisted → Edit it
 * freely → Save draft / Send". email-svc writes only a first draft from the
 * facts the CRM gives it (and checks every amount, date and lender in the
 * draft against them). Saving and sending stay the CRM's own routes: nothing
 * is ever sent automatically.
 */

/** The CRM's purposes → email-svc's six email types. */
const EMAIL_TYPES: Record<EmailIntent, string> = {
  FOLLOW_UP: 'follow_up',
  RENEWAL: 'renewal',
  BIRTHDAY: 'birthday',
  DOCUMENTS: 'document_request',
  ANNUAL_REVIEW: 'annual_review',
  REFINANCE: 'refinance_opportunity',
};

export const DOCUMENT_LABELS: Record<DocumentType, string> = {
  ID: 'Government photo ID',
  T4: 'T4 slip',
  NOA: 'Notice of Assessment',
  PAY_STUB: 'Recent pay stub',
  EMPLOYMENT_LETTER: 'Employment letter',
  BANK_STATEMENT: 'Bank statement',
  PURCHASE_AGREEMENT: 'Purchase agreement',
  MORTGAGE_STATEMENT: 'Mortgage statement',
  PROPERTY_TAX: 'Property tax bill',
  OTHER: 'Other requested document',
};

interface DraftView {
  id: string;
  clientId: string;
  status: string;
  [key: string]: unknown;
}

const money = (n: number) => `$${n.toLocaleString('en-CA', { maximumFractionDigits: 2 })}`;
const day = (d: Date) => d.toISOString().slice(0, 10);

/**
 * The verified facts for one email type, read from the CRM. email-svc drops
 * anything a type may not use and answers blocked_missing_context, naming what
 * is missing, when a required fact is absent (e.g. a renewal with no mortgage).
 */
async function factsFor(intent: EmailIntent, lead: { id: string; name: string; requiredDocuments: DocumentType[] }, purpose?: string) {
  const db = getPrismaClient();
  const facts: Record<string, string | string[]> = { firstName: lead.name.trim().split(/\s+/)[0] ?? lead.name };
  if (purpose) facts.purpose = purpose;

  if (intent === 'RENEWAL' || intent === 'ANNUAL_REVIEW' || intent === 'REFINANCE') {
    // The mortgage that matters next: the one maturing soonest.
    const m = await db.mortgage.findFirst({ where: { leadId: lead.id }, orderBy: { maturityDate: 'asc' } });
    if (m) {
      facts.lender = m.lender;
      facts.maturityDate = day(m.maturityDate);
      facts.currentRate = `${m.interestRate}%`;
      facts.balance = money(m.balance);
    }
  }
  if (intent === 'DOCUMENTS') {
    const received = await db.leadDocument.findMany({ where: { leadId: lead.id }, select: { type: true } });
    facts.missingDocuments = missingDocuments(lead.requiredDocuments, received).map((t) => DOCUMENT_LABELS[t]);
  }
  if (intent === 'FOLLOW_UP') {
    const open = await db.task.findMany({
      where: { leadId: lead.id, completed: false, suggested: false },
      orderBy: { dueDate: 'asc' },
      take: 5,
    });
    if (open.length) facts.openTasks = open.map((t) => t.title);
  }
  return facts;
}

/** "Is AI available? → Draft appears" — starts the draft; poll getEmailDraft until it is ready. */
export async function startEmailDraft(
  caller: AiCaller & { name?: string | null },
  leadId: string,
  accessIds: string[],
  input: { intent: EmailIntent; purpose?: string; tone?: 'professional' | 'warm' | 'concise' },
) {
  const lead = await getLeadWithAccess(caller.tenantId, leadId, accessIds);
  if (!lead.email) {
    throw new ApiError("Add this person's email before drafting an email to them", StatusCodes.BAD_REQUEST);
  }
  const profile = await getPrismaClient().tenantProfile.findUnique({ where: { tenantId: caller.tenantId } });
  return aiCall<DraftView>('email', 'POST', '/v1/drafts', {
    caller,
    json: {
      clientId: lead.id,
      emailType: EMAIL_TYPES[input.intent],
      recipient: { name: lead.name, email: lead.email },
      sender: {
        name: caller.name || 'Your mortgage broker',
        brokerage: profile?.displayName ?? null,
        phone: profile?.phone ?? null,
      },
      context: await factsFor(input.intent, lead, input.purpose),
      ...(input.tone ? { options: { tone: input.tone } } : {}),
    },
  });
}

/** A draft is visible to whoever may see its person. */
async function accessibleDraft(caller: AiCaller, draftId: string, accessIds: string[], version?: number) {
  const draft = await aiCall<DraftView>('email', 'GET', `/v1/drafts/${draftId}`, {
    caller,
    query: { version },
  });
  await getLeadWithAccess(caller.tenantId, draft.clientId, accessIds).catch(() => {
    throw new ApiError('Email draft not found', StatusCodes.NOT_FOUND);
  });
  return draft;
}

/** Poll until ready / ready_flagged (show it), or blocked / blocked_missing_context / failed (say why). */
export const getEmailDraft = accessibleDraft;

/** "Edit it freely" with the AI's help: "make it shorter" writes the next version. */
export async function refineEmailDraft(
  caller: AiCaller,
  draftId: string,
  accessIds: string[],
  input: { instruction: string; fromVersion?: number },
) {
  await accessibleDraft(caller, draftId, accessIds);
  return aiCall<DraftView>('email', 'POST', `/v1/drafts/${draftId}/refine`, { caller, json: input });
}

/** "Couldn't write it — retry" (email-svc never retries by itself, because each attempt is charged). */
export async function retryEmailDraft(caller: AiCaller, draftId: string, accessIds: string[]) {
  await accessibleDraft(caller, draftId, accessIds);
  return aiCall<DraftView>('email', 'POST', `/v1/drafts/${draftId}/retry`, { caller });
}
