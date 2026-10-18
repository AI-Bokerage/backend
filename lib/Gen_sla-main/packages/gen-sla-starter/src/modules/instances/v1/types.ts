export interface SlaInstanceDto {
  id: string;
  tenantId: string;
  policyId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  status: string;
  startedAt: string;
  dueAt: string;
  warningAt: string;
  breachedAt: string | null;
  resolvedAt: string | null;
  metadata: Record<string, unknown>;
}

export interface PaginatedInstances {
  data: SlaInstanceDto[];
  total: number;
  page: number;
  pageSize: number;
}
