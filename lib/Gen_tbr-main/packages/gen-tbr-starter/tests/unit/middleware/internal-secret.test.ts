import { describe, it, expect } from "vitest";
import express from "express";
import request from "supertest";
import { internalSecret } from "../../../src/middleware/internal-secret.ts";
import { errorHandler } from "../../../src/middleware/error-handler.ts";

describe("internalSecret", () => {
  function buildApp(secret: string) {
    const app = express();
    app.get("/internal/ping", internalSecret(secret), (_req, res) => res.json({ ok: true }));
    app.use(errorHandler);
    return app;
  }

  it("401s when the header is missing", async () => {
    const res = await request(buildApp("s3cret")).get("/internal/ping");
    expect(res.status).toBe(401);
  });

  it("401s when the header doesn't match", async () => {
    const res = await request(buildApp("s3cret")).get("/internal/ping").set("X-Internal-Secret", "wrong");
    expect(res.status).toBe(401);
  });

  it("passes through when the header matches", async () => {
    const res = await request(buildApp("s3cret")).get("/internal/ping").set("X-Internal-Secret", "s3cret");
    expect(res.status).toBe(200);
  });
});
