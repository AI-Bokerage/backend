import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import * as calendarService from './calendar.service';
import * as gmailService from './gmail.service';
import * as connectionService from './google-connection.service';
import { ApiError } from '../../../utils/api-error';
import { asyncHandler } from '../../../utils/async-handler';
import { getModAuthPerson } from '../modauth.client';

function crmUser(req: Request) {
  return req.crmUser!;
}

export const getConnectUrlHandler = asyncHandler(async (req: Request, res: Response) => {
  const url = connectionService.getConnectUrl(crmUser(req).userId);
  res.status(StatusCodes.OK).json({ success: true, data: { url } });
});

/**
 * Public — Google redirects the browser here directly, with no JWT.
 * `state` carries the userId through the redirect (see getConnectUrl); the
 * tenantId it needs to store comes from modules/auth's internal lookup,
 * exactly like crm-auth.middleware.ts resolves it for a normal request.
 */
export const oauthCallbackHandler = asyncHandler(async (req: Request, res: Response) => {
  const { code, state: userId } = req.query as { code: string; state: string };
  const person = await getModAuthPerson(userId);
  if (!person) {
    throw new ApiError('Unknown user for this connection attempt', StatusCodes.BAD_REQUEST);
  }
  const status = await connectionService.completeConnection(userId, person.tenantId, code);
  res.status(StatusCodes.OK).json({ success: true, data: status });
});

export const getConnectionStatusHandler = asyncHandler(async (req: Request, res: Response) => {
  const status = await connectionService.getStatus(crmUser(req).userId);
  res.status(StatusCodes.OK).json({ success: true, data: status });
});

export const disconnectHandler = asyncHandler(async (req: Request, res: Response) => {
  await connectionService.disconnect(crmUser(req).userId);
  res.status(StatusCodes.NO_CONTENT).send();
});

export const createAppointmentHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const appointment = await calendarService.createAppointment(user.tenantId, user.userId, req.body);
  res.status(StatusCodes.CREATED).json({ success: true, data: appointment });
});

export const listMyAppointmentsHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  const appointments = await calendarService.listMyAppointments(
    user.tenantId,
    user.userId,
    req.query,
  );
  res.status(StatusCodes.OK).json({ success: true, data: appointments });
});

export const sendEmailHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = crmUser(req);
  await gmailService.sendEmail(user.tenantId, user.userId, req.body);
  res.status(StatusCodes.NO_CONTENT).send();
});
