import type { Theme } from "../../common/types.ts";

export interface BrandingRecord {
  tenantId: string;
  displayName: string;
  tagline: string | null;
  logoUrl: string | null;
  logoDarkUrl: string | null;
  faviconUrl: string | null;
  primaryColor: string | null;
  secondaryColor: string | null;
  accentColor: string | null;
  fontFamily: string | null;
  theme: Theme;
  rawMeta: unknown;
  updatedAt: Date;
}

export interface UpsertBrandingInput {
  tenantId: string;
  displayName: string;
  tagline?: string | null;
  logoUrl?: string | null;
  logoDarkUrl?: string | null;
  faviconUrl?: string | null;
  primaryColor?: string | null;
  secondaryColor?: string | null;
  accentColor?: string | null;
  fontFamily?: string | null;
  theme?: Theme;
  rawMeta?: unknown;
}

export type PatchBrandingInput = Partial<Omit<UpsertBrandingInput, "tenantId">>;

export interface ITenantBrandingRepo {
  findByTenantId(tenantId: string): Promise<BrandingRecord | null>;
  upsert(input: UpsertBrandingInput): Promise<BrandingRecord>;
  patch(tenantId: string, input: PatchBrandingInput): Promise<BrandingRecord | null>;
}
