import { describe, it, expect, vi } from "vitest";
import type { Response } from "express";
import { ZodError, z } from "zod";
import { errorHandler } from "./error-handler.ts";
import { AppError } from "../common/errors.ts";

function fakeRes(): Response {
  const res = {} as Response;
  res.status = vi.fn().mockReturnValue(res);
  res.json = vi.fn().mockReturnValue(res);
  return res;
}

describe("errorHandler", () => {
  it("responds with the AppError's statusCode and code", () => {
    const res = fakeRes();
    errorHandler(new AppError(502, "UPSTREAM_FAILED", "boom"), {} as never, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(502);
    expect(res.json).toHaveBeenCalledWith({ error: "UPSTREAM_FAILED", message: "boom" });
  });

  it("responds 400 for a ZodError", () => {
    const res = fakeRes();
    let zodError: ZodError;
    try {
      z.object({ x: z.string() }).parse({});
      throw new Error("expected parse to throw");
    } catch (e) {
      zodError = e as ZodError;
    }
    errorHandler(zodError, {} as never, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(400);
  });

  it("responds 500 for an unrecognized error", () => {
    const res = fakeRes();
    errorHandler(new Error("unexpected"), {} as never, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(500);
  });
});
