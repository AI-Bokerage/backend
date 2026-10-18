import { describe, it, expect } from "vitest";
import express from "express";
import request from "supertest";
import { z } from "zod";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { BrandingNotFoundError } from "../../../src/common/errors.ts";

describe("errorHandler", () => {
  it("maps an AppError to its statusCode and { error, message } body", async () => {
    const app = express();
    app.get("/boom", () => {
      throw new BrandingNotFoundError("tenant-1");
    });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "BRANDING_NOT_FOUND", message: expect.stringContaining("tenant-1") });
  });

  it("maps an unknown Error to a 500 with a generic message", async () => {
    const app = express();
    app.get("/boom", () => {
      throw new Error("something unexpected");
    });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(500);
    expect(res.body.error).toBe("INTERNAL_ERROR");
  });

  it("maps a ZodError to a 400 with error: VALIDATION_ERROR", async () => {
    const app = express();
    app.get("/boom", () => {
      z.string().uuid().parse("not-a-uuid");
    });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });
});
