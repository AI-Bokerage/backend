import { leadsMissingDocuments } from './dashboard.service';
import { openLeadCountByBroker, renewalCountByBroker } from './lead.service';
import { overdueCountByBroker } from './task.service';

export interface BrokerWorkload {
  brokerUserId: string;
  brokerName: string | null;
  openLeads: number;
  overdueTasks: number;
  renewalsWithin90Days: number;
  /** Leads whose ticked document checklist isn't fully received. */
  missingDocuments: number;
}

/**
 * "The workload view shows, per broker: open leads, overdue tasks, renewals
 * within 90 days, and files missing documents. A broker with nothing
 * assigned is shown as empty rather than hidden" — hence iterating over
 * every team member, not just brokers with matching rows.
 */
export async function teamWorkload(
  tenantId: string,
  members: { userId: string; name: string | null }[],
): Promise<BrokerWorkload[]> {
  const brokerIds = members.map((m) => m.userId);
  const [openLeads, overdueTasks, renewals, missingDocs] = await Promise.all([
    openLeadCountByBroker(tenantId, brokerIds),
    overdueCountByBroker(tenantId, brokerIds),
    renewalCountByBroker(tenantId, brokerIds),
    leadsMissingDocuments(tenantId, brokerIds),
  ]);
  return members.map((m) => ({
    brokerUserId: m.userId,
    brokerName: m.name,
    openLeads: openLeads[m.userId] ?? 0,
    overdueTasks: overdueTasks[m.userId] ?? 0,
    renewalsWithin90Days: renewals[m.userId] ?? 0,
    missingDocuments: missingDocs.filter((l) => l.brokerUserId === m.userId).length,
  }));
}
