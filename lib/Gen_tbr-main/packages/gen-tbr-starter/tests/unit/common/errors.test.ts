import { describe, it, expect } from "vitest";
import {
  AppError,
  BrandingNotFoundError,
  BrandingValidationError,
  DomainNotFoundError,
  DomainAlreadyClaimedError,
  DomainVerificationFailedError,
  InvalidDomainStatusTransitionError,
  AssetStoreError,
  AssetNotFoundError,
  GenTbrConfigError,
} from "../../../src/common/errors.ts";

describe("error taxonomy", () => {
  it("BrandingNotFoundError is a 404 AppError", () => {
    const err = new BrandingNotFoundError("t-1");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(404);
    expect(err.code).toBe("BRANDING_NOT_FOUND");
  });

  it("DomainAlreadyClaimedError is a 409 AppError", () => {
    const err = new DomainAlreadyClaimedError("example.com");
    expect(err.statusCode).toBe(409);
    expect(err.code).toBe("DOMAIN_ALREADY_CLAIMED");
  });

  it("GenTbrConfigError is a plain Error, not an AppError", () => {
    const err = new GenTbrConfigError("missing DATABASE_URL");
    expect(err).toBeInstanceOf(Error);
    expect(err).not.toBeInstanceOf(AppError);
    expect(err.name).toBe("GenTbrConfigError");
  });
});
