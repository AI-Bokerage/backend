import { createHash } from 'node:crypto';

import { DOCUMENT_LABELS } from './ai-email.service';
import { aiCall, type AiCaller } from './ai.client';
import { getPrismaClient } from '../../config/database';
import { logger } from '../../config/logger';

/**
 * Smart search (search-svc). Each person is one searchable record built from
 * their profile, notes, mortgages, documents and open tasks; a search returns
 * people, opened in the person panel. search-svc keeps only vectors and ids,
 * never the text.
 */

const RECORD = 'client_profile';
// Tuned on jina-embeddings-v3 with retrieval tasks: a whole client file scores low (0.2–0.45) even
// when it is the right one, so keep matches from 0.2 and drop the tail far below the best.
const MIN_SCORE = 0.2;
const BEST_MARGIN = 0.1;
const MAX_CHARS = 60_000;

type LeadForSearch = Awaited<ReturnType<typeof leadsToSync>>[number];

const leadsToSync = (take: number, afterId?: string) =>
  getPrismaClient().lead.findMany({
    take,
    ...(afterId ? { skip: 1, cursor: { id: afterId } } : {}),
    orderBy: { id: 'asc' },
    include: {
      mortgages: true,
      documents: { select: { type: true, fileName: true } },
      tasks: { where: { completed: false, suggested: false }, select: { title: true } },
    },
  });

/** The words a person is found by. */
export function searchText(l: LeadForSearch) {
  return [
    `${l.name}. Stage: ${l.stage}. File: ${l.fileStatus}.`,
    l.city && `City: ${l.city}.`,
    l.tags.length && `Tags: ${l.tags.join(', ')}.`,
    l.propertyAddress && `Property: ${l.propertyAddress}.`,
    l.lostReason && `Lost: ${l.lostReason}. ${l.lostNote ?? ''}`,
    ...l.mortgages.map((m) => `Mortgage with ${m.lender} at ${m.interestRate}%, matures ${m.maturityDate.toISOString().slice(0, 10)}.`),
    l.documents.length && `Documents: ${l.documents.map((d) => `${DOCUMENT_LABELS[d.type]} (${d.fileName})`).join(', ')}.`,
    l.tasks.length && `Open tasks: ${l.tasks.map((t) => t.title).join('; ')}.`,
    l.notes && `Notes: ${l.notes}`,
    l.brokerNotes && `Broker notes: ${l.brokerNotes}`,
  ]
    .filter(Boolean)
    .join('\n')
    .slice(0, MAX_CHARS);
}

async function push(l: LeadForSearch) {
  const content = searchText(l);
  // The owner is part of the fingerprint: a reassigned person is re-sent with the new access.
  const hash = createHash('sha256').update(`${l.brokerUserId}\n${content}`).digest('hex');
  if (hash === l.searchHash) return false;
  await aiCall('search', 'PUT', `/v1/records/${RECORD}/${l.id}`, {
    caller: { tenantId: l.tenantId },
    json: {
      version: Date.now(),
      content,
      clientId: l.id,
      access: { ownerUserId: l.brokerUserId, assignedUserId: l.brokerUserId },
      knownEntities: [
        { type: 'PERSON', value: l.name },
        ...(l.email ? [{ type: 'EMAIL', value: l.email }] : []),
        ...(l.phone ? [{ type: 'PHONE', value: l.phone }] : []),
      ],
    },
  });
  await getPrismaClient().lead.update({ where: { id: l.id }, data: { searchHash: hash } });
  return true;
}

/**
 * Every few minutes: send search-svc the people whose searchable text changed.
 * Deleted people are removed by ai-deletion.service (DELETE .../ai-data).
 * shortcut: rebuilds every person's text each sweep, upgrade to change tracking if books grow past tens of thousands.
 */
export async function syncSearchIndex() {
  let sent = 0;
  let afterId: string | undefined;
  for (;;) {
    const batch = await leadsToSync(200, afterId);
    for (const l of batch) {
      try {
        if (await push(l)) sent += 1;
      } catch (err) {
        logger.warn({ err, leadId: l.id }, 'ai.search_push_failed'); // retried next sweep: its hash is unchanged
      }
    }
    if (batch.length < 200) return sent;
    afterId = batch[batch.length - 1]!.id;
  }
}

interface SearchAnswer {
  aiLabel: string;
  groups: { results: { recordId: string; clientId: string | null; score: number }[] }[];
}

/** "Search": people whose files mean what was typed, among those the user may see, best first. */
export async function smartSearch(caller: AiCaller & { userId: string }, accessIds: string[], query: string) {
  const answer = await aiCall<SearchAnswer>('search', 'POST', '/v1/search', {
    caller,
    json: {
      query,
      accessFilter: { recordTypes: [RECORD], scope: { type: 'assigned', userIds: accessIds } },
      limitPerType: 10,
      minScore: MIN_SCORE,
    },
  });
  const all = answer.groups.flatMap((g) => g.results);
  const best = Math.max(0, ...all.map((h) => h.score));
  const hits = all.filter((h) => h.score >= best - BEST_MARGIN);
  // The CRM has the final say on access (a person reassigned since the last sync is dropped).
  const leads = await getPrismaClient().lead.findMany({
    where: { tenantId: caller.tenantId, brokerUserId: { in: accessIds }, id: { in: hits.map((h) => h.recordId) } },
    select: { id: true, name: true, stage: true, city: true },
  });
  const byId = new Map(leads.map((l) => [l.id, l]));
  return {
    aiLabel: answer.aiLabel,
    results: hits.flatMap((h) => {
      const lead = byId.get(h.recordId);
      return lead ? [{ leadId: lead.id, name: lead.name, stage: lead.stage, city: lead.city, score: h.score }] : [];
    }),
  };
}
