import type {
  FmmClientPort,
  FmmFlagResponse,
  FmmFlagPatch,
  FmmOverrideResponse,
  FmmOverrideUpsertParams,
} from "../../domain/ports/fmm-client.port.ts";
import { logger } from "../../common/logger.ts";

export class FmmHttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "FmmHttpError";
  }
}

export class HttpFmmClient implements FmmClientPort {
  constructor(private readonly baseUrl: string) {}

  // IMPORTANT: this must call res.text() directly, never itself. The tenants
  // module's HttpTntClient once had a version of this that called
  // `this.safeBody(res)` instead of `res.text()` — infinite recursion caught
  // by its own try/catch, silently always logging an empty body.
  private async safeBody(res: Response): Promise<string> {
    try {
      return await res.text();
    } catch {
      return "";
    }
  }

  async listFlags(): Promise<FmmFlagResponse[]> {
    const res = await fetch(`${this.baseUrl}/api/v1/catalog/flags`);
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] listFlags failed");
      throw new FmmHttpError(res.status, `Gen_FMM listFlags failed: ${res.status}`);
    }
    const { flags } = (await res.json()) as { flags: FmmFlagResponse[] };
    return flags;
  }

  async updateFlag(key: string, patch: FmmFlagPatch): Promise<FmmFlagResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/catalog/flags/${encodeURIComponent(key)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    });
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] updateFlag failed");
      throw new FmmHttpError(res.status, `Gen_FMM updateFlag failed: ${res.status}`);
    }
    const { flag } = (await res.json()) as { flag: FmmFlagResponse };
    return flag;
  }

  async setOverride(params: FmmOverrideUpsertParams): Promise<FmmOverrideResponse> {
    const res = await fetch(`${this.baseUrl}/api/v1/overrides`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(params),
    });
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] setOverride failed");
      throw new FmmHttpError(res.status, `Gen_FMM setOverride failed: ${res.status}`);
    }
    const { override } = (await res.json()) as { override: FmmOverrideResponse };
    return override;
  }

  async listOverridesForTenant(tenantId: string): Promise<FmmOverrideResponse[]> {
    const res = await fetch(`${this.baseUrl}/api/v1/overrides/${encodeURIComponent(tenantId)}`);
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] listOverridesForTenant failed");
      throw new FmmHttpError(res.status, `Gen_FMM listOverridesForTenant failed: ${res.status}`);
    }
    const { overrides } = (await res.json()) as { overrides: FmmOverrideResponse[] };
    return overrides;
  }

  async clearOverride(tenantId: string, flagKey: string): Promise<boolean> {
    const res = await fetch(
      `${this.baseUrl}/api/v1/overrides/${encodeURIComponent(tenantId)}/${encodeURIComponent(flagKey)}`,
      { method: "DELETE" },
    );
    if (res.status === 404) return false;
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-fmm-client] clearOverride failed");
      throw new FmmHttpError(res.status, `Gen_FMM clearOverride failed: ${res.status}`);
    }
    return true;
  }
}
