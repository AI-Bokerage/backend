import type { ITenantPreferenceRepo, PreferenceRecord, UpsertPreferenceInput } from "../../../domain/ports/tenant-preference.repository.port.ts";

export class PreferenceService {
  constructor(private readonly repo: ITenantPreferenceRepo) {}

  listPreferences(tenantId: string, userId: string): Promise<PreferenceRecord[]> {
    return this.repo.findAll(tenantId, userId);
  }

  upsertPreference(input: UpsertPreferenceInput): Promise<PreferenceRecord> {
    return this.repo.upsert(input);
  }
}
