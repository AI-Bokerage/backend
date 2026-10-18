import { z } from 'zod';

const leadIdParams = z.object({ id: z.string().uuid() });

export const applicantInputSchema = z.object({
  firstName: z.string().min(1).max(255),
  lastName: z.string().min(1).max(255),
  email: z.string().email().optional(),
  phone: z.string().max(50).optional(),
  address: z.string().max(500).optional(),
  dateOfBirth: z.string().datetime().optional(),
  brokerNotes: z.string().max(5000).optional(),
});

export const addApplicantSchema = z.object({
  body: applicantInputSchema,
  query: z.object({}).optional(),
  params: leadIdParams,
});

export const updateApplicantSchema = z.object({
  body: applicantInputSchema.partial(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ applicantId: z.string().uuid() }),
});

export const applicantIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ applicantId: z.string().uuid() }),
});

export const addEmploymentSchema = z.object({
  body: z.object({
    type: z.enum(['EMPLOYED', 'SELF_EMPLOYED', 'CORPORATION']),
    employer: z.string().max(255).optional(),
    position: z.string().max(255).optional(),
    startDate: z.string().datetime().optional(),
    baseSalary: z.coerce.number().nonnegative().optional(),
    t4Values: z.record(z.string(), z.coerce.number()).optional(),
  }),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ applicantId: z.string().uuid() }),
});

export const addNoaIncomeSchema = z.object({
  body: z.object({
    taxYear: z.coerce.number().int().min(2000).max(2100),
    totalIncome: z.coerce.number().nonnegative(),
  }),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ applicantId: z.string().uuid() }),
});

const debtInput = z.object({
  description: z.string().max(255).optional(),
  balance: z.coerce.number().nonnegative(),
  monthlyPayment: z.coerce.number().nonnegative(),
});

export const addDebtSchema = z.object({
  body: debtInput,
  query: z.object({}).optional(),
  params: leadIdParams,
});

export const updateDebtSchema = z.object({
  body: debtInput.partial(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ debtId: z.string().uuid() }),
});

export const debtIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ debtId: z.string().uuid() }),
});

const propertyRecordInput = z.object({
  address: z.string().min(1).max(500),
  propertyTaxes: z.coerce.number().nonnegative().optional(),
  isCondo: z.boolean().optional(),
  isRental: z.boolean().optional(),
  mortgageBalance: z.coerce.number().nonnegative().optional(),
  mortgageLender: z.string().max(255).optional(),
  maturityDate: z.string().datetime().optional(),
  ownership: z.string().max(100).optional(),
});

export const addPropertyRecordSchema = z.object({
  body: propertyRecordInput,
  query: z.object({}).optional(),
  params: leadIdParams,
});

export const updatePropertyRecordSchema = z.object({
  body: propertyRecordInput.partial(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ propertyId: z.string().uuid() }),
});

export const propertyIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ propertyId: z.string().uuid() }),
});

export const addFundsSourceSchema = z.object({
  body: z.object({
    accountType: z.string().min(1).max(100),
    amount: z.coerce.number().nonnegative(),
    isGift: z.boolean().optional(),
    giftSource: z.string().max(255).optional(),
  }),
  query: z.object({}).optional(),
  params: leadIdParams,
});

export const fundsSourceIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ fundsSourceId: z.string().uuid() }),
});

export const addFollowUpSchema = z.object({
  body: z.object({
    type: z.string().min(1).max(100),
    scheduledAt: z.string().datetime(),
  }),
  query: z.object({}).optional(),
  params: leadIdParams,
});

export const followUpIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: leadIdParams.extend({ followUpId: z.string().uuid() }),
});

export const intakeBundleParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: leadIdParams,
});
