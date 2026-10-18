import { getLeadWithAccess } from './lead.service';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';

export interface EmailDraftInput {
  intent?: string;
  subject: string;
  body: string;
  aiAssisted?: boolean;
}

/** Flow 4.7 "Save draft — nothing is sent". */
export async function createDraft(
  tenantId: string,
  userId: string,
  leadId: string,
  allowedBrokerIds: string[],
  input: EmailDraftInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  return getPrismaClient().emailDraft.create({
    data: { tenantId, brokerUserId: userId, leadId: lead.id, ...input },
  });
}

export async function listDrafts(tenantId: string, leadId: string, allowedBrokerIds: string[]) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  return getPrismaClient().emailDraft.findMany({
    where: { leadId: lead.id },
    orderBy: { updatedAt: 'desc' },
  });
}

/** "Edit it freely" — access follows the draft's lead, same as every other per-lead record. */
export async function updateDraft(
  tenantId: string,
  draftId: string,
  allowedBrokerIds: string[],
  input: Partial<EmailDraftInput>,
) {
  const draft = await getPrismaClient().emailDraft.findUnique({ where: { id: draftId } });
  if (!draft || draft.tenantId !== tenantId) {
    throw new ApiError('Draft not found', 404);
  }
  await getLeadWithAccess(tenantId, draft.leadId, allowedBrokerIds);
  return getPrismaClient().emailDraft.update({ where: { id: draft.id }, data: input });
}
