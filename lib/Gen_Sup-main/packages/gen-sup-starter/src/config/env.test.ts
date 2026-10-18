import { describe, it, expect } from "vitest";
import { requireEnv } from "./env.ts";
import { GenSupConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  it("returns the value when the env var is set", () => {
    process.env.SOME_TEST_VAR = "value";
    expect(requireEnv("SOME_TEST_VAR")).toBe("value");
    delete process.env.SOME_TEST_VAR;
  });

  it("throws GenSupConfigError when the env var is missing", () => {
    delete process.env.MISSING_TEST_VAR;
    expect(() => requireEnv("MISSING_TEST_VAR")).toThrow(GenSupConfigError);
  });
});
