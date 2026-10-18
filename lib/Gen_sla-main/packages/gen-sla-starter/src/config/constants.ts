export const SLA_EXCHANGE = "gen-sla.events";

export const SLA_ROUTING_KEYS = {
  INSTANCE_CREATED: "sla.instance.created",
  INSTANCE_WARNING: "sla.instance.warning",
  INSTANCE_BREACHED: "sla.instance.breached",
  INSTANCE_RESOLVED: "sla.instance.resolved",
  INSTANCE_CANCELLED: "sla.instance.cancelled",
} as const;

export const SLA_STATUSES = ["ACTIVE", "WARNING", "BREACHED", "RESOLVED", "CANCELLED"] as const;
export type SlaStatus = (typeof SLA_STATUSES)[number];

export const DEFAULT_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
