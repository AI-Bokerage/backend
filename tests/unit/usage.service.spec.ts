import { getPrismaClient } from '../../config/database';
import { aiCall } from '../../modules/crm/ai.client';
import { recordActivity } from '../../modules/platform/activity-log';
import {
  __test,
  allowExtraToday,
  expireAiCostOverrides,
  getUsageStatus,
} from '../../modules/platform/usage.service';

jest.mock('../../modules/crm/ai.client');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../config/database');

const mockPrisma = {
  aiCostLimitOverride: {
    findUnique: jest.fn(),
    findMany: jest.fn(),
    upsert: jest.fn(),
    delete: jest.fn(),
  },
};

/** Gateway answers: limits (cap, default 50) and today's admin usage summary. */
function gateway({ cap = null as number | null, costCad = 0 } = {}) {
  (aiCall as jest.Mock).mockImplementation(async (_svc: string, method: string, path: string) => {
    if (path.endsWith('/limits')) {
      return { dailyCostCapCad: method === 'PUT' ? 100 : cap, defaults: { dailyCostCapCad: 50 } };
    }
    return { rows: [{ key: 'tenant-1', costCad }] };
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.aiCostLimitOverride.findUnique.mockResolvedValue(null);
});

describe('getUsageStatus — "Has a brokerage hit its daily cost limit?"', () => {
  it('is paused once today\'s real cost reaches the default cap', async () => {
    gateway({ costCad: 52.4 });
    const status = await getUsageStatus('tenant-1');
    expect(status).toMatchObject({ paused: true, outcome: 'BLOCK', current: 52.4, limit: 50, pct: 104.8 });
    expect(aiCall).toHaveBeenCalledWith(
      'gateway',
      'GET',
      '/admin/v1/usage/summary',
      expect.objectContaining({ admin: true, query: expect.objectContaining({ groupBy: 'brokerage', brokerageId: 'tenant-1' }) }),
    );
  });

  it('is the normal view under the brokerage\'s own cap', async () => {
    gateway({ cap: 200, costCad: 10 });
    const status = await getUsageStatus('tenant-1');
    expect(status).toMatchObject({ paused: false, outcome: 'ALLOW', limit: 200, pct: 5 });
  });

  it('surfaces an unexpired override as still active', async () => {
    gateway();
    const future = new Date(Date.now() + 60_000);
    mockPrisma.aiCostLimitOverride.findUnique.mockResolvedValue({ tenantId: 'tenant-1', expiresAt: future });
    const status = await getUsageStatus('tenant-1');
    expect(status.overrideExpiresAt).toBe(future.toISOString());
  });

  it('restores the default cap when the override has expired ("resets by itself tomorrow")', async () => {
    gateway();
    mockPrisma.aiCostLimitOverride.findUnique.mockResolvedValue({
      tenantId: 'tenant-1',
      expiresAt: new Date(Date.now() - 60_000),
    });
    const status = await getUsageStatus('tenant-1');
    expect(aiCall).toHaveBeenCalledWith('gateway', 'PUT', '/admin/v1/brokerages/tenant-1/limits', {
      admin: true,
      json: { dailyCostCapCad: null },
    });
    expect(mockPrisma.aiCostLimitOverride.delete).toHaveBeenCalledWith({ where: { tenantId: 'tenant-1' } });
    expect(status.overrideExpiresAt).toBeNull();
  });
});

describe('allowExtraToday', () => {
  it('raises the cap on the gateway, keeps it until the gateway\'s day ends, and records it', async () => {
    gateway();
    mockPrisma.aiCostLimitOverride.findUnique.mockResolvedValue({
      tenantId: 'tenant-1',
      expiresAt: new Date(Date.now() + 1000),
    });
    await allowExtraToday('tenant-1');

    expect(aiCall).toHaveBeenCalledWith('gateway', 'PUT', '/admin/v1/brokerages/tenant-1/limits', {
      admin: true,
      json: { dailyCostCapCad: 100 },
    });
    const expiresAt: Date = mockPrisma.aiCostLimitOverride.upsert.mock.calls[0][0].create.expiresAt;
    expect(expiresAt.getTime() - Date.now()).toBeGreaterThan(0);
    expect(expiresAt.getTime() - Date.now()).toBeLessThanOrEqual(25 * 60 * 60 * 1000);
    expect(recordActivity).toHaveBeenCalledWith(
      'platform.usage.extra_allowed_for_today',
      expect.objectContaining({ targetId: 'tenant-1' }),
    );
  });
});

describe('expireAiCostOverrides — hourly sweep', () => {
  it('restores every expired cap', async () => {
    gateway();
    const past = new Date(Date.now() - 1000);
    mockPrisma.aiCostLimitOverride.findMany.mockResolvedValue([{ tenantId: 'tenant-1', expiresAt: past }]);
    mockPrisma.aiCostLimitOverride.findUnique.mockResolvedValue({ tenantId: 'tenant-1', expiresAt: past });
    expect(await expireAiCostOverrides()).toBe(1);
    expect(mockPrisma.aiCostLimitOverride.delete).toHaveBeenCalled();
  });
});

describe('endOfUsageDay — midnight in America/Toronto', () => {
  it('handles EDT (UTC-4) and EST (UTC-5)', () => {
    expect(__test.endOfUsageDay(new Date('2026-10-16T03:30:00Z')).toISOString()).toBe('2026-10-16T04:00:00.000Z');
    expect(__test.endOfUsageDay(new Date('2026-12-01T15:00:00Z')).toISOString()).toBe('2026-12-02T05:00:00.000Z');
  });
});
