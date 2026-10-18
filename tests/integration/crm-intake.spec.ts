import jwt from 'jsonwebtoken';
import request from 'supertest';

// Same dev keypair as tests/integration/crm-person-panel.spec.ts.
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
jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import { getModAuthPerson } from '../../modules/crm/modauth.client';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const BROKER_ID = '33333333-3333-3333-3333-333333333333';
const LEAD_ID = '55555555-5555-5555-5555-555555555555';
const APPLICANT_ID = '66666666-6666-6666-6666-666666666666';

const mockPrisma = {
  lead: { findUnique: jest.fn() },
  applicant: { count: jest.fn(), create: jest.fn(), findUnique: jest.fn(), findMany: jest.fn() },
  employment: { create: jest.fn() },
  debt: { create: jest.fn(), findMany: jest.fn() },
  propertyRecord: { findMany: jest.fn() },
  fundsSource: { findMany: jest.fn() },
  followUp: { findMany: jest.fn() },
};

function leadRow(overrides: Record<string, unknown> = {}) {
  return {
    id: LEAD_ID,
    tenantId: TENANT_ID,
    brokerUserId: BROKER_ID,
    name: 'Jamie Rivera',
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId: BROKER_ID,
    tenantId: TENANT_ID,
    role: 'BROKER',
    teamId: null,
    name: 'Bob Broker',
    active: true,
  });
  mockPrisma.lead.findUnique.mockResolvedValue(leadRow());
});

describe('Structured intake — HTTP layer (SOW Module 5)', () => {
  it('POST /leads/:id/applicants creates an applicant', async () => {
    mockPrisma.applicant.count.mockResolvedValue(0);
    mockPrisma.applicant.create.mockResolvedValue({
      id: APPLICANT_ID,
      firstName: 'Jamie',
      lastName: 'Rivera',
    });

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/applicants`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ firstName: 'Jamie', lastName: 'Rivera' });

    expect(res.status).toBe(201);
    expect(res.body.data.firstName).toBe('Jamie');
  });

  it('refuses a 5th applicant with 400', async () => {
    mockPrisma.applicant.count.mockResolvedValue(4);

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/applicants`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ firstName: 'Jamie', lastName: 'Rivera' });

    expect(res.status).toBe(400);
    expect(mockPrisma.applicant.create).not.toHaveBeenCalled();
  });

  it('cross-tenant lead on an intake route 404s', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow({ tenantId: 'other-tenant' }));

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/debts`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ balance: 1000, monthlyPayment: 50 });

    expect(res.status).toBe(404);
  });

  it('a teammate-owned lead on an intake route 403s, not 404', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue(leadRow({ brokerUserId: 'someone-else' }));

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/debts`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ balance: 1000, monthlyPayment: 50 });

    expect(res.status).toBe(403);
  });

  it('POST /leads/:id/applicants/:applicantId/employment adds a job under the applicant', async () => {
    mockPrisma.applicant.findUnique.mockResolvedValue({ id: APPLICANT_ID, leadId: LEAD_ID });
    mockPrisma.employment.create.mockResolvedValue({ id: 'emp-1', type: 'EMPLOYED' });

    const res = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/applicants/${APPLICANT_ID}/employment`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ type: 'EMPLOYED', employer: 'Acme Co', baseSalary: 90000 });

    expect(res.status).toBe(201);
    expect(res.body.data.type).toBe('EMPLOYED');
  });

  it('GET /leads/:id/intake returns the full bundle', async () => {
    mockPrisma.applicant.findMany.mockResolvedValue([{ id: APPLICANT_ID }]);
    mockPrisma.debt.findMany.mockResolvedValue([{ id: 'debt-1' }]);
    mockPrisma.propertyRecord.findMany.mockResolvedValue([]);
    mockPrisma.fundsSource.findMany.mockResolvedValue([]);
    mockPrisma.followUp.findMany.mockResolvedValue([]);

    const res = await request(app)
      .get(`${BASE}/leads/${LEAD_ID}/intake`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.applicants).toEqual([{ id: APPLICANT_ID }]);
    expect(res.body.data.debts).toEqual([{ id: 'debt-1' }]);
  });

  it('rejects a Tenant Admin — this is Broker/Team Lead own-book territory', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: BROKER_ID,
      tenantId: TENANT_ID,
      role: 'TENANT_ADMIN',
      teamId: null,
      name: 'Alice Admin',
      active: true,
    });

    const res = await request(app)
      .get(`${BASE}/leads/${LEAD_ID}/intake`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(403);
  });
});
