import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import * as affordabilityService from './affordability.service';
import { createLeadFromReview } from './ai-notes.service';
import { bookBrokerIds, leadAccessIds } from './crm-auth.middleware';
import * as dashboardService from './dashboard.service';
import * as calendarService from './google/calendar.service';
import * as leadService from './lead.service';
import { resolveTeam } from './modauth.client';
import * as notificationService from './notification.service';
import * as taskService from './task.service';
import { teamWorkload } from './workload.service';
import { ApiError } from '../../utils/api-error';
import { asyncHandler } from '../../utils/async-handler';

function crmUser(req: Request) {
  // Guaranteed set by requireModAuthRole before any handler here runs.
  return req.crmUser!;
}

async function requireOwnTeam(req: Request) {
  const user = crmUser(req);
  if (!user.teamId) {
    throw new ApiError('You are not assigned to a team', StatusCodes.FORBIDDEN);
  }
  return resolveTeam(user.teamId, user.bearerToken);
}

// ── "Their own book" (Broker, and a Team Lead's own book) ───────────────────

export const createLeadHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  if (req.body.aiRunId) {
    // Flow 4.2 "Save" after the AI review: the lead plus everything the broker approved.
    const { tenantId, userId, role } = user;
    const result = await createLeadFromReview(
      { tenantId, userId, role },
      await leadAccessIds(user),
      req.body,
    );
    res.status(StatusCodes.CREATED).json({ success: true, data: result });
    return;
  }
  const lead = await leadService.createLead(user.tenantId, user.userId, req.body);
  res.status(StatusCodes.CREATED).json({ success: true, data: leadService.maskLead(lead) });
});

export const listMyLeadsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const { stage, ...filter } = req.query as leadService.LeadListFilter & {
    stage?: leadService.LeadListFilter['stages'];
  };
  const leads = await leadService.listMyLeads(user.tenantId, await bookBrokerIds(user), {
    ...filter,
    stages: stage,
  });
  res.status(StatusCodes.OK).json({ success: true, data: leads });
});

export const listMyClientsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const clients = await leadService.listMyClients(
    user.tenantId,
    await bookBrokerIds(user),
    req.query,
  );
  res.status(StatusCodes.OK).json({ success: true, data: clients });
});

export const getMyLeadHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const lead = await leadService.getLeadWithAccess(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
  );
  res.status(StatusCodes.OK).json({ success: true, data: leadService.maskLead(lead) });
});

// ── Person panel (Flow 4.3) — own book ───────────────────────────────────────

export const getPersonPanelHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const panel = await leadService.getPersonPanel(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
  );
  res.status(StatusCodes.OK).json({ success: true, data: panel });
});

export const getAffordabilityHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const result = await affordabilityService.getAffordability(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
  );
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

/** "Recalculate everyone" (Flow 4.4) — scoped to the broker's own book; body.leadIds lets the frontend "retry just those" failures. */
export const recalculateAffordabilityHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const summary = await affordabilityService.recalculateForBook(
    user.tenantId,
    await bookBrokerIds(user),
    req.body.leadIds,
  );
  res.status(StatusCodes.OK).json({ success: true, data: summary });
});

export const updateNotesHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const lead = await leadService.updateNotes(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body.brokerNotes,
    user.userId,
  );
  res.status(StatusCodes.OK).json({ success: true, data: lead });
});

export const updateTagsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const lead = await leadService.updateTags(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body.tags,
  );
  res.status(StatusCodes.OK).json({ success: true, data: lead });
});

/** "Inline profile edit" (SOW 6.9) — own book. */
export const updateLeadProfileHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const lead = await leadService.updateLeadProfile(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.OK).json({ success: true, data: lead });
});

export const revealFieldHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const revealed = await leadService.revealSensitiveField(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body.field,
    user.userId,
  );
  res.status(StatusCodes.OK).json({ success: true, data: revealed });
});

export const updateMyLeadStageHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const lead = await leadService.updateStage(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body.stage,
    req.body.lostReason,
    req.body.lostNote,
  );
  res.status(StatusCodes.OK).json({ success: true, data: lead });
});

export const fundLeadHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const lead = await leadService.fundLead(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.OK).json({ success: true, data: lead });
});

export const addMortgageHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const mortgage = await leadService.addMortgage(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body,
  );
  res.status(StatusCodes.CREATED).json({ success: true, data: mortgage });
});

export const createTaskHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const task = await taskService.createTask(user.tenantId, user.userId, req.body);
  res.status(StatusCodes.CREATED).json({ success: true, data: task });
});

export const listMyTasksHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const tasks = await taskService.listMyTasks(user.tenantId, user.userId, req.query);
  res.status(StatusCodes.OK).json({ success: true, data: tasks });
});

export const completeTaskHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const task = await taskService.completeTask(user.tenantId, user.userId, req.params.id as string);
  res.status(StatusCodes.OK).json({ success: true, data: task });
});

export const approveSuggestedTaskHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const task = await taskService.approveSuggestedTask(
    user.tenantId,
    user.userId,
    req.params.id as string,
  );
  res.status(StatusCodes.OK).json({ success: true, data: task });
});

export const deleteTaskHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await taskService.deleteTask(user.tenantId, user.userId, req.params.id as string);
  res.status(StatusCodes.NO_CONTENT).send();
});

export const linkTaskHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const task = await taskService.linkTask(
    user.tenantId,
    user.userId,
    req.params.id as string,
    req.body.leadId,
    await leadAccessIds(user),
  );
  res.status(StatusCodes.OK).json({ success: true, data: task });
});

/** Flow 4.11 "Delete this person" — own book only (an Assistant can't delete). */
export const deleteLeadHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await leadService.deleteLead(
    user.tenantId,
    req.params.id as string,
    await leadAccessIds(user),
    req.body.confirmName,
    user.userId,
  );
  res.status(StatusCodes.NO_CONTENT).send();
});

export const brokerDashboardHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const data = await dashboardService.brokerDashboard(user.tenantId, await bookBrokerIds(user));
  res.status(StatusCodes.OK).json({ success: true, data });
});

export const searchHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const leads = await dashboardService.searchBook(
    user.tenantId,
    await bookBrokerIds(user),
    req.query.q as string,
  );
  res.status(StatusCodes.OK).json({ success: true, data: leads });
});

export const listNotificationsHandler = asyncHandler(async (req: Request, res: Response) => {
  const items = await notificationService.listMyNotifications(crmUser(req).userId);
  res.status(StatusCodes.OK).json({ success: true, data: items });
});

export const markNotificationReadHandler = asyncHandler(async (req: Request, res: Response) => {
  const item = await notificationService.markRead(crmUser(req).userId, req.params.id as string);
  res.status(StatusCodes.OK).json({ success: true, data: item });
});

// ── Team Lead's team-scoped views (Flow 3) ───────────────────────────────────

export const teamDashboardHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const [pipeline, overdueByBroker, workload] = await Promise.all([
    leadService.pipelineCounts(user.tenantId, team.brokerIds),
    taskService.overdueCountByBroker(user.tenantId, team.brokerIds),
    teamWorkload(user.tenantId, team.members),
  ]);
  const overdueTasks = Object.values(overdueByBroker).reduce((sum, n) => sum + n, 0);
  res.status(StatusCodes.OK).json({ success: true, data: { pipeline, overdueTasks, workload } });
});

export const teamBookHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const leads = await leadService.listForBrokers(user.tenantId, team.brokerIds);
  res.status(StatusCodes.OK).json({ success: true, data: leads });
});

export const teamBookDetailHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const lead = await leadService.getLeadWithAccess(
    user.tenantId,
    req.params.id as string,
    team.brokerIds,
  );
  res.status(StatusCodes.OK).json({ success: true, data: leadService.maskLead(lead) });
});

export const teamBookPanelHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const panel = await leadService.getPersonPanel(
    user.tenantId,
    req.params.id as string,
    team.brokerIds,
  );
  res.status(StatusCodes.OK).json({ success: true, data: panel });
});

export const teamBookAffordabilityHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const result = await affordabilityService.getAffordability(
    user.tenantId,
    req.params.id as string,
    team.brokerIds,
  );
  res.status(StatusCodes.OK).json({ success: true, data: result });
});

export const reassignLeadHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const lead = await leadService.reassign(
    user.tenantId,
    req.params.id as string,
    team.brokerIds,
    req.body.toBrokerUserId,
    user.userId,
  );
  res.status(StatusCodes.OK).json({ success: true, data: lead });
});

export const teamTasksHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const tasks = await taskService.listForBrokers(user.tenantId, team.brokerIds);
  res.status(StatusCodes.OK).json({ success: true, data: tasks });
});

/** "Team calendar" — every meeting across the team's brokers (closes Flow 3's one remaining gap). */
export const teamCalendarHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const team = await requireOwnTeam(req);
  const appointments = await calendarService.listForBrokers(user.tenantId, team.brokerIds);
  res.status(StatusCodes.OK).json({ success: true, data: appointments });
});

// ── Internal (service-to-service, X-Internal-Secret, no JWT) ────────────────

/** modauth's deactivation check (SOW 2.11) — "do they still have clients assigned?" */
export const internalLeadCountHandler = asyncHandler(async (req: Request, res: Response) => {
  const count = await leadService.activeAssignedLeadCount(
    req.query.tenantId as string,
    req.params.userId as string,
  );
  res.status(StatusCodes.OK).json({ success: true, data: { count } });
});
