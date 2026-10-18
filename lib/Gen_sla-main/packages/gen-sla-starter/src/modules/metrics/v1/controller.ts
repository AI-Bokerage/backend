import type { Request, Response } from "express";
import { metricsQuerySchema, trendsQuerySchema } from "./schema.ts";
import type { MetricsService } from "./service.ts";

export function makeMetricsController(service: MetricsService) {
  return {
    async getSummary(req: Request, res: Response) {
      const query = metricsQuerySchema.parse(req.query);
      res.json(await service.getSummary(req.params.tenantId, query));
    },
    async getComplianceRates(req: Request, res: Response) {
      const query = metricsQuerySchema.parse(req.query);
      res.json(await service.getComplianceRates(req.params.tenantId, query));
    },
    async getBreaches(req: Request, res: Response) {
      const query = metricsQuerySchema.parse(req.query);
      res.json(await service.getBreaches(req.params.tenantId, query));
    },
    async getTrends(req: Request, res: Response) {
      const query = trendsQuerySchema.parse(req.query);
      res.json(await service.getTrends(req.params.tenantId, query));
    },
  };
}
