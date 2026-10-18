export type NotifChannel = "email" | "inapp" | "sms" | "webhook";

export interface PreferenceRecord {
  id: string;
  tenantId: string;
  userId: string;
  eventType: string;
  channel: NotifChannel;
  enabled: boolean;
  digestMode: boolean;
  createdAt: Date;
  updatedAt: Date;
}

export interface UpsertPreferenceInput {
  tenantId: string;
  userId: string;
  eventType: string;
  channel: NotifChannel;
  enabled: boolean;
  digestMode: boolean;
}

export interface ITenantPreferenceRepo {
  findAll(tenantId: string, userId: string): Promise<PreferenceRecord[]>;
  findByEventType(tenantId: string, userId: string, eventType: string): Promise<PreferenceRecord[]>;
  upsert(input: UpsertPreferenceInput): Promise<PreferenceRecord>;
}
