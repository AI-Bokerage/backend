import type { TntClientPort, TntTenantResponse, CreateTenantParams } from "../../domain/ports/tnt-client.port.ts";
import { logger } from "../../common/logger.ts";

export class TntHttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "TntHttpError";
  }
}

export class HttpTntClient implements TntClientPort {
  constructor(
    private readonly baseUrl: string,
    private readonly internalSecret: string,
  ) {}

  private headers(): Record<string, string> {
    return { "X-Internal-Secret": this.internalSecret, "Content-Type": "application/json" };
  }

  private async safeBody(res: Response): Promise<string> {
    try {
      return await res.text();
    } catch {
      return "";
    }
  }

  async createTenant(params: CreateTenantParams): Promise<TntTenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify(params),
    });
    if (res.status !== 202) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-tnt-client] createTenant failed");
      throw new TntHttpError(res.status, `Gen_TNT createTenant failed: ${res.status}`);
    }
    return res.json() as Promise<TntTenantResponse>;
  }

  async getTenant(id: string): Promise<TntTenantResponse | null> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${encodeURIComponent(id)}`, { headers: this.headers() });
    if (res.status === 404) return null;
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-tnt-client] getTenant failed");
      throw new TntHttpError(res.status, `Gen_TNT getTenant failed: ${res.status}`);
    }
    return res.json() as Promise<TntTenantResponse>;
  }

  async getTenantBySlug(slug: string): Promise<TntTenantResponse | null> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/by-slug/${encodeURIComponent(slug)}`, { headers: this.headers() });
    if (res.status === 404) return null;
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-tnt-client] getTenantBySlug failed");
      throw new TntHttpError(res.status, `Gen_TNT getTenantBySlug failed: ${res.status}`);
    }
    return res.json() as Promise<TntTenantResponse>;
  }

  async suspendTenant(id: string): Promise<TntTenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${encodeURIComponent(id)}/suspend`, {
      method: "PATCH",
      headers: this.headers(),
    });
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-tnt-client] suspendTenant failed");
      throw new TntHttpError(res.status, `Gen_TNT suspendTenant failed: ${res.status}`);
    }
    return res.json() as Promise<TntTenantResponse>;
  }

  async reactivateTenant(id: string): Promise<TntTenantResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/tenants/${encodeURIComponent(id)}/reactivate`, {
      method: "PATCH",
      headers: this.headers(),
    });
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-tnt-client] reactivateTenant failed");
      throw new TntHttpError(res.status, `Gen_TNT reactivateTenant failed: ${res.status}`);
    }
    return res.json() as Promise<TntTenantResponse>;
  }
}
