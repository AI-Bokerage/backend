import { describe, it, expect, afterEach } from "vitest";
import { requireEnv, optionalEnv } from "./env.ts";
import { GenUsgConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  const KEY = "GEN_USG_TEST_VAR";
  afterEach(() => { delete process.env[KEY]; });

  it("returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("throws GenUsgConfigError when unset", () => {
    expect(() => requireEnv(KEY)).toThrow(GenUsgConfigError);
  });
});

describe("optionalEnv", () => {
  it("falls back when unset", () => {
    expect(optionalEnv("GEN_USG_TEST_UNSET", "fallback")).toBe("fallback");
  });
});
