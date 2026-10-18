import jwt from 'jsonwebtoken';
import request from 'supertest';

// Same dev keypair as tests/unit/platform-auth.spec.ts.
const DEV_PRIVATE_KEY = [
  '-----BEGIN PRIVATE KEY-----',
  'MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCuNw6R4lzVmSWo',
  'Oafb9+sR6l0WM6eEgVGjzlwOoGxUHHue950mwOCDU/whAq0dcsGb+6cbtu5oKpAt',
  '9IDROQYZF6o+6e3ZoBFB1UKHiIo8eeunZef3wPj6WESxQepH/1TrHLR/ppsHXNjH',
  'p+P4a/cjnpPyh67Ug1v5L6yrthYQUSpFnOy/L+HiMcWzEXgPh4VQ8Adx8758ZTPQ',
  'Z+zY2R4i6PFh8bKRH7tD13sFqifUg5pqvq+U+VS0VqF6Ti8Ypl3OnGNyzcqc8f7q',
  'DKpGztBfEnCXopYFOqBhPt046AlRWNJtiAFFmffPLuTRckSbDSpbtImrNJuup3M3',
  'fLeMot61AgMBAAECggEAB2Vs/UKNSxWHuhRLN7/XFK6iKb7NqNwjTtSu/IFRDWJx',
  'g8viucd3K1GAeGz4U9y7CC1Ek0ej6CY5HXUDlwkDD5j9RaiBduYgdW62trGpn2xU',
  'Vd9csXGu4FSpA0GCJHEG9j2vaA/3BaFVJBImT+CoNbSWIWDN9QBoAjSaRxFMRIBW',
  'XtgJKlHMo3o51kcOwZzTnkBOeVap32mN/uxKv1dnWWbCsRuBfWBJmlii2PqPuE0G',
  'E2LGxXeyq9zJLQHB3adl1WuLtVyq/UiLxP98pVdhEiZ+JG+NabHuPWOWZvtUwznn',
  'QmYsI+9FixyJscUmq3AzRZbLj5+e/9OZWraZzICLQQKBgQDYdRzxdfsQfLJz5HnS',
  'wPDUE2/qJIcsTTlD/v9duVV2Jts8cK2m5hJaz4QA7gwe5dU/LaKHbvbaMujQ27Ol',
  'vyQwlE2q59NFTs4LECUL7eY6LmwUW+EMgcrMuVRfe9NcfRYhj8M5LejAHL1zGv1l',
  'xYFxp9jsnPG9laL7kSxoWcChpQKBgQDOCm2j5+ze5Xa72OBcSDeQelNM9xOEd8nw',
  'sbM9TKVr2/aDDhO/R+MN9LfXTV/Vc1njWAVa0rLBVOpz/EZr1pYBepZC7t2orAB2',
  'fBfdNJAJNssTXmkB9hRIoaUDSI94Leh6L18nZ3T4DYZRRDniABDipb5fGFWz6pb9',
  'lhAZ6oCb0QKBgQDWaArnUkoCJsJM8X+KNvtV1nsAnnYWd9fFdoxUtgPlKM/4qlQY',
  'AcUklnDWyvTOljIIhpd7N3rk35ClcTodb5vVUtEr/L3U5R5K7w8DJf7qmpkMAYaF',
  '8PqElv7wJaNS9cQ6MkDaHpNx2AYAQjfF20FA51WD6mG5vMLYFRC7vEuG0QKBgQCc',
  'upoEIBuyweG7qpGgIN8Da8mJtjiGf4iBKuspKfB7R2sQ7dhfnJM+vnRJtnW7rG8n',
  '3IDWQYfwQGPHrpLy3Nxma5V5fLHn6E7B0ktk3OLj32ZCaYJ/F2z+gtc+1CcuR92b',
  'dAVxt+Tl+4O8taVCIMK3ZVSWibBHl58bbtb4n8UMwQKBgGaVKXmhWmNzB0AQW3kj',
  'qsGZfPiAMtQMqw6t7I7dPBYDh6yASJu0Ds4Npy10v/N+RX3jFqAu5um2LRV2PL7t',
  'h4kqgePaxacb5bPLWq0+PNVCFz21LMyn/7UZwNiScel/TkuMRsTaErIgs8pg3cHr',
  'E2WQqOBZt1/TublW03v/qT9U',
  '-----END PRIVATE KEY-----',
].join('\n');

function tokenFor(sub: string): string {
  return jwt.sign({ sub, user_type: 'TENANT_USER' }, DEV_PRIVATE_KEY, {
    algorithm: 'RS256',
    issuer: 'https://auth.example.com',
    audience: 'example-api',
    expiresIn: '5m',
  });
}

jest.mock('../../modules/crm/modauth.client');
jest.mock('../../modules/platform/export.service');
jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import { getModAuthPerson, getTenantPeople } from '../../modules/crm/modauth.client';
import * as exportService from '../../modules/platform/export.service';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const TENANT_ADMIN_ID = '55555555-5555-5555-5555-555555555555';
const BROKER_A = '33333333-3333-3333-3333-333333333333';

const mockPrisma = {
  tenantProfile: { findUnique: jest.fn(), upsert: jest.fn() },
  bookingPageSettings: { findUnique: jest.fn(), upsert: jest.fn() },
  lead: { findMany: jest.fn() },
};

function mockTenantAdmin() {
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId: TENANT_ADMIN_ID,
    tenantId: TENANT_ID,
    role: 'TENANT_ADMIN',
    teamId: null,
    name: 'Priya Admin',
    active: true,
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  mockPrisma.tenantProfile.findUnique.mockResolvedValue({
    displayName: 'Acme',
    supportEmail: 'help@acme.test',
    phone: '555',
  });
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('Tenant Admin — Brokerage profile', () => {
  it('rejects a Broker', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: 'b1',
      tenantId: TENANT_ID,
      role: 'BROKER',
      teamId: null,
      name: 'Bob',
      active: true,
    });
    const res = await request(app)
      .get(`${BASE}/tenant/profile`)
      .set('Authorization', `Bearer ${tokenFor('b1')}`);
    expect(res.status).toBe(403);
  });

  it('gets and updates the profile', async () => {
    mockTenantAdmin();
    mockPrisma.tenantProfile.findUnique.mockResolvedValue(null);
    const getRes = await request(app)
      .get(`${BASE}/tenant/profile`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);
    expect(getRes.status).toBe(200);
    expect(getRes.body.data.tenantId).toBe(TENANT_ID);

    mockPrisma.tenantProfile.upsert.mockResolvedValue({
      tenantId: TENANT_ID,
      displayName: 'Acme Realty',
    });
    const patchRes = await request(app)
      .patch(`${BASE}/tenant/profile`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`)
      .send({ displayName: 'Acme Realty' });
    expect(patchRes.status).toBe(200);
    expect(patchRes.body.data.displayName).toBe('Acme Realty');
  });
});

describe('Tenant Admin — Booking page setup (settings/config only)', () => {
  it('rejects an invalid slug before hitting the service', async () => {
    mockTenantAdmin();
    const res = await request(app)
      .patch(`${BASE}/tenant/booking-settings`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`)
      .send({ slug: 'Not Valid!' });
    expect(res.status).toBe(400);
    expect(mockPrisma.bookingPageSettings.upsert).not.toHaveBeenCalled();
  });

  it('sets up the booking page with a valid slug', async () => {
    mockTenantAdmin();
    mockPrisma.bookingPageSettings.findUnique.mockResolvedValue(null);
    mockPrisma.bookingPageSettings.upsert.mockResolvedValue({
      tenantId: TENANT_ID,
      slug: 'acme-realty',
      enabled: true,
    });

    const res = await request(app)
      .patch(`${BASE}/tenant/booking-settings`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`)
      .send({ slug: 'acme-realty', enabled: true });

    expect(res.status).toBe(200);
    expect(res.body.data.enabled).toBe(true);
  });
});

describe('Tenant Admin — "every lead and client across the brokerage"', () => {
  it('lists leads for every person in the tenant, not just one team', async () => {
    mockTenantAdmin();
    (getTenantPeople as jest.Mock).mockResolvedValue([
      { userId: BROKER_A, name: 'Bob', role: 'BROKER', teamId: 'team-1' },
    ]);
    mockPrisma.lead.findMany.mockResolvedValue([{ id: 'lead-1' }]);

    const res = await request(app)
      .get(`${BASE}/tenant/book`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(1);
  });
});

describe('Tenant Admin — self-service "Export all our data"', () => {
  it('starts an export, checks its status, and downloads it', async () => {
    mockTenantAdmin();
    (exportService.startSelfServiceExport as jest.Mock).mockResolvedValue({
      id: 'job-1',
      status: 'READY',
    });

    const startRes = await request(app)
      .post(`${BASE}/tenant/export`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);
    expect(startRes.status).toBe(201);

    (exportService.getLatestExportForTenant as jest.Mock).mockResolvedValue({
      id: 'job-1',
      status: 'READY',
    });
    const statusRes = await request(app)
      .get(`${BASE}/tenant/export`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);
    expect(statusRes.status).toBe(200);
    expect(statusRes.body.data.id).toBe('job-1');
  });

  it("403s downloading a job id that isn't this tenant's latest export", async () => {
    mockTenantAdmin();
    (exportService.getLatestExportForTenant as jest.Mock).mockResolvedValue({
      id: 'job-1',
      status: 'READY',
    });

    const res = await request(app)
      .get(`${BASE}/tenant/export/22222222-2222-2222-2222-222222222222/download`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);

    expect(res.status).toBe(403);
  });
});
