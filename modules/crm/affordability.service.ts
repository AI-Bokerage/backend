import { getLeadWithAccess } from './lead.service';
import { getPrismaClient } from '../../config/database';

// ponytail: no provincial stress-test-rate table or property-tax/heating
// inputs — a flat benchmark + fixed amortization is the real formula's
// shape without a full underwriting engine. Upgrade to per-province rates
// if that distinction ever matters.
const BENCHMARK_QUALIFYING_RATE = 5.25; // Bank of Canada qualifying rate, as a constant
const STRESS_TEST_BUFFER = 2; // contract rate + 2%, per the federal stress test
const AMORTIZATION_YEARS = 25;
const TDS_LIMIT = 44; // standard Canadian Total Debt Service ceiling

export interface AffordabilityResult {
  sufficientData: boolean;
  missingFields: string[];
  tds: number | null;
  stressTestRate: number;
  maxPurchasePrice: number | null;
  passing: boolean | null;
  overBy: { field: string; excessPercent: number } | null;
}

function monthlyPaymentFor(loanAmount: number, annualRatePct: number, years: number): number {
  const r = annualRatePct / 100 / 12;
  const n = years * 12;
  if (r === 0) return loanAmount / n;
  const factor = Math.pow(1 + r, n);
  return (loanAmount * r * factor) / (factor - 1);
}

function maxLoanFor(maxMonthlyPayment: number, annualRatePct: number, years: number): number {
  const r = annualRatePct / 100 / 12;
  const n = years * 12;
  if (maxMonthlyPayment <= 0) return 0;
  if (r === 0) return maxMonthlyPayment * n;
  const factor = Math.pow(1 + r, n);
  return (maxMonthlyPayment * (factor - 1)) / (r * factor);
}

/**
 * "Ratios · stress-tested rate · maximum purchase price" (Flow 4.4). Computed
 * live from whatever's on the Lead right now — the tab gate is "enough
 * information to calculate?", not a cached value.
 */
export function computeAffordability(
  lead: {
    income: number | null;
    monthlyDebts: number | null;
    downPayment: number | null;
    propertyValue: number | null;
  },
  assumedContractRate = BENCHMARK_QUALIFYING_RATE,
): AffordabilityResult {
  const missingFields: string[] = [];
  if (lead.income === null || lead.income === undefined || lead.income <= 0)
    missingFields.push('income');
  if (lead.monthlyDebts === null || lead.monthlyDebts === undefined)
    missingFields.push('monthlyDebts');

  const stressTestRate = Math.max(
    assumedContractRate + STRESS_TEST_BUFFER,
    BENCHMARK_QUALIFYING_RATE,
  );

  if (missingFields.length > 0) {
    return {
      sufficientData: false,
      missingFields,
      tds: null,
      stressTestRate,
      maxPurchasePrice: null,
      passing: null,
      overBy: null,
    };
  }

  const grossMonthlyIncome = lead.income! / 12;
  const downPayment = lead.downPayment ?? 0;

  const maxMonthlyPayment = Math.max(
    (TDS_LIMIT / 100) * grossMonthlyIncome - lead.monthlyDebts!,
    0,
  );
  const maxLoan = maxLoanFor(maxMonthlyPayment, stressTestRate, AMORTIZATION_YEARS);
  const maxPurchasePrice = maxLoan + downPayment;

  let tds: number | null = null;
  let passing: boolean | null = null;
  let overBy: AffordabilityResult['overBy'] = null;
  if (lead.propertyValue !== null && lead.propertyValue !== undefined) {
    const loanAmount = Math.max(lead.propertyValue - downPayment, 0);
    const monthlyPayment = monthlyPaymentFor(loanAmount, stressTestRate, AMORTIZATION_YEARS);
    tds = ((monthlyPayment + lead.monthlyDebts!) / grossMonthlyIncome) * 100;
    passing = tds <= TDS_LIMIT;
    if (!passing) {
      overBy = { field: 'tds', excessPercent: Math.round((tds - TDS_LIMIT) * 100) / 100 };
    }
  }

  return {
    sufficientData: true,
    missingFields: [],
    tds: tds === null ? null : Math.round(tds * 100) / 100,
    stressTestRate,
    maxPurchasePrice: Math.round(maxPurchasePrice * 100) / 100,
    passing,
    overBy,
  };
}

interface AffordabilityInputs {
  income: number | null;
  monthlyDebts: number | null;
  downPayment: number | null;
  propertyValue: number | null;
}

/**
 * The structured intake (SOW Module 5) wins over the flat Lead fields when
 * it exists — one applicant with no income source still counts as "has
 * structured data," so a $0 household income here means exactly that, not
 * "fall back to the flat field." Income per applicant prefers their most
 * recent NOA total (verified) over an annualized base salary (self-reported).
 */
async function resolveAffordabilityInputs(lead: {
  id: string;
  income: number | null;
  monthlyDebts: number | null;
  downPayment: number | null;
  propertyValue: number | null;
}): Promise<AffordabilityInputs> {
  const applicants = await getPrismaClient().applicant.findMany({
    where: { leadId: lead.id },
    include: { employments: true, noaIncomes: true },
  });

  if (applicants.length === 0) {
    return lead;
  }

  const income = applicants.reduce((sum, applicant) => {
    const latestNoa = applicant.noaIncomes.sort((a, b) => b.taxYear - a.taxYear)[0];
    if (latestNoa) return sum + latestNoa.totalIncome;
    const latestJob = applicant.employments.sort(
      (a, b) => (b.startDate?.getTime() ?? 0) - (a.startDate?.getTime() ?? 0),
    )[0];
    return sum + (latestJob?.baseSalary ?? 0);
  }, 0);

  const debts = await getPrismaClient().debt.findMany({ where: { leadId: lead.id } });
  const monthlyDebts = debts.reduce((sum, debt) => sum + debt.monthlyPayment, 0);

  return { income, monthlyDebts, downPayment: lead.downPayment, propertyValue: lead.propertyValue };
}

export async function getAffordability(
  tenantId: string,
  leadId: string,
  allowedBrokerIds: string[],
): Promise<AffordabilityResult> {
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  const inputs = await resolveAffordabilityInputs(lead);
  return computeAffordability(inputs);
}

export interface RecalculateSummary {
  total: number;
  succeeded: number;
  failures: { leadId: string; name: string; reason: string }[];
}

/** "Recalculate everyone" — scoped to the caller's book (own or team); pass leadIds to retry just the failures. */
export async function recalculateForBook(
  tenantId: string,
  brokerIds: string[],
  leadIds?: string[],
): Promise<RecalculateSummary> {
  if (brokerIds.length === 0) return { total: 0, succeeded: 0, failures: [] };
  const leads = await getPrismaClient().lead.findMany({
    where: {
      tenantId,
      brokerUserId: { in: brokerIds },
      ...(leadIds ? { id: { in: leadIds } } : {}),
    },
  });

  const failures: RecalculateSummary['failures'] = [];
  let succeeded = 0;
  for (const lead of leads) {
    const inputs = await resolveAffordabilityInputs(lead);
    const result = computeAffordability(inputs);
    if (!result.sufficientData) {
      failures.push({
        leadId: lead.id,
        name: lead.name,
        reason: `Missing ${result.missingFields.join(', ')}`,
      });
      continue;
    }
    await getPrismaClient().lead.update({
      where: { id: lead.id },
      data: {
        affordabilitySnapshot: { ...result, calculatedAt: new Date().toISOString() } as never,
      },
    });
    succeeded += 1;
  }

  return { total: leads.length, succeeded, failures };
}
