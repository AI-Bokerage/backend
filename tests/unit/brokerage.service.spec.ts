// gen-sup-starter is loaded via dynamic import() (see brokerage.service.ts's
// loadGenSup) purely for CJS/ESM interop — jest.mock intercepts it the same
// way regardless, since ts-jest compiles the dynamic import to a resolvable
// module reference under the commonjs target this project uses.
class TenantSlugTakenError extends Error {}

const mockTenantsService = {
  create: jest.fn(),
  getById: jest.fn(),
  suspend: jest.fn(),
  reactivate: jest.fn(),
};

jest.mock('@gen-ms/gen-sup-starter', () => ({
  HttpTntClient: jest.fn(),
  TenantsService: jest.fn().mockImplementation(() => mockTenantsService),
  TenantSlugTakenError,
}));

jest.mock('../../modules/platform/auth-service.client');
jest.mock('../../modules/platform/tenant-service.client');
jest.mock('../../modules/platform/export.service');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import { recordActivity } from '../../modules/platform/activity-log';
import {
  emailAlreadyHasAccount,
  createBrokerageOwnerInvitation,
  setTenantAccess,
} from '../../modules/platform/auth-service.client';
import {
  createBrokerage,
  purgeCancelledBrokerages,
  reactivateBrokerage,
  startBrokerageCancellation,
  suspendBrokerage,
} from '../../modules/platform/brokerage.service';
import { purgeTenantCrmData, startExport } from '../../modules/platform/export.service';
import { tenantServiceGaps } from '../../modules/platform/tenant-service.client';
import { ApiError } from '../../utils/api-error';

const mockPrisma = {
  brokerageOnboarding: { create: jest.fn(), findMany: jest.fn(), update: jest.fn() },
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('createBrokerage — "Creating a brokerage"', () => {
  const input = {
    name: 'Acme Mortgages',
    ownerName: 'Jordan Lee',
    ownerEmail: 'jordan@acme.test',
    region: 'CA',
  };

  it('rejects when the owner email already belongs to another account', async () => {
    (emailAlreadyHasAccount as jest.Mock).mockResolvedValue(true);

    await expect(createBrokerage(input)).rejects.toThrow(ApiError);
    await expect(createBrokerage(input)).rejects.toMatchObject({ statusCode: 409 });
    expect(mockTenantsService.create).not.toHaveBeenCalled();
  });

  it('creates the tenant, sends the invitation, and records onboarding as PENDING', async () => {
    (emailAlreadyHasAccount as jest.Mock).mockResolvedValue(false);
    mockTenantsService.create.mockResolvedValue({
      id: 'tenant-1',
      slug: 'acme-mortgages',
      primaryOwnerUserId: 'user-1',
    });
    (createBrokerageOwnerInvitation as jest.Mock).mockResolvedValue({
      id: 'invite-1',
      status: 'PENDING',
    });
    mockPrisma.brokerageOnboarding.create.mockResolvedValue({});

    const result = await createBrokerage(input);

    expect(mockTenantsService.create).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Acme Mortgages',
        slug: 'acme-mortgages',
        ownerEmail: 'jordan@acme.test',
      }),
    );
    expect(createBrokerageOwnerInvitation).toHaveBeenCalledWith(
      expect.objectContaining({ tenantId: 'tenant-1', preAllocatedUserId: 'user-1' }),
    );
    expect(mockPrisma.brokerageOnboarding.create).toHaveBeenCalledWith({
      data: { tenantId: 'tenant-1', invitationId: 'invite-1', status: 'PENDING' },
    });
    expect(result.onboardingStatus).toBe('PENDING');
  });

  it('retries with a suffixed slug when the base slug is already taken', async () => {
    (emailAlreadyHasAccount as jest.Mock).mockResolvedValue(false);
    mockTenantsService.create
      .mockRejectedValueOnce(new TenantSlugTakenError())
      .mockResolvedValueOnce({
        id: 'tenant-2',
        slug: 'acme-mortgages-abc123',
        primaryOwnerUserId: 'user-2',
      });
    (createBrokerageOwnerInvitation as jest.Mock).mockResolvedValue({
      id: 'invite-2',
      status: 'PENDING',
    });
    mockPrisma.brokerageOnboarding.create.mockResolvedValue({});

    const result = await createBrokerage(input);

    expect(mockTenantsService.create).toHaveBeenCalledTimes(2);
    expect(result.slug).toBe('acme-mortgages-abc123');
  });
});

describe('suspend / reactivate a brokerage', () => {
  it('suspend delegates straight to gen-sup-starter with the reason', async () => {
    mockTenantsService.suspend.mockResolvedValue({ id: 'tenant-1', status: 'SUSPENDED' });

    const result = await suspendBrokerage('tenant-1', 'non-payment');

    expect(mockTenantsService.suspend).toHaveBeenCalledWith('tenant-1', 'non-payment');
    expect(result.status).toBe('SUSPENDED');
  });

  it('reactivate delegates straight to gen-sup-starter', async () => {
    mockTenantsService.reactivate.mockResolvedValue({ id: 'tenant-1', status: 'ACTIVE' });

    const result = await reactivateBrokerage('tenant-1', 'payment received');

    expect(mockTenantsService.reactivate).toHaveBeenCalledWith('tenant-1', 'payment received');
    expect(result.status).toBe('ACTIVE');
  });
});

describe('startBrokerageCancellation — confirm -> export packaged', () => {
  it('cancels the tenant, records the activity, and kicks off the export', async () => {
    const tenant = { id: 'tenant-1', status: 'CANCELLING' };
    (tenantServiceGaps.cancel as jest.Mock).mockResolvedValue(tenant);
    (startExport as jest.Mock).mockResolvedValue({ id: 'export-1', status: 'IN_PROGRESS' });

    const result = await startBrokerageCancellation('tenant-1');

    expect(tenantServiceGaps.cancel).toHaveBeenCalledWith('tenant-1');
    expect(recordActivity).toHaveBeenCalledWith(
      'platform.brokerage.cancellation_started',
      expect.objectContaining({ targetId: 'tenant-1' }),
    );
    expect(startExport).toHaveBeenCalledWith(tenant);
    expect(result.exportJob.id).toBe('export-1');
  });
});

describe('Flow 1 — suspend/cancel block sign-in; reactivate restores it', () => {
  it('blocks sign-in before suspending, so a failure never leaves people let in', async () => {
    const order: string[] = [];
    (setTenantAccess as jest.Mock).mockImplementation(async () => order.push('block'));
    mockTenantsService.suspend.mockImplementation(async () => order.push('suspend'));

    await suspendBrokerage('tenant-1', 'unpaid');

    expect(setTenantAccess).toHaveBeenCalledWith('tenant-1', true, 'SUSPENDED');
    expect(order).toEqual(['block', 'suspend']);
  });

  it('restores sign-in only after reactivating', async () => {
    const order: string[] = [];
    mockTenantsService.reactivate.mockImplementation(async () => order.push('reactivate'));
    (setTenantAccess as jest.Mock).mockImplementation(async () => order.push('unblock'));

    await reactivateBrokerage('tenant-1');

    expect(setTenantAccess).toHaveBeenCalledWith('tenant-1', false, 'ACTIVE');
    expect(order).toEqual(['reactivate', 'unblock']);
  });

  it('cancellation blocks sign-in and starts the retention clock', async () => {
    (tenantServiceGaps.cancel as jest.Mock).mockResolvedValue({ id: 'tenant-1' });
    await startBrokerageCancellation('tenant-1');
    expect(setTenantAccess).toHaveBeenCalledWith('tenant-1', true, 'CANCELLED');
    expect(mockPrisma.brokerageOnboarding.update).toHaveBeenCalledWith({
      where: { tenantId: 'tenant-1' },
      data: { cancelledAt: expect.any(Date) },
    });
  });
});

describe('purgeCancelledBrokerages — "permanently deleted after the retention period"', () => {
  it('purges only brokerages cancelled before the cutoff and not yet purged', async () => {
    const now = new Date('2026-12-31T00:00:00.000Z');
    mockPrisma.brokerageOnboarding.findMany.mockResolvedValue([{ tenantId: 'tenant-old' }]);

    const count = await purgeCancelledBrokerages(now);

    const where = mockPrisma.brokerageOnboarding.findMany.mock.calls[0][0].where;
    expect(where.purgedAt).toBeNull();
    expect(where.cancelledAt.lt.getTime()).toBe(now.getTime() - 90 * 24 * 60 * 60 * 1000);
    expect(purgeTenantCrmData).toHaveBeenCalledWith('tenant-old');
    expect(tenantServiceGaps.purge).toHaveBeenCalledWith('tenant-old');
    expect(mockPrisma.brokerageOnboarding.update).toHaveBeenCalledWith({
      where: { tenantId: 'tenant-old' },
      data: { purgedAt: now },
    });
    expect(count).toBe(1);
  });
});
