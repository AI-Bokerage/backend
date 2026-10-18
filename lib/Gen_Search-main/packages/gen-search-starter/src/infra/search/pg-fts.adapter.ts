import { Prisma, type PrismaClient } from "../../../__generated__/prisma/index.js";
import type { ISearchIndexWriter, IndexDocument } from "../../domain/ports/search-index.port.ts";
import type { ISearchQueryEngine, SearchQueryParams, SearchResultItem } from "../../domain/ports/search-query.port.ts";

interface RawSearchRow {
  entity_type: string;
  entity_id: string;
  title: string;
  snippet: string | null;
  rank: number;
}

// Both ports on one class because both operate on the same table and the
// same `tsv` column that only raw SQL can touch (Prisma.Unsupported type) —
// splitting them into two classes would just mean passing the same
// PrismaClient into two constructors for no isolation benefit.
export class PgFtsAdapter implements ISearchIndexWriter, ISearchQueryEngine {
  constructor(private readonly prisma: PrismaClient) {}

  private buildUpsert(doc: IndexDocument) {
    return Prisma.sql`
      INSERT INTO search_index (tenant_id, entity_type, entity_id, title, snippet, content, owner_id, metadata, updated_at)
      VALUES (${doc.tenantId}::uuid, ${doc.entityType}, ${doc.entityId}::uuid, ${doc.title}, ${doc.snippet ?? null}, ${doc.content ?? null}, ${doc.ownerId ?? null}::uuid, ${JSON.stringify(doc.metadata ?? {})}::jsonb, now())
      ON CONFLICT (tenant_id, entity_type, entity_id)
      DO UPDATE SET title = excluded.title, snippet = excluded.snippet, content = excluded.content, owner_id = excluded.owner_id, metadata = excluded.metadata, updated_at = now()
    `;
  }

  async upsert(doc: IndexDocument): Promise<void> {
    await this.prisma.$executeRaw(this.buildUpsert(doc));
  }

  async bulkUpsert(docs: IndexDocument[]): Promise<void> {
    if (docs.length === 0) return;
    await this.prisma.$transaction(docs.map((doc) => this.prisma.$executeRaw(this.buildUpsert(doc))));
  }

  async remove(tenantId: string, entityType: string, entityId: string): Promise<void> {
    await this.prisma.searchIndex.deleteMany({ where: { tenantId, entityType, entityId } });
  }

  async removeByOwner(tenantId: string, ownerId: string): Promise<number> {
    const result = await this.prisma.searchIndex.deleteMany({ where: { tenantId, ownerId } });
    return result.count;
  }

  async removeByEntityType(tenantId: string, entityType: string): Promise<number> {
    const result = await this.prisma.searchIndex.deleteMany({ where: { tenantId, entityType } });
    return result.count;
  }

  async search(params: SearchQueryParams): Promise<SearchResultItem[]> {
    const typeFilter =
      params.entityTypes && params.entityTypes.length > 0
        ? Prisma.sql`AND entity_type IN (${Prisma.join(params.entityTypes)})`
        : Prisma.empty;

    const rows = await this.prisma.$queryRaw<RawSearchRow[]>`
      SELECT entity_type, entity_id, title, snippet,
             ts_rank(tsv, plainto_tsquery('english', ${params.query})) AS rank
      FROM search_index
      WHERE tenant_id = ${params.tenantId}::uuid
        AND tsv @@ plainto_tsquery('english', ${params.query})
        ${typeFilter}
      ORDER BY rank DESC, entity_type, entity_id
      LIMIT ${params.limit}
    `;

    return rows.map((row) => ({
      entityType: row.entity_type,
      entityId: row.entity_id,
      title: row.title,
      snippet: row.snippet ?? undefined,
      score: Number(row.rank),
    }));
  }
}
