import type { ITntClient } from "../../domain/ports/tnt-client.port.ts";

export interface HttpTntClientConfig {
  baseUrl: string;
}

export class HttpTntClient implements ITntClient {
  constructor(private readonly config: HttpTntClientConfig) {}

  async notifyTenantSuspended(tenantId: string): Promise<void> {
    await fetch(`${this.config.baseUrl}/internal/tenants/${tenantId}/suspended`, {
      method: "POST",
    });
  }
}
