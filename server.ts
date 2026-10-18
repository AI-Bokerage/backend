import { app } from './app';
import { connectDatabases, disconnectDatabases } from './config/database';
import { env } from './config/env';
import { logger } from './config/logger';
import { retryAiDataDeletions } from './modules/crm/ai-deletion.service';
import { syncSearchIndex } from './modules/crm/ai-search.service';
import { runReminderSweep } from './modules/crm/reminder.service';
import { purgeCancelledBrokerages } from './modules/platform/brokerage.service';
import { purgeExpiredExports } from './modules/platform/export.service';
import { expireAiCostOverrides } from './modules/platform/usage.service';

let server: ReturnType<typeof app.listen> | null = null;
let exportPurgeInterval: ReturnType<typeof setInterval> | null = null;
let reminderInterval: ReturnType<typeof setInterval> | null = null;
let searchSyncInterval: ReturnType<typeof setInterval> | null = null;

const shutdown = async (signal: NodeJS.Signals): Promise<void> => {
  logger.info({ signal }, 'Graceful shutdown started');

  if (reminderInterval) {
    clearInterval(reminderInterval);
    reminderInterval = null;
  }
  if (exportPurgeInterval) {
    clearInterval(exportPurgeInterval);
  }
  if (searchSyncInterval) {
    clearInterval(searchSyncInterval);
  }

  if (server) {
    await new Promise<void>((resolve, reject) => {
      server?.close((error) => {
        if (error) {
          reject(error);
          return;
        }
        resolve();
      });
    });
  }

  await disconnectDatabases();
  logger.info('Shutdown completed');
  process.exit(0);
};

const bootstrap = async (): Promise<void> => {
  try {
    await connectDatabases();

    server = app.listen(env.port, () => {
      logger.info(`Server listening on port ${env.port}`);
    });

    // No internal scheduler in export.service.ts itself (matches the rest of
    // the Gen_MS family's convention — host-triggered sweeps, see its own
    // comment) — this is the host trigger. Hourly is fine for a
    // multi-day retention window; not worth a real cron dependency.
    exportPurgeInterval = setInterval(
      () => {
        purgeExpiredExports()
          .then((count) => {
            if (count > 0) logger.info({ count }, 'Purged expired brokerage exports');
          })
          .catch((error: unknown) => logger.error({ error }, 'Brokerage export purge failed'));
        purgeCancelledBrokerages()
          .then((count) => {
            if (count > 0) logger.info({ count }, 'Purged cancelled brokerages past retention');
          })
          .catch((error: unknown) => logger.error({ error }, 'Cancelled brokerage purge failed'));
        retryAiDataDeletions()
          .then((count) => {
            if (count > 0) logger.info({ count }, 'Finished deleting AI copies of deleted people');
          })
          .catch((error: unknown) => logger.error({ error }, 'AI data deletion retry failed'));
        expireAiCostOverrides()
          .then((count) => {
            if (count > 0) logger.info({ count }, 'Restored default AI cost caps after their day');
          })
          .catch((error: unknown) => logger.error({ error }, 'AI cost override expiry failed'));
      },
      60 * 60 * 1000,
    );

    // Smart search: send search-svc the people whose searchable text changed.
    if (env.ai.search.key) {
      const sync = () =>
        syncSearchIndex()
          .then((count) => {
            if (count > 0) logger.info({ count }, 'Sent changed people to smart search');
          })
          .catch((error: unknown) => logger.error({ error }, 'Smart search sync failed'));
      void sync();
      searchSyncInterval = setInterval(() => void sync(), 5 * 60 * 1000);
    }

    // Flow 4.6/4.8 reminders, 9am digest and calendar push retries — see reminder.service.ts.
    if (env.crm.remindersEnabled) {
      const sweep = () =>
        runReminderSweep().catch((error: unknown) =>
          logger.error({ error }, 'CRM reminder sweep failed'),
        );
      void sweep();
      reminderInterval = setInterval(() => void sweep(), 60 * 60 * 1000);
    }

    process.on('SIGINT', () => {
      void shutdown('SIGINT');
    });
    process.on('SIGTERM', () => {
      void shutdown('SIGTERM');
    });
  } catch (error) {
    // The reason in the message itself: log viewers (Railway) show only the message line.
    logger.fatal(
      { error },
      `Failed to bootstrap server: ${error instanceof Error ? error.message : String(error)}`,
    );
    process.exit(1);
  }
};

void bootstrap();
