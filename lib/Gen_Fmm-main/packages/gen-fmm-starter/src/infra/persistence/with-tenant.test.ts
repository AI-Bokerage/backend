import { describe, it, expect } from "vitest";
import { withTenant } from "./with-tenant.ts";
import { InvalidTenantIdError } from "../../common/errors.ts";

describe("withTenant", () => {
  it("rejects a non-UUID tenantId before touching the database", async () => {
    await expect(withTenant("not-a-uuid", async () => "unreachable")).rejects.toThrow(InvalidTenantIdError);
  });

  it("rejects an empty tenantId", async () => {
    await expect(withTenant("", async () => "unreachable")).rejects.toThrow(InvalidTenantIdError);
  });
});
