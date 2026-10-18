jest.mock('../../config/database');
jest.mock('../../modules/crm/ai.client');

import { getPrismaClient } from '../../config/database';
import { smartSearch, syncSearchIndex } from '../../modules/crm/ai-search.service';
import { aiCall } from '../../modules/crm/ai.client';

const lead = {
  id: 'lead-1',
  tenantId: 'tenant-1',
  brokerUserId: 'bob',
  name: 'Pat Lee',
  email: 'pat@example.com',
  phone: null,
  stage: 'NEW',
  fileStatus: 'ACTIVE',
  city: 'Toronto',
  tags: [],
  propertyAddress: null,
  lostReason: null,
  lostNote: null,
  notes: 'Wants a cottage near the lake.',
  brokerNotes: null,
  mortgages: [],
  documents: [],
  tasks: [],
  searchHash: null as string | null,
};
const mockPrisma = { lead: { findMany: jest.fn(), update: jest.fn() } };

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('syncSearchIndex — only changed people are sent', () => {
  it('sends a new person with their owner as access and their details to mask, then skips them while unchanged', async () => {
    mockPrisma.lead.findMany.mockResolvedValueOnce([lead]);
    await expect(syncSearchIndex()).resolves.toBe(1);
    const [, method, path, opts] = (aiCall as jest.Mock).mock.calls[0];
    expect([method, path]).toEqual(['PUT', '/v1/records/client_profile/lead-1']);
    expect(opts.json).toMatchObject({
      clientId: 'lead-1',
      access: { ownerUserId: 'bob', assignedUserId: 'bob' },
      knownEntities: [{ type: 'PERSON', value: 'Pat Lee' }, { type: 'EMAIL', value: 'pat@example.com' }],
    });
    expect(opts.json.content).toContain('cottage near the lake');
    const hash = mockPrisma.lead.update.mock.calls[0][0].data.searchHash;

    (aiCall as jest.Mock).mockClear();
    mockPrisma.lead.findMany.mockResolvedValueOnce([{ ...lead, searchHash: hash }]);
    await expect(syncSearchIndex()).resolves.toBe(0);
    expect(aiCall).not.toHaveBeenCalled();
  });
});

describe('smartSearch — only people the user may see', () => {
  it('asks for records assigned to the access list, and drops hits the CRM no longer lets them see', async () => {
    (aiCall as jest.Mock).mockResolvedValue({
      aiLabel: 'AI-assisted processing',
      groups: [{ results: [{ recordId: 'lead-1', clientId: 'lead-1', score: 0.6 }, { recordId: 'gone', clientId: 'gone', score: 0.5 }] }],
    });
    mockPrisma.lead.findMany.mockResolvedValue([{ id: 'lead-1', name: 'Pat Lee', stage: 'NEW', city: null }]);
    const found = await smartSearch({ tenantId: 'tenant-1', userId: 'bob' }, ['bob'], 'cottage');
    expect((aiCall as jest.Mock).mock.calls[0][3].json.accessFilter).toEqual({
      recordTypes: ['client_profile'],
      scope: { type: 'assigned', userIds: ['bob'] },
    });
    expect(found.results).toEqual([{ leadId: 'lead-1', name: 'Pat Lee', stage: 'NEW', city: null, score: 0.6 }]);
  });

  it('drops weak matches far below the best one', async () => {
    (aiCall as jest.Mock).mockResolvedValue({
      aiLabel: 'AI-assisted processing',
      groups: [{ results: [{ recordId: 'a', clientId: 'a', score: 0.42 }, { recordId: 'b', clientId: 'b', score: 0.25 }] }],
    });
    mockPrisma.lead.findMany.mockResolvedValue([{ id: 'a', name: 'A', stage: 'NEW', city: null }]);
    await smartSearch({ tenantId: 'tenant-1', userId: 'bob' }, ['bob'], 'renewal');
    expect(mockPrisma.lead.findMany.mock.calls[0][0].where.id).toEqual({ in: ['a'] });
  });
});
