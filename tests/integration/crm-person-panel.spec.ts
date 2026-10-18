import jwt from 'jsonwebtoken';
import request from 'supertest';

// Same dev keypair as tests/integration/crm-team-lead.spec.ts.
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
jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import { encryptToken } from '../../modules/crm/encryption';
import { getModAuthPerson } from '../../modules/crm/modauth.client';
import { listActivity, recordActivity } from '../../modules/platform/activity-log';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const BROKER_ID = '33333333-3333-3333-3333-333333333333';
const LEAD_ID = '55555555-5555-5555-5555-555555555555';

const mockPrisma = {
  leadDocument: { findMany: jest.fn().mockResolvedValue([]) },
  lead: { findUnique: jest.fn(), findMany: jest.fn(), update: jest.fn() },
  mortgage: { findMany: jest.fn() },
  applicant: { findMany: jest.fn() },
  debt: { findMany: jest.fn() },
};

function mockBroker() {
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId: BROKER_ID,
    tenantId: TENANT_ID,
    role: 'BROKER',
    teamId: null,
    name: 'Bob Broker',
    active: true,
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  (listActivity as jest.Mock).mockResolvedValue({ items: [], total: 0 });
  mockPrisma.mortgage.findMany.mockResolvedValue([]);
  mockPrisma.applicant.findMany.mockResolvedValue([]);
  mockPrisma.debt.findMany.mockResolvedValue([]);
  mockBroker();
});

function leadRow(overrides: Record<string, unknown> = {}) {
  return {
    id: LEAD_ID,
    tenantId: TENANT_ID,
    brokerUserId: BROKER_ID,
    name: 'Jamie Rivera',
    requiredDocuments: [],
    notes: 'source record',
    brokerNotes: null,
    tags: [],
    income: 120000,
    monthlyDebts: 500,
    downPayment: 50000,
    propertyValue: 400000,
    sin: null,
    bankDetails: null,
    ...overrides,
  };
}

describe('Person panel — HTTP layer (Flow 4.3/4.4)', () => {
  it('GET /leads/:id never leaks sin/bankDetails ciphertext, only hasSin/hasBankDetails', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(
      leadRow({ sin: encryptToken('123-456-789'), bankDetails: encryptToken('acct-1') }),
    );

    const res = await request(app)
      .get(`${BASE}/leads/${LEAD_ID}`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.sin).toBeUndefined();
    expect(res.body.data.bankDetails).toBeUndefined();
    expect(res.body.data.hasSin).toBe(true);
    expect(res.body.data.hasBankDetails).toBe(true);
  });

  it('GET /leads/:id/panel aggregates profile, mortgages, notes and activity', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow({ brokerNotes: 'my note' }));
    mockPrisma.mortgage.findMany.mockResolvedValue([{ id: 'mortgage-1' }]);

    const res = await request(app)
      .get(`${BASE}/leads/${LEAD_ID}/panel`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.profile.name).toBe('Jamie Rivera');
    expect(res.body.data.mortgages).toEqual([{ id: 'mortgage-1' }]);
    expect(res.body.data.notes).toEqual({
      original: 'source record',
      yours: 'my note',
      history: [],
    });
  });

  it('GET /leads/:id/affordability computes ratios live', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow());

    const res = await request(app)
      .get(`${BASE}/leads/${LEAD_ID}/affordability`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.sufficientData).toBe(true);
    expect(res.body.data.tds).toBeGreaterThan(0);
  });

  it('GET affordability flags missing income/debts instead of computing', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow({ income: null, monthlyDebts: null }));

    const res = await request(app)
      .get(`${BASE}/leads/${LEAD_ID}/affordability`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.sufficientData).toBe(false);
    expect(res.body.data.missingFields).toEqual(['income', 'monthlyDebts']);
  });

  it('PATCH /leads/:id/notes updates brokerNotes and records history', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow({ brokerNotes: 'old' }));
    mockPrisma.lead.update.mockResolvedValue(leadRow({ brokerNotes: 'updated' }));

    const res = await request(app)
      .patch(`${BASE}/leads/${LEAD_ID}/notes`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ brokerNotes: 'updated' });

    expect(res.status).toBe(200);
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.notes.updated',
      expect.objectContaining({ metadata: { previous: 'old', next: 'updated' } }),
    );
  });

  it('cross-tenant lead on the notes endpoint 404s, not 403 — same split as every other lead route', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow({ tenantId: 'other-tenant' }));

    const res = await request(app)
      .patch(`${BASE}/leads/${LEAD_ID}/notes`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ brokerNotes: 'updated' });

    expect(res.status).toBe(404);
  });

  it('PATCH /leads/:id/tags replaces the tag list', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow());
    mockPrisma.lead.update.mockResolvedValue(leadRow({ tags: ['hot', 'first-time-buyer'] }));

    const res = await request(app)
      .patch(`${BASE}/leads/${LEAD_ID}/tags`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ tags: ['hot', 'first-time-buyer'] });

    expect(res.status).toBe(200);
    expect(res.body.data.tags).toEqual(['hot', 'first-time-buyer']);
  });

  it('POST /leads/:id/reveal decrypts a hidden value and records the reveal', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow({ sin: encryptToken('123-456-789') }));

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/reveal`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ field: 'sin' });

    expect(res.status).toBe(200);
    expect(res.body.data).toEqual({ field: 'sin', value: '123-456-789' });
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.lead.sensitive_field_revealed',
      expect.objectContaining({ metadata: { field: 'sin' } }),
    );
  });

  it("reveal on someone else's lead 403s and never decrypts", async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(
      leadRow({ brokerUserId: 'someone-else', sin: encryptToken('123-456-789') }),
    );

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/reveal`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ field: 'sin' });

    expect(res.status).toBe(403);
  });

  it('POST /leads/affordability/recalculate reports successes and failures across the book', async () => {
    mockPrisma.lead.findMany.mockResolvedValue([
      leadRow({ id: 'lead-ok' }),
      leadRow({ id: 'lead-missing', income: null, monthlyDebts: null, name: 'Missing Data' }),
    ]);

    const res = await request(app)
      .post(`${BASE}/leads/affordability/recalculate`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({});

    expect(res.status).toBe(200);
    expect(res.body.data.total).toBe(2);
    expect(res.body.data.succeeded).toBe(1);
    expect(res.body.data.failures).toEqual([
      { leadId: 'lead-missing', name: 'Missing Data', reason: 'Missing income, monthlyDebts' },
    ]);
  });
});
