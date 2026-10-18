import { signToken, verifyToken } from '../../utils/jwt';

describe('JWT utilities', () => {
  it('should sign and verify access token', () => {
    const token = signToken({ sub: 'u_1', role: 'admin' }, 'access');
    const payload = verifyToken(token, 'access');

    expect(payload.sub).toBe('u_1');
    expect(payload.role).toBe('admin');
    expect(payload.tokenType).toBe('access');
  });
});
