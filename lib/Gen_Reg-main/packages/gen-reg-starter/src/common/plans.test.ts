import { describe, it, expect } from "vitest";
import { PLANS, findPlanByCode } from "./plans.ts";

describe("plans", () => {
  it("PLANS contains at least a STARTER and a PRO plan", () => {
    expect(PLANS.some((p) => p.code === "STARTER")).toBe(true);
    expect(PLANS.some((p) => p.code === "PRO")).toBe(true);
  });

  it("findPlanByCode returns the matching plan", () => {
    const plan = findPlanByCode("STARTER");
    expect(plan).toBeDefined();
    expect(plan!.code).toBe("STARTER");
  });

  it("findPlanByCode returns undefined for an unknown code", () => {
    expect(findPlanByCode("NOT_A_PLAN")).toBeUndefined();
  });

  it("every plan has non-negative prices in both currencies or null for custom pricing", () => {
    for (const plan of PLANS) {
      if (plan.monthlyPriceCentsUsd !== null) expect(plan.monthlyPriceCentsUsd).toBeGreaterThanOrEqual(0);
      if (plan.annualPriceCentsUsd !== null) expect(plan.annualPriceCentsUsd).toBeGreaterThanOrEqual(0);
      if (plan.monthlyPricePaiseInr !== null) expect(plan.monthlyPricePaiseInr).toBeGreaterThanOrEqual(0);
      if (plan.annualPricePaiseInr !== null) expect(plan.annualPricePaiseInr).toBeGreaterThanOrEqual(0);
    }
  });
});
