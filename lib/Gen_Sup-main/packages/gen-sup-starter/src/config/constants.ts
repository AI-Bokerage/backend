export const PLATFORM_AUDIT_EXCHANGE = "platform.audit";

export const TENANT_ROUTING_KEYS = {
  CREATED: "sup.tenant.created",
  SUSPENDED: "sup.tenant.suspended",
  REACTIVATED: "sup.tenant.reactivated",
} as const;

export const FLAG_ROUTING_KEYS = {
  UPDATED: "sup.flag.updated",
  OVERRIDE_SET: "sup.override.set",
  OVERRIDE_CLEARED: "sup.override.cleared",
} as const;

export const ANNOUNCEMENT_ROUTING_KEYS = {
  CREATED: "sup.announcement.created",
  BROADCAST: "sup.announcement.broadcast",
} as const;
