export interface IndexDocument {
  tenantId: string;
  entityType: string;
  entityId: string;
  title: string;
  snippet?: string;
  content?: string;
  /** Optional. Enables `removeByOwner` for GDPR-style erasure — set this to whatever "owns" the entity (e.g. the user who created it). */
  ownerId?: string;
  metadata?: Record<string, unknown>;
}

export interface ISearchIndexWriter {
  upsert(doc: IndexDocument): Promise<void>;
  bulkUpsert(docs: IndexDocument[]): Promise<void>;
  remove(tenantId: string, entityType: string, entityId: string): Promise<void>;
  /** Returns the number of documents removed. */
  removeByOwner(tenantId: string, ownerId: string): Promise<number>;
  /** Returns the number of documents removed. Scoped to one entityType — a shared backend (e.g. the pg table) can host multiple entity types, so clearing "the whole tenant" would wipe siblings a caller didn't intend to touch. */
  removeByEntityType(tenantId: string, entityType: string): Promise<number>;
}
