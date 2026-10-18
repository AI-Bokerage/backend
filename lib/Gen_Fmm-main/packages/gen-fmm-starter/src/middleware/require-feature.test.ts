import { describe, it, expect, vi } from "vitest";
import type { Request, Response, NextFunction } from "express";
import { requireFeatureMiddleware } from "./require-feature.ts";
import { AppError } from "../common/errors.ts";
import type { EntitlementService } from "../modules/entitlement/v1/service.ts";

function fakeReq(tenantId?: string): Request {
  return { tenantId } as unknown as Request;
}

describe("requireFeatureMiddleware", () => {
  it("calls next(AppError 401) when req.tenantId is not set", async () => {
    const entitlementService = { check: vi.fn() } as unknown as EntitlementService;
    const middleware = requireFeatureMiddleware("new_dashboard", entitlementService);
    const next = vi.fn() as NextFunction;
    await middleware(fakeReq(undefined), {} as Response, next);
    expect(next).toHaveBeenCalledWith(expect.objectContaining({ statusCode: 401 }));
  });

  it("calls next(AppError 403) when the feature resolves disabled", async () => {
    const entitlementService = {
      check: vi.fn(async () => ({ enabled: false, reason: "FLAG_DEFAULT" })),
    } as unknown as EntitlementService;
    const middleware = requireFeatureMiddleware("new_dashboard", entitlementService);
    const next = vi.fn() as NextFunction;
    await middleware(fakeReq("t1"), {} as Response, next);
    const err = next.mock.calls[0]?.[0] as AppError;
    expect(err.statusCode).toBe(403);
    expect(err.code).toBe("FEATURE_DISABLED");
  });

  it("calls next() with no error when the feature resolves enabled", async () => {
    const entitlementService = {
      check: vi.fn(async () => ({ enabled: true, reason: "FLAG_DEFAULT" })),
    } as unknown as EntitlementService;
    const middleware = requireFeatureMiddleware("new_dashboard", entitlementService);
    const next = vi.fn() as NextFunction;
    await middleware(fakeReq("t1"), {} as Response, next);
    expect(next).toHaveBeenCalledWith();
  });
});
