jest.mock('../../config/database');
jest.mock('../../modules/platform/activity-log');

import { getPrismaClient } from '../../config/database';
import * as contacts from '../../modules/crm/contacts.service';
import { recordActivity } from '../../modules/platform/activity-log';

const mockPrisma = {
  contact: {
    create: jest.fn(),
    findMany: jest.fn(),
    groupBy: jest.fn(),
    findUnique: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
};

const TENANT = 'tenant-1';

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('createContact', () => {
  it('creates a contact scoped to the caller', async () => {
    mockPrisma.contact.create.mockResolvedValue({ id: 'c1' });
    await contacts.createContact(TENANT, 'me', { category: 'REALTOR', name: 'Jamie Realtor' });
    expect(mockPrisma.contact.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          tenantId: TENANT,
          brokerUserId: 'me',
          category: 'REALTOR',
        }),
      }),
    );
  });
});

describe('listContacts — "search by name, company, email, or phone"', () => {
  it('builds an OR search clause across all four fields', async () => {
    mockPrisma.contact.findMany.mockResolvedValue([]);
    await contacts.listContacts(TENANT, 'me', { search: 'jamie' });
    const where = mockPrisma.contact.findMany.mock.calls[0][0].where;
    expect(where.OR).toHaveLength(4);
    expect(where.OR[0]).toEqual({ name: { contains: 'jamie', mode: 'insensitive' } });
  });

  it('filters by category when given', async () => {
    mockPrisma.contact.findMany.mockResolvedValue([]);
    await contacts.listContacts(TENANT, 'me', { category: 'LAWYER' });
    expect(mockPrisma.contact.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: expect.objectContaining({ category: 'LAWYER' }) }),
    );
  });
});

describe('categoryCounts — "filter by category with counts"', () => {
  it('shows every one of the 7 categories, zero-filled when empty', async () => {
    mockPrisma.contact.groupBy.mockResolvedValue([{ category: 'REALTOR', _count: 3 }]);
    const counts = await contacts.categoryCounts(TENANT, 'me');
    expect(counts.REALTOR).toBe(3);
    expect(counts.LAWYER).toBe(0);
    expect(Object.keys(counts)).toHaveLength(7);
  });
});

describe('ownership boundary — 404 vs 403', () => {
  it('404s a contact from another tenant', async () => {
    mockPrisma.contact.findUnique.mockResolvedValue({
      id: 'c1',
      tenantId: 'other-tenant',
      brokerUserId: 'me',
    });
    await expect(contacts.updateContact(TENANT, 'c1', ['me'], { name: 'X' })).rejects.toMatchObject(
      {
        statusCode: 404,
      },
    );
  });

  it("403s a teammate's contact", async () => {
    mockPrisma.contact.findUnique.mockResolvedValue({
      id: 'c1',
      tenantId: TENANT,
      brokerUserId: 'someone-else',
    });
    await expect(contacts.updateContact(TENANT, 'c1', ['me'], { name: 'X' })).rejects.toMatchObject(
      {
        statusCode: 403,
      },
    );
  });
});

describe('deleteContact — "permanent delete with confirmation and audit event"', () => {
  it('deletes and records the audit event', async () => {
    mockPrisma.contact.findUnique.mockResolvedValue({
      id: 'c1',
      tenantId: TENANT,
      brokerUserId: 'me',
      name: 'Jamie Realtor',
      category: 'REALTOR',
    });

    await contacts.deleteContact(TENANT, 'c1', ['me'], 'me');

    expect(mockPrisma.contact.delete).toHaveBeenCalledWith({ where: { id: 'c1' } });
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.contact.deleted',
      expect.objectContaining({ actorId: 'me', targetId: 'c1' }),
    );
  });
});

describe('listForBrokers — "Team contacts"', () => {
  it('returns nothing without hitting the DB when the team has no brokers', async () => {
    const result = await contacts.listForBrokers(TENANT, []);
    expect(result).toEqual([]);
    expect(mockPrisma.contact.findMany).not.toHaveBeenCalled();
  });
});
