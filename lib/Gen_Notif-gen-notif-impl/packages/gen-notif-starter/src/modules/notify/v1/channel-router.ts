import type { PreferenceRecord } from "../../../domain/ports/tenant-preference.repository.port.ts";

export interface ResolvedChannel {
  channel: PreferenceRecord["channel"];
  digestMode: boolean;
}

export function resolveChannels(prefs: PreferenceRecord[]): ResolvedChannel[] {
  const enabled = prefs.filter((p) => p.enabled).map((p) => ({ channel: p.channel, digestMode: p.digestMode }));
  if (enabled.length === 0) return [{ channel: "inapp", digestMode: false }];
  return enabled;
}
