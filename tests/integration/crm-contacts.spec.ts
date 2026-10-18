import jwt from 'jsonwebtoken';
import request from 'supertest';

// Same dev keypair as tests/integration/crm-intake.spec.ts.
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
import { getModAuthPerson } from '../../modules/crm/modauth.client';
import { recordActivity } from '../../modules/platform/activity-log';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const BROKER_ID = '33333333-3333-3333-3333-333333333333';
const CONTACT_ID = '77777777-7777-7777-7777-777777777777';
const LEAD_ID = '55555555-5555-5555-5555-555555555555';

const mockPrisma = {
  contact: {
    create: jest.fn(),
    findMany: jest.fn(),
    groupBy: jest.fn(),
    findUnique: jest.fn(),
    update: jest.fn(),
    delete: jest.fn(),
  },
  lead: { findUnique: jest.fn(), update: jest.fn() },
};

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
});

describe('Contacts / COI — HTTP layer (SOW Module 14)', () => {
  it('POST /contacts creates a real estate contact', async () => {
    mockPrisma.contact.create.mockResolvedValue({
      id: CONTACT_ID,
      category: 'REALTOR',
      name: 'Jamie Realtor',
    });

    const res = await request(app)
      .post(`${BASE}/contacts`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ category: 'REALTOR', name: 'Jamie Realtor' });

    expect(res.status).toBe(201);
    expect(res.body.data.category).toBe('REALTOR');
  });

  it('rejects a category outside the 7 fixed ones', async () => {
    const res = await request(app)
      .post(`${BASE}/contacts`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ category: 'OTHER', name: 'Jamie' });

    expect(res.status).toBe(400);
    expect(mockPrisma.contact.create).not.toHaveBeenCalled();
  });

  it('GET /contacts?search= passes the search term through', async () => {
    mockPrisma.contact.findMany.mockResolvedValue([]);

    const res = await request(app)
      .get(`${BASE}/contacts`)
      .query({ search: 'jamie' })
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(200);
    expect(mockPrisma.contact.findMany.mock.calls[0][0].where.OR).toBeDefined();
  });

  it('GET /contacts/category-counts returns all 7 categories', async () => {
    mockPrisma.contact.groupBy.mockResolvedValue([{ category: 'LAWYER', _count: 2 }]);

    const res = await request(app)
      .get(`${BASE}/contacts/category-counts`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.LAWYER).toBe(2);
    expect(res.body.data.REALTOR).toBe(0);
  });

  it('DELETE /contacts/:id permanently deletes and records an audit event', async () => {
    mockPrisma.contact.findUnique.mockResolvedValue({
      id: CONTACT_ID,
      tenantId: TENANT_ID,
      brokerUserId: BROKER_ID,
      name: 'Jamie Realtor',
      category: 'REALTOR',
    });

    const res = await request(app)
      .delete(`${BASE}/contacts/${CONTACT_ID}`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(204);
    expect(mockPrisma.contact.delete).toHaveBeenCalledWith({ where: { id: CONTACT_ID } });
    expect(recordActivity).toHaveBeenCalledWith(
      'crm.contact.deleted',
      expect.objectContaining({ targetId: CONTACT_ID }),
    );
  });

  it("404s a contact that doesn't belong to this tenant", async () => {
    mockPrisma.contact.findUnique.mockResolvedValue({
      id: CONTACT_ID,
      tenantId: 'other-tenant',
      brokerUserId: BROKER_ID,
    });

    const res = await request(app)
      .delete(`${BASE}/contacts/${CONTACT_ID}`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);

    expect(res.status).toBe(404);
  });
});

describe('PATCH /crm/leads/:id/profile — inline profile edit (SOW 6.9)', () => {
  it('updates the header fields and never leaks sensitive ones', async () => {
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: LEAD_ID,
      tenantId: TENANT_ID,
      brokerUserId: BROKER_ID,
      sin: null,
      bankDetails: null,
    });
    mockPrisma.lead.update.mockResolvedValue({
      id: LEAD_ID,
      city: 'Toronto',
      sin: null,
      bankDetails: null,
    });

    const res = await request(app)
      .patch(`${BASE}/leads/${LEAD_ID}/profile`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ city: 'Toronto' });

    expect(res.status).toBe(200);
    expect(res.body.data.city).toBe('Toronto');
    expect(res.body.data.sin).toBeUndefined();
  });
});
