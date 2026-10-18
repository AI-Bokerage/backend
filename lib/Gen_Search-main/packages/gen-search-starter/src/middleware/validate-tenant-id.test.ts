import { describe, it, expect, vi } from "vitest";
import { validateTenantId } from "./validate-tenant-id.ts";
import { TenantIdInvalidError } from "../common/errors.ts";

function fakeReqRes(tenantId: string) {
  const req = { params: { tenantId } } as any;
  const res = {} as any;
  const next = vi.fn();
  return { req, res, next };
}

describe("validateTenantId", () => {
  it("calls next() with no error for a valid UUID", () => {
    const { req, res, next } = fakeReqRes("8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11");
    validateTenantId(req, res, next);
    expect(next).toHaveBeenCalledWith();
  });

  it("calls next(err) with TenantIdInvalidError for a non-UUID", () => {
    const { req, res, next } = fakeReqRes("not-a-uuid");
    validateTenantId(req, res, next);
    expect(next).toHaveBeenCalledWith(expect.any(TenantIdInvalidError));
  });
});
