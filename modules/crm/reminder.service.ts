import { Prisma } from '@prisma/client';

import { pushAppointment } from './google/calendar.service';
import { notify } from './notification.service';
import { getPrismaClient } from '../../config/database';

/**
 * Flow 4.6/4.8 "Reminders fire on their own: renewals at 6, 3 and 1 month;
 * birthdays; follow-ups; appointments at 24 hours and 1 hour. One digest
 * arrives each morning at 9." — plus Flow 4.8's "queued and retried quietly
 * → still failing → reconnect your calendar". Run hourly from server.ts.
 *
 * shortcut: one process, setInterval, UTC clock (9am is 09:00 UTC) — move to
 * node-cron with a lock and per-tenant time zones before running more than
 * one app instance (scope.md Phase 5).
 */

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const MAX_PUSH_ATTEMPTS = 5;

/** Each reminder fires once: the ReminderLog insert is the "already sent?" check. */
async function once(key: string, send: () => Promise<unknown>) {
  try {
    await getPrismaClient().reminderLog.create({ data: { key } });
  } catch (error) {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') return;
    throw error;
  }
  await send();
}

function addMonths(date: Date, months: number) {
  const d = new Date(date);
  d.setUTCMonth(d.getUTCMonth() + months);
  return d;
}

function utcDayBounds(now: Date) {
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  return { start, end: new Date(start.getTime() + DAY) };
}

/** Each threshold covers only its own window, so a mortgage first seen 20 days out gets just the 1-month one. */
async function renewalReminders(now: Date) {
  const windows = [
    { months: 6, from: addMonths(now, 3), to: addMonths(now, 6) },
    { months: 3, from: addMonths(now, 1), to: addMonths(now, 3) },
    { months: 1, from: now, to: addMonths(now, 1) },
  ];
  for (const w of windows) {
    const mortgages = await getPrismaClient().mortgage.findMany({
      where: { maturityDate: { gt: w.from, lte: w.to } },
      include: { lead: true },
    });
    for (const m of mortgages) {
      await once(`renewal:${m.id}:${w.months}m`, () =>
        notify({
          tenantId: m.lead.tenantId,
          userId: m.lead.brokerUserId,
          type: 'reminder.renewal',
          title: `${m.lead.name}'s ${m.lender} mortgage renews in under ${w.months} month${w.months > 1 ? 's' : ''}`,
          body: `Matures ${m.maturityDate.toISOString().slice(0, 10)}`,
          leadId: m.leadId,
        }),
      );
    }
  }
}

async function birthdayReminders(now: Date) {
  const leads = await getPrismaClient().$queryRaw<
    { id: string; tenantId: string; brokerUserId: string; name: string }[]
  >(Prisma.sql`
    SELECT "id", "tenantId", "brokerUserId", "name" FROM "Lead"
    WHERE "dateOfBirth" IS NOT NULL
      AND EXTRACT(MONTH FROM "dateOfBirth") = ${now.getUTCMonth() + 1}
      AND EXTRACT(DAY FROM "dateOfBirth") = ${now.getUTCDate()}`);
  for (const l of leads) {
    await once(`birthday:${l.id}:${now.getUTCFullYear()}`, () =>
      notify({
        tenantId: l.tenantId,
        userId: l.brokerUserId,
        type: 'reminder.birthday',
        title: `It's ${l.name}'s birthday today`,
        leadId: l.id,
      }),
    );
  }
}

async function followUpReminders(now: Date) {
  const { start, end } = utcDayBounds(now);
  const due = await getPrismaClient().followUp.findMany({
    where: { scheduledAt: { gte: start, lt: end } },
    include: { lead: true },
  });
  for (const f of due) {
    await once(`followup:${f.id}`, () =>
      notify({
        tenantId: f.lead.tenantId,
        userId: f.lead.brokerUserId,
        type: 'reminder.follow_up',
        title: `Follow up with ${f.lead.name} today (${f.type})`,
        leadId: f.leadId,
      }),
    );
  }
}

async function appointmentReminders(now: Date) {
  const windows = [
    { label: '24h', from: new Date(now.getTime() + HOUR), to: new Date(now.getTime() + DAY) },
    { label: '1h', from: now, to: new Date(now.getTime() + HOUR) },
  ];
  for (const w of windows) {
    const upcoming = await getPrismaClient().appointment.findMany({
      where: { startTime: { gt: w.from, lte: w.to } },
    });
    for (const a of upcoming) {
      await once(`appointment:${a.id}:${w.label}`, () =>
        notify({
          tenantId: a.tenantId,
          userId: a.brokerUserId,
          type: 'reminder.appointment',
          title: `${a.title} — ${w.label === '1h' ? 'in under an hour' : 'tomorrow'}`,
          body: a.startTime.toISOString(),
          leadId: a.leadId ?? undefined,
        }),
      );
    }
  }
}

/** "One digest arrives each morning at 9" — today's meetings, tasks due/overdue, follow-ups. */
async function morningDigest(now: Date) {
  if (now.getUTCHours() !== 9) return;
  const { start, end } = utcDayBounds(now);
  const [meetings, tasks, followUps] = await Promise.all([
    getPrismaClient().appointment.findMany({
      where: { startTime: { gte: start, lt: end } },
      select: { tenantId: true, brokerUserId: true },
    }),
    getPrismaClient().task.findMany({
      where: { completed: false, suggested: false, dueDate: { lt: end } },
      select: { tenantId: true, brokerUserId: true },
    }),
    getPrismaClient().followUp.findMany({
      where: { scheduledAt: { gte: start, lt: end } },
      select: { lead: { select: { tenantId: true, brokerUserId: true } } },
    }),
  ]);
  const perBroker = new Map<
    string,
    { tenantId: string; meetings: number; tasks: number; followUps: number }
  >();
  const bump = (
    row: { tenantId: string; brokerUserId: string },
    key: 'meetings' | 'tasks' | 'followUps',
  ) => {
    const entry = perBroker.get(row.brokerUserId) ?? {
      tenantId: row.tenantId,
      meetings: 0,
      tasks: 0,
      followUps: 0,
    };
    entry[key] += 1;
    perBroker.set(row.brokerUserId, entry);
  };
  meetings.forEach((r) => bump(r, 'meetings'));
  tasks.forEach((r) => bump(r, 'tasks'));
  followUps.forEach((r) => bump(r.lead, 'followUps'));

  const day = start.toISOString().slice(0, 10);
  for (const [userId, c] of perBroker) {
    await once(`digest:${userId}:${day}`, () =>
      notify({
        tenantId: c.tenantId,
        userId,
        type: 'digest.morning',
        title: 'Your day',
        body: `${c.meetings} meetings · ${c.tasks} tasks due or overdue · ${c.followUps} follow-ups`,
      }),
    );
  }
}

/** Flow 4.8 "Queued and retried quietly → Still failing? → Reconnect your calendar". */
async function retryCalendarPushes(now: Date) {
  const pending = await getPrismaClient().appointment.findMany({
    where: {
      googleEventId: null,
      startTime: { gt: now },
      pushAttempts: { gte: 1, lt: MAX_PUSH_ATTEMPTS },
    },
  });
  for (const a of pending) {
    const pushed = await pushAppointment(a);
    if (!pushed && a.pushAttempts + 1 >= MAX_PUSH_ATTEMPTS) {
      await once(`calendar-reconnect:${a.id}`, () =>
        notify({
          tenantId: a.tenantId,
          userId: a.brokerUserId,
          type: 'calendar.reconnect',
          title: 'Reconnect your calendar',
          body: `"${a.title}" couldn't be added to Google Calendar.`,
          leadId: a.leadId ?? undefined,
        }),
      );
    }
  }
}

export async function runReminderSweep(now = new Date()) {
  await renewalReminders(now);
  await birthdayReminders(now);
  await followUpReminders(now);
  await appointmentReminders(now);
  await morningDigest(now);
  await retryCalendarPushes(now);
}
