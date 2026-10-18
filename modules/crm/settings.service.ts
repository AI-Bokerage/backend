import { aiCall } from './ai.client';
import { getPrismaClient } from '../../config/database';

/** Flow 4.12 Settings — per-user CRM preferences. No row yet means the defaults. */
export async function getSettings(userId: string) {
  const row = await getPrismaClient().crmUserSettings.findUnique({ where: { userId } });
  return { aiEnabled: row?.aiEnabled ?? true };
}

/**
 * "AI features on or off" — switched off, the AI gateway refuses every AI call
 * for this user (403 AI_DISABLED) and the frontend hides its buttons. The
 * gateway is set first: if it can't be reached nothing is saved, so the two never disagree.
 */
export async function updateSettings(
  tenantId: string,
  userId: string,
  input: { aiEnabled: boolean },
) {
  await aiCall('gateway', 'PUT', `/v1/users/${userId}/ai-settings`, {
    caller: { tenantId },
    json: { aiEnabled: input.aiEnabled },
  });
  const row = await getPrismaClient().crmUserSettings.upsert({
    where: { userId },
    create: { userId, tenantId, aiEnabled: input.aiEnabled },
    update: { aiEnabled: input.aiEnabled },
  });
  return { aiEnabled: row.aiEnabled };
}
