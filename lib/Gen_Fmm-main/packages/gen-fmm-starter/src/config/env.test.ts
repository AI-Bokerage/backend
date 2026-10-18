import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { requireEnv, optionalEnv, intEnv } from "./env.ts";
import { GenFmmConfigError } from "../common/errors.ts";

describe("env", () => {
  const KEY = "GEN_FMM_TEST_VAR";

  beforeEach(() => { delete process.env[KEY]; });
  afterEach(() => { delete process.env[KEY]; });

  it("requireEnv returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("requireEnv throws GenFmmConfigError when unset", () => {
    expect(() => requireEnv(KEY)).toThrow(GenFmmConfigError);
  });

  it("optionalEnv returns the fallback when unset", () => {
    expect(optionalEnv(KEY, "fallback")).toBe("fallback");
  });

  it("optionalEnv returns the value when set", () => {
    process.env[KEY] = "set";
    expect(optionalEnv(KEY, "fallback")).toBe("set");
  });

  it("intEnv returns the fallback when unset", () => {
    expect(intEnv(KEY, 42)).toBe(42);
  });

  it("intEnv parses a valid positive value", () => {
    process.env[KEY] = "100";
    expect(intEnv(KEY, 1)).toBe(100);
  });

  // Regression: Number("garbage") is NaN, and NaN comparisons are always
  // false — silently disabling eviction/expiry/buffer-capping downstream
  // instead of failing at boot. Must throw synchronously instead.
  it("intEnv throws GenFmmConfigError for a non-numeric value", () => {
    process.env[KEY] = "garbage";
    expect(() => intEnv(KEY, 1)).toThrow(GenFmmConfigError);
  });

  it("intEnv throws GenFmmConfigError for zero or negative values", () => {
    process.env[KEY] = "0";
    expect(() => intEnv(KEY, 1)).toThrow(GenFmmConfigError);
    process.env[KEY] = "-5";
    expect(() => intEnv(KEY, 1)).toThrow(GenFmmConfigError);
  });
});
