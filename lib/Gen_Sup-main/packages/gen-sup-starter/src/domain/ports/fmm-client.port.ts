export interface FmmFlagResponse {
  key: string;
  moduleCode: string | null;
  defaultEnabled: boolean;
  isGradualRollout: boolean;
  rolloutPercentage: number;
  createdAt: string;
  updatedAt: string;
}

export interface FmmFlagPatch {
  moduleCode?: string | null;
  defaultEnabled?: boolean;
  isGradualRollout?: boolean;
  rolloutPercentage?: number;
}

export interface FmmOverrideResponse {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config: Record<string, unknown>;
  reason: string | null;
  expiresAt: string | null;
  createdBy: string | null;
}

export interface FmmOverrideUpsertParams {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config?: Record<string, unknown>;
  reason?: string;
  expiresAt?: string;
}

export interface FmmClientPort {
  listFlags(): Promise<FmmFlagResponse[]>;
  updateFlag(key: string, patch: FmmFlagPatch): Promise<FmmFlagResponse>;
  setOverride(params: FmmOverrideUpsertParams): Promise<FmmOverrideResponse>;
  listOverridesForTenant(tenantId: string): Promise<FmmOverrideResponse[]>;
  clearOverride(tenantId: string, flagKey: string): Promise<boolean>;
}
