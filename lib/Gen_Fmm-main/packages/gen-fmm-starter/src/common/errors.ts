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

export class GenFmmConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenFmmConfigError";
  }
}

export class ConflictError extends AppError {
  constructor(code: string, message: string) {
    super(409, code, message);
  }
}

export class NotFoundError extends AppError {
  constructor(code: string, message: string) {
    super(404, code, message);
  }
}

export class BusinessRuleError extends AppError {
  constructor(code: string, message: string) {
    super(422, code, message);
  }
}

export class InvalidTenantIdError extends AppError {
  constructor(tenantId: string) {
    super(400, "INVALID_TENANT_ID", `Invalid tenantId format: ${tenantId}`);
  }
}
