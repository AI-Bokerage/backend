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
jest.mock('../../modules/crm/document-storage');
jest.mock('../../modules/crm/ai.client');
jest.mock('../../modules/platform/activity-log');
jest.mock('../../modules/platform/usage.service');
jest.mock('../../config/database');
// The clamd protocol is tested in tests/unit/document-processing.spec.ts; no scanner runs here.
jest.mock('../../modules/crm/document-processing', () => ({
  ...jest.requireActual('../../modules/crm/document-processing'),
  scanForViruses: jest.fn(async () => ({ clean: true })),
}));

import { getPrismaClient } from '../../config/database';
import { aiCall } from '../../modules/crm/ai.client';
import { storeFile } from '../../modules/crm/document-storage';
import { getModAuthPerson, resolveTeam } from '../../modules/crm/modauth.client';
import { getUsageStatus } from '../../modules/platform/usage.service';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const USER_ID = '66666666-6666-6666-6666-666666666666';
const LEAD_ID = '11111111-1111-1111-1111-111111111111';

const mockPrisma = {
  tenantProfile: { findUnique: jest.fn() },
  lead: { findUnique: jest.fn(), update: jest.fn(), findMany: jest.fn() },
  leadDocument: { create: jest.fn(), update: jest.fn() },
  mortgage: { findMany: jest.fn() },
  notification: { create: jest.fn() },
  crmUserSettings: { findUnique: jest.fn() },
};

function as(role: string, teamId: string | null = null) {
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId: USER_ID,
    tenantId: TENANT_ID,
    role,
    teamId,
    name: 'Someone',
    active: true,
  });
  return { Authorization: `Bearer ${tokenFor(USER_ID)}` };
}

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.lead.findUnique.mockResolvedValue({
    id: LEAD_ID,
    tenantId: TENANT_ID,
    brokerUserId: USER_ID,
    name: 'Pat Client',
  });
  mockPrisma.crmUserSettings.findUnique.mockResolvedValue(null);
  (getUsageStatus as jest.Mock).mockResolvedValue({ paused: false });
});

describe('Flow 0 — a Tenant Admin completes the brokerage profile before anything else', () => {
  it('blocks other Tenant Admin screens until the profile is complete, but not the profile itself', async () => {
    mockPrisma.tenantProfile.findUnique.mockResolvedValue({ displayName: 'Acme' });
    const dash = await request(app).get(`${BASE}/tenant/dashboard`).set(as('TENANT_ADMIN'));
    expect(dash.status).toBe(403);
    expect(dash.body.message ?? JSON.stringify(dash.body)).toContain(
      'Complete the brokerage profile',
    );

    const profile = await request(app).get(`${BASE}/tenant/profile`).set(as('TENANT_ADMIN'));
    expect(profile.status).toBe(200);
  });
});

describe('Flow 4.5 — uploads are the raw request body', () => {
  it('stores a file sent with its type and name in the query string, then sends it to be read', async () => {
    (storeFile as jest.Mock).mockResolvedValue('tenant-1/key');
    mockPrisma.leadDocument.create.mockImplementation(({ data }) => ({ id: 'doc-1', ...data }));
    mockPrisma.leadDocument.update.mockImplementation(({ data }) => ({
      id: 'doc-1',
      type: 'T4',
      fileName: 't4.pdf',
      mimeType: 'application/pdf',
      storagePath: 'tenant-1/key',
      readStatus: 'PENDING',
      ...data,
    }));
    mockPrisma.mortgage.findMany.mockResolvedValue([]);
    (aiCall as jest.Mock).mockResolvedValue({ id: 'ai-doc-1', status: 'queued' });

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/documents?type=T4&fileName=t4.pdf`)
      .set(as('BROKER'))
      .set('Content-Type', 'application/pdf')
      .send(Buffer.from('%PDF-1.4 fake'));

    expect(res.status).toBe(201);
    expect(res.body.data).toMatchObject({
      type: 'T4',
      fileName: 't4.pdf',
      mimeType: 'application/pdf',
    });
    expect(res.body.data).toMatchObject({ aiDocumentId: 'ai-doc-1', readStatus: 'PENDING' });
    expect((storeFile as jest.Mock).mock.calls[0][1].toString()).toBe('%PDF-1.4 fake');
    expect(aiCall).toHaveBeenCalledWith('docread', 'POST', '/v1/documents', expect.anything());
  });

  it('rejects an upload without a document type', async () => {
    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/documents?fileName=t4.pdf`)
      .set(as('BROKER'))
      .set('Content-Type', 'application/pdf')
      .send(Buffer.from('x'));
    expect(res.status).toBe(400);
  });
});

describe('Flow 4.1 — voice note upload', () => {
  it('forwards the raw audio to voice-svc as the caller and answers 202 (poll for the transcript)', async () => {
    (aiCall as jest.Mock).mockResolvedValue({
      id: 'vn-1',
      userId: USER_ID,
      status: 'queued',
      transcript: null,
      aiLabel: null,
      durationSec: null,
      error: null,
      createdAt: '2026-10-16T00:00:00.000Z',
    });
    const res = await request(app)
      .post(`${BASE}/voice-notes`)
      .set(as('BROKER'))
      .set('Content-Type', 'audio/mpeg')
      .send(Buffer.from('audio'));
    expect(res.status).toBe(202);
    expect(res.body.data).toMatchObject({ id: 'vn-1', status: 'queued' });
    expect(aiCall).toHaveBeenCalledWith(
      'voice',
      'POST',
      '/v1/voice-notes',
      expect.objectContaining({ caller: expect.objectContaining({ userId: USER_ID, role: 'BROKER' }) }),
    );
  });
});

describe('Role limits', () => {
  it('an Assistant cannot delete a person (Flow 4.11)', async () => {
    const res = await request(app)
      .delete(`${BASE}/leads/${LEAD_ID}`)
      .set(as('ASSISTANT', 'team-1'))
      .send({ confirmName: 'Pat Client' });
    expect(res.status).toBe(403);
  });
});

describe('Public routes — no login', () => {
  it('rejects a malformed borrower link before touching the database', async () => {
    const res = await request(app).get(`${BASE}/public/document-requests/not-a-token`);
    expect(res.status).toBe(400);
  });

  it('rejects a booking that ends before it starts', async () => {
    const res = await request(app).post(`${BASE}/public/bookings/acme`).send({
      bookingRef: 'x',
      name: 'Sam',
      email: 'sam@example.com',
      startTime: '2026-11-01T15:00:00.000Z',
      endTime: '2026-11-01T14:00:00.000Z',
    });
    expect(res.status).toBe(400);
  });
});

describe('Flow 1 — a suspended brokerage is blocked on every request', () => {
  it('returns 403 even with a still-valid token', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: USER_ID,
      tenantId: TENANT_ID,
      role: 'BROKER',
      teamId: null,
      name: 'Someone',
      active: true,
      tenantSuspended: true,
    });
    const res = await request(app)
      .get(`${BASE}/leads`)
      .set('Authorization', `Bearer ${tokenFor(USER_ID)}`);
    expect(res.status).toBe(403);
    expect(JSON.stringify(res.body)).toContain('suspended');
  });
});

describe("Flow 3 — a Team Lead has the owning broker's access to their team's people", () => {
  it("edits a teammate's client, and still can't reach another team's", async () => {
    const TEAMMATE = '77777777-7777-7777-7777-777777777777';
    (resolveTeam as jest.Mock).mockResolvedValue({
      members: [],
      brokerIds: [USER_ID, TEAMMATE],
    });
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: LEAD_ID,
      tenantId: TENANT_ID,
      brokerUserId: TEAMMATE,
      name: 'Pat Client',
      sin: null,
      bankDetails: null,
    });
    mockPrisma.lead.update.mockResolvedValue({ id: LEAD_ID, sin: null, bankDetails: null });

    const ok = await request(app)
      .patch(`${BASE}/leads/${LEAD_ID}/profile`)
      .set(as('TEAM_LEAD', 'team-1'))
      .send({ city: 'Toronto' });
    expect(ok.status).toBe(200);

    mockPrisma.lead.findUnique.mockResolvedValue({
      id: LEAD_ID,
      tenantId: TENANT_ID,
      brokerUserId: 'someone-on-another-team',
      name: 'Other',
    });
    const blocked = await request(app)
      .patch(`${BASE}/leads/${LEAD_ID}/profile`)
      .set(as('TEAM_LEAD', 'team-1'))
      .send({ city: 'Toronto' });
    expect(blocked.status).toBe(403);
  });
});

describe('Internal endpoints (X-Internal-Secret)', () => {
  it('reports brokerage-profile completeness to modules/auth', async () => {
    mockPrisma.tenantProfile.findUnique.mockResolvedValue({
      displayName: 'Acme',
      supportEmail: 'a@acme.test',
      phone: '555',
    });
    const res = await request(app)
      .get(`${BASE}/internal/tenants/${USER_ID}/profile-status`)
      .set('X-Internal-Secret', process.env.INTERNAL_SERVICE_SECRET ?? '');
    expect(res.status).toBe(200);
    expect(res.body.data).toEqual({ complete: true });
  });

  it('rejects a call without the secret', async () => {
    const res = await request(app).get(`${BASE}/internal/tenants/${USER_ID}/profile-status`);
    expect(res.status).toBe(401);
  });
});

describe('Flow 4 — dashboard cards open filtered lists', () => {
  it('parses comma-separated stages and flags from the query string', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([]);
    const res = await request(app)
      .get(`${BASE}/leads?stage=UNDERWRITING,APPROVED&hot=true`)
      .set(as('BROKER'));
    expect(res.status).toBe(200);
    expect(mockPrisma.lead.findMany.mock.calls[0][0].where.AND).toEqual([
      { stage: { in: ['UNDERWRITING', 'APPROVED'] } },
      { stage: { in: ['APPLICATION', 'UNDERWRITING', 'APPROVED'] } },
    ]);
  });

  it('rejects an unknown stage, a bad day count and an unknown due filter', async () => {
    const bad = await Promise.all([
      request(app).get(`${BASE}/leads?stage=BOGUS`).set(as('BROKER')),
      request(app).get(`${BASE}/clients?renewalWithinDays=-1`).set(as('BROKER')),
      request(app).get(`${BASE}/tasks?due=someday`).set(as('BROKER')),
    ]);
    expect(bad.map((r) => r.status)).toEqual([400, 400, 400]);
  });
});

describe('Flow 4.8 — appointment type', () => {
  it('rejects a type outside call · consultation · document review', async () => {
    const res = await request(app).post(`${BASE}/appointments`).set(as('BROKER')).send({
      title: 'Meet',
      type: 'LUNCH',
      startTime: '2026-11-01T15:00:00.000Z',
      endTime: '2026-11-01T15:30:00.000Z',
    });
    expect(res.status).toBe(400);
  });
});

describe("Bad input is the caller's error, not a 500", () => {
  it('malformed JSON → 400 with a plain message', async () => {
    const res = await request(app)
      .patch(`${BASE}/settings`)
      .set(as('BROKER'))
      .set('Content-Type', 'application/json')
      .send('{bad');
    expect(res.status).toBe(400);
    expect(res.body.message).toBe('Request body is not valid JSON');
  });

  it('an upload over the size limit → 413', async () => {
    const res = await request(app)
      .post(`${BASE}/voice-notes`)
      .set(as('BROKER'))
      .set('Content-Type', 'audio/webm')
      .send(Buffer.alloc(26 * 1024 * 1024));
    expect(res.status).toBe(413);
  });
});
