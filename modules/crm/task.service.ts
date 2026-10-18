import type { Task } from '@prisma/client';

import { aiCall } from './ai.client';
import { getLeadWithAccess, utcStartOfToday } from './lead.service';
import { getPrismaClient } from '../../config/database';
import { logger } from '../../config/logger';
import { ApiError } from '../../utils/api-error';
import { recordActivity } from '../platform/activity-log';

export interface CreateTaskInput {
  title: string;
  leadId?: string;
  dueDate?: string;
  priority?: 'LOW' | 'MEDIUM' | 'HIGH';
}

export async function createTask(tenantId: string, brokerUserId: string, input: CreateTaskInput) {
  return getPrismaClient().task.create({
    data: {
      tenantId,
      brokerUserId,
      title: input.title,
      leadId: input.leadId,
      dueDate: input.dueDate ? new Date(input.dueDate) : null,
      priority: input.priority,
    },
  });
}

/** Flow 4.9 task list; due=today|overdue are the dashboard's two task cards (real tasks only). */
export async function listMyTasks(
  tenantId: string,
  brokerUserId: string,
  filter: { due?: 'today' | 'overdue' } = {},
) {
  const start = utcStartOfToday();
  const end = new Date(start.getTime() + 24 * 60 * 60 * 1000);
  const due =
    filter.due === 'today'
      ? { completed: false, suggested: false, dueDate: { gte: start, lt: end } }
      : filter.due === 'overdue'
        ? { completed: false, suggested: false, dueDate: { lt: start } }
        : {};
  return getPrismaClient().task.findMany({
    where: { tenantId, brokerUserId, ...due },
    orderBy: { dueDate: 'asc' },
  });
}

/** Same 404-vs-403 split as lead.service.ts's getLeadWithAccess: not-in-tenant is 404, someone else's is 403. */
async function requireOwnTask(tenantId: string, brokerUserId: string, taskId: string) {
  const task = await getPrismaClient().task.findUnique({ where: { id: taskId } });
  if (!task || task.tenantId !== tenantId) {
    throw new ApiError('Task not found', 404);
  }
  if (task.brokerUserId !== brokerUserId) {
    throw new ApiError("You don't have access to this record", 403);
  }
  return task;
}

async function requireOwnSuggestedTask(tenantId: string, brokerUserId: string, taskId: string) {
  const task = await requireOwnTask(tenantId, brokerUserId, taskId);
  if (!task.suggested) {
    throw new ApiError('Only a suggested task can be approved', 409);
  }
  return task;
}

export async function completeTask(tenantId: string, brokerUserId: string, taskId: string) {
  const task = await requireOwnTask(tenantId, brokerUserId, taskId);
  if (task.suggested) {
    throw new ApiError('Approve this suggested task before completing it', 409);
  }
  const done = await getPrismaClient().task.update({
    where: { id: taskId },
    data: { completed: true },
  });
  // Flow 4.9 "Tick to complete — stays on the person's timeline" (the panel's Activity tab).
  if (task.leadId) {
    await recordActivity('crm.task.completed', {
      actorId: brokerUserId,
      targetType: 'lead',
      targetId: task.leadId,
      metadata: { taskId: task.id, title: task.title },
    });
  }
  return done;
}

/**
 * An AI-suggested task's approve/dismiss, recorded in notes-svc so "Suggest
 * tasks" never offers it again. Best effort: the broker's decision stands in
 * the CRM even when the AI service is unreachable.
 */
async function recordAiDecision(task: Task, brokerUserId: string, decision: 'approved' | 'rejected') {
  if (!task.aiSuggestionId || !task.aiRunId) return;
  await aiCall('notes', 'POST', `/v1/runs/${task.aiRunId}/decisions`, {
    caller: { tenantId: task.tenantId, userId: brokerUserId },
    json: { decisions: [{ suggestionId: task.aiSuggestionId, decision }] },
  }).catch((err) => logger.warn({ err, taskId: task.id }, 'ai.task_decision_not_recorded'));
}

/** Flow 4.9 "Approve — becomes a real task". */
export async function approveSuggestedTask(tenantId: string, brokerUserId: string, taskId: string) {
  const task = await requireOwnSuggestedTask(tenantId, brokerUserId, taskId);
  await recordAiDecision(task, brokerUserId, 'approved');
  return getPrismaClient().task.update({ where: { id: taskId }, data: { suggested: false } });
}

/**
 * Flow 4.9: "Dismiss — disappears" for a suggested task, and "reassign it or
 * delete it" for one left unlinked by a deleted person. A real, linked task
 * isn't deletable — it's completed, and "stays on the person's timeline".
 */
export async function deleteTask(tenantId: string, brokerUserId: string, taskId: string) {
  const task = await requireOwnTask(tenantId, brokerUserId, taskId);
  if (!task.suggested && !task.unlinked) {
    throw new ApiError('Only a suggested or unlinked task can be deleted', 409);
  }
  if (task.suggested) await recordAiDecision(task, brokerUserId, 'rejected');
  await getPrismaClient().task.delete({ where: { id: taskId } });
}

/** Flow 4.9 unlinked task → "reassign it" to another person in the caller's book. */
export async function linkTask(
  tenantId: string,
  brokerUserId: string,
  taskId: string,
  leadId: string,
  allowedBrokerIds: string[],
) {
  await requireOwnTask(tenantId, brokerUserId, taskId);
  const lead = await getLeadWithAccess(tenantId, leadId, allowedBrokerIds);
  return getPrismaClient().task.update({
    where: { id: taskId },
    data: { leadId: lead.id, unlinked: false },
  });
}

/** "Team tasks" — every task belonging to any broker on the team. */
export async function listForBrokers(tenantId: string, brokerIds: string[]) {
  if (brokerIds.length === 0) return [];
  return getPrismaClient().task.findMany({
    where: { tenantId, brokerUserId: { in: brokerIds } },
    orderBy: { dueDate: 'asc' },
  });
}

/** Overdue-task count per broker — for the "overdue tasks" dashboard widget and the workload view. */
export async function overdueCountByBroker(
  tenantId: string,
  brokerIds: string[],
): Promise<Record<string, number>> {
  const counts: Record<string, number> = Object.fromEntries(brokerIds.map((id) => [id, 0]));
  if (brokerIds.length === 0) return counts;
  const overdue = await getPrismaClient().task.findMany({
    where: {
      tenantId,
      brokerUserId: { in: brokerIds },
      completed: false,
      suggested: false,
      dueDate: { lt: new Date() },
    },
    select: { brokerUserId: true },
  });
  for (const row of overdue) counts[row.brokerUserId] = (counts[row.brokerUserId] ?? 0) + 1;
  return counts;
}
