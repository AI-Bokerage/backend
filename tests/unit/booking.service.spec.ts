jest.mock('../../config/database');
jest.mock('../../modules/crm/google/calendar.service');

import { getPrismaClient } from '../../config/database';
import { intakeBooking } from '../../modules/crm/booking.service';

const mockPrisma = {
  bookingPageSettings: { findUnique: jest.fn() },
  appointment: { findUnique: jest.fn(), create: jest.fn() },
  lead: { findFirst: jest.fn(), create: jest.fn() },
  notification: { create: jest.fn() },
};

const booking = {
  bookingRef: 'cal-123',
  name: 'Sam New',
  email: 'sam@example.com',
  startTime: '2026-11-01T15:00:00.000Z',
  endTime: '2026-11-01T15:30:00.000Z',
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.bookingPageSettings.findUnique.mockResolvedValue({
    tenantId: 't1',
    enabled: true,
    defaultBrokerUserId: 'default-broker',
  });
  mockPrisma.appointment.findUnique.mockResolvedValue(null);
  mockPrisma.appointment.create.mockImplementation(({ data }) => ({ id: 'appt-1', ...data }));
});

describe('Flow 4.8 public booking', () => {
  it('"Have we seen this booking before?" → ignored, nothing created', async () => {
    mockPrisma.appointment.findUnique.mockResolvedValue({ id: 'appt-0', leadId: 'lead-0' });
    const result = await intakeBooking('acme', booking);
    expect(result.duplicate).toBe(true);
    expect(mockPrisma.appointment.create).not.toHaveBeenCalled();
  });

  it('attaches to the existing person whose email matches, for their own broker', async () => {
    mockPrisma.lead.findFirst.mockResolvedValue({ id: 'lead-9', brokerUserId: 'their-broker' });
    const result = await intakeBooking('acme', booking);
    expect(result).toMatchObject({ duplicate: false, leadId: 'lead-9', newLead: false });
    expect(mockPrisma.lead.create).not.toHaveBeenCalled();
    expect(mockPrisma.appointment.create.mock.calls[0][0].data.brokerUserId).toBe('their-broker');
  });

  it("otherwise creates a new lead for the page's default broker", async () => {
    mockPrisma.lead.findFirst.mockResolvedValue(null);
    mockPrisma.lead.create.mockResolvedValue({ id: 'lead-new', brokerUserId: 'default-broker' });
    const result = await intakeBooking('acme', booking);
    expect(result).toMatchObject({ newLead: true, leadId: 'lead-new' });
    expect(mockPrisma.notification.create).toHaveBeenCalled();
  });

  it('404s a disabled page', async () => {
    mockPrisma.bookingPageSettings.findUnique.mockResolvedValue({ enabled: false });
    await expect(intakeBooking('acme', booking)).rejects.toMatchObject({ statusCode: 404 });
  });
});
