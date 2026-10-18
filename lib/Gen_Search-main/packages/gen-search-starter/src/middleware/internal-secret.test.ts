import { describe, it, expect, vi } from "vitest";
import { internalSecret } from "./internal-secret.ts";
import { AppError } from "../common/errors.ts";

function fakeReqRes(headerValue?: string) {
  const req = { header: vi.fn(() => headerValue) } as any;
  const res = {} as any;
  const next = vi.fn();
  return { req, res, next };
}

describe("internalSecret", () => {
  it("calls next() with no error when the header matches", () => {
    const { req, res, next } = fakeReqRes("correct-secret");
    internalSecret("correct-secret")(req, res, next);
    expect(next).toHaveBeenCalledWith();
  });

  it("calls next(err) with a 401 AppError when the header is missing", () => {
    const { req, res, next } = fakeReqRes(undefined);
    internalSecret("correct-secret")(req, res, next);
    expect(next).toHaveBeenCalledWith(expect.any(AppError));
    const err = next.mock.calls[0][0] as AppError;
    expect(err.statusCode).toBe(401);
  });

  it("calls next(err) with a 401 AppError when the header doesn't match", () => {
    const { req, res, next } = fakeReqRes("wrong-secret");
    internalSecret("correct-secret")(req, res, next);
    expect(next).toHaveBeenCalledWith(expect.any(AppError));
  });
});
