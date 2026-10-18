import { Client } from "@opensearch-project/opensearch";
import type { ISearchIndexWriter, IndexDocument } from "../../domain/ports/search-index.port.ts";
import type { ISearchQueryEngine, SearchQueryParams, SearchResultItem } from "../../domain/ports/search-query.port.ts";

export interface OpenSearchAdapterOptions {
  url: string;
  username?: string;
  password?: string;
  indexPrefix: string;
}

// One index per entity type (`${indexPrefix}${entityType}`), tenant_id as a
// filtered field within each doc — not one index per tenant. Matches the PG
// adapter's shape (one table, `tenant_id` column filters at query time)
// instead of the index-per-tenant explosion an index-per-tenant scheme would
// cause as tenant count grows.
export class OpenSearchAdapter implements ISearchIndexWriter, ISearchQueryEngine {
  private readonly client: Client;

  constructor(private readonly options: OpenSearchAdapterOptions) {
    this.client = new Client({
      node: options.url,
      ...(options.username && options.password ? { auth: { username: options.username, password: options.password } } : {}),
    });
  }

  private indexName(entityType: string): string {
    return `${this.options.indexPrefix}${entityType}`;
  }

  private docId(tenantId: string, entityId: string): string {
    return `${tenantId}:${entityId}`;
  }

  async upsert(doc: IndexDocument): Promise<void> {
    await this.client.index({
      index: this.indexName(doc.entityType),
      id: this.docId(doc.tenantId, doc.entityId),
      body: {
        tenant_id: doc.tenantId,
        entity_type: doc.entityType,
        entity_id: doc.entityId,
        title: doc.title,
        snippet: doc.snippet ?? null,
        content: doc.content ?? null,
        owner_id: doc.ownerId ?? null,
      },
      refresh: false,
    });
  }

  async bulkUpsert(docs: IndexDocument[]): Promise<void> {
    if (docs.length === 0) return;
    const body = docs.flatMap((doc) => [
      { index: { _index: this.indexName(doc.entityType), _id: this.docId(doc.tenantId, doc.entityId) } },
      {
        tenant_id: doc.tenantId,
        entity_type: doc.entityType,
        entity_id: doc.entityId,
        title: doc.title,
        snippet: doc.snippet ?? null,
        content: doc.content ?? null,
        owner_id: doc.ownerId ?? null,
      },
    ]);
    await this.client.bulk({ body, refresh: false });
  }

  async remove(tenantId: string, entityType: string, entityId: string): Promise<void> {
    try {
      await this.client.delete({ index: this.indexName(entityType), id: this.docId(tenantId, entityId) });
    } catch (err) {
      // 404 (already gone) is not an error for a remove operation.
      if (!(err && typeof err === "object" && "statusCode" in err && (err as { statusCode: number }).statusCode === 404)) {
        throw err;
      }
    }
  }

  async removeByOwner(tenantId: string, ownerId: string): Promise<number> {
    const res = await this.client.deleteByQuery({
      index: `${this.options.indexPrefix}*`,
      body: { query: { bool: { filter: [{ term: { tenant_id: tenantId } }, { term: { owner_id: ownerId } }] } } },
    });
    return (res.body.deleted as number | undefined) ?? 0;
  }

  async removeByEntityType(tenantId: string, entityType: string): Promise<number> {
    const res = await this.client.deleteByQuery({
      index: this.indexName(entityType),
      body: { query: { bool: { filter: [{ term: { tenant_id: tenantId } }] } } },
    });
    return (res.body.deleted as number | undefined) ?? 0;
  }

  async search(params: SearchQueryParams): Promise<SearchResultItem[]> {
    const index = params.entityTypes && params.entityTypes.length > 0 ? params.entityTypes.map((t) => this.indexName(t)) : `${this.options.indexPrefix}*`;

    const res = await this.client.search({
      index,
      body: {
        query: {
          bool: {
            filter: [{ term: { tenant_id: params.tenantId } }],
            must: [{ multi_match: { query: params.query, fields: ["title^2", "snippet", "content"] } }],
          },
        },
        size: params.limit,
        sort: [{ _score: "desc" }, { "entity_id.keyword": "asc" }],
      },
    });

    return res.body.hits.hits.map((hit: { _source: { entity_type: string; entity_id: string; title: string; snippet: string | null }; _score: number | null }) => ({
      entityType: hit._source.entity_type,
      entityId: hit._source.entity_id,
      title: hit._source.title,
      snippet: hit._source.snippet ?? undefined,
      score: hit._score ?? 0,
    }));
  }
}
