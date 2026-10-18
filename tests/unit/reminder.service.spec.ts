jest.mock('../../config/database');
jest.mock('../../modules/crm/google/calendar.service');

import { Prisma } from '@prisma/client';

import { getPrismaClient } from '../../config/database';
import { pushAppointment } from '../../modules/crm/google/calendar.service';
import { runReminderSweep } from '../../modules/crm/reminder.service';

const sent = new Set<string>();
const mockPrisma = {
  reminderLog: {
    create: jest.fn(({ data }: { data: { key: string } }) => {
      if (sent.has(data.key)) {
        throw new Prisma.PrismaClientKnownRequestError('dup', {
          code: 'P2002',
          clientVersion: 'x',
        });
      }
      sent.add(data.key);
      return data;
    }),
  },
  mortgage: { findMany: jest.fn() },
  $queryRaw: jest.fn(),
  followUp: { findMany: jest.fn() },
  appointment: { findMany: jest.fn() },
  task: { findMany: jest.fn() },
  notification: { create: jest.fn() },
};

const NOW = new Date('2026-10-12T14:00:00.000Z');
const lead = { tenantId: 't1', brokerUserId: 'b1', name: 'Pat' };

beforeEach(() => {
  jest.clearAllMocks();
  sent.clear();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.mortgage.findMany.mockResolvedValue([]);
  mockPrisma.$queryRaw.mockResolvedValue([]);
  mockPrisma.followUp.findMany.mockResolvedValue([]);
  mockPrisma.appointment.findMany.mockResolvedValue([]);
  mockPrisma.task.findMany.mockResolvedValue([]);
});

function titles() {
  return mockPrisma.notification.create.mock.calls.map((c) => c[0].data.title as string);
}

it('renewal reminders: one per threshold window, never repeated by the next sweep', async () => {
  // Only the 1-month window returns this mortgage.
  mockPrisma.mortgage.findMany.mockImplementation(({ where }) =>
    where.maturityDate.gt.getTime() === NOW.getTime()
      ? [{ id: 'm1', leadId: 'l1', lender: 'RBC', maturityDate: new Date('2026-10-30'), lead }]
      : [],
  );
  await runReminderSweep(NOW);
  await runReminderSweep(NOW);
  expect(titles()).toEqual(["Pat's RBC mortgage renews in under 1 month"]);
});

it('appointments: the 1h reminder for something starting within the hour, not the 24h one', async () => {
  mockPrisma.appointment.findMany.mockImplementation(({ where }) =>
    where.startTime?.gt?.getTime() === NOW.getTime()
      ? [
          {
            id: 'a1',
            tenantId: 't1',
            brokerUserId: 'b1',
            title: 'Call',
            startTime: NOW,
            leadId: null,
          },
        ]
      : [],
  );
  await runReminderSweep(NOW);
  expect(titles()).toEqual(['Call — in under an hour']);
});

it("9am digest: once per broker per day, only in the 9 o'clock hour", async () => {
  mockPrisma.task.findMany.mockResolvedValue([{ tenantId: 't1', brokerUserId: 'b1' }]);
  await runReminderSweep(NOW); // 14:00 — no digest
  expect(titles()).toEqual([]);

  const nine = new Date('2026-10-12T09:05:00.000Z');
  await runReminderSweep(nine);
  await runReminderSweep(nine);
  expect(titles()).toEqual(['Your day']);
});

it('calendar retry: after the last failed attempt, "Reconnect your calendar" once', async () => {
  mockPrisma.appointment.findMany.mockImplementation(({ where }) =>
    where.googleEventId === null
      ? [
          {
            id: 'a1',
            tenantId: 't1',
            brokerUserId: 'b1',
            title: 'Call',
            pushAttempts: 4,
            leadId: null,
          },
        ]
      : [],
  );
  (pushAppointment as jest.Mock).mockResolvedValue(null);
  await runReminderSweep(NOW);
  expect(pushAppointment).toHaveBeenCalled();
  expect(titles()).toEqual(['Reconnect your calendar']);
});

