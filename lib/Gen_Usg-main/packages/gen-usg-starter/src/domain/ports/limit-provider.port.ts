/**
 * Resolves every metric's limit for a tenant in one call (so the caller can
 * cache all of them at once). A value of -1 means unlimited.
 */
export interface ILimitProvider {
  getLimits(tenantId: string): Promise<Record<string, number>>;
}
