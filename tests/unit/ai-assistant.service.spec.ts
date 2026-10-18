jest.mock('../../config/database');
jest.mock('../../modules/crm/ai.client');
jest.mock('../../modules/crm/modauth.client');

import { getPrismaClient } from '../../config/database';
import * as assistant from '../../modules/crm/ai-assistant.service';
import { aiCall } from '../../modules/crm/ai.client';
import { getModAuthPerson } from '../../modules/crm/modauth.client';

const mockPrisma = {
  lead: { findFirst: jest.fn(), findMany: jest.fn(), findUnique: jest.fn() },
  task: { findUnique: jest.fn(), create: jest.fn() },
};
const LEAH = { tenantId: 'tenant-1', userId: 'leah', role: 'TEAM_LEAD' as const };

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('dataScope — "Is this within what you\'re allowed to see?"', () => {
  it("while a question is being answered, reads with exactly the asker's access, then stops", async () => {
    let seen: unknown;
    (aiCall as jest.Mock).mockImplementation(async (_s, _m, _p, opts) => {
      seen = await assistant.dataScope({ tenantId: 'tenant-1', userId: 'leah', requestId: opts.requestId });
      return { status: 'answered' };
    });
    await assistant.askAssistant(LEAH, ['leah', 'bob'], { question: 'Who renews soon?' });
    expect(seen).toEqual({ tenantId: 'tenant-1', accessIds: ['leah', 'bob'] });

    // The same request id after the answer, or for another user, gets no team access.
    const requestId = (aiCall as jest.Mock).mock.calls[0][3].requestId;
    (getModAuthPerson as jest.Mock).mockResolvedValue({ tenantId: 'tenant-1', role: 'TEAM_LEAD', active: true });
    await expect(assistant.dataScope({ tenantId: 'tenant-1', userId: 'leah', requestId })).rejects.toMatchObject({ statusCode: 403 });
  });

  it('without a live question: a broker reads their own book; another brokerage is refused', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({ tenantId: 'tenant-1', role: 'BROKER', active: true });
    await expect(assistant.dataScope({ tenantId: 'tenant-1', userId: 'bob' })).resolves.toEqual({ tenantId: 'tenant-1', accessIds: ['bob'] });
    await expect(assistant.dataScope({ tenantId: 'tenant-2', userId: 'bob' })).rejects.toMatchObject({ statusCode: 403 });
  });
});

describe('the records', () => {
  const scope = { tenantId: 'tenant-1', accessIds: ['bob'] };

  it('a client the user may not see is a plain 404', async () => {
    mockPrisma.lead.findFirst.mockResolvedValue(null);
    await expect(assistant.fileSummary(scope, 'lead-x')).rejects.toMatchObject({ statusCode: 404 });
    expect(mockPrisma.lead.findFirst.mock.calls[0][0].where).toMatchObject({ tenantId: 'tenant-1', brokerUserId: { in: ['bob'] } });
  });

  it('"tax documents" means the T4, the Notice of Assessment and the property tax bill', () => {
    expect(assistant.documentTypesMatching('tax documents').sort()).toEqual(['NOA', 'PROPERTY_TAX', 'T4']);
    expect(assistant.documentTypesMatching('pay stubs')).toEqual(['PAY_STUB']);
    expect(assistant.documentTypesMatching('NOA')).toEqual(['NOA']);
  });

  it('finds clients by name ignoring case and punctuation', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([
      { id: 'l1', name: "Sean O'Brien" },
      { id: 'l2', name: 'Maria Gonzalez' },
    ]);
    const found = await assistant.searchClients(scope, 'obrien', 10);
    expect(found.records.map((r) => r.id)).toEqual(['l1']);
    expect(found.total).toBe(1);
    const named = await assistant.searchClients(scope, "Summarize Maria Gonzalez's", 10);
    expect(named.records.map((r) => r.id)).toEqual(['l2']);
  });
});

describe('dashboard "AI suggestions"', () => {
  const scope = { tenantId: 'tenant-1', accessIds: ['bob'] };
  const daysAgo = (n: number) => new Date(Date.now() - n * 86_400_000);

  it('follow-up candidates: overdue tasks or no contact for 30 days, longest silence first', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([
      { id: 'fresh', name: 'Fresh', createdAt: daysAgo(2), appointments: [], followUps: [], tasks: [] },
      { id: 'quiet', name: 'Quiet', createdAt: daysAgo(90), appointments: [{ startTime: daysAgo(40) }], followUps: [], tasks: [] },
      { id: 'late', name: 'Late', createdAt: daysAgo(5), appointments: [], followUps: [], tasks: [{ id: 't1' }] },
    ]);
    const { records } = await assistant.recommendationCandidates(scope, 'follow_up', 10);
    expect(records.map((r) => r.id)).toEqual(['quiet', 'late']);
    expect(records[0]!.fields).toMatchObject({ daysSinceLastContact: 40, overdueTasks: 0 });
  });

  it('"Add as task" makes one real task, tells the assistant which, and gives the same task when asked again', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({ id: 'lead-1', tenantId: 'tenant-1', brokerUserId: 'bob' });
    (aiCall as jest.Mock).mockImplementation(async (_s: string, _m: string, path: string) => {
      if (path.endsWith('/create-task')) return { id: 'sug-1', task: null };
      if (path.endsWith('/decision')) return { id: 'sug-1', task: { title: 'Call Pat', notes: '', dueDate: '2026-11-01', priority: 'high' } };
      if (path === '/v1/recommendations/rec-1') return { clientId: 'lead-1' };
      return {};
    });
    mockPrisma.task.findUnique.mockResolvedValueOnce(null);
    mockPrisma.task.create.mockResolvedValue({ id: 'task-1' });
    await assistant.addRecommendationAsTask(LEAH, ['bob'], 'rec-1', {});
    expect(mockPrisma.task.create).toHaveBeenCalledWith({
      data: expect.objectContaining({ leadId: 'lead-1', brokerUserId: 'bob', title: 'Call Pat', priority: 'HIGH', aiSuggestionId: 'sug-1' }),
    });
    expect(aiCall).toHaveBeenCalledWith('assistant', 'POST', '/v1/task-suggestions/sug-1/handed-off', expect.objectContaining({ json: { crmTaskId: 'task-1' } }));

    mockPrisma.task.findUnique.mockResolvedValueOnce({ id: 'task-1' });
    await expect(assistant.addRecommendationAsTask(LEAH, ['bob'], 'rec-1', {})).resolves.toEqual({ id: 'task-1' });
    expect(mockPrisma.task.create).toHaveBeenCalledTimes(1);
  });
});
