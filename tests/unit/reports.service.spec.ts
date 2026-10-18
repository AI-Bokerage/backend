jest.mock('../../config/database');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/crm/ai.client');

import { getPrismaClient } from '../../config/database';
import { aiCall } from '../../modules/crm/ai.client';
import {
  aiUsageReport,
  exportReportCsv,
  pipelineReport,
  renewalsReport,
  runReport,
  teamPerformanceReport,
} from '../../modules/crm/reports.service';
import { recordActivity } from '../../modules/platform/activity-log';

const mockPrisma = {
  lead: { groupBy: jest.fn() },
  mortgage: { findMany: jest.fn() },
  task: { groupBy: jest.fn() },
};

const TENANT = 'tenant-1';

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('pipelineReport', () => {
  it('returns every stage at 0 without hitting the DB when the scope is empty', async () => {
    const report = await pipelineReport({ tenantId: TENANT, brokerIds: [] });
    expect(report.NEW).toBe(0);
    expect(mockPrisma.lead.groupBy).not.toHaveBeenCalled();
  });

  it('applies the date range when given', async () => {
    mockPrisma.lead.groupBy.mockResolvedValue([{ stage: 'NEW', _count: 2 }]);
    const from = new Date('2026-01-01');
    const to = new Date('2026-02-01');
    await pipelineReport({ tenantId: TENANT, brokerIds: ['b1'], from, to });
    expect(mockPrisma.lead.groupBy).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ createdAt: { gte: from, lte: to } }),
      }),
    );
  });
});

describe('renewalsReport — "renewals within 90 days" (or a given range)', () => {
  it('defaults to the next 90 days when no range is given', async () => {
    mockPrisma.mortgage.findMany.mockResolvedValue([]);
    await renewalsReport({ tenantId: TENANT, brokerIds: ['b1'] });
    const arg = mockPrisma.mortgage.findMany.mock.calls[0][0];
    const spanDays =
      (arg.where.maturityDate.lte.getTime() - arg.where.maturityDate.gte.getTime()) /
      (1000 * 60 * 60 * 24);
    expect(Math.round(spanDays)).toBe(90);
  });

  it('shapes rows with the lead name and broker id flattened out', async () => {
    mockPrisma.mortgage.findMany.mockResolvedValue([
      {
        lender: 'Acme Bank',
        balance: 300000,
        maturityDate: new Date('2026-12-01'),
        lead: { id: 'lead-1', name: 'Jamie Rivera', brokerUserId: 'b1' },
      },
    ]);
    const rows = await renewalsReport({ tenantId: TENANT, brokerIds: ['b1'] });
    expect(rows[0]).toMatchObject({
      leadId: 'lead-1',
      leadName: 'Jamie Rivera',
      brokerUserId: 'b1',
      lender: 'Acme Bank',
    });
  });
});

describe('teamPerformanceReport', () => {
  it('shows a broker with nothing as all zeros, not omitted', async () => {
    mockPrisma.lead.groupBy.mockResolvedValueOnce([{ brokerUserId: 'b1', _count: 2 }]); // funded
    mockPrisma.lead.groupBy.mockResolvedValueOnce([]); // lost
    mockPrisma.task.groupBy.mockResolvedValue([]); // completed tasks
    const rows = await teamPerformanceReport({ tenantId: TENANT, brokerIds: ['b1', 'b2'] });
    expect(rows).toEqual([
      { brokerUserId: 'b1', fundedCount: 2, lostCount: 0, tasksCompleted: 0 },
      { brokerUserId: 'b2', fundedCount: 0, lostCount: 0, tasksCompleted: 0 },
    ]);
  });
});

describe('aiUsageReport — per person, from the AI gateway', () => {
  it('keeps the brokers in scope, zero for anyone with no AI use', async () => {
    (aiCall as jest.Mock).mockResolvedValue({
      rows: [
        { key: 'b1', calls: 4, blocked: 1, credits: 12.5 },
        { key: 'outside-scope', calls: 9, blocked: 0, credits: 30 },
      ],
    });
    const result = await aiUsageReport({
      tenantId: TENANT,
      brokerIds: ['b1', 'b2'],
      from: new Date('2026-10-01T12:00:00Z'),
      to: new Date('2026-10-15T12:00:00Z'),
    });
    expect(aiCall).toHaveBeenCalledWith('gateway', 'GET', '/v1/usage/summary', {
      caller: { tenantId: TENANT },
      query: { from: '2026-10-01', to: '2026-10-15', groupBy: 'user' },
    });
    expect(result.rows).toEqual([
      { brokerUserId: 'b1', calls: 4, credits: 12.5, blocked: 1 },
      { brokerUserId: 'b2', calls: 0, credits: 0, blocked: 0 },
    ]);
  });
});

describe('runReport', () => {
  it('dispatches to the right report by type', async () => {
    (aiCall as jest.Mock).mockResolvedValue({ rows: [] });
    const result = await runReport('ai-usage', { tenantId: TENANT, brokerIds: [] });
    expect(result).toMatchObject({ rows: [] });
  });
});

describe('exportReportCsv — "the export itself is recorded"', () => {
  it('builds a CSV and records the activity', async () => {
    mockPrisma.lead.groupBy.mockResolvedValue([{ stage: 'NEW', _count: 1 }]);
    const csv = await exportReportCsv(
      'pipeline',
      { tenantId: TENANT, brokerIds: ['b1'] },
      'actor-1',
    );

    expect(csv).toContain('NEW');
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.report.exported',
      expect.objectContaining({ actorId: 'actor-1', targetId: 'pipeline' }),
    );
  });
});
