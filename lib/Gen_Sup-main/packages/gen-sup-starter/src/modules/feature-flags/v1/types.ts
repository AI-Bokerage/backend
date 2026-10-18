export type { FmmFlagResponse as FeatureFlagDto, FmmOverrideResponse as FeatureFlagOverrideDto } from "../../../domain/ports/fmm-client.port.ts";
import type { FmmFlagPatch } from "../../../domain/ports/fmm-client.port.ts";

export type UpdateFlagInput = FmmFlagPatch & { reason: string };

export interface SetOverrideInput {
  enabled: boolean;
  config?: Record<string, unknown>;
  expiresAt?: string;
  reason: string;
}
