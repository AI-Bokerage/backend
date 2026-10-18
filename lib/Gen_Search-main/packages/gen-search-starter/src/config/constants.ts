export const SEARCH_ENTITY_BACKENDS = ["pg", "opensearch"] as const;
export type SearchBackendName = (typeof SEARCH_ENTITY_BACKENDS)[number];

export const REINDEX_LOCK_TTL_S = 3600;

export const DEFAULT_PAGE = 1;
export const DEFAULT_PAGE_SIZE = 20;
export const MAX_PAGE_SIZE = 100;
