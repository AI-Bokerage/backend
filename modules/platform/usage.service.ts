import { recordActivity } from './activity-log';
import type { BrokerageUsageStatus, UsageOutcome } from './usage.types';
import { getPrismaClient } from '../../config/database';
import { env } from '../../config/env';
import { aiCall } from '../crm/ai.client';

/**
 * Flow 1 "Watching AI cost". The AI gateway is the one door to every AI
 * provider: it meters the real cost of each call and enforces each
 * brokerage's daily cost cap itself (brokers get 429 AI_LIMIT_REACHED), so
 * this only reads its numbers and moves the cap.
 */
interface GatewayLimits {
  dailyCostCapCad: number | null;
  defaults: { dailyCostCapCad: number };
}
interface GatewayUsage {
  rows: { key: string; costCad: number }[];
}

// The gateway's "today" is a calendar day in America/Toronto (its USAGE_TIMEZONE).
const USAGE_TZ = 'America/Toronto';
/** A date as the gateway's YYYY-MM-DD day. */
export const usageDay = (d = new Date()) => d.toLocaleDateString('en-CA', { timeZone: USAGE_TZ });

/** The instant the gateway's day rolls over — when its daily allowances reset. */
function endOfUsageDay(now = new Date()): Date {
  const local = new Date(now.toLocaleString('en-US', { timeZone: USAGE_TZ }));
  const midnight = new Date(local);
  midnight.setHours(24, 0, 0, 0);
  return new Date(now.getTime() + (midnight.getTime() - local.getTime()));
}

function outcomeFor(pct: number): UsageOutcome {
  if (pct >= 100) return 'BLOCK';
  if (pct >= 95) return 'SOFT_WARN_95';
  if (pct >= 80) return 'SOFT_WARN_80';
  return 'ALLOW';
}

const setCostCap = (tenantId: string, dailyCostCapCad: number | null) =>
  aiCall<GatewayLimits>('gateway', 'PUT', `/admin/v1/brokerages/${tenantId}/limits`, {
    admin: true,
    json: { dailyCostCapCad },
  });

/** "Leave it paused → resets by itself tomorrow": an expired override puts the default cap back. */
async function expireOverride(tenantId: string) {
  const override = await getPrismaClient().aiCostLimitOverride.findUnique({ where: { tenantId } });
  if (!override || override.expiresAt > new Date()) return override;
  await setCostCap(tenantId, null);
  await getPrismaClient().aiCostLimitOverride.delete({ where: { tenantId } });
  return null;
}

/** Hourly sweep (server.ts) so a raised cap doesn't outlive its day when nobody looks. */
export async function expireAiCostOverrides(): Promise<number> {
  const expired = await getPrismaClient().aiCostLimitOverride.findMany({
    where: { expiresAt: { lte: new Date() } },
  });
  for (const { tenantId } of expired) await expireOverride(tenantId);
  return expired.length;
}

/** "Has this brokerage hit its daily cost limit?" — today's real cost against its cap, in CAD. */
export async function getUsageStatus(tenantId: string): Promise<BrokerageUsageStatus> {
  const override = await expireOverride(tenantId);
  const day = usageDay();
  const [limits, usage] = await Promise.all([
    aiCall<GatewayLimits>('gateway', 'GET', `/admin/v1/brokerages/${tenantId}/limits`, { admin: true }),
    aiCall<GatewayUsage>('gateway', 'GET', '/admin/v1/usage/summary', {
      admin: true,
      query: { from: day, to: day, groupBy: 'brokerage', brokerageId: tenantId },
    }),
  ]);
  const limit = limits.dailyCostCapCad ?? limits.defaults.dailyCostCapCad;
  const current = usage.rows.find((r) => r.key === tenantId)?.costCad ?? 0;
  const pct = limit > 0 ? Math.round((current / limit) * 1000) / 10 : 100;
  const outcome = outcomeFor(pct);

  return {
    metric: 'ai_cost_cad_daily',
    outcome,
    paused: outcome === 'BLOCK',
    current,
    limit,
    pct,
    overrideExpiresAt: override ? override.expiresAt.toISOString() : null,
  };
}

/**
 * "Allow extra for today" — the gateway has no bypass, so this raises the
 * brokerage's cap to AI_COST_OVERRIDE_LIMIT until the gateway's day ends;
 * expireOverride then restores the default ("resets by itself tomorrow").
 */
export async function allowExtraToday(tenantId: string): Promise<BrokerageUsageStatus> {
  await setCostCap(tenantId, env.platform.aiCostOverrideLimit);
  const expiresAt = endOfUsageDay();
  await getPrismaClient().aiCostLimitOverride.upsert({
    where: { tenantId },
    create: { tenantId, expiresAt },
    update: { expiresAt },
  });
  await recordActivity('platform.usage.extra_allowed_for_today', {
    targetType: 'tenant',
    targetId: tenantId,
  });
  return getUsageStatus(tenantId);
}

export const __test = { endOfUsageDay, outcomeFor };
