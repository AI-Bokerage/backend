jest.mock('../../config/database');
jest.mock('../../modules/crm/google/google-connection.service');

const mockEventsInsert = jest.fn();
jest.mock('googleapis', () => ({
  google: { calendar: jest.fn(() => ({ events: { insert: mockEventsInsert } })) },
}));

import { getPrismaClient } from '../../config/database';
import {
  createAppointment,
  listForBrokers,
  listMyAppointments,
  requireOwnAppointment,
} from '../../modules/crm/google/calendar.service';
import { getAuthenticatedClientOrNull } from '../../modules/crm/google/google-connection.service';

const mockPrisma = {
  appointment: { create: jest.fn(), update: jest.fn(), findMany: jest.fn(), findUnique: jest.fn() },
};

const TENANT = 'tenant-1';
const input = {
  title: 'Call with client',
  startTime: '2026-11-01T10:00:00.000Z',
  endTime: '2026-11-01T10:30:00.000Z',
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('createAppointment — "Google Calendar connected?"', () => {
  it('stays in the CRM only when not connected', async () => {
    mockPrisma.appointment.create.mockResolvedValue({ id: 'appt-1', ...input });
    (getAuthenticatedClientOrNull as jest.Mock).mockResolvedValue(null);

    const result = await createAppointment(TENANT, 'broker-1', input);

    expect(result.pushedToGoogle).toBe(false);
    expect(mockEventsInsert).not.toHaveBeenCalled();
  });

  it('is pushed across when connected', async () => {
    mockPrisma.appointment.create.mockResolvedValue({
      id: 'appt-1',
      startTime: new Date(input.startTime),
      endTime: new Date(input.endTime),
    });
    mockPrisma.appointment.update.mockResolvedValue({ id: 'appt-1', googleEventId: 'g-event-1' });
    (getAuthenticatedClientOrNull as jest.Mock).mockResolvedValue({
      client: 'fake-client',
      googleEmail: 'b@gmail.com',
    });
    mockEventsInsert.mockResolvedValue({ data: { id: 'g-event-1' } });

    const result = await createAppointment(TENANT, 'broker-1', input);

    expect(result.pushedToGoogle).toBe(true);
    expect(mockPrisma.appointment.update).toHaveBeenCalledWith({
      where: { id: 'appt-1' },
      data: { googleEventId: 'g-event-1' },
    });
  });

  it('a Google API failure falls back to CRM-only rather than failing the request', async () => {
    mockPrisma.appointment.create.mockResolvedValue({
      id: 'appt-1',
      startTime: new Date(input.startTime),
      endTime: new Date(input.endTime),
    });
    (getAuthenticatedClientOrNull as jest.Mock).mockResolvedValue({
      client: 'fake-client',
      googleEmail: 'b@gmail.com',
    });
    mockEventsInsert.mockRejectedValue(new Error('Google API down'));

    const result = await createAppointment(TENANT, 'broker-1', input);

    expect(result.pushedToGoogle).toBe(false);
    // Counted so the hourly sweep retries it (Flow 4.8 "queued and retried quietly").
    expect(mockPrisma.appointment.update).toHaveBeenCalledWith({
      where: { id: 'appt-1' },
      data: { pushAttempts: { increment: 1 } },
    });
  });
});

describe('listForBrokers — "Team calendar"', () => {
  it('returns nothing without hitting the DB when the team has no brokers', async () => {
    const result = await listForBrokers(TENANT, []);
    expect(result).toEqual([]);
    expect(mockPrisma.appointment.findMany).not.toHaveBeenCalled();
  });

  it('scopes to every broker on the team', async () => {
    mockPrisma.appointment.findMany.mockResolvedValue([{ id: 'a1' }]);
    await listForBrokers(TENANT, ['b1', 'b2']);
    expect(mockPrisma.appointment.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tenantId: TENANT, brokerUserId: { in: ['b1', 'b2'] } } }),
    );
  });
});

describe('listMyAppointments', () => {
  it('scopes to the caller', async () => {
    mockPrisma.appointment.findMany.mockResolvedValue([]);
    await listMyAppointments(TENANT, 'broker-1');
    expect(mockPrisma.appointment.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tenantId: TENANT, brokerUserId: 'broker-1' } }),
    );
  });
});

describe('requireOwnAppointment', () => {
  it('404s outside the tenant', async () => {
    mockPrisma.appointment.findUnique.mockResolvedValue({
      id: 'a1',
      tenantId: 'other-tenant',
      brokerUserId: 'broker-1',
    });
    await expect(requireOwnAppointment(TENANT, 'broker-1', 'a1')).rejects.toMatchObject({
      statusCode: 404,
    });
  });

  it("403s someone else's appointment in the same tenant", async () => {
    mockPrisma.appointment.findUnique.mockResolvedValue({
      id: 'a1',
      tenantId: TENANT,
      brokerUserId: 'someone-else',
    });
    await expect(requireOwnAppointment(TENANT, 'broker-1', 'a1')).rejects.toMatchObject({
      statusCode: 403,
    });
  });
});

describe('Flow 4.8 — appointment type and the "today\'s meetings" range', () => {
  it('stores the chosen type', async () => {
    mockPrisma.appointment.create.mockResolvedValue({ id: 'a1' });
    (getAuthenticatedClientOrNull as jest.Mock).mockResolvedValue(null);
    await createAppointment(TENANT, 'broker-1', {
      title: 'Go through documents',
      startTime: '2026-11-01T15:00:00.000Z',
      endTime: '2026-11-01T15:30:00.000Z',
      type: 'DOCUMENT_REVIEW',
    });
    expect(mockPrisma.appointment.create.mock.calls[0][0].data.type).toBe('DOCUMENT_REVIEW');
  });

  it('narrows the calendar to a from/to window', async () => {
    mockPrisma.appointment.findMany.mockResolvedValue([]);
    await listMyAppointments(TENANT, 'broker-1', {
      from: '2026-11-01T00:00:00.000Z',
      to: '2026-11-02T00:00:00.000Z',
    });
    expect(mockPrisma.appointment.findMany.mock.calls[0][0].where.startTime).toEqual({
      gte: new Date('2026-11-01T00:00:00.000Z'),
      lt: new Date('2026-11-02T00:00:00.000Z'),
    });
  });
});
