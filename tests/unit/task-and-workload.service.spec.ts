jest.mock('../../config/database');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/crm/ai.client');

import { getPrismaClient } from '../../config/database';
import { aiCall } from '../../modules/crm/ai.client';
import {
  approveSuggestedTask,
  completeTask,
  createTask,
  deleteTask,
  listForBrokers,
  listMyTasks,
  overdueCountByBroker,
} from '../../modules/crm/task.service';
import { teamWorkload } from '../../modules/crm/workload.service';
import { recordActivity } from '../../modules/platform/activity-log';

const mockPrisma = {
  task: {
    create: jest.fn(),
    findUnique: jest.fn(),
    findMany: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
  lead: { groupBy: jest.fn(), findMany: jest.fn().mockResolvedValue([]) },
  mortgage: { findMany: jest.fn() },
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.mortgage.findMany.mockResolvedValue([]);
});

const TENANT = 'tenant-1';

describe('completeTask', () => {
  it('404s a task outside the tenant entirely', async () => {
    mockPrisma.task.findUnique.mockResolvedValue({
      id: 't1',
      tenantId: 'other-tenant',
      brokerUserId: 'me',
    });
    await expect(completeTask(TENANT, 'me', 't1')).rejects.toMatchObject({ statusCode: 404 });
    expect(mockPrisma.task.update).not.toHaveBeenCalled();
  });

  it('403s a task in-tenant but belonging to a different broker — "You don\'t have access to this record"', async () => {
    mockPrisma.task.findUnique.mockResolvedValue({
      id: 't1',
      tenantId: TENANT,
      brokerUserId: 'someone-else',
    });
    await expect(completeTask(TENANT, 'me', 't1')).rejects.toMatchObject({
      statusCode: 403,
      message: "You don't have access to this record",
    });
    expect(mockPrisma.task.update).not.toHaveBeenCalled();
  });

  it("marks the caller's own task complete", async () => {
    mockPrisma.task.findUnique.mockResolvedValue({
      id: 't1',
      tenantId: TENANT,
      brokerUserId: 'me',
    });
    mockPrisma.task.update.mockResolvedValue({ id: 't1', completed: true });
    const result = await completeTask(TENANT, 'me', 't1');
    expect(result.completed).toBe(true);
  });
});

describe('Flow 4.9 — AI-suggested tasks', () => {
  const suggested = { id: 't1', tenantId: TENANT, brokerUserId: 'me', suggested: true };

  it("can't be completed until approved", async () => {
    mockPrisma.task.findUnique.mockResolvedValue(suggested);
    await expect(completeTask(TENANT, 'me', 't1')).rejects.toMatchObject({ statusCode: 409 });
    expect(mockPrisma.task.update).not.toHaveBeenCalled();
  });

  it('"Approve" turns it into a real task', async () => {
    mockPrisma.task.findUnique.mockResolvedValue(suggested);
    mockPrisma.task.update.mockResolvedValue({ ...suggested, suggested: false });
    await approveSuggestedTask(TENANT, 'me', 't1');
    expect(mockPrisma.task.update).toHaveBeenCalledWith({
      where: { id: 't1' },
      data: { suggested: false },
    });
  });

  it('"Dismiss" deletes it — but never a real, linked task', async () => {
    mockPrisma.task.findUnique.mockResolvedValue(suggested);
    await deleteTask(TENANT, 'me', 't1');
    expect(mockPrisma.task.delete).toHaveBeenCalledWith({ where: { id: 't1' } });

    mockPrisma.task.delete.mockClear();
    mockPrisma.task.findUnique.mockResolvedValue({ ...suggested, suggested: false });
    await expect(deleteTask(TENANT, 'me', 't1')).rejects.toMatchObject({
      statusCode: 409,
    });
    expect(mockPrisma.task.delete).not.toHaveBeenCalled();
  });

  it('an AI-suggested task: dismissing is recorded in notes-svc, so "Suggest tasks" never offers it again', async () => {
    mockPrisma.task.findUnique.mockResolvedValue({ ...suggested, aiSuggestionId: 's1', aiRunId: 'r1' });
    (aiCall as jest.Mock).mockRejectedValueOnce(new Error('AI down'));
    await deleteTask(TENANT, 'me', 't1');
    expect(aiCall).toHaveBeenCalledWith('notes', 'POST', '/v1/runs/r1/decisions', expect.objectContaining({
      json: { decisions: [{ suggestionId: 's1', decision: 'rejected' }] },
    }));
    // The broker's dismissal stands even when the AI service is down.
    expect(mockPrisma.task.delete).toHaveBeenCalled();
  });

  it('only the owning broker can approve or dismiss', async () => {
    mockPrisma.task.findUnique.mockResolvedValue({ ...suggested, brokerUserId: 'someone-else' });
    await expect(approveSuggestedTask(TENANT, 'me', 't1')).rejects.toMatchObject({
      statusCode: 403,
    });
  });
});

describe('overdueCountByBroker', () => {
  it('shows a broker with no overdue tasks as 0, not omitted', async () => {
    mockPrisma.task.findMany.mockResolvedValue([{ brokerUserId: 'b1' }, { brokerUserId: 'b1' }]);
    const counts = await overdueCountByBroker(TENANT, ['b1', 'b2']);
    expect(counts).toEqual({ b1: 2, b2: 0 });
    // A suggested task isn't real yet, so it can't be overdue.
    expect(mockPrisma.task.findMany.mock.calls[0][0].where.suggested).toBe(false);
  });
});

describe('teamWorkload — "a broker with nothing assigned is shown as empty rather than hidden"', () => {
  it('returns one row per team member even when a broker has zero of everything', async () => {
    mockPrisma.lead.groupBy.mockResolvedValue([{ brokerUserId: 'b1', _count: 4 }]);
    mockPrisma.task.findMany.mockResolvedValue([{ brokerUserId: 'b1' }]);

    const workload = await teamWorkload(TENANT, [
      { userId: 'b1', name: 'Bob' },
      { userId: 'b2', name: 'Nobody Assigned' },
    ]);

    expect(workload).toHaveLength(2);
    const b2 = workload.find((w) => w.brokerUserId === 'b2')!;
    expect(b2).toMatchObject({
      openLeads: 0,
      overdueTasks: 0,
      renewalsWithin90Days: 0,
      missingDocuments: 0,
    });
    const b1 = workload.find((w) => w.brokerUserId === 'b1')!;
    expect(b1).toMatchObject({ openLeads: 4, overdueTasks: 1 });
  });
});

describe('createTask', () => {
  it('always creates the task against the caller, converting dueDate to a real Date', async () => {
    mockPrisma.task.create.mockResolvedValue({ id: 't1' });
    await createTask(TENANT, 'me', { title: 'Follow up', dueDate: '2026-10-01T00:00:00.000Z' });
    const arg = mockPrisma.task.create.mock.calls[0][0];
    expect(arg.data).toMatchObject({ tenantId: TENANT, brokerUserId: 'me', title: 'Follow up' });
    expect(arg.data.dueDate).toBeInstanceOf(Date);
  });

  it('leaves dueDate null when none is given', async () => {
    mockPrisma.task.create.mockResolvedValue({ id: 't1' });
    await createTask(TENANT, 'me', { title: 'No date task' });
    expect(mockPrisma.task.create.mock.calls[0][0].data.dueDate).toBeNull();
  });
});

describe('listMyTasks', () => {
  it("scopes to the caller's own tenant and broker id", async () => {
    mockPrisma.task.findMany.mockResolvedValue([]);
    await listMyTasks(TENANT, 'me');
    expect(mockPrisma.task.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tenantId: TENANT, brokerUserId: 'me' } }),
    );
  });
});

describe('listForBrokers — "Team tasks"', () => {
  it('returns nothing without hitting the DB when the team has no brokers', async () => {
    const tasks = await listForBrokers(TENANT, []);
    expect(tasks).toEqual([]);
    expect(mockPrisma.task.findMany).not.toHaveBeenCalled();
  });

  it('lists tasks for every broker on the team', async () => {
    mockPrisma.task.findMany.mockResolvedValue([{ id: 't1' }]);
    const tasks = await listForBrokers(TENANT, ['b1', 'b2']);
    expect(tasks).toHaveLength(1);
    expect(mockPrisma.task.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tenantId: TENANT, brokerUserId: { in: ['b1', 'b2'] } } }),
    );
  });
});

describe('Flow 4.9 — "tick to complete, stays on the person\'s timeline"', () => {
  it("records the completion on the person's activity timeline", async () => {
    mockPrisma.task.findUnique.mockResolvedValue({
      id: 't1',
      tenantId: TENANT,
      brokerUserId: 'me',
      leadId: 'lead-1',
      title: 'Call back',
    });
    mockPrisma.task.update.mockResolvedValue({ id: 't1', completed: true });
    await completeTask(TENANT, 'me', 't1');
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.task.completed',
      expect.objectContaining({ targetType: 'lead', targetId: 'lead-1' }),
    );
  });
});

describe('listMyTasks — dashboard task cards', () => {
  it('due=today and due=overdue count real, open tasks only', async () => {
    mockPrisma.task.findMany.mockResolvedValue([]);
    await listMyTasks(TENANT, 'me', { due: 'today' });
    const today = mockPrisma.task.findMany.mock.calls[0][0].where;
    expect(today).toMatchObject({ completed: false, suggested: false });
    expect(today.dueDate.gte).toBeInstanceOf(Date);
    expect(today.dueDate.lt.getTime() - today.dueDate.gte.getTime()).toBe(24 * 60 * 60 * 1000);

    await listMyTasks(TENANT, 'me', { due: 'overdue' });
    const overdue = mockPrisma.task.findMany.mock.calls[1][0].where;
    expect(overdue.dueDate).toEqual({ lt: today.dueDate.gte });
  });
});
