export interface SlaPolicyDto {
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
  createdAt: string;
  updatedAt: string;
}

export interface PaginatedPolicies {
  data: SlaPolicyDto[];
  total: number;
  page: number;
  pageSize: number;
}
