import type { TenantMetricsPort } from "@gen-ms/gen-sup-starter";

const SAMPLE_TENANTS = [
  { status: "ACTIVE", mrr: 199, provisionedAt: new Date("2026-08-08"), trialEndsAt: null, planCode: "STARTER", region: "us-east-1" },
  { status: "ACTIVE", mrr: 499, provisionedAt: new Date("2026-06-01"), trialEndsAt: null, planCode: "BUSINESS", region: "eu-west-1" },
  { status: "TRIAL", mrr: 0, provisionedAt: new Date("2026-08-09"), trialEndsAt: new Date("2026-08-15"), planCode: "STARTER", region: "us-east-1" },
  { status: "PAST_DUE", mrr: 99, provisionedAt: new Date("2026-05-01"), trialEndsAt: null, planCode: "STARTER", region: "ap-south-1" },
  { status: "SUSPENDED", mrr: 0, provisionedAt: new Date("2026-01-01"), trialEndsAt: null, planCode: null, region: "ap-south-1" },
];

export const sampleTenantMetricsPort: TenantMetricsPort = {
  async countActive() {
    return SAMPLE_TENANTS.filter((t) => t.status === "ACTIVE").length;
  },
  async countSignupsSince(date) {
    return SAMPLE_TENANTS.filter((t) => t.provisionedAt >= date).length;
  },
  async sumActiveAndTrialMrr() {
    return SAMPLE_TENANTS.filter((t) => t.status === "ACTIVE" || t.status === "TRIAL").reduce((sum, t) => sum + t.mrr, 0);
  },
  async countActiveTrials() {
    return SAMPLE_TENANTS.filter((t) => t.status === "TRIAL").length;
  },
  async countTrialsEndingBetween(start, end) {
    return SAMPLE_TENANTS.filter((t) => t.trialEndsAt && t.trialEndsAt >= start && t.trialEndsAt <= end).length;
  },
  async countChurnedSince(date) {
    // ponytail: fixed sample data doesn't vary by date range — real implementations should filter by date
    return SAMPLE_TENANTS.filter((t) => t.status === "PAST_DUE" || t.status === "SUSPENDED").length;
  },
  async countByStatus(status) {
    return SAMPLE_TENANTS.filter((t) => t.status === status).length;
  },
  async mrrByPlan(filter) {
    const filtered = SAMPLE_TENANTS.filter(
      (t) => (t.status === "ACTIVE" || t.status === "TRIAL") && (!filter?.region || t.region === filter.region),
    );
    const groups = new Map<string | null, { mrr: number; tenantCount: number }>();
    for (const t of filtered) {
      const g = groups.get(t.planCode) ?? { mrr: 0, tenantCount: 0 };
      g.mrr += t.mrr;
      g.tenantCount += 1;
      groups.set(t.planCode, g);
    }
    return Array.from(groups.entries()).map(([planCode, g]) => ({ planCode, ...g }));
  },
  async mrrByRegion(filter) {
    const filtered = SAMPLE_TENANTS.filter(
      (t) => (t.status === "ACTIVE" || t.status === "TRIAL") && (!filter?.planCode || t.planCode === filter.planCode),
    );
    const groups = new Map<string | null, number>();
    for (const t of filtered) {
      groups.set(t.region, (groups.get(t.region) ?? 0) + t.mrr);
    }
    return Array.from(groups.entries()).map(([region, mrr]) => ({ region, mrr }));
  },
  async planDistribution() {
    const groups = new Map<string | null, number>();
    for (const t of SAMPLE_TENANTS) {
      groups.set(t.planCode, (groups.get(t.planCode) ?? 0) + 1);
    }
    return Array.from(groups.entries()).map(([planCode, count]) => ({ planCode, count }));
  },
  async trialConversion() {
    // ponytail: fixed sample data doesn't vary by date range — real implementations should filter by date
    const trials = SAMPLE_TENANTS.filter((t) => t.trialEndsAt !== null).length;
    return { trials, converted: 0 };
  },
};
