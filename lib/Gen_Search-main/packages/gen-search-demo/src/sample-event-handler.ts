// Illustrative only. A real deployment wires its own routing keys, its own
// resolveDocument mapping, and its own reindexSource against its own event
// bus and services — Gen_SEARCH doesn't assume any business event
// vocabulary or own any source-of-truth data (see docs/source-audit-notes.md).
import type { IndexDocument, IndexerEventEnvelope } from "@gen-ms/gen-search-starter";

export function resolveLeadDocument(envelope: IndexerEventEnvelope): IndexDocument | null {
  if (!envelope.event_type.startsWith("lead.")) return null;
  return {
    tenantId: envelope.tenant_id,
    entityType: "lead",
    entityId: envelope.data.id as string,
    title: envelope.data.company_name as string,
    snippet: envelope.data.contact_name as string | undefined,
    ownerId: envelope.data.owner_id as string | undefined,
  };
}

// A real reindexSource fetches every current row from the owning service
// (e.g. paginating CRM-SVC's own leads table) and yields one IndexDocument
// per row — this library never has that data itself.
export async function* sampleReindexSource(tenantId: string): AsyncIterable<IndexDocument> {
  const leads: { id: string; company_name: string }[] = []; // fetch from your own service here
  for (const lead of leads) {
    yield { tenantId, entityType: "lead", entityId: lead.id, title: lead.company_name };
  }
}
