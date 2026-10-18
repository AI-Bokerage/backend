import type { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';

jest.mock('../../modules/crm/modauth.client');

import { requireModAuthRole } from '../../modules/crm/crm-auth.middleware';
import { getModAuthPerson } from '../../modules/crm/modauth.client';

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

function sign(sub: string): string {
  return jwt.sign({ sub, user_type: 'TENANT_USER' }, DEV_PRIVATE_KEY, {
    algorithm: 'RS256',
    issuer: 'https://auth.example.com',
    audience: 'example-api',
    expiresIn: '5m',
  });
}

function mockReqRes(token?: string) {
  const req = {
    headers: token ? { authorization: `Bearer ${token}` } : {},
    cookies: {},
  } as unknown as Request;
  const res = {} as Response;
  const next = jest.fn() as unknown as NextFunction;
  return { req, res, next: next as jest.Mock };
}

async function run(
  middleware: ReturnType<typeof requireModAuthRole>,
  req: Request,
  res: Response,
  next: jest.Mock,
) {
  middleware(req, res, next);
  // asyncHandler resolves on a microtask — flush it before asserting.
  await new Promise((r) => setImmediate(r));
}

describe('requireModAuthRole', () => {
  it('rejects when modules/auth has no role for this user', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue(null);
    const { req, res, next } = mockReqRes(sign('u1'));

    await run(requireModAuthRole('BROKER'), req, res, next);

    expect(next).toHaveBeenCalledWith(expect.objectContaining({ statusCode: 403 }));
  });

  it('rejects a deactivated person even with a role', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: 'u1',
      tenantId: 't1',
      role: 'BROKER',
      teamId: null,
      name: 'Bob',
      active: false,
    });
    const { req, res, next } = mockReqRes(sign('u1'));

    await run(requireModAuthRole('BROKER'), req, res, next);

    expect(next.mock.calls[0][0]).toMatchObject({
      statusCode: 403,
      message: expect.stringContaining('deactivated'),
    });
  });

  it('rejects a role not in the allowed set', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: 'u1',
      tenantId: 't1',
      role: 'TENANT_ADMIN',
      teamId: null,
      name: 'Priya',
      active: true,
    });
    const { req, res, next } = mockReqRes(sign('u1'));

    await run(requireModAuthRole('TEAM_LEAD'), req, res, next);

    expect(next.mock.calls[0][0]).toMatchObject({ statusCode: 403 });
  });

  it('attaches req.crmUser and calls next() with no error for an allowed role', async () => {
    (getModAuthPerson as jest.Mock).mockResolvedValue({
      userId: 'u1',
      tenantId: 't1',
      role: 'TEAM_LEAD',
      teamId: 'team-1',
      name: 'Leah',
      active: true,
    });
    const { req, res, next } = mockReqRes(sign('u1'));

    await run(requireModAuthRole('TEAM_LEAD'), req, res, next);

    expect(next).toHaveBeenCalledWith();
    expect(req.crmUser).toMatchObject({ userId: 'u1', role: 'TEAM_LEAD', teamId: 'team-1' });
  });
});
