import type { Appointment, AppointmentType } from '@prisma/client';
import { google } from 'googleapis';

import { getAuthenticatedClientOrNull } from './google-connection.service';
import { getPrismaClient } from '../../../config/database';
import { ApiError } from '../../../utils/api-error';

export interface CreateAppointmentInput {
  title: string;
  startTime: string;
  endTime: string;
  leadId?: string;
  // Flow 4.8 "New appointment: call · consultation · document review".
  type?: AppointmentType;
  attendeeEmails?: string[];
}

/**
 * "New appointment" (Flow 4.8): Google Calendar connected? No -> stays in
 * the CRM only. Yes -> pushed across. A failed push leaves googleEventId
 * null and counts the attempt; the hourly sweep (reminder.service.ts) is the
 * "queued and retried quietly" part.
 */
export async function createAppointment(
  tenantId: string,
  brokerUserId: string,
  input: CreateAppointmentInput,
) {
  const appointment = await getPrismaClient().appointment.create({
    data: {
      tenantId,
      brokerUserId,
      leadId: input.leadId,
      title: input.title,
      type: input.type,
      startTime: new Date(input.startTime),
      endTime: new Date(input.endTime),
    },
  });
  const pushed = await pushAppointment(appointment, input.attendeeEmails);
  return { ...(pushed ?? appointment), pushedToGoogle: pushed !== null };
}

/** Push one appointment to its broker's Google Calendar; null if not connected or the push failed. */
export async function pushAppointment(appointment: Appointment, attendeeEmails?: string[]) {
  const connection = await getAuthenticatedClientOrNull(appointment.brokerUserId);
  if (!connection) return null;
  try {
    const calendar = google.calendar({ version: 'v3', auth: connection.client });
    const { data: event } = await calendar.events.insert({
      calendarId: 'primary',
      requestBody: {
        summary: appointment.title,
        start: { dateTime: appointment.startTime.toISOString() },
        end: { dateTime: appointment.endTime.toISOString() },
        attendees: attendeeEmails?.map((email) => ({ email })),
      },
    });
    return await getPrismaClient().appointment.update({
      where: { id: appointment.id },
      data: { googleEventId: event.id },
    });
  } catch {
    await getPrismaClient().appointment.update({
      where: { id: appointment.id },
      data: { pushAttempts: { increment: 1 } },
    });
    return null;
  }
}

/** "Calendar"; from/to narrows it — e.g. the dashboard's "today's meetings" card. */
export async function listMyAppointments(
  tenantId: string,
  brokerUserId: string,
  range: { from?: string; to?: string } = {},
) {
  return getPrismaClient().appointment.findMany({
    where: {
      tenantId,
      brokerUserId,
      ...(range.from || range.to
        ? {
            startTime: {
              ...(range.from && { gte: new Date(range.from) }),
              ...(range.to && { lt: new Date(range.to) }),
            },
          }
        : {}),
    },
    orderBy: { startTime: 'asc' },
  });
}

/** "Team calendar" — the one piece Flow 3 was missing. */
export async function listForBrokers(tenantId: string, brokerIds: string[]) {
  if (brokerIds.length === 0) return [];
  return getPrismaClient().appointment.findMany({
    where: { tenantId, brokerUserId: { in: brokerIds } },
    orderBy: { startTime: 'asc' },
  });
}

export async function requireOwnAppointment(
  tenantId: string,
  brokerUserId: string,
  appointmentId: string,
) {
  const appointment = await getPrismaClient().appointment.findUnique({
    where: { id: appointmentId },
  });
  if (!appointment || appointment.tenantId !== tenantId) {
    throw new ApiError('Appointment not found', 404);
  }
  if (appointment.brokerUserId !== brokerUserId) {
    throw new ApiError("You don't have access to this record", 403);
  }
  return appointment;
}
