jest.mock('../../config/database');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/crm/ai.client');

import { getPrismaClient } from '../../config/database';
import * as notes from '../../modules/crm/ai-notes.service';
import { aiCall } from '../../modules/crm/ai.client';

const CALLER = { tenantId: 'tenant-1', userId: 'me', role: 'BROKER' as const };
const mockPrisma = {
  lead: { findUnique: jest.fn(), create: jest.fn(), update: jest.fn() },
  task: { createMany: jest.fn(), findMany: jest.fn() },
};

const value = (target: string, v: string | number, extra: Record<string, unknown> = {}) => ({
  key: target,
  label: target,
  instance: 0,
  target,
  value: v,
  display: String(v),
  replaceExisting: false,
  ...extra,
});
const approved = (values: ReturnType<typeof value>[], extra: Record<string, unknown> = {}) => ({
  values,
  tasks: [],
  emailDraft: null,
  aiLabel: 'AI-assisted processing',
  ...extra,
});

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('planApply — 4.2 "Would it overwrite something already filled in?"', () => {
  it('fills blank fields, leaves filled ones alone unless the broker chose to replace them', () => {
    const plan = notes.planApply(
      approved([
        value('contact.email', 'new@example.com'),
        value('income.annualAmount', '95000'),
        value('request.downPayment', 60000, { replaceExisting: true }),
      ]),
      { email: 'kept@example.com', downPayment: 10000 },
    );
    expect(plan.lead).toEqual({ income: 95000, downPayment: 60000 });
    expect(plan.skipped).toEqual(['contact.email']);
  });

  it('turns a date of birth into a Date and a second property into "not applied"', () => {
    const plan = notes.planApply(
      approved([
        value('applicant.dateOfBirth', '1985-03-02'),
        value('properties[].value', 500000, { instance: 1 }),
        value('profile.summary', 'First-time buyer'),
      ]),
      {},
    );
    expect(plan.lead.dateOfBirth).toEqual(new Date('1985-03-02'));
    expect(plan.notApplied.map((n) => n.label)).toEqual(['properties[].value', 'profile.summary']);
  });

  it('builds the main applicant and their job when the notes named them', () => {
    const plan = notes.planApply(
      approved([
        value('applicant.firstName', 'Pat'),
        value('applicant.lastName', 'Lee'),
        value('employment.employerName', 'Acme'),
        value('employment.type', 'self_employed'),
      ]),
      {},
    );
    expect(plan.applicant).toEqual({
      firstName: 'Pat',
      lastName: 'Lee',
      employments: { create: { type: 'SELF_EMPLOYED', employer: 'Acme', position: undefined } },
    });
  });

  it("uses the person's own name for the applicant when the AI suggested a job but no name", () => {
    const plan = notes.planApply(approved([value('employment.employerName', 'Shopify')]), {
      name: 'Maria de la Cruz',
    });
    expect(plan.applicant).toMatchObject({ firstName: 'Maria', lastName: 'de la Cruz' });
    expect(plan.applied).toEqual(['employment.employerName']);
  });

  it('creates no applicant when no person details were approved', () => {
    expect(notes.planApply(approved([value('income.annualAmount', 1)]), { name: 'Pat Lee' }).applicant).toBeNull();
  });

  it('maps approved tasks and the approved email draft', () => {
    const plan = notes.planApply(
      approved([], {
        tasks: [{ suggestionId: 's1', task: { title: 'Call Pat', notes: '', dueDate: '2026-11-01', priority: 'urgent' } }],
        emailDraft: { email: { subject: 'Documents', body: 'Hi Pat' } },
      }),
      {},
    );
    expect(plan.tasks).toEqual([{ aiSuggestionId: 's1', title: 'Call Pat', dueDate: new Date('2026-11-01'), priority: 'HIGH' }]);
    expect(plan.email).toEqual({ subject: 'Documents', body: 'Hi Pat' });
  });
});

describe('who may see what', () => {
  it("a voice note is its uploader's only", async () => {
    (aiCall as jest.Mock).mockResolvedValue({ id: 'vn-1', userId: 'someone-else' });
    await expect(notes.getVoiceNote(CALLER, 'vn-1')).rejects.toMatchObject({ statusCode: 404 });
  });

  it("a review with no person yet is its author's only", async () => {
    (aiCall as jest.Mock).mockResolvedValue({ id: 'r1', userId: 'someone-else', clientId: null });
    await expect(notes.getRun(CALLER, 'r1', ['me'])).rejects.toMatchObject({ statusCode: 404 });
  });

  it("a review of a person follows that person's access", async () => {
    (aiCall as jest.Mock).mockResolvedValue({ id: 'r1', userId: 'someone-else', clientId: 'lead-1' });
    mockPrisma.lead.findUnique.mockResolvedValue({ id: 'lead-1', tenantId: 'tenant-1', brokerUserId: 'me' });
    await expect(notes.getRun(CALLER, 'r1', ['me'])).resolves.toMatchObject({ id: 'r1' });
  });
});

describe('createLeadFromReview — 4.2 "Save"', () => {
  it('links the review to the new person before finalizing, then saves lead + approved items together', async () => {
    const calls: string[] = [];
    (aiCall as jest.Mock).mockImplementation(async (_s: string, method: string, path: string) => {
      calls.push(`${method} ${path}`);
      if (method === 'GET') return { id: 'r1', userId: 'me', clientId: null };
      if (path.endsWith('/finalize')) {
        return approved([value('income.annualAmount', 90000)], {
          tasks: [{ suggestionId: 's1', task: { title: 'Call', notes: '', dueDate: null, priority: 'low' } }],
        });
      }
      return {};
    });
    mockPrisma.lead.create.mockImplementation(async ({ data }) => ({ ...data, sin: null, bankDetails: null }));

    const result = await notes.createLeadFromReview(CALLER, ['me'], { name: 'Pat', aiRunId: 'r1' });

    expect(calls).toEqual(['GET /v1/runs/r1', 'PUT /v1/runs/r1/client', 'POST /v1/runs/r1/finalize']);
    const data = mockPrisma.lead.create.mock.calls[0][0].data;
    const linkedTo = (aiCall as jest.Mock).mock.calls[1][3].json.clientId;
    expect(data.id).toBe(linkedTo);
    expect(data.income).toBe(90000);
    expect(data.tasks.create[0]).toMatchObject({ title: 'Call', priority: 'LOW', brokerUserId: 'me' });
    expect(result.applied).toEqual(['income.annualAmount']);
  });

  it('refuses a review that already belongs to a person', async () => {
    (aiCall as jest.Mock).mockResolvedValue({ id: 'r1', userId: 'me', clientId: 'lead-9' });
    mockPrisma.lead.findUnique.mockResolvedValue({ id: 'lead-9', tenantId: 'tenant-1', brokerUserId: 'me' });
    await expect(
      notes.createLeadFromReview(CALLER, ['me'], { name: 'Pat', aiRunId: 'r1' }),
    ).rejects.toMatchObject({ statusCode: 409 });
    expect(mockPrisma.lead.create).not.toHaveBeenCalled();
  });
});

describe('suggestTasks — 4.9 "Suggested by AI → Shown as Suggested"', () => {
  const lead = { id: 'lead-1', tenantId: 'tenant-1', brokerUserId: 'teammate', name: 'Pat Lee', notes: 'Call Pat Friday' };
  // POST /v1/runs answers without the suggestions; only GET /v1/runs/{id} carries them.
  const run = (status: string, tasks: unknown[] = []) => async (_s: string, method: string, path: string) =>
    path === '/v1/notes' ? { id: 'n1' } : { id: 'r1', status, ...(method === 'GET' ? { tasks } : {}) };

  it('while the notes are being read, says so and creates nothing', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(lead);
    (aiCall as jest.Mock).mockImplementation(run('processing'));
    await expect(notes.suggestTasks(CALLER, 'lead-1', ['teammate'])).resolves.toMatchObject({ ready: false, runId: 'r1' });
    expect(mockPrisma.task.createMany).not.toHaveBeenCalled();
  });

  it("adds each proposed task once, as Suggested, for the person's own broker", async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(lead);
    mockPrisma.task.findMany.mockResolvedValue([{ id: 't1' }]);
    (aiCall as jest.Mock).mockImplementation(
      run('processed', [
        { id: 's1', title: 'Call Pat', dueDate: '2026-11-01', priority: 'high', decision: 'pending' },
        { id: 's2', title: null, dueDate: null, priority: null, decision: 'rejected' },
      ]),
    );
    const result = await notes.suggestTasks(CALLER, 'lead-1', ['teammate']);
    expect(mockPrisma.task.createMany).toHaveBeenCalledWith({
      data: [
        expect.objectContaining({ aiSuggestionId: 's1', brokerUserId: 'teammate', suggested: true, priority: 'HIGH' }),
      ],
      skipDuplicates: true,
    });
    expect(result).toMatchObject({ ready: true, tasks: [{ id: 't1' }] });
  });

  it('needs notes to read', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({ ...lead, notes: null });
    await expect(notes.suggestTasks(CALLER, 'lead-1', ['teammate'])).rejects.toMatchObject({ statusCode: 400 });
  });
});
