jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import {
  listClientsSchema,
  listLeadsSchema,
  listTasksSchema,
} from '../../modules/crm/crm.validation';
import { brokerDashboard } from '../../modules/crm/dashboard.service';
import { listAppointmentsSchema } from '../../modules/crm/google/google.validation';

const empty = jest.fn().mockResolvedValue([]);
const mockPrisma = {
  lead: { findMany: empty, groupBy: empty },
  mortgage: {
    aggregate: jest.fn().mockResolvedValue({ _sum: { balance: null }, _count: 0 }),
    findMany: empty,
  },
  appointment: { findMany: empty },
  followUp: { findMany: empty },
  task: { count: jest.fn().mockResolvedValue(0) },
};

beforeEach(() => (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma));

describe('Flow 4 — "every card is clickable and lands on a filtered list"', () => {
  it('gives every card a link whose query the target list accepts', async () => {
    const { links } = await brokerDashboard('t1', ['me']);
    const schemaFor: Record<string, { parse: (v: unknown) => unknown }> = {
      '/crm/leads': listLeadsSchema,
      '/crm/clients': listClientsSchema,
      '/crm/tasks': listTasksSchema,
      '/crm/appointments': listAppointmentsSchema,
    };
    const all = [
      ...Object.entries(links)
        .filter(([k]) => k !== 'pipeline')
        .map(([, v]) => v as string),
      ...Object.values(links.pipeline),
    ];
    expect(all.length).toBeGreaterThanOrEqual(16);
    for (const link of all) {
      const url = new URL(link, 'http://x');
      const schema = schemaFor[url.pathname];
      expect(schema).toBeDefined();
      // Throws (fails the test) if a card links to a filter its list would reject.
      schema!.parse({ query: Object.fromEntries(url.searchParams) });
    }
  });
});
