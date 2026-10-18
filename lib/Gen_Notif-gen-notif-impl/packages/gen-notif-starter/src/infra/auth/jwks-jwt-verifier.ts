import { createRemoteJWKSet, jwtVerify } from "jose";
import type { IJwtVerifier, VerifiedClaims } from "../../domain/ports/jwt-verifier.port.ts";

export class JwksJwtVerifier implements IJwtVerifier {
  private readonly jwks: ReturnType<typeof createRemoteJWKSet>;

  constructor(
    private readonly jwksUrl: string,
    private readonly issuer: string,
  ) {
    this.jwks = createRemoteJWKSet(new URL(jwksUrl));
  }

  async verify(token: string): Promise<VerifiedClaims> {
    const { payload } = await jwtVerify(token, this.jwks, { issuer: this.issuer });
    const tenantId = payload["tenant_id"];
    const roles = payload["roles"];
    if (typeof payload.sub !== "string" || typeof tenantId !== "string") {
      throw new Error("JWT payload missing required sub/tenant_id claims");
    }
    return {
      sub: payload.sub,
      tenantId,
      roles: Array.isArray(roles) ? roles.map(String) : [],
    };
  }
}
