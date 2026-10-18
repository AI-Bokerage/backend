import type { Request, Response } from "express";
import { revenueQuerySchema, revenueHistoryQuerySchema, usageQuerySchema } from "./schema.ts";
import type { AnalyticsService } from "./service.ts";
import type { RevenueBreakdownByPlan } from "./types.ts";

function csvField(value: string): string {
  // Guard against spreadsheet formula injection: a leading =/+/-/@ is
  // interpreted as a formula by Excel/Sheets when the CSV is opened, not
  // just displayed as text. Prefixing with a tab neutralizes it without
  // altering the visible value.
  const safe = /^[=+\-@]/.test(value) ? `\t${value}` : value;
  if (/[",\r\n]/.test(safe)) return `"${safe.replace(/"/g, '""')}"`;
  return safe;
}

function toCsv(rows: RevenueBreakdownByPlan[]): string {
  const header = "planCode,mrr,tenantCount";
  const lines = rows.map((r) => `${csvField(r.planCode ?? "")},${r.mrr},${r.tenantCount}`);
  return [header, ...lines].join("\n");
}

// Filename segments come from user-supplied query params (planCode/region) —
// arbitrary text, including non-latin1 characters that crash res.setHeader
// and separators/traversal-looking sequences a browser could misparse.
// Reduce each segment to a small safe character set before it ever reaches
// the Content-Disposition header.
function filenameSegment(value: string): string {
  return value.replace(/[^A-Za-z0-9._-]/g, "-");
}

export function makeAnalyticsController(service: AnalyticsService) {
  return {
    async getRevenue(req: Request, res: Response) {
      const query = revenueQuerySchema.parse(req.query);
      const snapshot = await service.getRevenueSnapshot({
        planCode: query.planCode,
        region: query.region,
        from: query.from ? new Date(query.from) : undefined,
        to: query.to ? new Date(query.to) : undefined,
      });
      if (query.export === "csv") {
        const filename = `mrr-by-plan${query.region ? `-${filenameSegment(query.region)}` : ""}${query.planCode ? `-${filenameSegment(query.planCode)}` : ""}.csv`;
        res.setHeader("Content-Type", "text/csv");
        res.setHeader("Content-Disposition", `attachment; filename=${filename}`);
        res.send(toCsv(snapshot.byPlan));
        return;
      }
      res.json(snapshot);
    },
    async getRevenueHistory(req: Request, res: Response) {
      const query = revenueHistoryQuerySchema.parse(req.query);
      const history = await service.getRevenueHistory({
        period: query.period,
        from: query.from ? new Date(query.from) : undefined,
        to: query.to ? new Date(query.to) : undefined,
      });
      res.json({ history });
    },
    async getUsage(req: Request, res: Response) {
      const { tenantIds } = usageQuerySchema.parse(req.query);
      const result = await service.getUsageAcrossTenants(tenantIds);
      res.json(result);
    },
  };
}
