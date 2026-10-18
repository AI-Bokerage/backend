// src/worker.ts
import { SignupState, ABANDONABLE_STATES } from "./domain/enums/signup-state.enum.ts";
import type { ISignupSessionRepo } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";

export async function sweepProvisioning(repo: ISignupSessionRepo, tntClient: ITntClient): Promise<void> {
  const sessions = await repo.findInState(SignupState.PROVISIONING);

  for (const session of sessions) {
    if (!session.provisionedTenantId) continue;

    const tenant = await tntClient.getTenant(session.provisionedTenantId);
    if (tenant.status === "ACTIVE") {
      await repo.updateState(session.id, SignupState.ACTIVE);
      continue;
    }

    if (!session.provisioningJobId) continue;
    const job = await tntClient.getJob(session.provisioningJobId);
    if (job.status === "DEAD") {
      await repo.updateState(session.id, SignupState.PROVISION_FAILED, {
        lastProvisioningError: job.lastError,
      });
    }
    // Still PROVISIONING and the job isn't DEAD — leave it for the next sweep.
  }
}

export async function sweepAbandoned(repo: ISignupSessionRepo): Promise<void> {
  const expired = await repo.findExpiredInStates([...ABANDONABLE_STATES], new Date());
  for (const session of expired) {
    await repo.updateState(session.id, SignupState.ABANDONED);
  }
}
