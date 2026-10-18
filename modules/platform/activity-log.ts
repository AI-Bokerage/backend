import { getPrismaClient } from '../../config/database';

/** Structurally matches @gen-ms/gen-sup-starter's EventPublisher port — see brokerage.service.ts. */
export interface EventEnvelope {
  event_type: string;
  occurred_at: string;
  tenant_id: string;
  data: Record<string, unknown>;
}

export interface EventPublisher {
  publish(exchange: string, routingKey: string, envelope: EventEnvelope): Promise<void>;
}

/**
 * Deliberately simple: a row in this app's own Postgres, not the full
 * Gen_Audit hash-chained ledger service (its own Postgres + Mongo + RabbitMQ
 * + S3 — a lot of extra infra for what the "Activity log" screen needs right
 * now). Implements gen-sup-starter's own EventPublisher port so
 * TenantsService's audit events land here with zero changes to that library
 * code — swappable for a real Gen_Audit/RabbitMQ publisher later without
 * touching TenantsService or this shape.
 */
export const activityLogEventPublisher: EventPublisher = {
  async publish(_exchange, routingKey, envelope) {
    await getPrismaClient().activityLog.create({
      data: {
        action: routingKey,
        targetType: 'tenant',
        targetId: envelope.tenant_id,
        metadata: envelope.data as never,
      },
    });
  },
};

/** For events outside gen-sup-starter's own routingKey vocabulary (AI-cost overrides, exports, ...). */
export async function recordActivity(
  action: string,
  opts: {
    actorId?: string;
    targetType?: string;
    targetId?: string;
    metadata?: Record<string, unknown>;
  } = {},
): Promise<void> {
  await getPrismaClient().activityLog.create({
    data: {
      action,
      actorId: opts.actorId ?? null,
      targetType: opts.targetType ?? null,
      targetId: opts.targetId ?? null,
      metadata: (opts.metadata ?? undefined) as never,
    },
  });
}

export interface ActivityLogFilter {
  action?: string;
  actions?: string[];
  targetId?: string;
  from?: Date;
  to?: Date;
  page?: number;
  size?: number;
}

/** "Filtered and exported" — the Super Admin console's read side of the log every recordActivity call writes into. */
export async function listActivity(filter: ActivityLogFilter = {}) {
  const page = filter.page ?? 0;
  const size = filter.size ?? 20;
  const where = {
    ...(filter.action ? { action: filter.action } : {}),
    ...(filter.actions ? { action: { in: filter.actions } } : {}),
    ...(filter.targetId ? { targetId: filter.targetId } : {}),
    ...(filter.from || filter.to
      ? {
          createdAt: {
            ...(filter.from ? { gte: filter.from } : {}),
            ...(filter.to ? { lte: filter.to } : {}),
          },
        }
      : {}),
  };
  const [items, total] = await Promise.all([
    getPrismaClient().activityLog.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      skip: page * size,
      take: size,
    }),
    getPrismaClient().activityLog.count({ where }),
  ]);
  return { items, total };
}

/** "The export is itself recorded" — same rule as every other export in this app. */
export async function exportActivityCsv(
  filter: ActivityLogFilter,
  actorId: string,
): Promise<string> {
  const { items } = await listActivity({ ...filter, page: 0, size: 10000 });
  const rows = items.map((r) => ({
    id: r.id,
    action: r.action,
    actorId: r.actorId,
    targetType: r.targetType,
    targetId: r.targetId,
    createdAt: r.createdAt.toISOString(),
  }));
  const csv = toCsv(rows);
  await recordActivity('platform.activity_log.exported', {
    actorId,
    metadata: { action: filter.action },
  });
  return csv;
}

function toCsv(rows: Record<string, unknown>[]): string {
  if (rows.length === 0) return '';
  const headers = Object.keys(rows[0]!);
  const lines = [headers.join(',')];
  for (const row of rows) {
    lines.push(headers.map((h) => JSON.stringify(row[h] ?? '')).join(','));
  }
  return lines.join('\n');
}
