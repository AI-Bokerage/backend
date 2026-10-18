import { Prisma } from '@prisma/client';

import { pushAppointment } from './google/calendar.service';
import { notify } from './notification.service';
import { getPrismaClient } from '../../config/database';
import { ApiError } from '../../utils/api-error';

export interface PublicBookingInput {
  bookingRef: string;
  name: string;
  email: string;
  phone?: string;
  title?: string;
  startTime: string;
  endTime: string;
}

/**
 * Flow 4.8 "When someone books through the public booking page": seen this
 * booking before → ignored; email matches someone we have → attach the
 * appointment to them; otherwise a new lead (for the page's default broker)
 * plus the appointment. No login — the page slug identifies the brokerage.
 */
export async function intakeBooking(slug: string, input: PublicBookingInput) {
  const page = await getPrismaClient().bookingPageSettings.findUnique({ where: { slug } });
  if (!page || !page.enabled) {
    throw new ApiError('Booking page not found', 404);
  }
  const { tenantId } = page;

  const seen = await getPrismaClient().appointment.findUnique({
    where: { tenantId_externalRef: { tenantId, externalRef: input.bookingRef } },
  });
  if (seen) {
    return { duplicate: true, appointmentId: seen.id, leadId: seen.leadId, newLead: false };
  }

  let lead = await getPrismaClient().lead.findFirst({
    where: { tenantId, email: { equals: input.email, mode: 'insensitive' } },
    orderBy: { createdAt: 'asc' },
  });
  const newLead = !lead;
  if (!lead) {
    if (!page.defaultBrokerUserId) {
      throw new ApiError('This booking page has no broker assigned yet', 409);
    }
    lead = await getPrismaClient().lead.create({
      data: {
        tenantId,
        brokerUserId: page.defaultBrokerUserId,
        name: input.name,
        email: input.email,
        phone: input.phone,
      },
    });
  }

  let appointment;
  try {
    appointment = await getPrismaClient().appointment.create({
      data: {
        tenantId,
        brokerUserId: lead.brokerUserId,
        leadId: lead.id,
        title: input.title ?? `Consultation — ${input.name}`,
        type: 'CONSULTATION',
        startTime: new Date(input.startTime),
        endTime: new Date(input.endTime),
        externalRef: input.bookingRef,
      },
    });
  } catch (error) {
    // Same booking arriving twice at once — the unique (tenantId, externalRef) wins.
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
      return { duplicate: true, appointmentId: null, leadId: lead.id, newLead: false };
    }
    throw error;
  }

  await pushAppointment(appointment, [input.email]);
  await notify({
    tenantId,
    userId: lead.brokerUserId,
    type: 'booking.new',
    title: `New booking: ${input.name}${newLead ? ' (new lead)' : ''}`,
    body: appointment.startTime.toISOString(),
    leadId: lead.id,
  });
  return { duplicate: false, appointmentId: appointment.id, leadId: lead.id, newLead };
}
