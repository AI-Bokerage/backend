import type { Request, Response } from "express";
import { createPolicySchema, updatePolicySchema, listPoliciesQuerySchema, policyIdSchema } from "./schema.ts";
import type { PolicyService } from "./service.ts";

export function makePolicyController(service: PolicyService) {
  return {
    async createPolicy(req: Request, res: Response) {
      const input = createPolicySchema.parse(req.body);
      const policy = await service.createPolicy(req.params.tenantId, input);
      res.status(201).json(policy);
    },
    async listPolicies(req: Request, res: Response) {
      const query = listPoliciesQuerySchema.parse(req.query);
      const result = await service.listPolicies(req.params.tenantId, query);
      res.json(result);
    },
    async getPolicy(req: Request, res: Response) {
      const { id } = policyIdSchema.parse(req.params);
      const policy = await service.getPolicy(req.params.tenantId, id);
      res.json(policy);
    },
    async updatePolicy(req: Request, res: Response) {
      const { id } = policyIdSchema.parse(req.params);
      const input = updatePolicySchema.parse(req.body);
      const policy = await service.updatePolicy(req.params.tenantId, id, input);
      res.json(policy);
    },
    async deletePolicy(req: Request, res: Response) {
      const { id } = policyIdSchema.parse(req.params);
      await service.deletePolicy(req.params.tenantId, id);
      res.status(204).send();
    },
  };
}
