// src/infra/tnt-client/tnt-client.ts
import { GenTntProvisioningError } from "../../common/errors.ts";
import type {
  ITntClient,
  TenantResponse,
  ProvisioningJobResponse,
  CreateTenantInput,
} from "../../domain/ports/tnt-client.port.ts";

export class HttpTntClient implements ITntClient {
  constructor(
    private readonly baseUrl: string,
    private readonly internalSecret: string,
  ) {}

  private headers(): Record<string, string> {
    return { "X-Internal-Secret": this.internalSecret, "Content-Type": "application/json" };
  }

  async isSlugTaken(slug: string): Promise<boolean> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/by-slug/${encodeURIComponent(slug)}`, {
      headers: this.headers(),
    });
    if (res.status === 404) return false;
    if (res.status === 200) return true;
    throw new GenTntProvisioningError(`Gen_TNT slug check failed: ${res.status}`);
  }

  async createTenant(input: CreateTenantInput): Promise<TenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify(input),
    });
    if (res.status !== 202) {
      throw new GenTntProvisioningError(`Gen_TNT createTenant failed: ${res.status} ${await res.text()}`);
    }
    return res.json() as Promise<TenantResponse>;
  }

  async getTenant(id: string): Promise<TenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${id}`, { headers: this.headers() });
    if (!res.ok) throw new GenTntProvisioningError(`Gen_TNT getTenant failed: ${res.status}`);
    return res.json() as Promise<TenantResponse>;
  }

  async getJob(jobId: string): Promise<ProvisioningJobResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/provisioning/jobs/${jobId}`, { headers: this.headers() });
    if (!res.ok) throw new GenTntProvisioningError(`Gen_TNT getJob failed: ${res.status}`);
    return res.json() as Promise<ProvisioningJobResponse>;
  }
}
