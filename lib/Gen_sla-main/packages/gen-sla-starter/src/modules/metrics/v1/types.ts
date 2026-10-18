export interface SlaSummary {
  totalActive: number;
  totalWarning: number;
  totalBreached: number;
  totalResolved: number;
  totalCancelled: number;
}

export interface SlaComplianceRate {
  entityType: string;
  slaType: string;
  total: number;
  breached: number;
  resolved: number;
  compliancePct: number;
}

export interface SlaBreachRecord {
  instanceId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  breachedAt: string;
  dueAt: string;
  overdueMs: number;
}

export interface SlaTrendPoint {
  date: string;
  created: number;
  breached: number;
  resolved: number;
}
