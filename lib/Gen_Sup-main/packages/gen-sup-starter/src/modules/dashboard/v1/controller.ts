import type { Request, Response } from "express";
import { dashboardKpisQuerySchema } from "./schema.ts";
import type { DashboardService } from "./service.ts";

export function makeDashboardController(service: DashboardService) {
  return {
    async getKpis(req: Request, res: Response) {
      const { forceRefresh } = dashboardKpisQuerySchema.parse(req.query);
      const kpis = await service.getKpis(forceRefresh);
      res.json(kpis);
    },
  };
}
