import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { requireEnv, optionalEnv } from "../../../src/config/env.ts";
import { GenTbrConfigError } from "../../../src/common/errors.ts";

describe("requireEnv", () => {
  const KEY = "GEN_TBR_TEST_VAR";

  afterEach(() => {
    delete process.env[KEY];
  });

  it("returns the value when set", () => {
    process.env[KEY] = "hello";
    expect(requireEnv(KEY)).toBe("hello");
  });

  it("throws GenTbrConfigError when unset", () => {
    delete process.env[KEY];
    expect(() => requireEnv(KEY)).toThrow(GenTbrConfigError);
    expect(() => requireEnv(KEY)).toThrow(/GEN_TBR_TEST_VAR/);
  });
});

describe("optionalEnv", () => {
  it("returns undefined when unset", () => {
    expect(optionalEnv("GEN_TBR_NOT_SET")).toBeUndefined();
  });
});
