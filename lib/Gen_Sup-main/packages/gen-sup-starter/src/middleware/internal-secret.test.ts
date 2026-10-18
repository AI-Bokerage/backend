import { describe, it, expect, vi } from "vitest";
import type { Request, Response } from "express";
import { internalSecret } from "./internal-secret.ts";

function fakeReq(header?: string): Request {
  return { header: () => header } as unknown as Request;
}

describe("internalSecret", () => {
  it("calls next() with no error when the header matches", () => {
    const next = vi.fn();
    internalSecret("s3cret")(fakeReq("s3cret"), {} as Response, next);
    expect(next).toHaveBeenCalledWith();
  });

  it("calls next(error) when the header is missing", () => {
    const next = vi.fn();
    internalSecret("s3cret")(fakeReq(undefined), {} as Response, next);
    expect(next).toHaveBeenCalledWith(expect.objectContaining({ statusCode: 401 }));
  });

  it("calls next(error) when the header doesn't match", () => {
    const next = vi.fn();
    internalSecret("s3cret")(fakeReq("wrong"), {} as Response, next);
    expect(next).toHaveBeenCalledWith(expect.objectContaining({ statusCode: 401 }));
  });
});
