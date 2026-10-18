export interface SearchQueryParams {
  tenantId: string;
  query: string;
  /** Restrict to these entity types. Omit to search every entity type this backend is configured to serve. */
  entityTypes?: string[];
  limit: number;
}

export interface SearchResultItem {
  entityType: string;
  entityId: string;
  title: string;
  snippet?: string;
  score: number;
}

export interface ISearchQueryEngine {
  search(params: SearchQueryParams): Promise<SearchResultItem[]>;
}
