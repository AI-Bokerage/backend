import type { UsgClientPort, UsgUsageSummary } from "../../domain/ports/usg-client.port.ts";
import { logger } from "../../common/logger.ts";

export class UsgHttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "UsgHttpError";
  }
}

export class HttpUsgClient implements UsgClientPort {
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

  async getSummary(tenantId: string): Promise<UsgUsageSummary> {
    const res = await fetch(`${this.baseUrl}/api/v1/usage/summary?tenantId=${encodeURIComponent(tenantId)}`);
    if (!res.ok) {
      logger.warn({ status: res.status, body: await this.safeBody(res) }, "[http-usg-client] getSummary failed");
      throw new UsgHttpError(res.status, `Gen_USG getSummary failed: ${res.status}`);
    }
    return (await res.json()) as UsgUsageSummary;
  }
}
