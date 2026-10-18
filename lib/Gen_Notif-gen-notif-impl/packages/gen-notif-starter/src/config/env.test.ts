import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { requireEnv, optionalEnv } from "./env.ts";
import { GenNotifConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  const KEY = "GEN_NOTIF_TEST_VAR";
  afterEach(() => { delete process.env[KEY]; });

  it("returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("throws GenNotifConfigError when unset", () => {
    expect(() => requireEnv(KEY)).toThrow(GenNotifConfigError);
  });
});

describe("optionalEnv", () => {
  it("falls back when unset", () => {
    expect(optionalEnv("GEN_NOTIF_TEST_UNSET", "fallback")).toBe("fallback");
  });
});
