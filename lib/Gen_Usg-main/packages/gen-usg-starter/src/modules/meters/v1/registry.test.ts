import { describe, it, expect, afterEach } from "vitest";
import { registerMeter, getMeterDefinition, listRegisteredMeterCodes, _clearRegistryForTests } from "./registry.ts";

describe("meter registry", () => {
  afterEach(() => { _clearRegistryForTests(); });

  it("registers a meter with defaults applied", () => {
    registerMeter("seats", { unit: "seat" });
    expect(getMeterDefinition("seats")).toEqual({ unit: "seat", graceEligible: false, mode: "counter" });
  });

  it("registers a meter with explicit graceEligible and resource mode", () => {
    registerMeter("storage_bytes", { unit: "byte", graceEligible: true, mode: "resource" });
    expect(getMeterDefinition("storage_bytes")).toEqual({ unit: "byte", graceEligible: true, mode: "resource" });
  });

  it("returns undefined for an unregistered code", () => {
    expect(getMeterDefinition("nope")).toBeUndefined();
  });

  it("lists every registered code", () => {
    registerMeter("seats", { unit: "seat" });
    registerMeter("api_calls", { unit: "call" });
    expect(listRegisteredMeterCodes().sort()).toEqual(["api_calls", "seats"]);
  });
});
