import { getLeadWithAccess } from './lead.service';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';

const MAX_APPLICANTS = 4;

export interface ApplicantInput {
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  address?: string;
  dateOfBirth?: string;
  brokerNotes?: string;
}

/** SOW 5.1/5.3 — "one to four applicants," enforced here since the schema itself doesn't cap it. */
export async function addApplicant(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  input: ApplicantInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const existing = await getPrismaClient().applicant.count({ where: { leadId: lead.id } });
  if (existing >= MAX_APPLICANTS) {
    throw new ApiError(`A lead can have at most ${MAX_APPLICANTS} applicants`, 400);
  }
  const { dateOfBirth, ...rest } = input;
  return getPrismaClient().applicant.create({
    data: {
      leadId: lead.id,
      ...rest,
      dateOfBirth: dateOfBirth ? new Date(dateOfBirth) : undefined,
    },
  });
}

async function requireApplicant(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  applicantId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const applicant = await getPrismaClient().applicant.findUnique({ where: { id: applicantId } });
  if (!applicant || applicant.leadId !== lead.id) {
    throw new ApiError('Applicant not found', 404);
  }
  return applicant;
}

export async function updateApplicant(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  applicantId: string,
  input: Partial<ApplicantInput>,
) {
  const applicant = await requireApplicant(tenantId, leadId, allowedBrokerIds, applicantId);
  const { dateOfBirth, ...rest } = input;
  return getPrismaClient().applicant.update({
    where: { id: applicant.id },
    data: { ...rest, dateOfBirth: dateOfBirth ? new Date(dateOfBirth) : undefined },
  });
}

export async function removeApplicant(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  applicantId: string,
) {
  const applicant = await requireApplicant(tenantId, leadId, allowedBrokerIds, applicantId);
  await getPrismaClient().applicant.delete({ where: { id: applicant.id } });
}

export interface EmploymentInput {
  type: 'EMPLOYED' | 'SELF_EMPLOYED' | 'CORPORATION';
  employer?: string;
  position?: string;
  startDate?: string;
  baseSalary?: number;
  t4Values?: Record<string, number>;
}

/** SOW 5.4/5.5/5.6 — multiple jobs per applicant, so this always adds rather than replaces. */
export async function addEmployment(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  applicantId: string,
  input: EmploymentInput,
) {
  const applicant = await requireApplicant(tenantId, leadId, allowedBrokerIds, applicantId);
  const { startDate, ...rest } = input;
  return getPrismaClient().employment.create({
    data: {
      applicantId: applicant.id,
      ...rest,
      startDate: startDate ? new Date(startDate) : undefined,
      t4Values: (input.t4Values ?? undefined) as never,
    },
  });
}

export interface NoaIncomeInput {
  taxYear: number;
  totalIncome: number;
}

/** SOW 5.7 — "2 years per applicant" is a form-level rule, not a DB cap; one row per tax year (unique). */
export async function addNoaIncome(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  applicantId: string,
  input: NoaIncomeInput,
) {
  const applicant = await requireApplicant(tenantId, leadId, allowedBrokerIds, applicantId);
  try {
    return await getPrismaClient().noaIncome.create({
      data: { applicantId: applicant.id, ...input },
    });
  } catch {
    throw new ApiError(`NOA income for tax year ${input.taxYear} is already on file`, 409);
  }
}

export interface DebtInput {
  description?: string;
  balance: number;
  monthlyPayment: number;
}

export async function addDebt(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  input: DebtInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  return getPrismaClient().debt.create({ data: { leadId: lead.id, ...input } });
}

async function requireDebt(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  debtId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const debt = await getPrismaClient().debt.findUnique({ where: { id: debtId } });
  if (!debt || debt.leadId !== lead.id) {
    throw new ApiError('Debt not found', 404);
  }
  return debt;
}

export async function updateDebt(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  debtId: string,
  input: Partial<DebtInput>,
) {
  const debt = await requireDebt(tenantId, leadId, allowedBrokerIds, debtId);
  return getPrismaClient().debt.update({ where: { id: debt.id }, data: input });
}

export async function removeDebt(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  debtId: string,
) {
  const debt = await requireDebt(tenantId, leadId, allowedBrokerIds, debtId);
  await getPrismaClient().debt.delete({ where: { id: debt.id } });
}

export interface PropertyRecordInput {
  address: string;
  propertyTaxes?: number;
  isCondo?: boolean;
  isRental?: boolean;
  mortgageBalance?: number;
  mortgageLender?: string;
  maturityDate?: string;
  ownership?: string;
}

/** SOW 5.9 — properties already owned, distinct from Lead.propertyAddress (the one being purchased). */
export async function addPropertyRecord(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  input: PropertyRecordInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const { maturityDate, ...rest } = input;
  return getPrismaClient().propertyRecord.create({
    data: {
      leadId: lead.id,
      ...rest,
      maturityDate: maturityDate ? new Date(maturityDate) : undefined,
    },
  });
}

async function requireProperty(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  propertyId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const property = await getPrismaClient().propertyRecord.findUnique({ where: { id: propertyId } });
  if (!property || property.leadId !== lead.id) {
    throw new ApiError('Property not found', 404);
  }
  return property;
}

export async function updatePropertyRecord(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  propertyId: string,
  input: Partial<PropertyRecordInput>,
) {
  const property = await requireProperty(tenantId, leadId, allowedBrokerIds, propertyId);
  const { maturityDate, ...rest } = input;
  return getPrismaClient().propertyRecord.update({
    where: { id: property.id },
    data: { ...rest, maturityDate: maturityDate ? new Date(maturityDate) : undefined },
  });
}

export async function removePropertyRecord(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  propertyId: string,
) {
  const property = await requireProperty(tenantId, leadId, allowedBrokerIds, propertyId);
  await getPrismaClient().propertyRecord.delete({ where: { id: property.id } });
}

export interface FundsSourceInput {
  accountType: string;
  amount: number;
  isGift?: boolean;
  giftSource?: string;
}

/** SOW 5.10 — repeatable down-payment funds-source rows. */
export async function addFundsSource(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  input: FundsSourceInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  return getPrismaClient().fundsSource.create({ data: { leadId: lead.id, ...input } });
}

export async function removeFundsSource(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  fundsSourceId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const row = await getPrismaClient().fundsSource.findUnique({ where: { id: fundsSourceId } });
  if (!row || row.leadId !== lead.id) {
    throw new ApiError('Funds source not found', 404);
  }
  await getPrismaClient().fundsSource.delete({ where: { id: row.id } });
}

export interface FollowUpInput {
  type: string;
  scheduledAt: string;
}

/** SOW 5.11 — a scheduled commitment from the intake itself, distinct from a generic Task. */
export async function addFollowUp(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  input: FollowUpInput,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  return getPrismaClient().followUp.create({
    data: { leadId: lead.id, type: input.type, scheduledAt: new Date(input.scheduledAt) },
  });
}

export async function removeFollowUp(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
  followUpId: string,
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const row = await getPrismaClient().followUp.findUnique({ where: { id: followUpId } });
  if (!row || row.leadId !== lead.id) {
    throw new ApiError('Follow-up not found', 404);
  }
  await getPrismaClient().followUp.delete({ where: { id: row.id } });
}

/** The full structured-intake bundle for one lead — feeds the Person Panel's Profile tab and the affordability calculator. */
export async function getIntakeBundle(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
) {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const [applicants, debts, properties, fundsSources, followUps] = await Promise.all([
    getPrismaClient().applicant.findMany({
      where: { leadId: lead.id },
      include: { employments: true, noaIncomes: true },
      orderBy: { createdAt: 'asc' },
    }),
    getPrismaClient().debt.findMany({ where: { leadId: lead.id }, orderBy: { createdAt: 'asc' } }),
    getPrismaClient().propertyRecord.findMany({
      where: { leadId: lead.id },
      orderBy: { createdAt: 'asc' },
    }),
    getPrismaClient().fundsSource.findMany({
      where: { leadId: lead.id },
      orderBy: { createdAt: 'asc' },
    }),
    getPrismaClient().followUp.findMany({
      where: { leadId: lead.id },
      orderBy: { scheduledAt: 'asc' },
    }),
  ]);
  return { applicants, debts, properties, fundsSources, followUps };
}
