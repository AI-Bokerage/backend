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
jest.mock('../../config/database');

import { getPrismaClient } from '../../config/database';
import { getModAuthPerson, resolveTeam } from '../../modules/crm/modauth.client';
// eslint-disable-next-line import/order -- app must load after the mocks above are registered
import { app } from '../../app';

const BASE = '/api/v1/crm';
const TENANT_ID = 'tenant-1';
const TEAM_ID = 'team-1';
const LEAD_USER_ID = '22222222-2222-2222-2222-222222222222';
const BROKER_A = '33333333-3333-3333-3333-333333333333';
const BROKER_B_OUTSIDE_TEAM = '44444444-4444-4444-4444-444444444444';

const mockPrisma = {
  notification: { create: jest.fn() },
  lead: {
    create: jest.fn(),
    findUnique: jest.fn(),
    findMany: jest.fn(),
    update: jest.fn(),
    groupBy: jest.fn(),
  },
  task: { create: jest.fn(), findUnique: jest.fn(), findMany: jest.fn(), update: jest.fn() },
  mortgage: { create: jest.fn(), findMany: jest.fn() },
  $transaction: jest.fn(),
};

function mockBroker(userId: string) {
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId,
    tenantId: TENANT_ID,
    role: 'BROKER',
    teamId: null,
    name: 'Bob Broker',
    active: true,
  });
}

function mockTeamLead() {
  (getModAuthPerson as jest.Mock).mockResolvedValue({
    userId: LEAD_USER_ID,
    tenantId: TENANT_ID,
    role: 'TEAM_LEAD',
    teamId: TEAM_ID,
    name: 'Leah Lead',
    active: true,
  });
}

function mockRoster() {
  (resolveTeam as jest.Mock).mockResolvedValue({
    members: [
      { userId: LEAD_USER_ID, name: 'Leah Lead' },
      { userId: BROKER_A, name: 'Bob Broker' },
    ],
    brokerIds: [LEAD_USER_ID, BROKER_A],
  });
}

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
  mockPrisma.mortgage.findMany.mockResolvedValue([]);
  mockPrisma.lead.findMany.mockResolvedValue([]);
});

describe('Flow 3 — Team Lead, HTTP layer', () => {
  it('rejects without a token', async () => {
    const res = await request(app).get(`${BASE}/team/book`);
    expect(res.status).toBe(401);
  });

  it('rejects a Broker trying to reach team-scoped routes', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: 'b1',
      tenantId: TENANT_ID,
      role: 'BROKER',
      teamId: null,
      name: 'Bob',
      active: true,
    });

    const res = await request(app)
      .get(`${BASE}/team/book`)
      .set('Authorization', `Bearer ${tokenFor('b1')}`);
    expect(res.status).toBe(403);
  });

  it('"Team book" — lists every lead across the team\'s brokers', async () => {
    mockTeamLead();
    mockRoster();
    mockPrisma.lead.findMany.mockResolvedValue([{ id: 'lead-1', brokerUserId: BROKER_A }]);

    const res = await request(app)
      .get(`${BASE}/team/book`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(1);
    expect(mockPrisma.lead.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { tenantId: TENANT_ID, brokerUserId: { in: [LEAD_USER_ID, BROKER_A] } },
      }),
    );
  });

  it('"Open a record from another team?" — blocked with "You don\'t have access to this record"', async () => {
    mockTeamLead();
    mockRoster();
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-outside',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_B_OUTSIDE_TEAM,
    });

    const res = await request(app)
      .get(`${BASE}/team/book/11111111-1111-1111-1111-111111111111`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(403);
    expect(res.body.message).toContain("don't have access");
  });

  it('reassign — inside the team succeeds', async () => {
    mockTeamLead();
    mockRoster();
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: LEAD_USER_ID,
    });
    mockPrisma.lead.update.mockResolvedValue({ id: 'lead-1', brokerUserId: BROKER_A });

    const res = await request(app)
      .patch(`${BASE}/team/leads/11111111-1111-1111-1111-111111111111/reassign`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`)
      .send({ toBrokerUserId: BROKER_A });

    expect(res.status).toBe(200);
    expect(res.body.data.brokerUserId).toBe(BROKER_A);
  });

  it('reassign — outside the team is "Not selectable" (400)', async () => {
    mockTeamLead();
    mockRoster();
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: LEAD_USER_ID,
    });

    const res = await request(app)
      .patch(`${BASE}/team/leads/11111111-1111-1111-1111-111111111111/reassign`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`)
      .send({ toBrokerUserId: BROKER_B_OUTSIDE_TEAM });

    expect(res.status).toBe(400);
    expect(mockPrisma.lead.update).not.toHaveBeenCalled();
  });

  it('"Team dashboard" — pipeline, overdue count, and workload with a zero-row for an unassigned broker', async () => {
    mockTeamLead();
    (resolveTeam as jest.Mock).mockResolvedValue({
      members: [
        { userId: LEAD_USER_ID, name: 'Leah Lead' },
        { userId: BROKER_A, name: 'Bob Broker' },
        { userId: 'broker-idle', name: 'Idle Broker' },
      ],
      brokerIds: [LEAD_USER_ID, BROKER_A, 'broker-idle'],
    });
    mockPrisma.lead.groupBy.mockResolvedValue([
      { stage: 'NEW', _count: 2 },
      { brokerUserId: BROKER_A, _count: 2 },
    ]);
    mockPrisma.task.findMany.mockResolvedValue([]);

    const res = await request(app)
      .get(`${BASE}/team/dashboard`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data.workload).toHaveLength(3); // lead + 2 members
    const idle = res.body.data.workload.find(
      (w: { brokerUserId: string }) => w.brokerUserId === 'broker-idle',
    );
    expect(idle).toMatchObject({ openLeads: 0, overdueTasks: 0 });
  });

  it('creating a lead validates the body and rejects malformed input', async () => {
    mockTeamLead();
    const res = await request(app)
      .post(`${BASE}/leads`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`)
      .send({ email: 'not-a-name-field@only.test' });

    expect(res.status).toBe(400);
    expect(mockPrisma.lead.create).not.toHaveBeenCalled();
  });
});

describe("Flow 3 — a Broker's own book, HTTP layer", () => {
  it('creates a lead, then lists and fetches it back', async () => {
    mockBroker(BROKER_A);
    mockPrisma.lead.create.mockResolvedValue({
      id: 'lead-1',
      brokerUserId: BROKER_A,
      name: 'Jamie Rivera',
    });

    const createRes = await request(app)
      .post(`${BASE}/leads`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send({ name: 'Jamie Rivera' });
    expect(createRes.status).toBe(201);

    mockPrisma.lead.findMany.mockResolvedValue([{ id: 'lead-1', brokerUserId: BROKER_A }]);
    const listRes = await request(app)
      .get(`${BASE}/leads`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);
    expect(listRes.status).toBe(200);
    expect(listRes.body.data).toHaveLength(1);

    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_A,
    });
    const getRes = await request(app)
      .get(`${BASE}/leads/11111111-1111-1111-1111-111111111111`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);
    expect(getRes.status).toBe(200);
  });

  it("403s a lead the caller doesn't own", async () => {
    mockBroker(BROKER_A);
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_B_OUTSIDE_TEAM,
    });

    const res = await request(app)
      .get(`${BASE}/leads/11111111-1111-1111-1111-111111111111`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);

    expect(res.status).toBe(403);
  });

  it('moves a lead through the pipeline', async () => {
    mockBroker(BROKER_A);
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_A,
    });
    mockPrisma.lead.update.mockResolvedValue({ id: 'lead-1', stage: 'QUALIFIED' });

    const res = await request(app)
      .patch(`${BASE}/leads/11111111-1111-1111-1111-111111111111/stage`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send({ stage: 'QUALIFIED' });

    expect(res.status).toBe(200);
    expect(res.body.data.stage).toBe('QUALIFIED');
  });

  it('rejects an invalid lead id (not a UUID) before hitting the service', async () => {
    mockBroker(BROKER_A);
    const res = await request(app)
      .get(`${BASE}/leads/not-a-uuid`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);
    expect(res.status).toBe(400);
    expect(mockPrisma.lead.findUnique).not.toHaveBeenCalled();
  });

  it('creates a task, lists it, then completes it', async () => {
    mockBroker(BROKER_A);
    mockPrisma.task.create.mockResolvedValue({ id: 'task-1', title: 'Follow up' });

    const createRes = await request(app)
      .post(`${BASE}/tasks`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send({ title: 'Follow up' });
    expect(createRes.status).toBe(201);

    mockPrisma.task.findMany.mockResolvedValue([{ id: 'task-1', completed: false }]);
    const listRes = await request(app)
      .get(`${BASE}/tasks`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);
    expect(listRes.status).toBe(200);
    expect(listRes.body.data).toHaveLength(1);

    mockPrisma.task.findUnique.mockResolvedValue({
      id: 'task-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_A,
    });
    mockPrisma.task.update.mockResolvedValue({ id: 'task-1', completed: true });
    const completeRes = await request(app)
      .patch(`${BASE}/tasks/11111111-1111-1111-1111-111111111111/complete`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);
    expect(completeRes.status).toBe(200);
    expect(completeRes.body.data.completed).toBe(true);
  });

  it("403s completing a task that isn't the caller's", async () => {
    mockBroker(BROKER_A);
    mockPrisma.task.findUnique.mockResolvedValue({
      id: 'task-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_B_OUTSIDE_TEAM,
    });

    const res = await request(app)
      .patch(`${BASE}/tasks/11111111-1111-1111-1111-111111111111/complete`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);

    expect(res.status).toBe(403);
    expect(mockPrisma.task.update).not.toHaveBeenCalled();
  });
});

describe('Flow 3 — "Team tasks"', () => {
  it("lists every task across the team's brokers", async () => {
    mockTeamLead();
    mockRoster();
    mockPrisma.task.findMany.mockResolvedValue([{ id: 'task-1' }, { id: 'task-2' }]);

    const res = await request(app)
      .get(`${BASE}/team/tasks`)
      .set('Authorization', `Bearer ${tokenFor(LEAD_USER_ID)}`);

    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(2);
    expect(mockPrisma.task.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { tenantId: TENANT_ID, brokerUserId: { in: [LEAD_USER_ID, BROKER_A] } },
      }),
    );
  });

  it('a Broker cannot reach the team tasks route', async () => {
    mockBroker(BROKER_A);
    const res = await request(app)
      .get(`${BASE}/team/tasks`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);
    expect(res.status).toBe(403);
  });
});

describe('Flow 4 core — funding a lead into a client', () => {
  const mortgageBody = {
    lender: 'Acme Bank',
    interestRate: 4.5,
    balance: 300000,
    monthlyPayment: 1500,
    maturityDate: '2031-01-01T00:00:00.000Z',
  };

  it('funds a lead and the client shows up under /clients, not /leads', async () => {
    mockBroker(BROKER_A);
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_A,
    });
    mockPrisma.$transaction.mockResolvedValue([
      { id: 'mortgage-1' },
      { id: 'lead-1', stage: 'FUNDED' },
    ]);

    const fundRes = await request(app)
      .post(`${BASE}/leads/11111111-1111-1111-1111-111111111111/fund`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send(mortgageBody);

    expect(fundRes.status).toBe(200);
    expect(fundRes.body.data.stage).toBe('FUNDED');

    mockPrisma.lead.findMany.mockResolvedValue([{ id: 'lead-1', stage: 'FUNDED' }]);
    const clientsRes = await request(app)
      .get(`${BASE}/clients`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`);
    expect(clientsRes.status).toBe(200);
    expect(mockPrisma.lead.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ AND: [{ stage: 'FUNDED' }] }),
      }),
    );
  });

  it('rejects funding without mortgage details (cannot be skipped)', async () => {
    mockBroker(BROKER_A);
    const res = await request(app)
      .post(`${BASE}/leads/11111111-1111-1111-1111-111111111111/fund`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send({ lender: 'Acme Bank' }); // missing rate/balance/payment/maturityDate

    expect(res.status).toBe(400);
    expect(mockPrisma.$transaction).not.toHaveBeenCalled();
  });

  it('blocks a direct stage=FUNDED through the plain stage-update endpoint', async () => {
    mockBroker(BROKER_A);
    const res = await request(app)
      .patch(`${BASE}/leads/11111111-1111-1111-1111-111111111111/stage`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send({ stage: 'FUNDED' });

    expect(res.status).toBe(400);
    expect(mockPrisma.lead.update).not.toHaveBeenCalled();
  });

  it('adds a second mortgage to an already-funded client', async () => {
    mockBroker(BROKER_A);
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_A,
      stage: 'FUNDED',
    });
    mockPrisma.mortgage.create.mockResolvedValue({ id: 'mortgage-2' });

    const res = await request(app)
      .post(`${BASE}/leads/11111111-1111-1111-1111-111111111111/mortgages`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send(mortgageBody);

    expect(res.status).toBe(201);
    expect(res.body.data.id).toBe('mortgage-2');
  });

  it('rejects adding a mortgage to a lead that is not yet funded', async () => {
    mockBroker(BROKER_A);
    mockPrisma.lead.findUnique.mockResolvedValue({
      id: 'lead-1',
      tenantId: TENANT_ID,
      brokerUserId: BROKER_A,
      stage: 'NEW',
    });

    const res = await request(app)
      .post(`${BASE}/leads/11111111-1111-1111-1111-111111111111/mortgages`)
      .set('Authorization', `Bearer ${tokenFor(BROKER_A)}`)
      .send(mortgageBody);

    expect(res.status).toBe(400);
    expect(mockPrisma.mortgage.create).not.toHaveBeenCalled();
  });
});

describe('Assistant (SOW 2.5) — view/edit the team book, never create/delete/reveal', () => {
  const ASSISTANT_ID = '55555555-5555-5555-5555-555555555555';
  const LEAD_ID = '11111111-1111-1111-1111-111111111111';

  function mockAssistant(teamId: string | null = TEAM_ID) {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: ASSISTANT_ID,
      tenantId: TENANT_ID,
      role: 'ASSISTANT',
      teamId,
      name: 'Ann Assistant',
      active: true,
    });
  }

  function auth() {
    return { Authorization: `Bearer ${tokenFor(ASSISTANT_ID)}` };
  }

  it("lists the whole team's leads, not just their own", async () => {
    mockAssistant();
    mockRoster();
    mockPrisma.lead.findMany.mockResolvedValue([]);

    const res = await request(app).get(`${BASE}/leads`).set(auth());

    expect(res.status).toBe(200);
    expect(mockPrisma.lead.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ brokerUserId: { in: [LEAD_USER_ID, BROKER_A] } }),
      }),
    );
  });

  it("opens a teammate's lead but gets 403 outside the team", async () => {
    mockAssistant();
    mockRoster();
    mockPrisma.lead.findUnique.mockResolvedValueOnce({
      id: LEAD_ID,
      tenantId: TENANT_ID,
      brokerUserId: BROKER_A,
      sin: null,
      bankDetails: null,
    });
    const inside = await request(app).get(`${BASE}/leads/${LEAD_ID}`).set(auth());
    expect(inside.status).toBe(200);

    mockPrisma.lead.findUnique.mockResolvedValueOnce({
      id: LEAD_ID,
      tenantId: TENANT_ID,
      brokerUserId: BROKER_B_OUTSIDE_TEAM,
      sin: null,
      bankDetails: null,
    });
    const outside = await request(app).get(`${BASE}/leads/${LEAD_ID}`).set(auth());
    expect(outside.status).toBe(403);
  });

  it('cannot create a lead, delete intake records, or reveal sensitive fields', async () => {
    mockAssistant();
    mockRoster();

    const create = await request(app).post(`${BASE}/leads`).set(auth()).send({});
    const del = await request(app).delete(`${BASE}/leads/${LEAD_ID}/debts/${LEAD_ID}`).set(auth());
    const reveal = await request(app)
      .post(`${BASE}/leads/${LEAD_ID}/reveal`)
      .set(auth())
      .send({ field: 'sin' });

    expect([create.status, del.status, reveal.status]).toEqual([403, 403, 403]);
  });

  it('is rejected when not assigned to a team', async () => {
    mockAssistant(null);

    const res = await request(app).get(`${BASE}/leads`).set(auth());

    expect(res.status).toBe(403);
    expect(resolveTeam).not.toHaveBeenCalled();
  });
});
