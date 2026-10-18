import type { DocumentReadStatus, LeadDocument, Prisma } from '@prisma/client';
import { StatusCodes } from 'http-status-codes';

import type { AiCallback } from './ai-webhooks';
import { aiCall, type AiCaller } from './ai.client';
import { getLeadWithAccess, maskLead } from './lead.service';
import { notify } from './notification.service';
import { getPrismaClient } from '../../config/database';
import { env } from '../../config/env';
import { logger } from '../../config/logger';
import { ApiError } from '../../utils/api-error';
import { recordActivity } from '../platform/activity-log';

/**
 * Flow 4.5 "Could the system read it?" — docread-svc reads each stored file
 * (OCR + AI extraction), the broker reviews the values it found, and only
 * approved values reach the CRM, filling blank fields unless the broker chose
 * to replace one.
 */

interface DocreadDoc {
  id: string;
  clientId: string | null;
  crmDocumentId: string | null;
  status: 'queued' | 'processing' | 'processed' | 'processed_with_errors' | 'rejected' | 'failed';
  [key: string]: unknown;
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
interface ApprovedValues {
  mortgage: { mortgageId: string | null; createNew: boolean } | null;
  values: ApprovedValue[];
}

/** docread job status → the CRM's own read status (null = still reading). */
const READ_STATUS: Record<DocreadDoc['status'], DocumentReadStatus> = {
  queued: 'PENDING',
  processing: 'PENDING',
  processed: 'READ',
  processed_with_errors: 'PARTIAL',
  rejected: 'FAILED',
  failed: 'FAILED',
};

const OUTCOMES: Partial<Record<DocumentReadStatus, { type: string; title: (f: string) => string }>> = {
  READ: { type: 'document.ready_for_review', title: (f) => `Review the details read from ${f}` },
  PARTIAL: { type: 'document.partially_read', title: (f) => `${f} was only partly read — check it` },
  FAILED: { type: 'document.unreadable', title: (f) => `Couldn't read ${f} — retry, or type the values in` },
};

/**
 * Moves the CRM's copy to docread's status and tells the broker once — the
 * callback and a review read may both get here, so a repeat changes nothing.
 */
async function syncStatus(doc: LeadDocument, status: DocumentReadStatus, docreadStatus?: string) {
  if (doc.readStatus === status) return doc;
  const updated = await getPrismaClient().leadDocument.update({
    where: { id: doc.id },
    data: { readStatus: status },
  });
  const outcome = OUTCOMES[status];
  const lead = await getPrismaClient().lead.findUnique({ where: { id: doc.leadId } });
  if (outcome && lead) {
    await notify({
      tenantId: doc.tenantId,
      userId: lead.brokerUserId,
      leadId: lead.id,
      type: outcome.type,
      // docread's "rejected" (blank, not a document) is final — retrying can't help.
      title:
        docreadStatus === 'rejected'
          ? `Couldn't read ${doc.fileName} — upload a clearer copy, or type the values in`
          : outcome.title(doc.fileName),
      body: `${lead.name} · ${doc.type}`,
    });
  }
  return updated;
}

/**
 * Hands a stored file to docread-svc with what it needs: the person (to mask
 * their details), their mortgages (to match a statement to one), and where to
 * call back. Charged to the person's broker. If docread can't take it the file
 * stays stored and is marked unreadable, so the broker can retry or type values.
 */
export async function sendForReading(
  doc: LeadDocument,
  file: { data: Buffer; mimeType: string; fileName: string },
  lead: { id: string; name: string; email: string | null; brokerUserId: string; propertyAddress: string | null },
  uploadedBy: 'BROKER' | 'BORROWER',
) {
  const mortgages = await getPrismaClient().mortgage.findMany({ where: { leadId: lead.id } });
  const form = new FormData();
  form.append('file', new Blob([file.data], { type: file.mimeType }), file.fileName);
  form.append('clientId', lead.id);
  form.append('crmDocumentId', doc.id);
  form.append('source', uploadedBy === 'BORROWER' ? 'borrower_link' : 'user');
  if (env.ai.callbackBaseUrl) {
    form.append('callbackUrl', `${env.ai.callbackBaseUrl}${env.apiPrefix}/crm/webhooks/ai/docread`);
  }
  form.append(
    'context',
    JSON.stringify({
      knownEntities: [
        { type: 'PERSON', value: lead.name },
        ...(lead.email ? [{ type: 'EMAIL', value: lead.email }] : []),
      ],
      mortgages: mortgages.map((m) => ({
        id: m.id,
        lender: m.lender,
        propertyAddress: lead.propertyAddress,
        borrowerNames: [lead.name],
      })),
    }),
  );
  try {
    const ai = await aiCall<DocreadDoc>('docread', 'POST', '/v1/documents', {
      caller: { tenantId: doc.tenantId, userId: lead.brokerUserId },
      form,
      idempotencyKey: `doc:${doc.id}`,
    });
    const sent = await getPrismaClient().leadDocument.update({
      where: { id: doc.id },
      data: { aiDocumentId: ai.id },
    });
    return syncStatus(sent, READ_STATUS[ai.status], ai.status);
  } catch (error) {
    logger.warn({ error, documentId: doc.id }, 'docread.upload_failed');
    return syncStatus(doc, 'FAILED');
  }
}

/** docread-svc callback: the reading ended (or the review was finalized — nothing to do then). */
export async function onDocreadEvent({ data }: AiCallback) {
  if (!data.crmDocumentId) return;
  const doc = await getPrismaClient().leadDocument.findUnique({
    where: { id: data.crmDocumentId as string },
  });
  // Only ever about our own document, in the brokerage that owns it.
  if (!doc || doc.aiDocumentId !== data.id || doc.tenantId !== data.brokerageId) return;
  const status = READ_STATUS[data.status as DocreadDoc['status']];
  if (status) await syncStatus(doc, status, data.status);
}

// ─── The broker's review ─────────────────────────────────────────

async function accessibleDocument(caller: AiCaller, documentId: string, accessIds: string[]) {
  const doc = await getPrismaClient().leadDocument.findUnique({ where: { id: documentId } });
  if (!doc || doc.tenantId !== caller.tenantId) {
    throw new ApiError('Document not found', StatusCodes.NOT_FOUND);
  }
  const lead = await getLeadWithAccess(caller.tenantId, doc.leadId, accessIds);
  return { doc, lead };
}

function aiDocumentId(doc: LeadDocument) {
  if (!doc.aiDocumentId) {
    throw new ApiError('This file was not sent for reading yet — retry reading it', StatusCodes.CONFLICT);
  }
  return doc.aiDocumentId;
}

/** "Review the extracted details" — every value with its confidence, flags and the line it came from. */
export async function getDocumentReview(caller: AiCaller, documentId: string, accessIds: string[]) {
  const { doc } = await accessibleDocument(caller, documentId, accessIds);
  const ai = await aiCall<DocreadDoc>('docread', 'GET', `/v1/documents/${aiDocumentId(doc)}`, { caller });
  const synced = await syncStatus(doc, READ_STATUS[ai.status], ai.status);
  const { storagePath, ...document } = synced;
  void storagePath; // the file's location never leaves the server
  return { document, reading: ai };
}

/** "Accept, edit or reject each value". */
export async function decideDocument(
  caller: AiCaller,
  documentId: string,
  accessIds: string[],
  decisions: unknown[],
) {
  const { doc } = await accessibleDocument(caller, documentId, accessIds);
  return aiCall<DocreadDoc>('docread', 'POST', `/v1/documents/${aiDocumentId(doc)}/decisions`, {
    caller,
    json: { decisions },
  });
}

/** Which of this person's mortgages the document is about, or a new one. */
export async function confirmDocumentMortgage(
  caller: AiCaller,
  documentId: string,
  accessIds: string[],
  choice: { mortgageId?: string; createNew?: boolean },
) {
  const { doc, lead } = await accessibleDocument(caller, documentId, accessIds);
  if (choice.mortgageId) {
    const owned = await getPrismaClient().mortgage.findFirst({
      where: { id: choice.mortgageId, leadId: lead.id },
    });
    if (!owned) throw new ApiError('That mortgage is not this person\'s', StatusCodes.NOT_FOUND);
  }
  return aiCall<DocreadDoc>('docread', 'PUT', `/v1/documents/${aiDocumentId(doc)}/mortgage`, {
    caller,
    json: choice.mortgageId ? { mortgageId: choice.mortgageId } : { createNew: true },
  });
}

/** "Retry" — read again; a file docread never received is sent now. */
export async function retryDocumentRead(caller: AiCaller, documentId: string, accessIds: string[]) {
  const { doc, lead } = await accessibleDocument(caller, documentId, accessIds);
  if (!doc.aiDocumentId) {
    const { readStoredFile } = await import('./document-storage');
    const data = await readStoredFile(doc.storagePath);
    return sendForReading(doc, { data, mimeType: doc.mimeType, fileName: doc.fileName }, lead, doc.uploadedBy as 'BROKER' | 'BORROWER');
  }
  const ai = await aiCall<DocreadDoc>('docread', 'POST', `/v1/documents/${doc.aiDocumentId}/retry`, { caller });
  return syncStatus(doc, READ_STATUS[ai.status], ai.status);
}

// ─── Apply what was approved ─────────────────────────────────────

const num = (v: string | number) => Number(v);
const isBlank = (v: unknown) => v === null || v === undefined || v === '';

/**
 * docread targets → CRM writes. Lead columns are filled only when blank
 * (unless the broker chose to replace); a mortgage needs the broker's
 * confirmation first (docread enforces it); anything with no CRM home comes
 * back as notApplied so the broker sees it was not lost silently.
 */
export function planDocumentApply(
  approved: ApprovedValues,
  lead: { name: string; income: number | null; propertyAddress: string | null; propertyValue: number | null },
) {
  const leadData: Prisma.LeadUpdateInput = {};
  const applied: string[] = [];
  const skipped: string[] = [];
  const notApplied: { label: string; display: string }[] = [];
  const mortgage: Record<string, string | number> = {};
  const debts = new Map<number, Record<string, string | number>>();
  const job: Record<string, string> = {};
  let applicantName: string | undefined;

  const fillLead = (
    v: ApprovedValue,
    column: 'income' | 'propertyAddress' | 'propertyValue',
    value: string | number,
  ) => {
    if (!isBlank(lead[column]) && !v.replaceExisting) {
      skipped.push(v.label);
      return;
    }
    leadData[column] = value as never;
    applied.push(v.label);
  };

  for (const v of approved.values) {
    if (v.value === null) continue;
    switch (v.target) {
      case 'income.annualAmount':
        fillLead(v, 'income', num(v.value));
        break;
      case 'property.address':
        fillLead(v, 'propertyAddress', String(v.value));
        break;
      case 'property.value':
        fillLead(v, 'propertyValue', num(v.value));
        break;
      case 'applicant.fullName':
        applicantName = String(v.value);
        applied.push(v.label);
        break;
      default:
        if (v.target?.startsWith('employment.')) {
          job[v.target] = String(v.value);
          applied.push(v.label);
        } else if (v.target?.startsWith('mortgage.')) {
          mortgage[v.target.slice('mortgage.'.length)] = v.value;
          applied.push(v.label);
        } else if (v.target?.startsWith('debts[].')) {
          const d = debts.get(v.instance) ?? {};
          d[v.target.slice('debts[].'.length)] = v.value;
          debts.set(v.instance, d);
          applied.push(v.label);
        } else {
          notApplied.push({ label: v.label, display: v.display });
        }
    }
  }

  // A debt row needs both amounts (the CRM's Debt has no optional balance/payment).
  const debtRows = [...debts.values()]
    .filter((d) => d.balance !== undefined && d.monthlyPayment !== undefined)
    .map((d) => ({
      description: [d.creditor, d.type].filter(Boolean).join(' · ') || null,
      balance: num(d.balance!),
      monthlyPayment: num(d.monthlyPayment!),
    }));

  const [first = '', ...rest] = (applicantName ?? lead.name).trim().split(/\s+/);
  const employment = Object.keys(job).length
    ? {
        type: job['employment.type'] === 'self_employed' ? ('SELF_EMPLOYED' as const) : ('EMPLOYED' as const),
        employer: job['employment.employerName'],
        position: job['employment.jobTitle'],
        startDate: job['employment.startDate'] ? new Date(job['employment.startDate']) : undefined,
      }
    : null;

  return {
    leadData,
    applicant: { firstName: first, lastName: rest.join(' ') },
    newApplicant: !!applicantName,
    employment,
    mortgage,
    debtRows,
    applied,
    skipped,
    notApplied,
  };
}

// The CRM's Mortgage needs all of these; a partial statement can't create one.
const MORTGAGE_REQUIRED = ['lender', 'interestRate', 'balance', 'paymentAmount', 'maturityDate'];

/** "Accept → Filled in → Missing-documents list and dashboard update". */
export async function applyDocumentReview(
  caller: AiCaller & { userId: string },
  documentId: string,
  accessIds: string[],
) {
  const { doc, lead } = await accessibleDocument(caller, documentId, accessIds);
  // Check a new mortgage can really be created *before* finalizing: once finalized the review can't change.
  const reading = await aiCall<{
    mortgageSelection: { createNew: boolean } | null;
    fields: { target: string | null; decision: string }[] | null;
  }>('docread', 'GET', `/v1/documents/${aiDocumentId(doc)}`, { caller });
  if (reading.mortgageSelection?.createNew) {
    const have = new Set(
      (reading.fields ?? [])
        .filter((f) => f.decision === 'approved' || f.decision === 'edited')
        .map((f) => f.target?.replace('mortgage.', '')),
    );
    const missing = MORTGAGE_REQUIRED.filter((k) => !have.has(k));
    if (missing.length) {
      throw new ApiError(
        `A new mortgage needs ${missing.join(', ')} — approve or edit those values, or pick an existing mortgage`,
        StatusCodes.UNPROCESSABLE_ENTITY,
      );
    }
  }
  const approved = await aiCall<ApprovedValues>('docread', 'POST', `/v1/documents/${aiDocumentId(doc)}/finalize`, {
    caller,
    json: { discardPending: true },
  });
  const plan = planDocumentApply(approved, lead);
  const db = getPrismaClient();

  await db.$transaction(async (tx) => {
    await tx.lead.update({ where: { id: lead.id }, data: plan.leadData });
    if (plan.debtRows.length) {
      await tx.debt.createMany({ data: plan.debtRows.map((d) => ({ ...d, leadId: lead.id })) });
    }
    if (plan.employment || plan.newApplicant) {
      const existing = plan.newApplicant
        ? null
        : await tx.applicant.findFirst({ where: { leadId: lead.id }, orderBy: { createdAt: 'asc' } });
      const applicant = existing ?? (await tx.applicant.create({ data: { leadId: lead.id, ...plan.applicant } }));
      // "Add a job — always adds, never replaces."
      if (plan.employment) await tx.employment.create({ data: { applicantId: applicant.id, ...plan.employment } });
    }
    const m = plan.mortgage;
    const target = approved.mortgage;
    if (Object.keys(m).length && target?.mortgageId) {
      await tx.mortgage.update({
        where: { id: target.mortgageId },
        data: {
          lender: m.lender !== undefined ? String(m.lender) : undefined,
          interestRate: m.interestRate !== undefined ? num(m.interestRate) : undefined,
          balance: m.balance !== undefined ? num(m.balance) : undefined,
          monthlyPayment: m.paymentAmount !== undefined ? num(m.paymentAmount) : undefined,
          maturityDate: m.maturityDate !== undefined ? new Date(String(m.maturityDate)) : undefined,
        },
      });
    } else if (target?.createNew && MORTGAGE_REQUIRED.every((k) => m[k] !== undefined)) {
      await tx.mortgage.create({
        data: {
          leadId: lead.id,
          lender: String(m.lender),
          interestRate: num(m.interestRate!),
          balance: num(m.balance!),
          monthlyPayment: num(m.paymentAmount!),
          maturityDate: new Date(String(m.maturityDate)),
        },
      });
    }
  });

  if (plan.applied.length) {
    await recordActivity('crm.document.values_applied', {
      actorId: caller.userId,
      targetType: 'lead',
      targetId: lead.id,
      metadata: { documentId: doc.id, applied: plan.applied },
    });
  }
  const updated = await db.lead.findUniqueOrThrow({ where: { id: lead.id } });
  return {
    lead: maskLead(updated),
    applied: plan.applied,
    skipped: plan.skipped,
    notApplied: plan.notApplied,
  };
}
