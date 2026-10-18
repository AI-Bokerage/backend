jest.mock('../../modules/platform/activity-log');
jest.mock('../../config/database');
jest.mock('../../modules/crm/document-storage');
jest.mock('../../modules/crm/ai-deletion.service');

import { getPrismaClient } from '../../config/database';
import { deleteClientsAiData } from '../../modules/crm/ai-deletion.service';
import { removeStoredFiles } from '../../modules/crm/document-storage';
import { decryptToken, encryptToken } from '../../modules/crm/encryption';
import {
  deleteLead,
  reassignAll,
  activeAssignedLeadCount,
  addMortgage,
  createLead,
  fundLead,
  getLeadWithAccess,
  getPersonPanel,
  listForBrokers,
  listMyClients,
  listMyLeads,
  maskLead,
  openLeadCountByBroker,
  pipelineCounts,
  reassign,
  revealSensitiveField,
  updateLeadProfile,
  updateNotes,
  updateStage,
  updateTags,
} from '../../modules/crm/lead.service';
import { listActivity, recordActivity } from '../../modules/platform/activity-log';

const mockPrisma = {
  leadDocument: { findMany: jest.fn().mockResolvedValue([]) },
  notification: { create: jest.fn() },
  lead: {
    create: jest.fn(),
    findUnique: jest.fn(),
    findMany: jest.fn(),
    update: jest.fn(),
    groupBy: jest.fn(),
    count: jest.fn(),
    updateMany: jest.fn(),
    delete: jest.fn(),
  },
  task: { updateMany: jest.fn() },
  mortgage: {
    create: jest.fn(),
    findMany: jest.fn(),
  },
  $transaction: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

const TENANT = 'tenant-1';

describe('getLeadWithAccess', () => {
  it('404s a lead from another tenant', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: 'other-tenant',
      brokerUserId: 'b1',
    });
    await expect(getLeadWithAccess(TENANT, 'lead-1', ['b1'])).rejects.toMatchObject({
      statusCode: 404,
    });
  });

  it('403s a lead outside the allowed broker set — "You don\'t have access to this record"', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'someone-else',
    });
    await expect(getLeadWithAccess(TENANT, 'lead-1', ['b1'])).rejects.toMatchObject({
      statusCode: 403,
      message: "You don't have access to this record",
    });
  });

  it('allows a lead whose broker is in the allowed set', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'b1',
    });
    const lead = await getLeadWithAccess(TENANT, 'lead-1', ['b1', 'b2']);
    expect(lead.id).toBe('lead-1');
  });
});

describe('updateStage — "Change the stage to Lost"', () => {
  it('requires a reason when moving to LOST', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'b1',
    });
    await expect(updateStage(TENANT, 'lead-1', ['b1'], 'LOST')).rejects.toMatchObject({
      statusCode: 400,
    });
    expect(mockPrisma.lead.update).not.toHaveBeenCalled();
  });

  it('clears any stale lostReason when moving off LOST', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'b1',
    });
    mockPrisma.lead.update.mockResolvedValue({});
    await updateStage(TENANT, 'lead-1', ['b1'], 'QUALIFIED');
    expect(mockPrisma.lead.update).toHaveBeenCalledWith({
      where: { id: 'lead-1' },
      data: { stage: 'QUALIFIED', lostReason: null, lostNote: null },
    });
  });
});

describe('reassign — "Reassign to another broker"', () => {
  it('reassigns when the target broker is inside the team, and records it', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'b1',
    });
    mockPrisma.lead.update.mockResolvedValue({ id: 'lead-1', brokerUserId: 'b2' });

    const result = await reassign(TENANT, 'lead-1', ['b1', 'b2'], 'b2', 'team-lead-1');

    expect(result.brokerUserId).toBe('b2');
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.lead.reassigned',
      expect.objectContaining({ actorId: 'team-lead-1', targetId: 'lead-1' }),
    );
  });

  it('refuses a target broker outside the team — "Not selectable"', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'b1',
    });

    await expect(
      reassign(TENANT, 'lead-1', ['b1', 'b2'], 'outsider', 'team-lead-1'),
    ).rejects.toMatchObject({
      statusCode: 400,
    });
    expect(mockPrisma.lead.update).not.toHaveBeenCalled();
    expect(recordActivity).not.toHaveBeenCalled();
  });

  it('refuses to reassign a lead the caller cannot access in the first place', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'outside-the-team',
    });

    await expect(
      reassign(TENANT, 'lead-1', ['b1', 'b2'], 'b2', 'team-lead-1'),
    ).rejects.toMatchObject({
      statusCode: 403,
    });
  });
});

describe('pipelineCounts — "Team pipeline"', () => {
  it('returns every stage at 0 when the team has no leads', async () => {
    const counts = await pipelineCounts(TENANT, []);
    expect(counts).toEqual({
      NEW: 0,
      QUALIFIED: 0,
      APPLICATION: 0,
      UNDERWRITING: 0,
      APPROVED: 0,
      FUNDED: 0,
      LOST: 0,
    });
    expect(mockPrisma.lead.groupBy).not.toHaveBeenCalled();
  });

  it('fills in real counts per stage, leaving untouched stages at 0', async () => {
    mockPrisma.lead.groupBy.mockResolvedValue([
      { stage: 'NEW', _count: 3 },
      { stage: 'FUNDED', _count: 1 },
    ]);
    const counts = await pipelineCounts(TENANT, ['b1', 'b2']);
    expect(counts.NEW).toBe(3);
    expect(counts.FUNDED).toBe(1);
    expect(counts.LOST).toBe(0);
  });
});

describe('openLeadCountByBroker — workload view input', () => {
  it('shows a broker with nothing assigned as 0, not omitted', async () => {
    mockPrisma.lead.groupBy.mockResolvedValue([{ brokerUserId: 'b1', _count: 2 }]);
    const counts = await openLeadCountByBroker(TENANT, ['b1', 'b2']);
    expect(counts).toEqual({ b1: 2, b2: 0 });
  });
});

describe('listForBrokers — "Team book"', () => {
  it('returns nothing without hitting the DB when the team has no brokers', async () => {
    const leads = await listForBrokers(TENANT, []);
    expect(leads).toEqual([]);
    expect(mockPrisma.lead.findMany).not.toHaveBeenCalled();
  });
});

describe('activeAssignedLeadCount — "Do they still have clients assigned?" (SOW 2.11)', () => {
  it('counts everything except Lost', async () => {
    mockPrisma.lead.count.mockResolvedValue(14);

    const count = await activeAssignedLeadCount(TENANT, 'broker-1');

    expect(count).toBe(14);
    expect(mockPrisma.lead.count).toHaveBeenCalledWith({
      where: { tenantId: TENANT, brokerUserId: 'broker-1', stage: { not: 'LOST' } },
    });
  });
});

describe('createLead — "New client"', () => {
  it('always creates the lead against the caller, never an arbitrary broker', async () => {
    mockPrisma.lead.create.mockResolvedValue({ id: 'lead-1' });
    await createLead(TENANT, 'me', { name: 'Jamie Rivera', email: 'jamie@example.com' });
    expect(mockPrisma.lead.create).toHaveBeenCalledWith({
      data: {
        tenantId: TENANT,
        brokerUserId: 'me',
        name: 'Jamie Rivera',
        email: 'jamie@example.com',
        phone: undefined,
      },
    });
  });
});

describe('listMyLeads — "Leads (not funded yet)"', () => {
  const whereOf = () => mockPrisma.lead.findMany.mock.calls[0][0].where;

  it("scopes to the caller's own tenant and broker id, excluding Funded/Lost", async () => {
    mockPrisma.lead.findMany.mockResolvedValue([]);
    await listMyLeads(TENANT, ['me']);
    expect(whereOf()).toEqual({
      tenantId: TENANT,
      brokerUserId: { in: ['me'] },
      AND: [{ stage: { notIn: ['FUNDED', 'LOST'] } }],
    });
  });
});

describe('Flow 4 dashboard cards → filtered lists', () => {
  const whereOf = () => mockPrisma.lead.findMany.mock.calls[0][0].where;
  const today = new Date();
  const dob = (inDays: number) => {
    const d = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()));
    d.setUTCDate(d.getUTCDate() + inDays);
    d.setUTCFullYear(1985);
    return d;
  };

  it('stage=X narrows to that stage; ALL lifts the default; hot means Application→Approved', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([]);
    await listMyLeads(TENANT, ['me'], { stages: ['UNDERWRITING'] });
    expect(whereOf().AND).toEqual([{ stage: { in: ['UNDERWRITING'] } }]);

    mockPrisma.lead.findMany.mockClear();
    await listMyLeads(TENANT, ['me'], { stages: ['ALL'], hot: true });
    expect(whereOf().AND).toEqual([{ stage: { in: ['APPLICATION', 'UNDERWRITING', 'APPROVED'] } }]);
  });

  it('renewals and follow-ups become relation filters', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([]);
    await listMyClients(TENANT, ['me'], { renewalWithinDays: 180, followUpWithinDays: 7 });
    const and = whereOf().AND;
    expect(and[0]).toEqual({ stage: 'FUNDED' });
    expect(and[1].mortgages.some.maturityDate.lte.getTime()).toBeGreaterThan(
      Date.now() + 179 * 86400000,
    );
    expect(and[2].followUps.some.scheduledAt.lt).toBeInstanceOf(Date);
    expect(mockPrisma.lead.findMany.mock.calls[0][0].include).toEqual({
      mortgages: true,
      documents: false,
    });
  });

  it('birthdays within N days, and only files still missing a ticked document', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([
      {
        id: 'soon',
        dateOfBirth: dob(3),
        requiredDocuments: ['T4'],
        documents: [],
        sin: null,
        bankDetails: null,
      },
      {
        id: 'later',
        dateOfBirth: dob(20),
        requiredDocuments: ['T4'],
        documents: [],
        sin: null,
        bankDetails: null,
      },
      {
        id: 'complete',
        dateOfBirth: dob(1),
        requiredDocuments: ['T4'],
        documents: [{ type: 'T4' }],
        sin: null,
        bankDetails: null,
      },
    ]);
    const result = await listMyLeads(TENANT, ['me'], {
      stages: ['ALL'],
      birthdayWithinDays: 7,
      missingDocuments: true,
    });
    expect(result.map((l) => l.id)).toEqual(['soon']);
    // Only fetched for the check — never sent to the client.
    expect(JSON.parse(JSON.stringify(result[0]))).not.toHaveProperty('documents');
  });
});

describe('listMyClients — "Clients (funded)"', () => {
  it('scopes to Funded leads only and includes their mortgages', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([]);
    await listMyClients(TENANT, ['me']);
    expect(mockPrisma.lead.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { tenantId: TENANT, brokerUserId: { in: ['me'] }, AND: [{ stage: 'FUNDED' }] },
        include: { mortgages: true, documents: false },
      }),
    );
  });
});

describe('fundLead — "Change the stage to Funded"', () => {
  it('creates the mortgage and moves the stage to FUNDED in one transaction', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
    });
    mockPrisma.$transaction.mockResolvedValue([
      { id: 'mortgage-1' },
      { id: 'lead-1', stage: 'FUNDED' },
    ]);

    const result = await fundLead(TENANT, 'lead-1', ['me'], {
      lender: 'Acme Bank',
      interestRate: 4.5,
      balance: 300000,
      monthlyPayment: 1500,
      maturityDate: '2031-01-01T00:00:00.000Z',
    });

    expect(result.stage).toBe('FUNDED');
    expect(mockPrisma.$transaction).toHaveBeenCalled();
  });

  it('blocks a direct FUNDED transition through updateStage', async () => {
    await expect(updateStage(TENANT, 'lead-1', ['me'], 'FUNDED')).rejects.toMatchObject({
      statusCode: 400,
    });
    expect(mockPrisma.lead.findUnique).not.toHaveBeenCalled();
  });
});

describe('addMortgage', () => {
  it('rejects adding a mortgage to a lead that is not yet Funded', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      stage: 'NEW',
    });

    await expect(
      addMortgage(TENANT, 'lead-1', ['me'], {
        lender: 'Acme Bank',
        interestRate: 4.5,
        balance: 300000,
        monthlyPayment: 1500,
        maturityDate: '2031-01-01T00:00:00.000Z',
      }),
    ).rejects.toMatchObject({ statusCode: 400 });
  });

  it('allows a second mortgage on an already-funded client', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      stage: 'FUNDED',
    });
    mockPrisma.mortgage.create.mockResolvedValue({ id: 'mortgage-2' });

    const result = await addMortgage(TENANT, 'lead-1', ['me'], {
      lender: 'Second Bank',
      interestRate: 5,
      balance: 100000,
      monthlyPayment: 600,
      maturityDate: '2029-01-01T00:00:00.000Z',
    });

    expect(result.id).toBe('mortgage-2');
  });
});

describe('createLead — sensitive fields', () => {
  it('encrypts sin and bankDetails before storing, and parses dateOfBirth', async () => {
    mockPrisma.lead.create.mockResolvedValue({ id: 'lead-1' });
    await createLead(TENANT, 'me', {
      name: 'Jamie Rivera',
      dateOfBirth: '1990-01-01T00:00:00.000Z',
      sin: '123-456-789',
      bankDetails: 'acct-999',
    });

    const data = mockPrisma.lead.create.mock.calls[0][0].data;
    expect(data.dateOfBirth).toEqual(new Date('1990-01-01T00:00:00.000Z'));
    expect(data.sin).not.toBe('123-456-789');
    expect(decryptToken(data.sin)).toBe('123-456-789');
    expect(decryptToken(data.bankDetails)).toBe('acct-999');
  });
});

describe('maskLead — "hidden values"', () => {
  it('strips ciphertext and exposes only whether a value is on file', () => {
    const masked = maskLead({
      id: 'lead-1',
      sin: encryptToken('123-456-789'),
      bankDetails: null,
    });
    expect(masked).not.toHaveProperty('sin');
    expect(masked).not.toHaveProperty('bankDetails');
    expect(masked.hasSin).toBe(true);
    expect(masked.hasBankDetails).toBe(false);
  });
});

describe('updateNotes — "Your notes - editable"', () => {
  it('updates brokerNotes and records the edit with before/after on the activity log', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      brokerNotes: 'old note',
      sin: null,
      bankDetails: null,
    });
    mockPrisma.lead.update.mockResolvedValue({
      id: 'lead-1',
      brokerNotes: 'new note',
      sin: null,
      bankDetails: null,
    });

    const result = await updateNotes(TENANT, 'lead-1', ['me'], 'new note', 'me');

    expect(mockPrisma.lead.update).toHaveBeenCalledWith({
      where: { id: 'lead-1' },
      data: { brokerNotes: 'new note' },
    });
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.notes.updated',
      expect.objectContaining({
        actorId: 'me',
        targetId: 'lead-1',
        metadata: { previous: 'old note', next: 'new note' },
      }),
    );
    expect(result).not.toHaveProperty('sin');
  });
});

describe('updateTags', () => {
  it('replaces the tag list on the lead', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      sin: null,
      bankDetails: null,
    });
    mockPrisma.lead.update.mockResolvedValue({
      id: 'lead-1',
      tags: ['hot'],
      sin: null,
      bankDetails: null,
    });

    const result = await updateTags(TENANT, 'lead-1', ['me'], ['hot']);

    expect(mockPrisma.lead.update).toHaveBeenCalledWith({
      where: { id: 'lead-1' },
      data: { tags: ['hot'] },
    });
    expect(result.tags).toEqual(['hot']);
  });
});

describe('updateLeadProfile — "Inline profile edit with Save and Cancel"', () => {
  it('updates the header/profile fields and masks the sensitive ones on return', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      sin: null,
      bankDetails: null,
    });
    mockPrisma.lead.update.mockResolvedValue({
      id: 'lead-1',
      city: 'Toronto',
      sin: null,
      bankDetails: null,
    });

    const result = await updateLeadProfile(TENANT, 'lead-1', ['me'], { city: 'Toronto' });

    expect(mockPrisma.lead.update).toHaveBeenCalledWith({
      where: { id: 'lead-1' },
      data: { city: 'Toronto' },
    });
    expect(result.city).toBe('Toronto');
    expect(result).not.toHaveProperty('sin');
  });

  it("403s a teammate's lead, same ownership check as everything else", async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'someone-else',
    });

    await expect(
      updateLeadProfile(TENANT, 'lead-1', ['me'], { city: 'Toronto' }),
    ).rejects.toMatchObject({ statusCode: 403 });
    expect(mockPrisma.lead.update).not.toHaveBeenCalled();
  });
});

describe('revealSensitiveField — "Reveal... the reveal is recorded"', () => {
  it('decrypts sin and records the reveal on the activity log', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      sin: encryptToken('123-456-789'),
      bankDetails: null,
    });

    const result = await revealSensitiveField(TENANT, 'lead-1', ['me'], 'sin', 'me');

    expect(result).toEqual({ field: 'sin', value: '123-456-789' });
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.lead.sensitive_field_revealed',
      expect.objectContaining({ actorId: 'me', targetId: 'lead-1', metadata: { field: 'sin' } }),
    );
  });

  it('returns null, not an error, when no value is on file', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      sin: null,
      bankDetails: null,
    });

    const result = await revealSensitiveField(TENANT, 'lead-1', ['me'], 'sin', 'me');
    expect(result).toEqual({ field: 'sin', value: null });
  });

  it('403s a reveal attempt outside the allowed broker set, same as any other access', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'someone-else',
      sin: encryptToken('123-456-789'),
    });

    await expect(revealSensitiveField(TENANT, 'lead-1', ['me'], 'sin', 'me')).rejects.toMatchObject(
      {
        statusCode: 403,
      },
    );
    expect(recordActivity).not.toHaveBeenCalled();
  });
});

describe('getPersonPanel — "same panel everywhere"', () => {
  it('aggregates profile, mortgages, notes (original/yours/history) and recent activity', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      requiredDocuments: [],
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      notes: 'source record',
      brokerNotes: 'broker note',
      sin: encryptToken('123-456-789'),
      bankDetails: null,
    });
    mockPrisma.mortgage.findMany.mockResolvedValue([{ id: 'mortgage-1' }]);
    (listActivity as jest.Mock)
      .mockResolvedValueOnce({ items: [{ action: 'crm.notes.updated' }], total: 1 })
      .mockResolvedValueOnce({ items: [{ action: 'crm.lead.reassigned' }], total: 1 });

    const panel = await getPersonPanel(TENANT, 'lead-1', ['me']);

    expect(panel.profile).not.toHaveProperty('sin');
    expect(panel.profile.hasSin).toBe(true);
    expect(panel.mortgages).toEqual([{ id: 'mortgage-1' }]);
    expect(panel.notes).toEqual({
      original: 'source record',
      yours: 'broker note',
      history: [{ action: 'crm.notes.updated' }],
    });
    expect(panel.activity).toEqual([{ action: 'crm.lead.reassigned' }]);
  });
});

describe('deleteLead — Flow 4.11 "type their name to confirm"', () => {
  beforeEach(() => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT,
      brokerUserId: 'me',
      name: 'Pat Client',
    });
    mockPrisma.leadDocument.findMany.mockResolvedValue([{ storagePath: 'tenant-1/key' }]);
  });

  it('refuses unless the typed name matches', async () => {
    await expect(deleteLead(TENANT, 'lead-1', ['me'], 'Pat', 'me')).rejects.toMatchObject({
      statusCode: 400,
    });
    expect(mockPrisma.$transaction).not.toHaveBeenCalled();
  });

  it('deletes permanently, leaves tasks unlinked, removes stored files, and records it', async () => {
    await deleteLead(TENANT, 'lead-1', ['me'], ' Pat Client ', 'me');
    expect(mockPrisma.task.updateMany).toHaveBeenCalledWith({
      where: { leadId: 'lead-1' },
      data: { unlinked: true },
    });
    expect(mockPrisma.lead.delete).toHaveBeenCalledWith({ where: { id: 'lead-1' } });
    expect(mockPrisma.$transaction).toHaveBeenCalled();
    expect(removeStoredFiles).toHaveBeenCalledWith(['tenant-1/key']);
    // Their copies in the AI services go too.
    expect(deleteClientsAiData).toHaveBeenCalledWith(TENANT, ['lead-1']);
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.lead.deleted',
      expect.objectContaining({ targetId: 'lead-1' }),
    );
  });
});

describe('reassignAll — Flow 2 "reassign their clients first"', () => {
  it('moves every lead and notifies both brokers', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([
      { id: 'l1', name: 'A' },
      { id: 'l2', name: 'B' },
    ]);
    const result = await reassignAll(TENANT, 'leaving', 'receiving', 'admin');
    expect(result).toEqual({ reassigned: 2 });
    expect(mockPrisma.lead.updateMany).toHaveBeenCalledWith({
      where: { tenantId: TENANT, brokerUserId: 'leaving' },
      data: { brokerUserId: 'receiving' },
    });
    const notified = mockPrisma.notification.create.mock.calls.map((c) => c[0].data.userId);
    expect(notified.sort()).toEqual(['leaving', 'receiving']);
  });

  it("won't reassign to the same person", async () => {
    await expect(reassignAll(TENANT, 'x', 'x', 'admin')).rejects.toMatchObject({ statusCode: 400 });
  });
});
