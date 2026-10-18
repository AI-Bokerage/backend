import jwt from 'jsonwebtoken';
import request from 'supertest';

// Same dev keypair as tests/unit/platform-auth.spec.ts, whose public half is
// wired into tests/setup-env.ts's AUTH_JWT_PUBLIC_KEY.
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

function superAdminToken(): string {
  return jwt.sign(
    { sub: 'super-1', user_type: 'SUPER_ADMIN', user_email: 'admin@example.com' },
    DEV_PRIVATE_KEY,
    {
      algorithm: 'RS256',
      issuer: 'https://auth.example.com',
      audience: 'example-api',
      expiresIn: '5m',
    },
  );
}

function tenantUserToken(): string {
  return jwt.sign({ sub: 'user-1', user_type: 'TENANT_USER' }, DEV_PRIVATE_KEY, {
    algorithm: 'RS256',
    issuer: 'https://auth.example.com',
    audience: 'example-api',
    expiresIn: '5m',
  });
}

jest.mock('../../modules/platform/brokerage.service');
jest.mock('../../modules/platform/usage.service');

import {
  createBrokerage,
  listBrokerages,
  suspendBrokerage,
  reactivateBrokerage,
  recordAuthEvent,
} from '../../modules/platform/brokerage.service';
import { allowExtraToday, getUsageStatus } from '../../modules/platform/usage.service';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/platform';

describe('Flow 1 — Super Admin console, HTTP layer', () => {
  describe('access control', () => {
    it('rejects an unauthenticated request', async () => {
      const res = await request(app).get(`${BASE}/brokerages`);
      expect(res.status).toBe(401);
    });

    it('rejects a non-SUPER_ADMIN token', async () => {
      const res = await request(app)
        .get(`${BASE}/brokerages`)
        .set('Authorization', `Bearer ${tenantUserToken()}`);
      expect(res.status).toBe(403);
    });
  });

  describe('POST /brokerages — "Creating a brokerage"', () => {
    it('rejects a malformed payload before ever calling the service', async () => {
      const res = await request(app)
        .post(`${BASE}/brokerages`)
        .set('Authorization', `Bearer ${superAdminToken()}`)
        .send({ name: 'A' }); // missing ownerName/ownerEmail, name too short

      expect(res.status).toBe(400);
      expect(createBrokerage).not.toHaveBeenCalled();
    });

    it('creates a brokerage and returns it as Pending', async () => {
      (createBrokerage as jest.Mock).mockResolvedValue({
        id: 'tenant-1',
        name: 'Acme Mortgages',
        slug: 'acme-mortgages',
        onboardingStatus: 'PENDING',
      });

      const res = await request(app)
        .post(`${BASE}/brokerages`)
        .set('Authorization', `Bearer ${superAdminToken()}`)
        .send({ name: 'Acme Mortgages', ownerName: 'Jordan Lee', ownerEmail: 'jordan@acme.test' });

      expect(res.status).toBe(201);
      expect(res.body.data.onboardingStatus).toBe('PENDING');
      expect(createBrokerage).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Acme Mortgages', ownerEmail: 'jordan@acme.test' }),
      );
    });

    it('surfaces the duplicate-email conflict as a 409', async () => {
      const { ApiError } = jest.requireActual('../../utils/api-error');
      (createBrokerage as jest.Mock).mockRejectedValue(
        new ApiError('That email already belongs to another account', 409),
      );

      const res = await request(app)
        .post(`${BASE}/brokerages`)
        .set('Authorization', `Bearer ${superAdminToken()}`)
        .send({ name: 'Acme Mortgages', ownerName: 'Jordan Lee', ownerEmail: 'jordan@acme.test' });

      expect(res.status).toBe(409);
    });
  });

  describe('GET /brokerages — pagination defaults', () => {
    it('lists brokerages with default page/size', async () => {
      (listBrokerages as jest.Mock).mockResolvedValue({ items: [{ id: 'tenant-1' }], total: 1 });

      const res = await request(app)
        .get(`${BASE}/brokerages`)
        .set('Authorization', `Bearer ${superAdminToken()}`);

      expect(res.status).toBe(200);
      expect(listBrokerages).toHaveBeenCalledWith(0, 20);
      expect(res.body.total).toBe(1);
    });
  });

  describe('PATCH /brokerages/:id/suspend and /reactivate', () => {
    const tenantId = '11111111-1111-1111-1111-111111111111';

    it('suspend requires a reason of at least 10 characters', async () => {
      const res = await request(app)
        .patch(`${BASE}/brokerages/${tenantId}/suspend`)
        .set('Authorization', `Bearer ${superAdminToken()}`)
        .send({ reason: 'too short' });

      expect(res.status).toBe(400);
      expect(suspendBrokerage).not.toHaveBeenCalled();
    });

    it('suspend blocks the brokerage — every one of its users is then blocked at login', async () => {
      (suspendBrokerage as jest.Mock).mockResolvedValue({ id: tenantId, status: 'SUSPENDED' });

      const res = await request(app)
        .patch(`${BASE}/brokerages/${tenantId}/suspend`)
        .set('Authorization', `Bearer ${superAdminToken()}`)
        .send({ reason: 'non-payment for 60 days' });

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('SUSPENDED');
    });

    it('reactivate restores access', async () => {
      (reactivateBrokerage as jest.Mock).mockResolvedValue({ id: tenantId, status: 'ACTIVE' });

      const res = await request(app)
        .patch(`${BASE}/brokerages/${tenantId}/reactivate`)
        .set('Authorization', `Bearer ${superAdminToken()}`)
        .send({});

      expect(res.status).toBe(200);
      expect(res.body.data.status).toBe('ACTIVE');
    });
  });

  describe('Watching AI cost', () => {
    const tenantId = '22222222-2222-2222-2222-222222222222';

    it('GET usage shows the brokerage is paused once it hits its daily limit', async () => {
      (getUsageStatus as jest.Mock).mockResolvedValue({
        paused: true,
        outcome: 'BLOCK',
        current: 1200,
        limit: 1000,
      });

      const res = await request(app)
        .get(`${BASE}/brokerages/${tenantId}/usage`)
        .set('Authorization', `Bearer ${superAdminToken()}`);

      expect(res.status).toBe(200);
      expect(res.body.data.paused).toBe(true);
    });

    it('POST allow-extra-today un-pauses it', async () => {
      (allowExtraToday as jest.Mock).mockResolvedValue({ paused: false, outcome: 'OK' });

      const res = await request(app)
        .post(`${BASE}/brokerages/${tenantId}/usage/allow-extra-today`)
        .set('Authorization', `Bearer ${superAdminToken()}`)
        .send({});

      expect(res.status).toBe(200);
      expect(res.body.data.paused).toBe(false);
      expect(allowExtraToday).toHaveBeenCalledWith(tenantId);
    });
  });
});

describe('Flow 1 — sign-in and security events from modules/auth', () => {
  const event = { action: 'auth.login.success', tenantId: null, userId: null, ip: '1.2.3.4' };

  it('records a forwarded event into the activity log', async () => {
    const res = await request(app)
      .post(`${BASE}/webhooks/auth-events`)
      .set('X-Internal-Secret', process.env.INTERNAL_SERVICE_SECRET ?? '')
      .send(event);
    expect(res.status).toBe(204);
    expect(recordAuthEvent).toHaveBeenCalledWith(
      expect.objectContaining({ action: 'auth.login.success' }),
    );
  });

  it('rejects it without the internal secret, and rejects non-auth action names', async () => {
    const noSecret = await request(app).post(`${BASE}/webhooks/auth-events`).send(event);
    expect(noSecret.status).toBe(401);

    const badAction = await request(app)
      .post(`${BASE}/webhooks/auth-events`)
      .set('X-Internal-Secret', process.env.INTERNAL_SERVICE_SECRET ?? '')
      .send({ ...event, action: 'platform.brokerage.purged' });
    expect(badAction.status).toBe(400);
  });
});
