import { describe, it, expect } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { z } from "zod";
import { errorHandler } from "./error-handler.ts";
import { NotificationNotFoundError } from "../common/errors.ts";

describe("errorHandler", () => {
  it("maps an AppError subclass to its statusCode/code", async () => {
    const app = express();
    app.get("/boom", () => { throw new NotificationNotFoundError("abc"); });
    app.use(errorHandler);
    const res = await request(app).get("/boom");
    expect(res.status).toBe(404);
    expect(res.body).toEqual({ error: "NOTIFICATION_NOT_FOUND", message: 'Notification "abc" not found' });
  });

  it("maps a ZodError to 400 VALIDATION_ERROR", async () => {
    const app = express();
    app.get("/validate", () => { z.string().uuid().parse("not-a-uuid"); });
    app.use(errorHandler);
    const res = await request(app).get("/validate");
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("VALIDATION_ERROR");
  });

  it("maps an unknown error to 500 INTERNAL_ERROR", async () => {
    const app = express();
    app.get("/crash", () => { throw new Error("something broke"); });
    app.use(errorHandler);
    const res = await request(app).get("/crash");
    expect(res.status).toBe(500);
    expect(res.body.error).toBe("INTERNAL_ERROR");
  });
});
