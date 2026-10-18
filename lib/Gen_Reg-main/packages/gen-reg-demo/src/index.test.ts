// src/index.test.ts
import { describe, it, expect, beforeEach } from "vitest";
import { createGenReg } from "@gen-ms/gen-reg-starter";

describe("gen-reg-demo boot", () => {
  beforeEach(() => {
    process.env.DATABASE_URL = "postgresql://unused/unused";
    process.env.GEN_TNT_BASE_URL = "http://localhost:8201";
    process.env.GEN_TNT_INTERNAL_SECRET = "test-secret";
    process.env.GEN_AUTH_BASE_URL = "http://localhost:8101";
    process.env.STRIPE_SECRET_KEY = "sk_test_unused";
    process.env.STRIPE_WEBHOOK_SECRET = "whsec_unused";
    process.env.VALKEY_URL = "redis://unused/unused";
  });

  it("createGenReg({}) does not throw with a full env set", () => {
    expect(() => createGenReg({})).not.toThrow();
  });

  it("the returned app responds to GET /health", async () => {
    const request = (await import("supertest")).default;
    const { app } = createGenReg({});
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
  });
});
