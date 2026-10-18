export interface TenantMetricsPort {
  countActive(): Promise<number>;
  countSignupsSince(date: Date): Promise<number>;
  sumActiveAndTrialMrr(): Promise<number>;
  countActiveTrials(): Promise<number>;
  countTrialsEndingBetween(start: Date, end: Date): Promise<number>;
  countChurnedSince(date: Date): Promise<number>;
  countByStatus(status: string): Promise<number>;

  // New for analytics:
  mrrByPlan(filter?: { region?: string }): Promise<Array<{ planCode: string | null; mrr: number; tenantCount: number }>>;
  mrrByRegion(filter?: { planCode?: string }): Promise<Array<{ region: string | null; mrr: number }>>;
  planDistribution(): Promise<Array<{ planCode: string | null; count: number }>>;
  trialConversion(from: Date, to: Date): Promise<{ trials: number; converted: number }>;
}

// Default adapter: read-only reporting, so an all-zero/all-empty snapshot is
// a safe fallback (unlike a mutating flow, which should hard-fail without a
// real dependency). Lets createGenSup() boot with dashboard/analytics
// enabled and no TenantMetricsPort supplied yet.
export const noopTenantMetricsPort: TenantMetricsPort = {
  countActive: async () => 0,
  countSignupsSince: async () => 0,
  sumActiveAndTrialMrr: async () => 0,
  countActiveTrials: async () => 0,
  countTrialsEndingBetween: async () => 0,
  countChurnedSince: async () => 0,
  countByStatus: async () => 0,
  mrrByPlan: async () => [],
  mrrByRegion: async () => [],
  planDistribution: async () => [],
  trialConversion: async () => ({ trials: 0, converted: 0 }),
};
