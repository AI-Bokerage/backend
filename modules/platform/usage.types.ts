export type UsageOutcome = 'ALLOW' | 'SOFT_WARN_80' | 'SOFT_WARN_95' | 'BLOCK';

export interface BrokerageUsageStatus {
  metric: string;
  outcome: UsageOutcome;
  /** true once BLOCK — "Has this brokerage hit its daily cost limit?" from the diagram. */
  paused: boolean;
  current: number;
  limit: number;
  pct: number;
  /** Present only while an "Allow extra for today" override is active. */
  overrideExpiresAt: string | null;
}
