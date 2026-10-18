export interface VerifiedClaims {
  sub: string;
  tenantId: string;
  roles: string[];
}

export interface IJwtVerifier {
  verify(token: string): Promise<VerifiedClaims>;
}
