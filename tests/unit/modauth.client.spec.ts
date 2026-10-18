import { getModAuthPerson, getTeamRoster, resolveTeam } from '../../modules/crm/modauth.client';

const originalFetch = global.fetch;

afterEach(() => {
  global.fetch = originalFetch;
  jest.restoreAllMocks();
});

function mockFetchOnce(status: number, body: unknown) {
  global.fetch = jest.fn().mockResolvedValue({
    status,
    ok: status >= 200 && status < 300,
    json: () => Promise.resolve(body),
  }) as unknown as typeof fetch;
}

describe('getModAuthPerson', () => {
  it('returns null on a 404 (no role assigned)', async () => {
    mockFetchOnce(404, {});
    const person = await getModAuthPerson('u1');
    expect(person).toBeNull();
  });

  it('throws a 502 ApiError on any other non-ok status', async () => {
    mockFetchOnce(500, {});
    await expect(getModAuthPerson('u1')).rejects.toMatchObject({ statusCode: 502 });
  });

  it('returns the parsed person on success', async () => {
    const body = {
      userId: 'u1',
      tenantId: 't1',
      role: 'BROKER',
      teamId: null,
      name: 'Bob',
      active: true,
    };
    mockFetchOnce(200, body);
    const person = await getModAuthPerson('u1');
    expect(person).toEqual(body);
  });

  it("sends the internal secret header, not the caller's JWT", async () => {
    mockFetchOnce(200, { userId: 'u1' });
    await getModAuthPerson('u1');
    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(init.headers['X-Internal-Secret']).toBeDefined();
    expect(init.headers.Authorization).toBeUndefined();
  });
});

describe('getTeamRoster', () => {
  it("forwards the caller's own bearer token, not the internal secret", async () => {
    mockFetchOnce(200, { id: 't1', name: 'X', teamLead: null, members: [] });
    await getTeamRoster('t1', 'caller-jwt');
    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain('/api/v1/modauth/teams/t1');
    expect(init.headers.Authorization).toBe('Bearer caller-jwt');
  });

  it('throws a 502 ApiError when modules/auth rejects the request', async () => {
    mockFetchOnce(403, {});
    await expect(getTeamRoster('t1', 'caller-jwt')).rejects.toMatchObject({ statusCode: 502 });
  });
});

describe('resolveTeam', () => {
  it('combines the team lead and members into one broker-id list', async () => {
    mockFetchOnce(200, {
      id: 't1',
      name: 'North Region',
      teamLead: { userId: 'lead-1', name: 'Leah' },
      members: [
        { userId: 'b1', name: 'Bob' },
        { userId: 'b2', name: 'Bea' },
      ],
    });

    const resolved = await resolveTeam('t1', 'caller-jwt');

    expect(resolved.brokerIds).toEqual(['lead-1', 'b1', 'b2']);
    expect(resolved.members).toHaveLength(3);
  });

  it('handles a team with no lead yet (should not happen in practice, but must not throw)', async () => {
    mockFetchOnce(200, {
      id: 't1',
      name: 'X',
      teamLead: null,
      members: [{ userId: 'b1', name: 'Bob' }],
    });

    const resolved = await resolveTeam('t1', 'caller-jwt');

    expect(resolved.brokerIds).toEqual(['b1']);
  });
});
