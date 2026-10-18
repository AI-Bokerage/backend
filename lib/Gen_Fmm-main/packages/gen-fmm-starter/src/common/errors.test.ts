import { describe, it, expect } from "vitest";
import { AppError, ConflictError, NotFoundError, BusinessRuleError, InvalidTenantIdError, GenFmmConfigError } from "./errors.ts";

describe("errors", () => {
  it("AppError carries statusCode/code/message", () => {
    const err = new AppError(418, "TEAPOT", "I'm a teapot");
    expect(err.statusCode).toBe(418);
    expect(err.code).toBe("TEAPOT");
    expect(err.message).toBe("I'm a teapot");
  });

  it("ConflictError is a 409 AppError", () => {
    const err = new ConflictError("FLAG_ALREADY_EXISTS", "dup");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(409);
    expect(err.code).toBe("FLAG_ALREADY_EXISTS");
  });

  it("NotFoundError is a 404 AppError", () => {
    expect(new NotFoundError("FLAG_NOT_FOUND", "missing").statusCode).toBe(404);
  });

  it("BusinessRuleError is a 422 AppError", () => {
    expect(new BusinessRuleError("OVERRIDE_EXPIRY_IN_PAST", "bad").statusCode).toBe(422);
  });

  it("InvalidTenantIdError is a 400 AppError naming the bad value", () => {
    const err = new InvalidTenantIdError("not-a-uuid");
    expect(err.statusCode).toBe(400);
    expect(err.code).toBe("INVALID_TENANT_ID");
    expect(err.message).toContain("not-a-uuid");
  });

  it("GenFmmConfigError is a plain Error, not an AppError", () => {
    const err = new GenFmmConfigError("missing DATABASE_URL");
    expect(err).not.toBeInstanceOf(AppError);
    expect(err.name).toBe("GenFmmConfigError");
  });
});
