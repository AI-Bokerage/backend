jest.mock('../../config/database');
jest.mock('../../modules/crm/google/google-oauth.client');

import { getPrismaClient } from '../../config/database';
import {
  completeConnection,
  disconnect,
  getAuthenticatedClientOrNull,
  getConnectUrl,
  getStatus,
  requireAuthenticatedClient,
} from '../../modules/crm/google/google-connection.service';
import {
  buildAuthUrl,
  clientFor,
  exchangeCodeForTokens,
} from '../../modules/crm/google/google-oauth.client';

const mockPrisma = {
  googleConnection: {
    findUnique: jest.fn(),
    upsert: jest.fn(),
    deleteMany: jest.fn(),
    update: jest.fn(),
  },
};

beforeEach(() => {
  jest.clearAllMocks();
  (getPrismaClient as jest.Mock).mockReturnValue(mockPrisma);
});

describe('getConnectUrl', () => {
  it('carries the userId through as state', () => {
    (buildAuthUrl as jest.Mock).mockReturnValue('https://accounts.google.com/o/oauth2/v2/auth?...');
    getConnectUrl('user-1');
    expect(buildAuthUrl).toHaveBeenCalledWith('user-1');
  });
});

describe('completeConnection', () => {
  it('encrypts both tokens before storing, never the raw values', async () => {
    (exchangeCodeForTokens as jest.Mock).mockResolvedValue({
      accessToken: 'raw-access-token',
      refreshToken: 'raw-refresh-token',
      expiresAt: new Date(),
      scope: 'calendar.events gmail.send',
      googleEmail: 'broker@gmail.com',
    });
    mockPrisma.googleConnection.upsert.mockResolvedValue({});

    const result = await completeConnection('user-1', 'tenant-1', 'auth-code');

    expect(result).toEqual({ connected: true, googleEmail: 'broker@gmail.com' });
    const upsertArg = mockPrisma.googleConnection.upsert.mock.calls[0][0];
    expect(upsertArg.create.accessToken).not.toBe('raw-access-token');
    expect(upsertArg.create.refreshToken).not.toBe('raw-refresh-token');
  });
});

describe('disconnect / getStatus', () => {
  it('reports not connected when no row exists', async () => {
    mockPrisma.googleConnection.findUnique.mockResolvedValue(null);
    const status = await getStatus('user-1');
    expect(status).toEqual({ connected: false, googleEmail: null });
  });

  it('reports connected with the stored email', async () => {
    mockPrisma.googleConnection.findUnique.mockResolvedValue({ googleEmail: 'broker@gmail.com' });
    const status = await getStatus('user-1');
    expect(status).toEqual({ connected: true, googleEmail: 'broker@gmail.com' });
  });

  it('removes the connection row', async () => {
    mockPrisma.googleConnection.deleteMany.mockResolvedValue({ count: 1 });
    await disconnect('user-1');
    expect(mockPrisma.googleConnection.deleteMany).toHaveBeenCalledWith({
      where: { userId: 'user-1' },
    });
  });
});

describe('getAuthenticatedClientOrNull / requireAuthenticatedClient', () => {
  it('returns null when nothing is connected', async () => {
    mockPrisma.googleConnection.findUnique.mockResolvedValue(null);
    const result = await getAuthenticatedClientOrNull('user-1');
    expect(result).toBeNull();
  });

  it('decrypts stored tokens before handing them to clientFor', async () => {
    const { encryptToken } = jest.requireActual('../../modules/crm/encryption');
    mockPrisma.googleConnection.findUnique.mockResolvedValue({
      accessToken: encryptToken('real-access-token'),
      refreshToken: encryptToken('real-refresh-token'),
      googleEmail: 'broker@gmail.com',
    });
    (clientFor as jest.Mock).mockReturnValue('fake-oauth2-client');

    const result = await getAuthenticatedClientOrNull('user-1');

    expect(clientFor).toHaveBeenCalledWith(
      'real-access-token',
      'real-refresh-token',
      expect.any(Function),
    );
    expect(result).toEqual({ client: 'fake-oauth2-client', googleEmail: 'broker@gmail.com' });
  });

  it('requireAuthenticatedClient throws "Connect Gmail to send" when not connected', async () => {
    mockPrisma.googleConnection.findUnique.mockResolvedValue(null);
    await expect(requireAuthenticatedClient('user-1')).rejects.toMatchObject({
      statusCode: 400,
      message: 'Connect Gmail to send',
    });
  });
});
