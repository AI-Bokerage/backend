// Illustrative only. A real deployment wires its own routing keys and handler
// logic against its own event bus — Gen_SLA doesn't assume any business event
// vocabulary (see docs/source-audit-notes.md for why the ancestor's ticket/
// approval/invoice/KT consumer handlers were not ported as fixed logic).
import type { InstanceService, EventEnvelope } from "@gen-ms/gen-sla-starter";

export function makeSampleEntityCreatedHandler(instanceService: InstanceService) {
  return async (envelope: EventEnvelope): Promise<void> => {
    const entityId = envelope.data.entity_id as string;
    const startedAt = new Date(envelope.occurred_at);
    await instanceService.createFromPolicy(envelope.tenant_id, "GENERIC_ENTITY", entityId, "DEFAULT_SLA", envelope.data, startedAt);
  };
}
