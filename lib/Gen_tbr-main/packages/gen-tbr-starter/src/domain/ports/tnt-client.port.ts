export interface ITntClient {
  notifyTenantSuspended(tenantId: string): Promise<void>;
}
