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
jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/crm/ai.client');
jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import { aiCall } from '../../modules/crm/ai.client';
import { getModAuthPerson, getTenantPeople, resolveTeam } from '../../modules/crm/modauth.client';
import { recordActivity } from '../../modules/platform/activity-log';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const TENANT_ADMIN_ID = '55555555-5555-5555-5555-555555555555';
const LEAD_USER_ID = '22222222-2222-2222-2222-222222222222';
const BROKER_A = '33333333-3333-3333-3333-333333333333';

const mockPrisma = {
  tenantProfile: {
    findUnique: jest
      .fn()
      .mockResolvedValue({ displayName: 'Acme', supportEmail: 'help@acme.test', phone: '555' }),
  },
  lead: { groupBy: jest.fn() },
  mortgage: { findMany: jest.fn() },
  task: { groupBy: jest.fn() },
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

function mockTeamLead() {
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId: LEAD_USER_ID,
    tenantId: TENANT_ID,
    role: 'TEAM_LEAD',
    teamId: 'team-1',
    name: 'Leah Lead',
    active: true,
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('Reports — shared by Tenant Admin and Team Lead', () => {
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
      .get(`${BASE}/reports?type=pipeline`)
      .set('Authorization', `Bearer ${tokenFor('b1')}`);
    expect(res.status).toBe(403);
  });

  it('rejects an unknown report type before ever calling a service', async () => {
    mockTenantAdmin();
    const res = await request(app)
      .get(`${BASE}/reports?type=not-a-real-report`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);
    expect(res.status).toBe(400);
  });

  it('Tenant Admin with no filters scopes to every person in the brokerage', async () => {
    mockTenantAdmin();
    (getTenantPeople as jest.Mock).mockResolvedValue([
      { userId: LEAD_USER_ID, name: 'Leah', role: 'TEAM_LEAD', teamId: 'team-1' },
      { userId: BROKER_A, name: 'Bob', role: 'BROKER', teamId: 'team-1' },
    ]);
    mockPrisma.lead.groupBy.mockResolvedValue([{ stage: 'NEW', _count: 5 }]);

    const res = await request(app)
      .get(`${BASE}/reports?type=pipeline`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.NEW).toBe(5);
    expect(mockPrisma.lead.groupBy).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ brokerUserId: { in: [LEAD_USER_ID, BROKER_A] } }),
      }),
    );
  });

  it('Tenant Admin can narrow to one team', async () => {
    mockTenantAdmin();
    (resolveTeam as jest.Mock).mockResolvedValue({ members: [], brokerIds: [BROKER_A] });
    mockPrisma.lead.groupBy.mockResolvedValue([]);

    const res = await request(app)
      .get(`${BASE}/reports?type=pipeline&teamId=11111111-1111-1111-1111-111111111111`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);

    expect(res.status).toBe(200);
    expect(getTenantPeople).not.toHaveBeenCalled();
  });

  it('Team Lead is always scoped to their own team, never the whole tenant', async () => {
    mockTeamLead();
    (resolveTeam as jest.Mock).mockResolvedValue({
      members: [],
      brokerIds: [LEAD_USER_ID, BROKER_A],
    });
    mockPrisma.lead.groupBy.mockResolvedValue([]);

    const res = await request(app)
      .get(`${BASE}/reports?type=pipeline`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(200);
    expect(getTenantPeople).not.toHaveBeenCalled();
    expect(resolveTeam).toHaveBeenCalledWith('team-1', expect.any(String));
  });

  it("a brokerId outside the caller's scope is 403", async () => {
    mockTeamLead();
    (resolveTeam as jest.Mock).mockResolvedValue({
      members: [],
      brokerIds: [LEAD_USER_ID, BROKER_A],
    });

    const res = await request(app)
      .get(`${BASE}/reports?type=pipeline&brokerId=99999999-9999-9999-9999-999999999999`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(403);
  });

  it('ai-usage is per person in the Team Lead\'s scope, from the AI gateway', async () => {
    mockTeamLead();
    (resolveTeam as jest.Mock).mockResolvedValue({
      members: [],
      brokerIds: [LEAD_USER_ID, BROKER_A],
    });
    (aiCall as jest.Mock).mockResolvedValue({
      rows: [{ key: BROKER_A, calls: 3, blocked: 0, credits: 7 }],
    });

    const res = await request(app)
      .get(`${BASE}/reports?type=ai-usage`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.rows).toEqual([
      { brokerUserId: LEAD_USER_ID, calls: 0, credits: 0, blocked: 0 },
      { brokerUserId: BROKER_A, calls: 3, credits: 7, blocked: 0 },
    ]);
    expect(aiCall).toHaveBeenCalledWith(
      'gateway',
      'GET',
      '/v1/usage/summary',
      expect.objectContaining({ caller: { tenantId: TENANT_ID } }),
    );
  });

  it('export returns a CSV download and records the activity', async () => {
    mockTenantAdmin();
    (getTenantPeople as jest.Mock).mockResolvedValue([
      { userId: BROKER_A, name: 'Bob', role: 'BROKER', teamId: null },
    ]);
    mockPrisma.lead.groupBy.mockResolvedValue([{ stage: 'NEW', _count: 1 }]);

    const res = await request(app)
      .get(`${BASE}/reports/export?type=pipeline`)
      .set('Authorization', `Bearer ${tokenFor(TENANT_ADMIN_ID)}`);

    expect(res.status).toBe(200);
    expect(res.headers['content-type']).toContain('text/csv');
    expect(res.text).toContain('NEW');
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.report.exported',
      expect.objectContaining({ actorId: TENANT_ADMIN_ID }),
    );
  });
});
