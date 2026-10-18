export interface Plan {
  code: string;
  name: string;
  /** Price in USD cents (e.g. 2900 = $29.00). null for custom/enterprise pricing. */
  monthlyPriceCentsUsd: number | null;
  annualPriceCentsUsd: number | null;
  /** Price in INR paise (e.g. 249900 = ₹2499.00). null for custom/enterprise pricing. */
  monthlyPricePaiseInr: number | null;
  annualPricePaiseInr: number | null;
  trialDays: number;
}

export const PLANS: Plan[] = [
  {
    code: "STARTER",
    name: "Starter",
    monthlyPriceCentsUsd: 2900,
    annualPriceCentsUsd: 29000,
    monthlyPricePaiseInr: 249900,
    annualPricePaiseInr: 2499000,
    trialDays: 14,
  },
  {
    code: "PRO",
    name: "Pro",
    monthlyPriceCentsUsd: 9900,
    annualPriceCentsUsd: 99000,
    monthlyPricePaiseInr: 799900,
    annualPricePaiseInr: 7999000,
    trialDays: 14,
  },
  {
    code: "ENTERPRISE",
    name: "Enterprise",
    monthlyPriceCentsUsd: null,
    annualPriceCentsUsd: null,
    monthlyPricePaiseInr: null,
    annualPricePaiseInr: null,
    trialDays: 30,
  },
];

export function findPlanByCode(code: string): Plan | undefined {
  return PLANS.find((p) => p.code === code);
}
