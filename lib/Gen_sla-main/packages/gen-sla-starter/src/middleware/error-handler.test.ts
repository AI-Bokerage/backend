import { describe, it, expect, vi } from "vitest";
import { ZodError, z } from "zod";
import { errorHandler } from "./error-handler.ts";
import { AppError } from "../common/errors.ts";

function fakeRes() {
  const res: any = {};
  res.status = vi.fn(() => res);
  res.json = vi.fn(() => res);
  return res;
}

describe("errorHandler", () => {
  it("maps AppError to its statusCode/code/message", () => {
    const res = fakeRes();
    errorHandler(new AppError(404, "NOT_FOUND", "missing"), {} as any, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(404);
    expect(res.json).toHaveBeenCalledWith({ error: "NOT_FOUND", message: "missing" });
  });

  it("maps ZodError to 400 VALIDATION_ERROR", () => {
    const res = fakeRes();
    const zodErr = z.object({ x: z.string() }).safeParse({ x: 1 });
    errorHandler((zodErr as any).error as ZodError, {} as any, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(400);
  });

  it("maps unknown errors to 500 internal_error", () => {
    const res = fakeRes();
    errorHandler(new Error("boom"), {} as any, res, vi.fn());
    expect(res.status).toHaveBeenCalledWith(500);
    expect(res.json).toHaveBeenCalledWith({ error: "internal_error", message: "An unexpected error occurred" });
  });
});
