import { z } from 'zod';

const CATEGORIES = [
  'REALTOR',
  'LAWYER',
  'APPRAISER',
  'INSURANCE',
  'ACCOUNTANT',
  'HOME_INSPECTOR',
  'FINANCIAL_ADVISOR',
] as const;

const contactInput = z.object({
  category: z.enum(CATEGORIES),
  name: z.string().min(1).max(255),
  company: z.string().max(255).optional(),
  phone: z.string().max(50).optional(),
  email: z.string().email().optional(),
  dateOfBirth: z.string().datetime().optional(),
  address: z.string().max(500).optional(),
  city: z.string().max(255).optional(),
  lastContactedAt: z.string().datetime().optional(),
  notes: z.string().max(5000).optional(),
});

export const createContactSchema = z.object({
  body: contactInput,
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const listContactsSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({
    search: z.string().max(255).optional(),
    category: z.enum(CATEGORIES).optional(),
  }),
  params: z.object({}).optional(),
});

export const updateContactSchema = z.object({
  body: contactInput.partial(),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const contactIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});
