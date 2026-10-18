import type { UsgUsageSummary } from "../../../domain/ports/usg-client.port.ts";

export interface RevenueBreakdownByPlan {
  planCode: string | null;
  mrr: number;
  tenantCount: number;
}

export interface RevenueBreakdownByRegion {
  region: string | null;
  mrr: number;
}

export interface PlanDistributionEntry {
  planCode: string | null;
  count: number;
  percentage: number;
}

export interface TrialConversionStats {
  trials: number;
  converted: number;
  rate: number;
}

export interface RevenueSnapshotDto {
  totalMrr: number;
  totalArr: number;
  averageRevenuePerTenant: number;
  byPlan: RevenueBreakdownByPlan[];
  byRegion: RevenueBreakdownByRegion[];
  planDistribution: PlanDistributionEntry[];
  trialConversion: TrialConversionStats;
  generatedAt: string;
}

export interface RevenueFilter {
  planCode?: string;
  region?: string;
  from?: Date;
  to?: Date;
}

export type SnapshotPeriod = "daily" | "weekly" | "monthly";

export interface RevenueHistoryQuery {
  period: SnapshotPeriod;
  from?: Date;
  to?: Date;
}

export interface RevenueHistoryEntry {
  id: string;
  period: string;
  capturedAt: string;
  totalMrr: number;
  totalArr: number;
  averageRevenuePerTenant: number;
  byPlan: RevenueBreakdownByPlan[];
  byRegion: RevenueBreakdownByRegion[];
  planDistribution: PlanDistributionEntry[];
}

export interface UsageAcrossTenantsResult {
  tenants: UsgUsageSummary[];
  failures: Array<{ tenantId: string; error: string }>;
  totals: Record<string, number>;
}
