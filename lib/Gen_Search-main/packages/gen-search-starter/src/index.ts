export { createGenSearch } from "./create-gen-search.ts";
export type { GenSearchConfig, GenSearchModulesConfig, GenSearchLimits, GenSearchWorkerConfig, GenSearchWorker, GenSearchInstance } from "./create-gen-search.ts";

export type { IndexDocument, ISearchIndexWriter } from "./domain/ports/search-index.port.ts";
export type { SearchQueryParams, SearchResultItem, ISearchQueryEngine } from "./domain/ports/search-query.port.ts";
export type { VisibilityCheckParams, IVisibilityFilter } from "./domain/ports/visibility-filter.port.ts";
export { AllowAllVisibilityFilter } from "./domain/ports/visibility-filter.port.ts";
export type { IDistributedLock } from "./domain/ports/distributed-lock.port.ts";
export type { IEventConsumer, IndexerEventEnvelope, ConsumeOptions } from "./domain/ports/event-bus.port.ts";
export type { SearchEntitiesConfig } from "./domain/entities-config.ts";
export type { SearchBackendName } from "./config/constants.ts";

export { PgFtsAdapter } from "./infra/search/pg-fts.adapter.ts";
export { OpenSearchAdapter } from "./infra/search/opensearch.adapter.ts";
export { ValkeyLock } from "./infra/cache/valkey-lock.ts";
export { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export { SearchQueryService } from "./modules/query/v1/service.ts";
export { ReindexService } from "./modules/reindex/v1/service.ts";
export type { ReindexSource } from "./modules/reindex/v1/service.ts";
export { ErasureService } from "./modules/erasure/v1/service.ts";

export { startIndexerWorker } from "./workers/indexer.worker.ts";
export type { IndexerWorkerDeps, IndexerWorker, RemovalTarget } from "./workers/indexer.worker.ts";

export { errorHandler } from "./middleware/error-handler.ts";
export {
  AppError,
  GenSearchConfigError,
  TenantIdInvalidError,
  SearchInvalidQueryError,
  SearchQueryTooBroadError,
  SearchTimeoutError,
  SearchIndexUnavailableError,
  IndexDocumentInvalidError,
  ReindexAlreadyRunningError,
} from "./common/errors.ts";
