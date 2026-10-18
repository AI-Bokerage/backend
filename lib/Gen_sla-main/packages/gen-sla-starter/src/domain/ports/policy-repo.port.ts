export interface SlaPolicyRecord {
  id: string;
  tenantId: string;
  name: string;
  entityType: string;
  slaType: string;
  durationMins: number;
  warningMins: number;
  isEnabled: boolean;
  description: string | null;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreatePolicyParams {
  tenantId: string;
  name: string;
  entityType: string;
  slaType: string;
  durationMins: number;
  warningMins: number;
  isEnabled: boolean;
  description?: string;
  createdBy?: string;
}

export interface UpdatePolicyParams {
  name?: string;
  durationMins?: number;
  warningMins?: number;
  isEnabled?: boolean;
  description?: string | null;
  updatedBy?: string;
}

export interface ListPoliciesFilters {
  entityType?: string;
  isEnabled?: boolean;
  page: number;
  pageSize: number;
}

export interface ISlaPolicyRepo {
  create(params: CreatePolicyParams): Promise<SlaPolicyRecord>;
  findById(id: string, tenantId: string): Promise<SlaPolicyRecord | null>;
  findByEntityAndType(tenantId: string, entityType: string, slaType: string): Promise<SlaPolicyRecord | null>;
  findAll(tenantId: string, filters: ListPoliciesFilters): Promise<{ data: SlaPolicyRecord[]; total: number }>;
  update(id: string, tenantId: string, params: UpdatePolicyParams): Promise<{ count: number }>;
  countActiveInstances(policyId: string, tenantId: string): Promise<number>;
  delete(id: string, tenantId: string): Promise<{ count: number }>;
}
