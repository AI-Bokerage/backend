export interface SlaInstanceRecord {
  id: string;
  tenantId: string;
  policyId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  status: string;
  startedAt: Date;
  dueAt: Date;
  warningAt: Date;
  breachedAt: Date | null;
  resolvedAt: Date | null;
  metadata: Record<string, unknown>;
}

export interface CreateInstanceParams {
  tenantId: string;
  policyId: string;
  entityType: string;
  entityId: string;
  slaType: string;
  startedAt: Date;
  dueAt: Date;
  warningAt: Date;
  metadata: Record<string, unknown>;
}

export interface ListInstancesFilters {
  entityType?: string;
  entityId?: string;
  status?: string;
  page: number;
  pageSize: number;
}

export interface ISlaInstanceRepo {
  create(params: CreateInstanceParams): Promise<SlaInstanceRecord>;
  findById(id: string, tenantId: string): Promise<SlaInstanceRecord | null>;
  findByEntityAndType(tenantId: string, entityId: string, slaType: string): Promise<SlaInstanceRecord | null>;
  findAll(tenantId: string, filters: ListInstancesFilters): Promise<{ data: SlaInstanceRecord[]; total: number }>;
  updateStatus(
    id: string,
    tenantId: string,
    status: string,
    extra?: { breachedAt?: Date; resolvedAt?: Date },
  ): Promise<SlaInstanceRecord>;
  appendHistory(tenantId: string, instanceId: string, fromStatus: string | null, toStatus: string, note?: string): Promise<void>;
  findActiveInstancesDue(batchSize: number): Promise<SlaInstanceRecord[]>;
  findActiveInstancesNearWarning(batchSize: number): Promise<SlaInstanceRecord[]>;
  cancelByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }>;
  resolveByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }>;
  findActiveByEntityId(tenantId: string, entityId: string): Promise<SlaInstanceRecord[]>;
  createEscalation(tenantId: string, instanceId: string, level: number, eventType: string): Promise<void>;
}
