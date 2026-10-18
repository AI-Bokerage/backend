import { describe, it, expect } from "vitest";
import { generateToken, hashToken } from "./token.ts";

describe("token", () => {
  it("generates a hex token of the requested byte length", () => {
    const token = generateToken(32);
    expect(token).toMatch(/^[0-9a-f]{64}$/);
  });

  it("generates different tokens on each call", () => {
    expect(generateToken()).not.toBe(generateToken());
  });

  it("hashes the same token to the same hash deterministically", async () => {
    const token = "fixed-test-token";
    const h1 = await hashToken(token);
    const h2 = await hashToken(token);
    expect(h1).toBe(h2);
    expect(h1).not.toBe(token);
  });
});
