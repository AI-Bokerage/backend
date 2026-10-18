import type { DocumentType } from '@prisma/client';
import { createHash, randomBytes } from 'node:crypto';

import { sendForReading } from './ai-documents.service';
import { missingDocuments, scanForViruses } from './document-processing';
import { readStoredFile, storeFile } from './document-storage';
import * as gmailService from './google/gmail.service';
import { getStatus as getGoogleStatus } from './google/google-connection.service';
import { getLeadWithAccess } from './lead.service';
import { notify } from './notification.service';
import { sendSystemEmail } from './system-mailer';
import { getPrismaClient } from '../../config/database';
import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';
import { recordActivity } from '../platform/activity-log';

const LINK_TTL_MS = 7 * 24 * 60 * 60 * 1000;

export interface IncomingFile {
  type: DocumentType;
  fileName: string;
  mimeType: string;
  data: Buffer;
}

function hashToken(token: string) {
  return createHash('sha256').update(token).digest('hex');
}

/** Flow 4.5 "Tick which documents this person needs to send". */
export async function setRequiredDocuments(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  types: DocumentType[],
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  await getPrismaClient().lead.update({
    where: { id: lead.id },
    data: { requiredDocuments: [...new Set(types)] },
  });
  return listDocuments(tenantId, leadId, allowedBrokerIds);
}

/** The Documents tab: what's been received, what was ticked, what's still missing. */
export async function listDocuments(tenantId: string, leadId: string, allowedBrokerIds: string[]) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const items = await getPrismaClient().leadDocument.findMany({
    where: { leadId: lead.id },
    orderBy: { createdAt: 'desc' },
    omit: { storagePath: true },
  });
  return {
    required: lead.requiredDocuments,
    items,
    missing: missingDocuments(lead.requiredDocuments, items),
  };
}

/**
 * "What happens to every file that arrives": virus check → (rejected, never
 * stored, broker notified) or stored → "Could the system read it?" (the AI
 * document service reads it in the background; ai-documents.service.ts tells
 * the broker when it's ready to review, or that it couldn't be read).
 */
async function ingestFile(
  lead: {
    id: string;
    tenantId: string;
    brokerUserId: string;
    name: string;
    email: string | null;
    propertyAddress: string | null;
  },
  file: IncomingFile,
  uploadedBy: 'BROKER' | 'BORROWER',
) {
  if (file.data.length === 0) {
    throw new ApiError('No file received', 400);
  }
  if (file.data.length > env.crm.documentMaxBytes) {
    throw new ApiError('That file is too large', 413);
  }
  const base = { tenantId: lead.tenantId, userId: lead.brokerUserId, leadId: lead.id };

  const scan = await scanForViruses(file.data);
  if (!scan.clean) {
    await notify({
      ...base,
      type: 'document.rejected',
      title: `A file for ${lead.name} failed the virus check and was not stored`,
      body: scan.virus ? `${file.fileName} — ${scan.virus}` : file.fileName,
    });
    throw new ApiError('This file failed the virus check and was not stored', 422);
  }

  const storagePath = await storeFile(lead.tenantId, file.data);
  const doc = await getPrismaClient().leadDocument.create({
    data: {
      tenantId: lead.tenantId,
      leadId: lead.id,
      type: file.type,
      fileName: file.fileName,
      mimeType: file.mimeType,
      size: file.data.length,
      storagePath,
      uploadedBy,
      readStatus: 'PENDING',
    },
  });
  const { storagePath: stored, ...sent } = await sendForReading(doc, file, lead, uploadedBy);
  void stored; // the file's location never leaves the server
  return sent;
}

/** "Or upload files yourself". */
export async function uploadDocument(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  file: IncomingFile,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  return ingestFile(lead, file, 'BROKER');
}

/**
 * Flow 4.5 "Request documents → Is the broker's email connected? → No →
 * Connect it, or send from the system address instead → Link emailed to the
 * borrower → works for that one person only, expires after 7 days".
 * Calling it again is "Generate a new link".
 */
export async function requestDocuments(
  tenantId: string,
  brokerUserId: string,
  leadId: string,
  allowedBrokerIds: string[],
  options: { useSystemAddress?: boolean },
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  if (!lead.email) {
    throw new ApiError("Add this person's email before requesting documents", 400);
  }
  const received = await getPrismaClient().leadDocument.findMany({
    where: { leadId: lead.id },
    select: { type: true },
  });
  const missing = missingDocuments(lead.requiredDocuments, received);
  if (missing.length === 0) {
    throw new ApiError('Tick which documents this person needs to send first', 400);
  }

  const { connected } = await getGoogleStatus(brokerUserId);
  if (!connected && !options.useSystemAddress) {
    throw new ApiError('Connect your email, or send from the system address instead', 409);
  }

  const token = randomBytes(32).toString('hex');
  const expiresAt = new Date(Date.now() + LINK_TTL_MS);
  await getPrismaClient().documentRequestLink.create({
    data: { tenantId, leadId: lead.id, tokenHash: hashToken(token), expiresAt },
  });
  const url = `${env.crm.frontendUrl}/documents/upload/${token}`;
  const message = {
    to: lead.email,
    subject: 'Documents needed for your mortgage file',
    body: `Hi ${lead.name},\n\nPlease upload these documents: ${missing.join(', ')}.\n\n${url}\n\nThis link is just for you and expires in 7 days.`,
  };
  const sentVia = connected ? 'BROKER_EMAIL' : 'SYSTEM_ADDRESS';
  if (connected) {
    await gmailService.sendEmail(tenantId, brokerUserId, { ...message, leadId: lead.id });
  } else {
    await sendSystemEmail(message);
  }
  await recordActivity('crm.documents.requested', {
    actorId: brokerUserId,
    targetType: 'lead',
    targetId: lead.id,
    metadata: { missing, sentVia },
  });
  return { url, expiresAt, sentVia, missing };
}

/** Borrower side — "Expired? → Generate a new link" is the broker's job, so this only explains. */
async function resolveLink(token: string) {
  const link = await getPrismaClient().documentRequestLink.findUnique({
    where: { tokenHash: hashToken(token) },
    include: { lead: true },
  });
  if (!link) {
    throw new ApiError('This link is not valid', 404);
  }
  if (link.expiresAt < new Date()) {
    throw new ApiError('This link has expired — ask your broker for a new one', 410);
  }
  return link;
}

export async function getPublicRequest(token: string) {
  const { lead, expiresAt } = await resolveLink(token);
  const received = await getPrismaClient().leadDocument.findMany({
    where: { leadId: lead.id },
    select: { type: true },
  });
  // Only what the borrower needs — first name and the outstanding list, nothing else from the file.
  return {
    firstName: lead.name.split(' ')[0],
    missing: missingDocuments(lead.requiredDocuments, received),
    expiresAt,
  };
}

export async function publicUpload(token: string, file: IncomingFile) {
  const { lead } = await resolveLink(token);
  const doc = await ingestFile(lead, file, 'BORROWER');
  return { id: doc.id, type: doc.type, fileName: doc.fileName };
}

async function requireDocument(tenantId: string, documentId: string, allowedBrokerIds: string[]) {
  const doc = await getPrismaClient().leadDocument.findUnique({ where: { id: documentId } });
  if (!doc || doc.tenantId !== tenantId) {
    throw new ApiError('Document not found', 404);
  }
  await getLeadWithAccess(tenantId, doc.leadId, allowedBrokerIds);
  return doc;
}

/** Downloads are recorded in the activity log (Flow 1). */
export async function downloadDocument(
  tenantId: string,
  documentId: string,
  allowedBrokerIds: string[],
  actorId: string,
) {
  const doc = await requireDocument(tenantId, documentId, allowedBrokerIds);
  const data = await readStoredFile(doc.storagePath);
  await recordActivity('crm.document.downloaded', {
    actorId,
    targetType: 'lead',
    targetId: doc.leadId,
    metadata: { documentId: doc.id, fileName: doc.fileName },
  });
  return { fileName: doc.fileName, mimeType: doc.mimeType, data };
}
