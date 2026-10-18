export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = new.target.name;
  }
}

export class BrandingNotFoundError extends AppError {
  constructor(tenantId: string) {
    super(404, "BRANDING_NOT_FOUND", `No branding found for tenant ${tenantId}`);
  }
}

export class BrandingValidationError extends AppError {
  constructor(message: string) {
    super(400, "BRANDING_VALIDATION_ERROR", message);
  }
}

export class DomainNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "DOMAIN_NOT_FOUND", `No domain found with id ${id}`);
  }
}

export class DomainAlreadyClaimedError extends AppError {
  constructor(domain: string) {
    super(409, "DOMAIN_ALREADY_CLAIMED", `Domain ${domain} is already claimed`);
  }
}

export class DomainVerificationFailedError extends AppError {
  constructor(domain: string) {
    super(409, "DOMAIN_VERIFICATION_FAILED", `DNS verification failed for ${domain}`);
  }
}

export class InvalidDomainStatusTransitionError extends AppError {
  constructor(from: string, to: string) {
    super(409, "INVALID_DOMAIN_STATUS_TRANSITION", `Cannot transition domain from ${from} to ${to}`);
  }
}

export class AssetStoreError extends AppError {
  constructor(message: string) {
    super(502, "ASSET_STORE_ERROR", message);
  }
}

export class AssetNotFoundError extends AppError {
  constructor(assetId: string) {
    super(404, "ASSET_NOT_FOUND", `No asset found with id ${assetId}`);
  }
}

export class GenTbrConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenTbrConfigError";
  }
}
