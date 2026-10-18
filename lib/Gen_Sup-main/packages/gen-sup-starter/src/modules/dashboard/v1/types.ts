export interface DashboardKpis {
  activeTenants: number;
  signupsToday: number;
  signupsThisWeek: number;
  signupsThisMonth: number;
  mrr: number;
  activeTrials: number;
  trialsEndingIn7d: number;
  churnedThisMonth: number;
  pastDueCount: number;
  suspendedCount: number;
  generatedAt: string;
}
