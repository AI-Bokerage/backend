import { describe, it, expect, vi } from "vitest";
import { validateTenantId } from "./validate-tenant-id.ts";
import { TenantIdInvalidError } from "../common/errors.ts";

describe("validateTenantId", () => {
  it("calls next() when tenantId is a valid UUID", () => {
    const req = { params: { tenantId: "8400e29b-4be9-4a1e-9f3a-6a7b6e2f1a11" } } as any;
    const next = vi.fn();
    validateTenantId(req, {} as any, next);
    expect(next).toHaveBeenCalledWith();
  });

  it("calls next(err) with TenantIdInvalidError when tenantId is not a UUID", () => {
    const req = { params: { tenantId: "not-a-uuid" } } as any;
    const next = vi.fn();
    validateTenantId(req, {} as any, next);
    expect(next).toHaveBeenCalledWith(expect.any(TenantIdInvalidError));
  });
});
