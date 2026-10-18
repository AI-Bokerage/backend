import * as dns from "node:dns/promises";
import type { IDnsVerifier } from "../../domain/ports/dns-verifier.port.ts";

function isNotFound(err: unknown): boolean {
  return typeof err === "object" && err !== null && "code" in err &&
    ["ENOTFOUND", "ENODATA", "NOTFOUND"].includes((err as { code?: string }).code ?? "");
}

export class NodeDnsVerifier implements IDnsVerifier {
  async resolveTxt(hostname: string): Promise<string[][]> {
    try {
      return await dns.resolveTxt(hostname);
    } catch (err) {
      if (isNotFound(err)) return [];
      throw err;
    }
  }

  async resolveCname(hostname: string): Promise<string[]> {
    try {
      return await dns.resolveCname(hostname);
    } catch (err) {
      if (isNotFound(err)) return [];
      throw err;
    }
  }
}
