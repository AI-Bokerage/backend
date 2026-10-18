import { logger } from "../common/logger.ts";
import type { ISlaInstanceRepo } from "../domain/ports/instance-repo.port.ts";
import type { InstanceService } from "../modules/instances/v1/service.ts";
import type { IDistributedLock } from "../domain/ports/distributed-lock.port.ts";

export interface SlaTimerWorkerDeps {
  instanceRepo: ISlaInstanceRepo;
  instanceService: Pick<InstanceService, "transitionToWarning" | "transitionToBreached">;
  lock: IDistributedLock;
  pollIntervalMs: number;
  batchSize: number;
  lockTtlS: number;
}

export function startSlaTimerWorker(deps: SlaTimerWorkerDeps): { close: () => void } {
  async function processWarnings(): Promise<void> {
    const instances = await deps.instanceRepo.findActiveInstancesNearWarning(deps.batchSize);
    for (const instance of instances) {
      const acquired = await deps.lock.acquire(`gen-sla:lock:timer:warning:${instance.id}`, deps.lockTtlS);
      if (!acquired) continue;
      try {
        await deps.instanceService.transitionToWarning(instance.id, instance.tenantId);
      } catch (err) {
        logger.error({ err, instanceId: instance.id }, "[timer] failed to process warning transition");
      }
    }
  }

  async function processBreaches(): Promise<void> {
    const instances = await deps.instanceRepo.findActiveInstancesDue(deps.batchSize);
    for (const instance of instances) {
      const acquired = await deps.lock.acquire(`gen-sla:lock:timer:breach:${instance.id}`, deps.lockTtlS);
      if (!acquired) continue;
      try {
        await deps.instanceService.transitionToBreached(instance.id, instance.tenantId);
      } catch (err) {
        logger.error({ err, instanceId: instance.id }, "[timer] failed to process breach transition");
      }
    }
  }

  async function tick(): Promise<void> {
    try {
      await processWarnings();
      await processBreaches();
    } catch (err) {
      logger.error({ err }, "[timer] unhandled error in tick");
    }
  }

  logger.info({ intervalMs: deps.pollIntervalMs }, "[timer] SLA timer worker started");
  const intervalId = setInterval(() => void tick(), deps.pollIntervalMs);
  void tick();

  return {
    close: () => {
      clearInterval(intervalId);
      logger.info("[timer] SLA timer worker stopped");
    },
  };
}
