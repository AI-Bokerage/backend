import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import { env, requireEnv } from "./config/env.ts";
import { REINDEX_LOCK_TTL_S, type SearchBackendName } from "./config/constants.ts";
import { GenSearchConfigError } from "./common/errors.ts";
import { logger } from "./common/logger.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { backendsUsed, type SearchEntitiesConfig } from "./domain/entities-config.ts";
import { AllowAllVisibilityFilter, type IVisibilityFilter } from "./domain/ports/visibility-filter.port.ts";
import type { IDistributedLock } from "./domain/ports/distributed-lock.port.ts";
import type { IEventConsumer } from "./domain/ports/event-bus.port.ts";
import type { ISearchIndexWriter } from "./domain/ports/search-index.port.ts";
import type { ISearchQueryEngine } from "./domain/ports/search-query.port.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { PgFtsAdapter } from "./infra/search/pg-fts.adapter.ts";
import { OpenSearchAdapter } from "./infra/search/opensearch.adapter.ts";
import { ValkeyLock } from "./infra/cache/valkey-lock.ts";
import { RabbitMqBus } from "./infra/messaging/rabbitmq-bus.ts";
import { SearchQueryService } from "./modules/query/v1/service.ts";
import { createQueryRouter } from "./modules/query/v1/router.ts";
import type { SearchQueryLimits } from "./modules/query/v1/schema.ts";
import { ReindexService, type ReindexSource } from "./modules/reindex/v1/service.ts";
import { createReindexRouter } from "./modules/reindex/v1/router.ts";
import { ErasureService } from "./modules/erasure/v1/service.ts";
import { createErasureRouter } from "./modules/erasure/v1/router.ts";
import { startIndexerWorker, type IndexerWorker, type IndexerWorkerDeps } from "./workers/indexer.worker.ts";

type BackendAdapter = ISearchIndexWriter & ISearchQueryEngine;

export interface GenSearchWorkerConfig {
  exchange: string;
  queue: string;
  routingKeys: string[];
  deadLetterExchange?: string;
  resolveDocument: IndexerWorkerDeps["resolveDocument"];
  resolveRemoval?: IndexerWorkerDeps["resolveRemoval"];
  prefetch?: number;
}

export interface GenSearchModulesConfig {
  query?: boolean;
  reindex?: boolean;
  erasure?: boolean;
  worker?: boolean;
}

export interface GenSearchLimits {
  minQueryLength?: number;
  maxQueryLength?: number;
  defaultResults?: number;
  maxResults?: number;
  maxOffset?: number;
  timeoutMs?: number;
}

export interface GenSearchConfig {
  /** Required — the library has no default entity types. Maps a host-defined entity type name to which backend serves it. */
  entities: SearchEntitiesConfig;
  pgAdapter?: BackendAdapter;
  openSearchAdapter?: BackendAdapter;
  visibilityFilter?: IVisibilityFilter;
  lock?: IDistributedLock;
  eventBus?: IEventConsumer;
  /** Required when modules.reindex is enabled (the default). See docs/source-audit-notes.md. */
  reindexSource?: ReindexSource;
  /** Required when modules.worker is enabled (the default). */
  worker?: GenSearchWorkerConfig;
  internalSecret?: string;
  limits?: GenSearchLimits;
  modules?: GenSearchModulesConfig;
}

export interface GenSearchWorker {
  close(): Promise<void>;
}

export interface GenSearchInstance {
  app: Express;
  worker?: GenSearchWorker;
}

function resolvePgAdapter(override: BackendAdapter | undefined): BackendAdapter {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PgFtsAdapter(getPrismaClient());
}

function resolveOpenSearchAdapter(override: BackendAdapter | undefined): BackendAdapter {
  if (override) return override;
  const url = requireEnv("OPENSEARCH_URL");
  return new OpenSearchAdapter({
    url,
    username: process.env.OPENSEARCH_USERNAME,
    password: process.env.OPENSEARCH_PASSWORD,
    indexPrefix: env.OPENSEARCH_INDEX_PREFIX,
  });
}

function resolveLock(override: IDistributedLock | undefined): IDistributedLock {
  if (override) return override;
  const url = requireEnv("VALKEY_URL");
  return new ValkeyLock(url);
}

function resolveEventBus(override: IEventConsumer | undefined): IEventConsumer {
  if (override) return override;
  const url = requireEnv("RABBITMQ_URL");
  return new RabbitMqBus(url);
}

function resolveInternalSecret(override: string | undefined): string {
  return override ?? requireEnv("GEN_SEARCH_INTERNAL_SECRET");
}

export function createGenSearch(config: GenSearchConfig): GenSearchInstance {
  if (!config.entities || Object.keys(config.entities).length === 0) {
    throw new GenSearchConfigError("config.entities must have at least one entity type configured");
  }

  const modules: Required<GenSearchModulesConfig> = {
    query: config.modules?.query ?? true,
    reindex: config.modules?.reindex ?? true,
    erasure: config.modules?.erasure ?? true,
    worker: config.modules?.worker ?? true,
  };

  if (!modules.query && !modules.reindex && !modules.erasure) {
    throw new GenSearchConfigError("At least one of modules.query, modules.reindex, or modules.erasure must be enabled");
  }

  const internalSecretValue = resolveInternalSecret(config.internalSecret);
  const used = backendsUsed(config.entities);

  const writers: Partial<Record<SearchBackendName, ISearchIndexWriter>> = {};
  const queryEngines: Partial<Record<SearchBackendName, ISearchQueryEngine>> = {};

  if (used.includes("pg")) {
    const adapter = resolvePgAdapter(config.pgAdapter);
    writers.pg = adapter;
    queryEngines.pg = adapter;
  }
  if (used.includes("opensearch")) {
    const adapter = resolveOpenSearchAdapter(config.openSearchAdapter);
    writers.opensearch = adapter;
    queryEngines.opensearch = adapter;
  }

  const visibilityFilter = config.visibilityFilter ?? new AllowAllVisibilityFilter();
  const limits: SearchQueryLimits = {
    minQueryLength: config.limits?.minQueryLength ?? env.MIN_QUERY_LENGTH,
    maxQueryLength: config.limits?.maxQueryLength ?? env.MAX_QUERY_LENGTH,
    defaultResults: config.limits?.defaultResults ?? env.DEFAULT_RESULTS,
    maxResults: config.limits?.maxResults ?? env.MAX_RESULTS,
    maxOffset: config.limits?.maxOffset ?? env.MAX_SEARCH_OFFSET,
  };
  const timeoutMs = config.limits?.timeoutMs ?? env.SEARCH_TIMEOUT_MS;

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  if (modules.query) {
    const searchQueryService = new SearchQueryService({ backends: queryEngines, entities: config.entities, visibilityFilter, timeoutMs });
    app.use("/api/v1/search/:tenantId", createQueryRouter({ searchQueryService, internalSecretValue, limits }));
  }

  if (modules.reindex) {
    if (!config.reindexSource) {
      throw new GenSearchConfigError("config.reindexSource is required when modules.reindex is enabled");
    }
    const lock = resolveLock(config.lock);
    const reindexService = new ReindexService({
      writers,
      entities: config.entities,
      lock,
      reindexSource: config.reindexSource,
      batchSize: env.INDEXER_BATCH_SIZE,
      lockTtlS: REINDEX_LOCK_TTL_S,
    });
    app.use("/api/v1/search/:tenantId/reindex", createReindexRouter({ reindexService, internalSecretValue }));
  }

  if (modules.erasure) {
    const erasureService = new ErasureService(writers);
    app.use("/api/v1/search/:tenantId/erasure", createErasureRouter({ erasureService, internalSecretValue }));
  }

  app.use(errorHandler);

  let worker: GenSearchWorker | undefined;
  if (modules.worker) {
    if (!config.worker) {
      throw new GenSearchConfigError("config.worker is required when modules.worker is enabled");
    }
    const bus = resolveEventBus(config.eventBus);
    const workerConfig = config.worker;

    let closed = false;
    let started: IndexerWorker | undefined;
    const startup = startIndexerWorker({
      bus,
      exchange: workerConfig.exchange,
      queue: workerConfig.queue,
      routingKeys: workerConfig.routingKeys,
      deadLetterExchange: workerConfig.deadLetterExchange,
      writers,
      entities: config.entities,
      resolveDocument: workerConfig.resolveDocument,
      resolveRemoval: workerConfig.resolveRemoval,
      batchSize: env.INDEXER_BATCH_SIZE,
      flushIntervalMs: env.INDEXER_FLUSH_INTERVAL_MS,
      prefetch: workerConfig.prefetch,
    });
    // Fire-and-forget, matching create-gen-sla.ts's RabbitMqBus.connect()
    // startup style — createGenSearch() stays synchronous, the queue
    // topology finishes asserting/binding in the background.
    startup
      .then((w) => {
        if (closed) void w.close();
        else started = w;
      })
      .catch((err) => logger.error({ err }, "[create-gen-search] indexer worker failed to start"));

    worker = {
      close: async () => {
        closed = true;
        await started?.close();
      },
    };
  }

  return { app, worker };
}
