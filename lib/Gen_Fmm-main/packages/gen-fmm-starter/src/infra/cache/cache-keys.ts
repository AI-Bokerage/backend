export const FmmCacheKey = {
  check: (tenantId: string, flagKey: string): string => `fmm:check:${tenantId}:${flagKey}`,
  bulk: (tenantId: string): string => `fmm:flags:${tenantId}`,
  tenantCheckPrefix: (tenantId: string): string => `fmm:check:${tenantId}:`,
  tenantBulkPrefix: (tenantId: string): string => `fmm:flags:${tenantId}`,
};
