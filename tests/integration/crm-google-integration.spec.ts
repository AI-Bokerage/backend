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
jest.mock('../../modules/crm/google/google-connection.service');
jest.mock('../../modules/crm/google/calendar.service');
jest.mock('../../modules/crm/google/gmail.service');

import * as calendarService from '../../modules/crm/google/calendar.service';
import * as gmailService from '../../modules/crm/google/gmail.service';
import * as connectionService from '../../modules/crm/google/google-connection.service';
import { getModAuthPerson, resolveTeam } from '../../modules/crm/modauth.client';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const BROKER_ID = '33333333-3333-3333-3333-333333333333';
const LEAD_USER_ID = '22222222-2222-2222-2222-222222222222';

function mockBroker(userId = BROKER_ID) {
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId,
    tenantId: TENANT_ID,
    role: 'BROKER',
    teamId: null,
    name: 'Bob Broker',
    active: true,
  });
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('Connect Google — Flow 4.12', () => {
  it('rejects a Tenant Admin (no "own book" to connect for)', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: 'admin-1',
      tenantId: TENANT_ID,
      role: 'TENANT_ADMIN',
      teamId: null,
      name: 'Admin',
      active: true,
    });
    const res = await request(app)
      .get(`${BASE}/integrations/google/connect`)
      .set('Authorization', `Bearer ${tokenFor('admin-1')}`);
    expect(res.status).toBe(403);
  });

  it('returns a Google consent URL for a Broker', async () => {
    mockBroker();
    (connectionService.getConnectUrl as jest.Mock).mockReturnValue(
      'https://accounts.google.com/o/oauth2/v2/auth?state=...',
    );
    const res = await request(app)
      .get(`${BASE}/integrations/google/connect`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);
    expect(res.status).toBe(200);
    expect(res.body.data.url).toContain('accounts.google.com');
  });

  it('the callback is public (no JWT needed — Google redirects the browser here)', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: BROKER_ID,
      tenantId: TENANT_ID,
      role: 'BROKER',
      teamId: null,
      name: 'Bob',
      active: true,
    });
    (connectionService.completeConnection as jest.Mock).mockResolvedValue({
      connected: true,
      googleEmail: 'bob@gmail.com',
    });

    const res = await request(app).get(
      `${BASE}/integrations/google/callback?code=auth-code-123&state=${BROKER_ID}`,
    );

    expect(res.status).toBe(200);
    expect(res.body.data.googleEmail).toBe('bob@gmail.com');
    expect(connectionService.completeConnection).toHaveBeenCalledWith(
      BROKER_ID,
      TENANT_ID,
      'auth-code-123',
    );
  });

  it('the callback 400s when state refers to an unknown user', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue(null);
    const res = await request(app).get(
      `${BASE}/integrations/google/callback?code=xyz&state=unknown-user`,
    );
    expect(res.status).toBe(400);
  });

  it('status and disconnect work', async () => {
    mockBroker();
    (connectionService.getStatus as jest.Mock).mockResolvedValue({
      connected: true,
      googleEmail: 'bob@gmail.com',
    });
    const statusRes = await request(app)
      .get(`${BASE}/integrations/google/status`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);
    expect(statusRes.body.data.connected).toBe(true);

    const disconnectRes = await request(app)
      .delete(`${BASE}/integrations/google`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);
    expect(disconnectRes.status).toBe(204);
  });
});

describe('Appointments — Flow 4.8', () => {
  const body = {
    title: 'Call with client',
    startTime: '2026-11-01T10:00:00.000Z',
    endTime: '2026-11-01T10:30:00.000Z',
  };

  it('creates an appointment, CRM-only or pushed depending on the service result', async () => {
    mockBroker();
    (calendarService.createAppointment as jest.Mock).mockResolvedValue({
      id: 'appt-1',
      pushedToGoogle: false,
    });

    const res = await request(app)
      .post(`${BASE}/appointments`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send(body);

    expect(res.status).toBe(201);
    expect(res.body.data.pushedToGoogle).toBe(false);
  });

  it('rejects a malformed body before reaching the service', async () => {
    mockBroker();
    const res = await request(app)
      .post(`${BASE}/appointments`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send({ title: 'Missing times' });
    expect(res.status).toBe(400);
    expect(calendarService.createAppointment).not.toHaveBeenCalled();
  });

  it("lists the caller's own appointments", async () => {
    mockBroker();
    (calendarService.listMyAppointments as jest.Mock).mockResolvedValue([{ id: 'appt-1' }]);
    const res = await request(app)
      .get(`${BASE}/appointments`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);
    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(1);
  });
});

describe('Team calendar — the one piece Flow 3 was missing', () => {
  it('a Team Lead sees every appointment across the team', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: LEAD_USER_ID,
      tenantId: TENANT_ID,
      role: 'TEAM_LEAD',
      teamId: 'team-1',
      name: 'Leah',
      active: true,
    });
    (resolveTeam as jest.Mock).mockResolvedValue({
      members: [],
      brokerIds: [LEAD_USER_ID, BROKER_ID],
    });
    (calendarService.listForBrokers as jest.Mock).mockResolvedValue([
      { id: 'appt-1' },
      { id: 'appt-2' },
    ]);

    const res = await request(app)
      .get(`${BASE}/team/calendar`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(2);
  });

  it('a Broker cannot reach the team calendar route', async () => {
    mockBroker();
    const res = await request(app)
      .get(`${BASE}/team/calendar`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`);
    expect(res.status).toBe(403);
  });
});

describe('Sending an email — Flow 4.7', () => {
  const body = { to: 'client@example.com', subject: 'Following up', body: 'Hi there' };

  it('400s with "Connect Gmail to send" when not connected', async () => {
    mockBroker();
    const { ApiError } = jest.requireActual('../../utils/api-error');
    (gmailService.sendEmail as jest.Mock).mockRejectedValue(
      new ApiError('Connect Gmail to send', 400),
    );

    const res = await request(app)
      .post(`${BASE}/emails/send`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send(body);

    expect(res.status).toBe(400);
  });

  it('sends successfully when connected', async () => {
    mockBroker();
    (gmailService.sendEmail as jest.Mock).mockResolvedValue(undefined);

    const res = await request(app)
      .post(`${BASE}/emails/send`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_ID)}`)
      .send(body);

    expect(res.status).toBe(204);
  });
});
