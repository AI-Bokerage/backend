import type { Request, Response } from 'express';
import { StatusCodes } from 'http-status-codes';

import { getTenantPeople, resolveTeam } from './modauth.client';
import * as reportsService from './reports.service';
import type { ReportType } from './reports.service';
import { ApiError } from '../../utils/api-error';
import { asyncHandler } from '../../utils/async-handler';

interface ReportQuery {
  type: ReportType;
  from?: string;
  to?: string;
  brokerId?: string;
  teamId?: string;
}

/**
 * "Set date range, broker, team" — a Tenant Admin's scope defaults to every
 * person in the brokerage (or one team/broker if given); a Team Lead's scope
 * is always their own team, optionally narrowed to one broker on it.
 */
async function resolveReportScope(req: Request) {
  const user = req.crmUser!;
  const { brokerId, teamId } = req.query as unknown as ReportQuery;

  let brokerIds: string[];
  if (user.role === 'TENANT_ADMIN') {
    if (teamId) {
      brokerIds = (await resolveTeam(teamId, user.bearerToken)).brokerIds;
    } else {
      brokerIds = (await getTenantPeople(user.bearerToken)).map((p) => p.userId);
    }
  } else {
    if (!user.teamId) {
      throw new ApiError('You are not assigned to a team', StatusCodes.FORBIDDEN);
    }
    brokerIds = (await resolveTeam(user.teamId, user.bearerToken)).brokerIds;
  }

  if (brokerId) {
    if (!brokerIds.includes(brokerId)) {
      throw new ApiError('That broker is outside your scope', StatusCodes.FORBIDDEN);
    }
    brokerIds = [brokerId];
  }

  return { tenantId: user.tenantId, brokerIds };
}

function parseFilter(req: Request, scope: { tenantId: string; brokerIds: string[] }) {
  const { from, to } = req.query as unknown as ReportQuery;
  return { ...scope, from: from ? new Date(from) : undefined, to: to ? new Date(to) : undefined };
}

export const getReportHandler = asyncHandler(async (req: Request, res: Response) => {
  const { type } = req.query as unknown as ReportQuery;
  const scope = await resolveReportScope(req);
  const data = await reportsService.runReport(type, parseFilter(req, scope));
  res.status(StatusCodes.OK).json({ success: true, data });
});

export const exportReportHandler = asyncHandler(async (req: Request, res: Response) => {
  const user = req.crmUser!;
  const { type } = req.query as unknown as ReportQuery;
  const scope = await resolveReportScope(req);
  const csv = await reportsService.exportReportCsv(type, parseFilter(req, scope), user.userId);
  res
    .status(StatusCodes.OK)
    .set('Content-Type', 'text/csv')
    .set('Content-Disposition', `attachment; filename="${type}-report.csv"`)
    .send(csv);
});
