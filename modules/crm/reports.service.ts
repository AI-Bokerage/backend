import { aiCall } from './ai.client';
import { getPrismaClient } from '../../config/database';
import { recordActivity } from '../platform/activity-log';
import { usageDay } from '../platform/usage.service';

export type ReportType = 'pipeline' | 'renewals' | 'team-performance' | 'ai-usage';

export interface ReportFilter {
  tenantId: string;
  brokerIds: string[];
  from?: Date;
  to?: Date;
}

const STAGES = [
  'NEW',
  'QUALIFIED',
  'APPLICATION',
  'UNDERWRITING',
  'APPROVED',
  'FUNDED',
  'LOST',
] as const;

/** "Pick one: renewals · AI usage · pipeline · team performance", then "Set date range, broker, team." */
export async function pipelineReport(filter: ReportFilter) {
  const base: Record<string, number> = Object.fromEntries(STAGES.map((s) => [s, 0]));
  if (filter.brokerIds.length === 0) return base;
  const grouped = await getPrismaClient().lead.groupBy({
    by: ['stage'],
    where: {
      tenantId: filter.tenantId,
      brokerUserId: { in: filter.brokerIds },
      createdAt: dateRangeFilter(filter),
    },
    _count: true,
  });
  for (const row of grouped) base[row.stage] = row._count;
  return base;
}

/** Mortgages maturing in the given range — defaults to the next 90 days when no range is given. */
export async function renewalsReport(filter: ReportFilter) {
  if (filter.brokerIds.length === 0) return [];
  const from = filter.from ?? new Date();
  const to = filter.to ?? addDays(from, 90);
  const mortgages = await getPrismaClient().mortgage.findMany({
    where: {
      maturityDate: { gte: from, lte: to },
      lead: { tenantId: filter.tenantId, brokerUserId: { in: filter.brokerIds } },
    },
    include: { lead: { select: { id: true, name: true, brokerUserId: true } } },
    orderBy: { maturityDate: 'asc' },
  });
  return mortgages.map((m) => ({
    leadId: m.lead.id,
    leadName: m.lead.name,
    brokerUserId: m.lead.brokerUserId,
    lender: m.lender,
    balance: m.balance,
    maturityDate: m.maturityDate.toISOString(),
  }));
}

/** Per broker: funded/lost counts and tasks completed, within the given date range. */
export async function teamPerformanceReport(filter: ReportFilter) {
  if (filter.brokerIds.length === 0) return [];
  const where = {
    tenantId: filter.tenantId,
    brokerUserId: { in: filter.brokerIds },
    createdAt: dateRangeFilter(filter),
  };
  const [fundedGrouped, lostGrouped, completedTasksGrouped] = await Promise.all([
    getPrismaClient().lead.groupBy({
      by: ['brokerUserId'],
      where: { ...where, stage: 'FUNDED' },
      _count: true,
    }),
    getPrismaClient().lead.groupBy({
      by: ['brokerUserId'],
      where: { ...where, stage: 'LOST' },
      _count: true,
    }),
    getPrismaClient().task.groupBy({
      by: ['brokerUserId'],
      where: {
        tenantId: filter.tenantId,
        brokerUserId: { in: filter.brokerIds },
        completed: true,
        createdAt: dateRangeFilter(filter),
      },
      _count: true,
    }),
  ]);
  const fundedMap = new Map(fundedGrouped.map((r) => [r.brokerUserId, r._count]));
  const lostMap = new Map(lostGrouped.map((r) => [r.brokerUserId, r._count]));
  const completedMap = new Map(completedTasksGrouped.map((r) => [r.brokerUserId, r._count]));
  return filter.brokerIds.map((brokerUserId) => ({
    brokerUserId,
    fundedCount: fundedMap.get(brokerUserId) ?? 0,
    lostCount: lostMap.get(brokerUserId) ?? 0,
    tasksCompleted: completedMap.get(brokerUserId) ?? 0,
  }));
}

interface GatewayUsageRow {
  key: string;
  calls: number;
  blocked: number;
  credits: number;
}

const THIRTY_DAYS = 30 * 24 * 60 * 60 * 1000;

/**
 * "AI usage" — each person's AI calls and credits from the AI gateway, within
 * the broker/team scope. Credits, not dollars: the gateway shows the real
 * provider cost to the Super Admin only. No date range = the last 30 days.
 */
export async function aiUsageReport(filter: ReportFilter) {
  const from = usageDay(filter.from ?? new Date(Date.now() - THIRTY_DAYS));
  const to = usageDay(filter.to);
  const summary = await aiCall<{ rows: GatewayUsageRow[] }>('gateway', 'GET', '/v1/usage/summary', {
    caller: { tenantId: filter.tenantId },
    query: { from, to, groupBy: 'user' },
  });
  const byUser = new Map(summary.rows.map((r) => [r.key, r]));
  return {
    from,
    to,
    rows: filter.brokerIds.map((brokerUserId) => {
      const row = byUser.get(brokerUserId);
      return {
        brokerUserId,
        calls: row?.calls ?? 0,
        credits: row?.credits ?? 0,
        blocked: row?.blocked ?? 0,
      };
    }),
  };
}

export async function runReport(type: ReportType, filter: ReportFilter) {
  switch (type) {
    case 'pipeline':
      return pipelineReport(filter);
    case 'renewals':
      return renewalsReport(filter);
    case 'team-performance':
      return teamPerformanceReport(filter);
    case 'ai-usage':
      return aiUsageReport(filter);
  }
}

/** "Export as a spreadsheet — the export itself is recorded." Plain CSV: no new dependency, opens natively in any spreadsheet app. */
export async function exportReportCsv(
  type: ReportType,
  filter: ReportFilter,
  actorId: string,
): Promise<string> {
  const data = await runReport(type, filter);
  const rows = Array.isArray(data)
    ? data
    : type === 'ai-usage'
      ? (data as Awaited<ReturnType<typeof aiUsageReport>>).rows
      : [data];
  const csv = toCsv(rows);
  await recordActivity('crm.report.exported', {
    actorId,
    targetType: 'report',
    targetId: type,
    metadata: {
      tenantId: filter.tenantId,
      from: filter.from?.toISOString(),
      to: filter.to?.toISOString(),
    },
  });
  return csv;
}

function toCsv(rows: unknown[]): string {
  if (rows.length === 0) return '';
  const records = rows as Record<string, unknown>[];
  const headers = Object.keys(records[0]!);
  const lines = [headers.join(',')];
  for (const row of records) {
    lines.push(headers.map((h) => JSON.stringify(row[h] ?? '')).join(','));
  }
  return lines.join('\n');
}

function dateRangeFilter(filter: ReportFilter): { gte?: Date; lte?: Date } | undefined {
  if (!filter.from && !filter.to) return undefined;
  return { gte: filter.from, lte: filter.to };
}

function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}
