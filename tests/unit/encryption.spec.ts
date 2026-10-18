import { decryptToken, encryptToken } from '../../modules/crm/encryption';

describe('encryption — Google tokens and sensitive lead fields are never stored in plaintext', () => {
  it('round-trips a token through encrypt/decrypt', () => {
    const plaintext = 'ya29.a0AfH6SMB_fake_access_token_value';
    const encrypted = encryptToken(plaintext);
    expect(encrypted).not.toContain(plaintext);
    expect(decryptToken(encrypted)).toBe(plaintext);
  });

  it('produces different ciphertext for the same plaintext each time (random IV)', () => {
    const plaintext = 'same-token';
    const a = encryptToken(plaintext);
    const b = encryptToken(plaintext);
    expect(a).not.toBe(b);
    expect(decryptToken(a)).toBe(plaintext);
    expect(decryptToken(b)).toBe(plaintext);
  });

  it('fails to decrypt tampered ciphertext (auth tag check)', () => {
    const encrypted = encryptToken('a-real-refresh-token');
    const tampered = encrypted.slice(0, -4) + 'abcd';
    expect(() => decryptToken(tampered)).toThrow();
  });
});
