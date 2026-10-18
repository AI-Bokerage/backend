import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import * as aiAssistant from './ai-assistant.service';
import * as aiEmail from './ai-email.service';
import * as aiNotes from './ai-notes.service';
import * as aiReviewService from './ai-review.service';
import * as aiSearch from './ai-search.service';
import { leadAccessIds } from './crm-auth.middleware';
import { asyncHandler } from '../../utils/async-handler';

function crmUser(req: Request) {
  // Guaranteed set by requireModAuthRole before any handler here runs.
  return req.crmUser!;
}

/** Who the AI services act for: brokerage, user and role from the verified CRM user. */
function aiCaller(req: Request) {
  const { tenantId, userId, role } = crmUser(req);
  return { tenantId, userId, role };
}

const id = (req: Request) => req.params.id as string;

// Jobs answer 202: the frontend polls the matching GET until a terminal status.
export const uploadVoiceNoteHandler = asyncHandler(async (req: Request, res: Response) => {
  const audio = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
  const note = await aiNotes.uploadVoiceNote(
    aiCaller(req),
    audio,
    req.headers['content-type'] ?? 'application/octet-stream',
  );
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: note });
});

export const getVoiceNoteHandler = asyncHandler(async (req: Request, res: Response) => {
  res.json({ success: true, data: await aiNotes.getVoiceNote(aiCaller(req), id(req)) });
});

export const retryVoiceNoteHandler = asyncHandler(async (req: Request, res: Response) => {
  const note = await aiNotes.retryVoiceNote(aiCaller(req), id(req));
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: note });
});

export const analyseDraftNotesHandler = asyncHandler(async (req: Request, res: Response) => {
  const run = await aiNotes.analyseDraftNotes(aiCaller(req), req.body);
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: run });
});

export const analyseLeadNotesHandler = asyncHandler(async (req: Request, res: Response) => {
  const run = await aiNotes.analyseLeadNotes(aiCaller(req), id(req), await leadAccessIds(crmUser(req)));
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: run });
});

export const getRunHandler = asyncHandler(async (req: Request, res: Response) => {
  const run = await aiNotes.getRun(aiCaller(req), id(req), await leadAccessIds(crmUser(req)));
  res.json({ success: true, data: run });
});

export const decideRunHandler = asyncHandler(async (req: Request, res: Response) => {
  const run = await aiNotes.decideRun(
    aiCaller(req),
    id(req),
    await leadAccessIds(crmUser(req)),
    req.body.decisions,
  );
  res.json({ success: true, data: run });
});

export const retryRunHandler = asyncHandler(async (req: Request, res: Response) => {
  const run = await aiNotes.retryRun(aiCaller(req), id(req), await leadAccessIds(crmUser(req)));
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: run });
});

export const applyReviewHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await aiNotes.applyReviewToLead(
    aiCaller(req),
    await leadAccessIds(crmUser(req)),
    id(req),
    req.body.aiRunId,
  );
  res.json({ success: true, data: result });
});

export const draftEmailHandler = asyncHandler(async (req: Request, res: Response) => {
  const draft = await aiEmail.startEmailDraft(
    { ...aiCaller(req), name: crmUser(req).name },
    id(req),
    await leadAccessIds(crmUser(req)),
    req.body,
  );
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: draft });
});

export const getEmailDraftHandler = asyncHandler(async (req: Request, res: Response) => {
  const version = req.query.version ? Number(req.query.version) : undefined;
  const draft = await aiEmail.getEmailDraft(aiCaller(req), id(req), await leadAccessIds(crmUser(req)), version);
  res.json({ success: true, data: draft });
});

export const refineEmailDraftHandler = asyncHandler(async (req: Request, res: Response) => {
  const draft = await aiEmail.refineEmailDraft(aiCaller(req), id(req), await leadAccessIds(crmUser(req)), req.body);
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: draft });
});

export const retryEmailDraftHandler = asyncHandler(async (req: Request, res: Response) => {
  const draft = await aiEmail.retryEmailDraft(aiCaller(req), id(req), await leadAccessIds(crmUser(req)));
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: draft });
});

export const suggestTasksHandler = asyncHandler(async (req: Request, res: Response) => {
  const result = await aiNotes.suggestTasks(aiCaller(req), id(req), await leadAccessIds(crmUser(req)));
  res.status(result.ready ? StatusCodes.OK : StatusCodes.ACCEPTED).json({ success: true, data: result });
});

export const askAssistantHandler = asyncHandler(async (req: Request, res: Response) => {
  const answer = await aiAssistant.askAssistant(aiCaller(req), await leadAccessIds(crmUser(req)), req.body);
  res.status(StatusCodes.OK).json({ success: true, data: answer });
});

export const listEmailIntentsHandler = asyncHandler(async (_req: Request, res: Response) => {
  const intents = Object.entries(aiReviewService.EMAIL_INTENTS).map(([key, label]) => ({
    key,
    label,
  }));
  res.status(StatusCodes.OK).json({ success: true, data: intents });
});

// Dashboard "AI suggestions" (assistant-svc recommendations).
export const refreshRecommendationsHandler = asyncHandler(async (req: Request, res: Response) => {
  const run = await aiAssistant.refreshRecommendations(aiCaller(req), await leadAccessIds(crmUser(req)));
  res.status(StatusCodes.ACCEPTED).json({ success: true, data: run });
});

export const latestRecommendationRunHandler = asyncHandler(async (req: Request, res: Response) => {
  res.status(StatusCodes.OK).json({ success: true, data: await aiAssistant.latestRecommendationRun(aiCaller(req)) });
});

export const listRecommendationsHandler = asyncHandler(async (req: Request, res: Response) => {
  res.status(StatusCodes.OK).json({ success: true, data: await aiAssistant.listRecommendations(aiCaller(req)) });
});

export const dismissRecommendationHandler = asyncHandler(async (req: Request, res: Response) => {
  res.status(StatusCodes.OK).json({ success: true, data: await aiAssistant.dismissRecommendation(aiCaller(req), id(req)) });
});

export const recommendationTaskHandler = asyncHandler(async (req: Request, res: Response) => {
  const task = await aiAssistant.addRecommendationAsTask(aiCaller(req), await leadAccessIds(crmUser(req)), id(req), req.body);
  res.status(StatusCodes.CREATED).json({ success: true, data: task });
});

export const smartSearchHandler = asyncHandler(async (req: Request, res: Response) => {
  const found = await aiSearch.smartSearch(aiCaller(req), await leadAccessIds(crmUser(req)), req.body.query);
  res.status(StatusCodes.OK).json({ success: true, data: found });
});
