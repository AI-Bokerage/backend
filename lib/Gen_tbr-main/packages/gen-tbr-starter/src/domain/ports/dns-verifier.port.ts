export type DnsRecordKind = "TXT" | "CNAME";

export interface DnsRecord {
  kind: DnsRecordKind;
  name: string;
  expectedValue: string;
}

export interface IDnsVerifier {
  resolveTxt(hostname: string): Promise<string[][]>;
  resolveCname(hostname: string): Promise<string[]>;
}
