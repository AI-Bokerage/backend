import { describe, it, expect } from "vitest";
import { AppError, MeterNotRegisteredError, UsageEventOutOfWindowError } from "./errors.ts";

describe("error taxonomy", () => {
  it("MeterNotRegisteredError carries 400 and the metric code in its message", () => {
    const err = new MeterNotRegisteredError("seats");
    expect(err).toBeInstanceOf(AppError);
    expect(err.statusCode).toBe(400);
    expect(err.code).toBe("METER_NOT_REGISTERED");
    expect(err.message).toContain("seats");
  });

  it("UsageEventOutOfWindowError is a 422 with the window length in its message", () => {
    const err = new UsageEventOutOfWindowError(30);
    expect(err.statusCode).toBe(422);
    expect(err.code).toBe("USAGE_EVENT_OUT_OF_WINDOW");
    expect(err.message).toContain("30");
  });
});
