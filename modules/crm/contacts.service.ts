import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';
import { recordActivity } from '../platform/activity-log';

export type ContactCategory =
  | 'REALTOR'
  | 'LAWYER'
  | 'APPRAISER'
  | 'INSURANCE'
  | 'ACCOUNTANT'
  | 'HOME_INSPECTOR'
  | 'FINANCIAL_ADVISOR';

const CATEGORIES: ContactCategory[] = [
  'REALTOR',
  'LAWYER',
  'APPRAISER',
  'INSURANCE',
  'ACCOUNTANT',
  'HOME_INSPECTOR',
  'FINANCIAL_ADVISOR',
];

export interface ContactInput {
  category: ContactCategory;
  name: string;
  company?: string;
  phone?: string;
  email?: string;
  dateOfBirth?: string;
  address?: string;
  city?: string;
  lastContactedAt?: string;
  notes?: string;
}

function toData<T extends Partial<ContactInput>>(input: T) {
  const { dateOfBirth, lastContactedAt, ...rest } = input;
  return {
    ...rest,
    dateOfBirth: dateOfBirth ? new Date(dateOfBirth) : undefined,
    lastContactedAt: lastContactedAt ? new Date(lastContactedAt) : undefined,
  };
}

/** SOW 14.1-14.2 — the broker's own referral network, exactly these 7 categories. */
export async function createContact(tenantId: string, brokerUserId: string, input: ContactInput) {
  return getPrismaClient().contact.create({ data: { tenantId, brokerUserId, ...toData(input) } });
}

export interface ContactFilter {
  search?: string;
  category?: ContactCategory;
}

/** SOW 14.3/14.4 — search by name/company/email/phone, optionally narrowed to one category. */
export async function listContacts(
  tenantId: string,
  brokerUserId: string,
  filter: ContactFilter = {},
) {
  return getPrismaClient().contact.findMany({
    where: {
      tenantId,
      brokerUserId,
      ...(filter.category ? { category: filter.category } : {}),
      ...(filter.search
        ? {
            OR: [
              { name: { contains: filter.search, mode: 'insensitive' } },
              { company: { contains: filter.search, mode: 'insensitive' } },
              { email: { contains: filter.search, mode: 'insensitive' } },
              { phone: { contains: filter.search, mode: 'insensitive' } },
            ],
          }
        : {}),
    },
    orderBy: { name: 'asc' },
  });
}

/** SOW 14.4 — "filter by category with counts," all 7 shown even at 0. */
export async function categoryCounts(
  tenantId: string,
  brokerUserId: string,
): Promise<Record<ContactCategory, number>> {
  const base = Object.fromEntries(CATEGORIES.map((c) => [c, 0])) as Record<ContactCategory, number>;
  const grouped = await getPrismaClient().contact.groupBy({
    by: ['category'],
    where: { tenantId, brokerUserId },
    _count: true,
  });
  for (const row of grouped) base[row.category as ContactCategory] = row._count;
  return base;
}

/** Same 404-vs-403 split as every other owned record in this module. */
async function requireContact(tenantId: string, contactId: string, allowedBrokerIds: string[]) {
  const contact = await getPrismaClient().contact.findUnique({ where: { id: contactId } });
  if (!contact || contact.tenantId !== tenantId) {
    throw new ApiError('Contact not found', 404);
  }
  if (!allowedBrokerIds.includes(contact.brokerUserId)) {
    throw new ApiError("You don't have access to this record", 403);
  }
  return contact;
}

export async function updateContact(
  tenantId: string,
  contactId: string,
  allowedBrokerIds: string[],
  input: Partial<ContactInput>,
) {
  const contact = await requireContact(tenantId, contactId, allowedBrokerIds);
  return getPrismaClient().contact.update({ where: { id: contact.id }, data: toData(input) });
}

/** SOW 14.7 — "permanent delete with confirmation and audit event." Confirmation is a frontend concern; this records the audit event. */
export async function deleteContact(
  tenantId: string,
  contactId: string,
  allowedBrokerIds: string[],
  actorId: string,
) {
  const contact = await requireContact(tenantId, contactId, allowedBrokerIds);
  await getPrismaClient().contact.delete({ where: { id: contact.id } });
  await recordActivity('crm.contact.deleted', {
    actorId,
    targetType: 'contact',
    targetId: contact.id,
    metadata: { name: contact.name, category: contact.category },
  });
}

/** "Team" contacts — every contact belonging to any broker on the team (mirrors lead.service.ts's listForBrokers). */
export async function listForBrokers(tenantId: string, brokerIds: string[]) {
  if (brokerIds.length === 0) return [];
  return getPrismaClient().contact.findMany({
    where: { tenantId, brokerUserId: { in: brokerIds } },
    orderBy: { name: 'asc' },
  });
}
