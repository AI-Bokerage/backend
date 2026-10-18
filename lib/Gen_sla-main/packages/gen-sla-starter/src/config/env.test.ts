import { describe, it, expect, afterEach } from "vitest";
import { requireEnv } from "./env.ts";
import { GenSlaConfigError } from "../common/errors.ts";

describe("requireEnv", () => {
  const ORIGINAL_ENV = process.env.SOME_TEST_VAR;

  afterEach(() => {
    if (ORIGINAL_ENV === undefined) delete process.env.SOME_TEST_VAR;
    else process.env.SOME_TEST_VAR = ORIGINAL_ENV;
  });

  it("returns the value when the env var is set", () => {
    process.env.SOME_TEST_VAR = "hello";
    expect(requireEnv("SOME_TEST_VAR")).toBe("hello");
  });

  it("throws GenSlaConfigError with a clear message when the env var is missing", () => {
    delete process.env.SOME_TEST_VAR;
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(GenSlaConfigError);
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(/SOME_TEST_VAR/);
  });

  it("throws GenSlaConfigError when the env var is set to an empty string", () => {
    process.env.SOME_TEST_VAR = "";
    expect(() => requireEnv("SOME_TEST_VAR")).toThrow(GenSlaConfigError);
  });
});
