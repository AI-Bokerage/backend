jest.mock('../../config/database');
jest.mock('../../modules/crm/lead.service', () => ({
  ...jest.requireActual('../../modules/crm/lead.service'),
  getLeadWithAccess: jest.fn(),
}));

import { getPrismaClient } from '../../config/database';
import * as intake from '../../modules/crm/intake.service';
import { getLeadWithAccess } from '../../modules/crm/lead.service';

const mockPrisma = {
  applicant: {
    count: jest.fn(),
    create: jest.fn(),
    findUnique: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    findMany: jest.fn(),
  },
  employment: { create: jest.fn() },
  noaIncome: { create: jest.fn() },
  debt: {
    create: jest.fn(),
    findUnique: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    findMany: jest.fn(),
  },
  propertyRecord: {
    create: jest.fn(),
    findUnique: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
    findMany: jest.fn(),
  },
  fundsSource: { create: jest.fn(), findUnique: jest.fn(), delete: jest.fn(), findMany: jest.fn() },
  followUp: { create: jest.fn(), findUnique: jest.fn(), delete: jest.fn(), findMany: jest.fn() },
};

const TENANT = 'tenant-1';
const LEAD = { id: 'lead-1', tenantId: TENANT, brokerUserId: 'me' };

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  (getLeadWithAccess as jest.Mock).mockResolvedValue(LEAD);
});

describe('addApplicant — "one to four applicants"', () => {
  it('creates an applicant under the lead', async () => {
    mockPrisma.applicant.count.mockResolvedValue(1);
    mockPrisma.applicant.create.mockResolvedValue({ id: 'app-1' });

    await intake.addApplicant(TENANT, LEAD.id, ['me'], { firstName: 'Jamie', lastName: 'Rivera' });

    expect(mockPrisma.applicant.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ leadId: LEAD.id, firstName: 'Jamie' }),
      }),
    );
  });

  it('refuses a 5th applicant', async () => {
    mockPrisma.applicant.count.mockResolvedValue(4);

    await expect(
      intake.addApplicant(TENANT, LEAD.id, ['me'], { firstName: 'Jamie', lastName: 'Rivera' }),
    ).rejects.toMatchObject({ statusCode: 400 });
    expect(mockPrisma.applicant.create).not.toHaveBeenCalled();
  });
});

describe('removeApplicant — cross-lead access', () => {
  it('404s an applicant belonging to a different lead', async () => {
    mockPrisma.applicant.findUnique.mockResolvedValue({
      id: 'app-1',
      leadId: 'someone-elses-lead',
    });

    await expect(intake.removeApplicant(TENANT, LEAD.id, ['me'], 'app-1')).rejects.toMatchObject({
      statusCode: 404,
    });
    expect(mockPrisma.applicant.delete).not.toHaveBeenCalled();
  });

  it('deletes an applicant that does belong to this lead', async () => {
    mockPrisma.applicant.findUnique.mockResolvedValue({ id: 'app-1', leadId: LEAD.id });

    await intake.removeApplicant(TENANT, LEAD.id, ['me'], 'app-1');

    expect(mockPrisma.applicant.delete).toHaveBeenCalledWith({ where: { id: 'app-1' } });
  });
});

describe('addEmployment / addNoaIncome — nested under an applicant', () => {
  it('adds a job under the applicant, never replacing an existing one', async () => {
    mockPrisma.applicant.findUnique.mockResolvedValue({ id: 'app-1', leadId: LEAD.id });
    mockPrisma.employment.create.mockResolvedValue({ id: 'emp-1' });

    await intake.addEmployment(TENANT, LEAD.id, ['me'], 'app-1', {
      type: 'EMPLOYED',
      baseSalary: 90000,
    });

    expect(mockPrisma.employment.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ applicantId: 'app-1', type: 'EMPLOYED' }),
      }),
    );
  });

  it('rejects a duplicate NOA tax year with 409, not a raw DB error', async () => {
    mockPrisma.applicant.findUnique.mockResolvedValue({ id: 'app-1', leadId: LEAD.id });
    mockPrisma.noaIncome.create.mockRejectedValue(new Error('unique constraint'));

    await expect(
      intake.addNoaIncome(TENANT, LEAD.id, ['me'], 'app-1', { taxYear: 2024, totalIncome: 90000 }),
    ).rejects.toMatchObject({ statusCode: 409 });
  });
});

describe('debts — plain children of the lead', () => {
  it('adds a debt', async () => {
    mockPrisma.debt.create.mockResolvedValue({ id: 'debt-1' });
    await intake.addDebt(TENANT, LEAD.id, ['me'], { balance: 5000, monthlyPayment: 150 });
    expect(mockPrisma.debt.create).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ leadId: LEAD.id, balance: 5000 }),
      }),
    );
  });

  it('404s updating a debt from another lead', async () => {
    mockPrisma.debt.findUnique.mockResolvedValue({ id: 'debt-1', leadId: 'other-lead' });
    await expect(
      intake.updateDebt(TENANT, LEAD.id, ['me'], 'debt-1', { balance: 1 }),
    ).rejects.toMatchObject({ statusCode: 404 });
  });
});

describe('getIntakeBundle', () => {
  it('aggregates every child collection for the lead in one call', async () => {
    mockPrisma.applicant.findMany.mockResolvedValue([{ id: 'app-1' }]);
    mockPrisma.debt.findMany.mockResolvedValue([{ id: 'debt-1' }]);
    mockPrisma.propertyRecord.findMany.mockResolvedValue([]);
    mockPrisma.fundsSource.findMany.mockResolvedValue([]);
    mockPrisma.followUp.findMany.mockResolvedValue([]);

    const bundle = await intake.getIntakeBundle(TENANT, LEAD.id, ['me']);

    expect(bundle.applicants).toEqual([{ id: 'app-1' }]);
    expect(bundle.debts).toEqual([{ id: 'debt-1' }]);
  });
});
