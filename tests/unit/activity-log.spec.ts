jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import {
  exportActivityCsv,
  listActivity,
  recordActivity,
} from '../../modules/platform/activity-log';

const mockPrisma = {
  activityLog: { create: jest.fn(), findMany: jest.fn(), count: jest.fn() },
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('listActivity', () => {
  it('paginates with defaults when no filter is given', async () => {
    mockPrisma.activityLog.findMany.mockResolvedValue([]);
    mockPrisma.activityLog.count.mockResolvedValue(0);
    await listActivity();
    expect(mockPrisma.activityLog.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: {}, skip: 0, take: 20 }),
    );
  });

  it('applies the action and date-range filters', async () => {
    mockPrisma.activityLog.findMany.mockResolvedValue([]);
    mockPrisma.activityLog.count.mockResolvedValue(0);
    const from = new Date('2026-01-01');
    const to = new Date('2026-02-01');
    await listActivity({ action: 'platform.brokerage.created', from, to, page: 2, size: 10 });
    expect(mockPrisma.activityLog.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { action: 'platform.brokerage.created', createdAt: { gte: from, lte: to } },
        skip: 20,
        take: 10,
      }),
    );
  });
});

describe('exportActivityCsv — "the export is itself recorded"', () => {
  it('builds a CSV from the matching rows and records the export', async () => {
    mockPrisma.activityLog.findMany.mockResolvedValue([
      {
        id: 'a1',
        action: 'platform.brokerage.created',
        actorId: 'admin-1',
        targetType: 'tenant',
        targetId: 't1',
        createdAt: new Date('2026-01-01'),
      },
    ]);
    mockPrisma.activityLog.count.mockResolvedValue(1);

    const csv = await exportActivityCsv({}, 'admin-1');

    expect(csv).toContain('platform.brokerage.created');
    expect(mockPrisma.activityLog.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ action: 'platform.activity_log.exported' }),
      }),
    );
  });
});

describe('recordActivity', () => {
  it('writes a row with nullable fields defaulted', async () => {
    mockPrisma.activityLog.create.mockResolvedValue({});
    await recordActivity('some.action');
    expect(mockPrisma.activityLog.create).toHaveBeenCalledWith({
      data: {
        action: 'some.action',
        actorId: null,
        targetType: null,
        targetId: null,
        metadata: undefined,
      },
    });
  });
});
