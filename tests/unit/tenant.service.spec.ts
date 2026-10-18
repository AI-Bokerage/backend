jest.mock('../../config/database');
jest.mock('../../modules/crm/modauth.client');
jest.mock('../../modules/platform/export.service');

import { getPrismaClient } from '../../config/database';
import { getTenantPeople } from '../../modules/crm/modauth.client';
import {
  getBookingSettings,
  getExportDownloadPath,
  getExportStatus,
  getProfile,
  startSelfServiceExport,
  tenantWideBook,
  updateBookingSettings,
  updateProfile,
} from '../../modules/crm/tenant.service';
import * as exportService from '../../modules/platform/export.service';

const mockPrisma = {
  tenantProfile: { findUnique: jest.fn(), upsert: jest.fn() },
  bookingPageSettings: { findUnique: jest.fn(), upsert: jest.fn() },
  lead: { findMany: jest.fn() },
};

const TENANT = 'tenant-1';

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('Brokerage profile', () => {
  it('returns a default (empty) profile when none exists yet', async () => {
    mockPrisma.tenantProfile.findUnique.mockResolvedValue(null);
    const profile = await getProfile(TENANT);
    expect(profile.tenantId).toBe(TENANT);
    expect(profile.displayName).toBeNull();
  });

  it('upserts on update', async () => {
    mockPrisma.tenantProfile.upsert.mockResolvedValue({
      tenantId: TENANT,
      displayName: 'Acme Realty',
    });
    const result = await updateProfile(TENANT, { displayName: 'Acme Realty' });
    expect(result.displayName).toBe('Acme Realty');
    expect(mockPrisma.tenantProfile.upsert).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tenantId: TENANT } }),
    );
  });
});

describe('Booking page settings — config only', () => {
  it('returns disabled defaults when none exist yet', async () => {
    mockPrisma.bookingPageSettings.findUnique.mockResolvedValue(null);
    const settings = await getBookingSettings(TENANT);
    expect(settings.enabled).toBe(false);
    expect(settings.bufferMinutes).toBe(15);
  });

  it('requires a slug on first setup', async () => {
    mockPrisma.bookingPageSettings.findUnique.mockResolvedValue(null);
    await expect(updateBookingSettings(TENANT, { enabled: true })).rejects.toMatchObject({
      statusCode: 400,
    });
  });

  it('rejects a slug already taken by another tenant', async () => {
    mockPrisma.bookingPageSettings.findUnique.mockResolvedValueOnce({
      tenantId: 'other-tenant',
      slug: 'acme',
    });
    await expect(updateBookingSettings(TENANT, { slug: 'acme' })).rejects.toMatchObject({
      statusCode: 409,
    });
  });

  it('allows re-saving your own existing slug', async () => {
    mockPrisma.bookingPageSettings.findUnique.mockResolvedValueOnce({
      tenantId: TENANT,
      slug: 'acme',
    });
    mockPrisma.bookingPageSettings.findUnique.mockResolvedValueOnce({
      tenantId: TENANT,
      slug: 'acme',
    });
    mockPrisma.bookingPageSettings.upsert.mockResolvedValue({
      tenantId: TENANT,
      slug: 'acme',
      enabled: true,
    });
    const result = await updateBookingSettings(TENANT, { slug: 'acme', enabled: true });
    expect(result.enabled).toBe(true);
  });
});

describe('tenantWideBook — "every lead and client across the brokerage"', () => {
  it('scopes to every person in the tenant, no team filter', async () => {
    (getTenantPeople as jest.Mock).mockResolvedValue([
      { userId: 'b1', name: 'Bob', role: 'BROKER', teamId: 'team-1' },
      { userId: 'b2', name: 'Bea', role: 'BROKER', teamId: 'team-2' },
    ]);
    mockPrisma.lead.findMany.mockResolvedValue([{ id: 'lead-1' }]);

    const leads = await tenantWideBook(TENANT, 'caller-jwt');

    expect(leads).toHaveLength(1);
    expect(mockPrisma.lead.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { tenantId: TENANT, brokerUserId: { in: ['b1', 'b2'] } } }),
    );
  });

  it('returns nothing without hitting the DB when the tenant has no people', async () => {
    (getTenantPeople as jest.Mock).mockResolvedValue([]);
    const leads = await tenantWideBook(TENANT, 'caller-jwt');
    expect(leads).toEqual([]);
    expect(mockPrisma.lead.findMany).not.toHaveBeenCalled();
  });
});

describe('Self-service export — distinct from the Super Admin cancellation export', () => {
  it('delegates to export.service.startSelfServiceExport', async () => {
    (exportService.startSelfServiceExport as jest.Mock).mockResolvedValue({
      id: 'job-1',
      status: 'READY',
    });
    const job = await startSelfServiceExport(TENANT);
    expect(exportService.startSelfServiceExport).toHaveBeenCalledWith(TENANT);
    expect(job.id).toBe('job-1');
  });

  it("refuses to download a jobId that is not this tenant's latest export", async () => {
    (exportService.getLatestExportForTenant as jest.Mock).mockResolvedValue({
      id: 'job-1',
      status: 'READY',
    });
    await expect(getExportDownloadPath(TENANT, 'someone-elses-job')).rejects.toMatchObject({
      statusCode: 403,
    });
  });

  it("allows downloading the tenant's own latest export", async () => {
    (exportService.getLatestExportForTenant as jest.Mock).mockResolvedValue({
      id: 'job-1',
      status: 'READY',
    });
    (exportService.getDownloadPath as jest.Mock).mockResolvedValue('/tmp/job-1.zip');
    const path = await getExportDownloadPath(TENANT, 'job-1');
    expect(path).toBe('/tmp/job-1.zip');
  });

  it('getExportStatus returns null when nothing has been exported yet', async () => {
    (exportService.getLatestExportForTenant as jest.Mock).mockResolvedValue(null);
    const status = await getExportStatus(TENANT);
    expect(status).toBeNull();
  });
});
