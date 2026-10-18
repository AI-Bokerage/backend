jest.mock('../../config/database');
jest.mock('../../modules/crm/ai.client');

import { getPrismaClient } from '../../config/database';
import * as email from '../../modules/crm/ai-email.service';
import { aiCall } from '../../modules/crm/ai.client';

const CALLER = { tenantId: 'tenant-1', userId: 'me', role: 'BROKER' as const, name: 'Leah Lead' };
const LEAD = {
  id: 'lead-1',
  tenantId: 'tenant-1',
  brokerUserId: 'me',
  name: 'Maria Gonzalez',
  email: 'maria@example.com',
  requiredDocuments: ['T4', 'PAY_STUB', 'ID'],
};
const mockPrisma = {
  lead: { findUnique: jest.fn() },
  tenantProfile: { findUnique: jest.fn() },
  mortgage: { findFirst: jest.fn() },
  leadDocument: { findMany: jest.fn() },
  task: { findMany: jest.fn() },
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.lead.findUnique.mockResolvedValue(LEAD);
  mockPrisma.tenantProfile.findUnique.mockResolvedValue({ displayName: 'Acme Mortgages', phone: '+1 416 555 0100' });
  (aiCall as jest.Mock).mockResolvedValue({ id: 'd1', status: 'queued' });
});

const sent = () => (aiCall as jest.Mock).mock.calls[0][3].json;

describe('startEmailDraft — 4.7 "Pick the purpose"', () => {
  it('a renewal carries the soonest-maturing mortgage as facts, the broker as sender', async () => {
    mockPrisma.mortgage.findFirst.mockResolvedValue({
      lender: 'TD Bank',
      maturityDate: new Date('2027-03-01T00:00:00Z'),
      interestRate: 4.79,
      balance: 570000,
    });
    await email.startEmailDraft(CALLER, 'lead-1', ['me'], { intent: 'RENEWAL' });
    expect(sent()).toEqual({
      clientId: 'lead-1',
      emailType: 'renewal',
      recipient: { name: 'Maria Gonzalez', email: 'maria@example.com' },
      sender: { name: 'Leah Lead', brokerage: 'Acme Mortgages', phone: '+1 416 555 0100' },
      context: {
        firstName: 'Maria',
        lender: 'TD Bank',
        maturityDate: '2027-03-01',
        currentRate: '4.79%',
        balance: '$570,000',
      },
    });
  });

  it('a document request lists only what is ticked and still missing, in plain words', async () => {
    mockPrisma.leadDocument.findMany.mockResolvedValue([{ type: 'T4' }]);
    await email.startEmailDraft(CALLER, 'lead-1', ['me'], { intent: 'DOCUMENTS', tone: 'warm' });
    expect(sent()).toMatchObject({
      emailType: 'document_request',
      context: { firstName: 'Maria', missingDocuments: ['Recent pay stub', 'Government photo ID'] },
      options: { tone: 'warm' },
    });
  });

  it('a follow-up carries the purpose and the open tasks', async () => {
    mockPrisma.task.findMany.mockResolvedValue([{ title: 'Call about pre-approval' }]);
    await email.startEmailDraft(CALLER, 'lead-1', ['me'], { intent: 'FOLLOW_UP', purpose: 'Check in after our call' });
    expect(sent().context).toEqual({
      firstName: 'Maria',
      purpose: 'Check in after our call',
      openTasks: ['Call about pre-approval'],
    });
  });

  it("needs the person's email, and never calls the AI without it", async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({ ...LEAD, email: null });
    await expect(email.startEmailDraft(CALLER, 'lead-1', ['me'], { intent: 'BIRTHDAY' })).rejects.toMatchObject({
      statusCode: 400,
    });
    expect(aiCall).not.toHaveBeenCalled();
  });
});

describe('a draft follows its person\'s access', () => {
  it("is 404 for someone who can't see the person", async () => {
    (aiCall as jest.Mock).mockResolvedValue({ id: 'd1', clientId: 'lead-1', status: 'ready' });
    mockPrisma.lead.findUnique.mockResolvedValue({ ...LEAD, brokerUserId: 'someone-else' });
    await expect(email.getEmailDraft(CALLER, 'd1', ['me'])).rejects.toMatchObject({ statusCode: 404 });
  });
});
