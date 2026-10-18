import { describe, it, expect } from "vitest";
import { AppError, NotificationNotFoundError, SmsNotConfiguredError } from "./errors.ts";

describe("error taxonomy", () => {
  it("NotificationNotFoundError carries 404 and the id in its message", () => {
    const err = new NotificationNotFoundError("abc-123");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(404);
    expect(err.code).toBe("NOTIFICATION_NOT_FOUND");
    expect(err.message).toContain("abc-123");
  });

  it("SmsNotConfiguredError is a 501", () => {
    const err = new SmsNotConfiguredError();
    expect(err.statusCode).toBe(501);
    expect(err.code).toBe("SMS_NOT_CONFIGURED");
  });
});
