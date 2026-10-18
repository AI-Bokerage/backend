import type { TenantRecord } from './platform.types';
import { env } from '../../config/env';
import { ApiError } from '../../utils/api-error';

const baseUrl = env.platform.tenantServiceBaseUrl;
const headers = {
  'Content-Type': 'application/json',
  'X-Internal-Secret': env.platform.internalSecret,
};

async function call<T>(path: string, method: string): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, { method, headers });
  if (!res.ok) {
    const body = await res.text();
    throw new ApiError(
      `Tenant service call failed: ${method} ${path} -> ${res.status} ${body}`,
      502,
    );
  }
  return (await res.json()) as T;
}

interface TenantPage {
  content: TenantRecord[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * Fills the gaps gen-sup-starter's own TntClientPort/HttpTntClient don't
 * cover (list, cancel, purge) — create/get/suspend/reactivate go through
 * gen-sup-starter's TenantsService instead, see brokerage.service.ts.
 */
export const tenantServiceGaps = {
  list: (page: number, size: number) =>
    call<TenantPage>(`/api/v1/tenants?page=${page}&size=${size}`, 'GET'),
  cancel: (id: string) => call<TenantRecord>(`/api/v1/tenants/${id}/cancel`, 'PATCH'),
  purge: (id: string) => call<TenantRecord>(`/api/v1/tenants/${id}/purge`, 'PATCH'),
};
