import { describe, it, expect, beforeAll } from "vitest";
import { SignJWT, generateKeyPair, exportJWK } from "jose";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { JwksJwtVerifier } from "./jwks-jwt-verifier.ts";

describe("JwksJwtVerifier", () => {
  let jwksUrl: string;
  let privateKey: CryptoKey;
  let kid: string;
  let server: http.Server;

  beforeAll(async () => {
    const { publicKey, privateKey: priv } = await generateKeyPair("RS256");
    privateKey = priv;
    const jwk = await exportJWK(publicKey);
    kid = "test-key-1";
    server = http.createServer((_req, res) => {
      res.setHeader("content-type", "application/json");
      res.end(JSON.stringify({ keys: [{ ...jwk, kid, alg: "RS256", use: "sig" }] }));
    });
    await new Promise<void>((resolve) => server.listen(0, resolve));
    const { port } = server.address() as AddressInfo;
    jwksUrl = `http://localhost:${port}/jwks.json`;
  });

  it("verifies a valid token and extracts tenant_id/roles", async () => {
    const token = await new SignJWT({ tenant_id: "11111111-1111-1111-1111-111111111111", roles: ["admin"] })
      .setProtectedHeader({ alg: "RS256", kid })
      .setSubject("user-1")
      .setIssuer("gen-auth")
      .setExpirationTime("5m")
      .sign(privateKey);

    const verifier = new JwksJwtVerifier(jwksUrl, "gen-auth");
    const claims = await verifier.verify(token);
    expect(claims.sub).toBe("user-1");
    expect(claims.tenantId).toBe("11111111-1111-1111-1111-111111111111");
    expect(claims.roles).toEqual(["admin"]);
  });

  it("rejects a token signed with the wrong issuer", async () => {
    const token = await new SignJWT({ tenant_id: "11111111-1111-1111-1111-111111111111" })
      .setProtectedHeader({ alg: "RS256", kid })
      .setSubject("user-1")
      .setIssuer("someone-else")
      .setExpirationTime("5m")
      .sign(privateKey);

    const verifier = new JwksJwtVerifier(jwksUrl, "gen-auth");
    await expect(verifier.verify(token)).rejects.toThrow();
  });
});
