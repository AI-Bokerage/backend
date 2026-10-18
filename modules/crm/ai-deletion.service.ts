import type { AiDataDeletion } from '@prisma/client';

import { aiCall, type AiService } from './ai.client';
import { getPrismaClient } from '../../config/database';
import { logger } from '../../config/logger';

/**
 * Flow 4.11 "Deleted permanently" and Flow 1's brokerage purge reach the AI
 * services too: every one holding a copy of the person is told to delete it
 * (ai service/docs/runbooks/delete-client.md). The services never call each
 * other, so the backend fans out; a service that is down is retried hourly.
 */
const SERVICES: AiService[] = ['voice', 'docread', 'notes', 'email', 'assistant', 'search', 'gateway'];

async function attempt(row: AiDataDeletion) {
  const results = await Promise.allSettled(
    row.pending.map((service) =>
      aiCall(service as AiService, 'DELETE', `/v1/clients/${encodeURIComponent(row.clientId)}/ai-data`, {
        caller: { tenantId: row.tenantId },
      }),
    ),
  );
  const failed = row.pending.filter((_, i) => results[i]!.status === 'rejected');
  const db = getPrismaClient();
  if (!failed.length) {
    await db.aiDataDeletion.delete({ where: { id: row.id } });
    return true;
  }
  const errors = results.flatMap((r, i) => (r.status === 'rejected' ? [`${row.pending[i]}: ${(r.reason as Error).message}`] : []));
  await db.aiDataDeletion.update({
    where: { id: row.id },
    data: { pending: failed, attempts: { increment: 1 }, lastError: errors.join('; ').slice(0, 1000) },
  });
  logger.warn({ clientId: row.clientId, pending: failed }, 'ai.client_deletion_incomplete');
  return false;
}

/** Records the deletion first (so nothing is lost if a call fails), then tries every service once. */
export async function deleteClientsAiData(tenantId: string, clientIds: string[]) {
  if (!clientIds.length) return;
  const db = getPrismaClient();
  await db.aiDataDeletion.createMany({
    data: clientIds.map((clientId) => ({ tenantId, clientId, pending: SERVICES })),
    skipDuplicates: true,
  });
  const rows = await db.aiDataDeletion.findMany({ where: { tenantId, clientId: { in: clientIds } } });
  for (const row of rows) await attempt(row);
}

/** Hourly: retry the services that were down. Returns how many people are now fully wiped. */
export async function retryAiDataDeletions() {
  const rows = await getPrismaClient().aiDataDeletion.findMany({ orderBy: { createdAt: 'asc' }, take: 200 });
  let done = 0;
  for (const row of rows) if (await attempt(row)) done += 1;
  return done;
}
