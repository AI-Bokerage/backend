import type { Router } from 'express';

import { buildSwaggerSpec } from '../../config/swagger';
import { REQUEST_EXAMPLES } from '../../config/swagger-examples';
import { crmRouter } from '../../modules/crm/crm.routes';
import { platformRouter } from '../../modules/platform/platform.routes';
import { sessionRouter } from '../../modules/platform/session.routes';

type Spec = { paths: Record<string, Record<string, unknown>> };

/** Every route actually registered on a router, as "METHOD /openapi/{path}". */
function routesOf(router: Router, prefix: string): string[] {
  const out: string[] = [];
  for (const layer of router.stack as {
    route?: { path: string; methods: Record<string, boolean> };
  }[]) {
    if (!layer.route) continue;
    const path = prefix + layer.route.path.replace(/:(\w+)/g, '{$1}');
    for (const method of Object.keys(layer.route.methods))
      out.push(`${method.toUpperCase()} ${path}`);
  }
  return out;
}

describe('Swagger covers every route, end to end', () => {
  const spec = buildSwaggerSpec() as unknown as Spec;
  const documented = new Set(
    Object.entries(spec.paths).flatMap(([path, ops]) =>
      Object.keys(ops).map((m) => `${m.toUpperCase()} ${path}`),
    ),
  );

  it.each([
    ['CRM', routesOf(crmRouter, '/crm')],
    ['Platform', routesOf(platformRouter, '/platform')],
    ['Sign-in', routesOf(sessionRouter, '/session')],
  ])('%s: no undocumented routes', (_name, routes) => {
    expect(routes.filter((r) => !documented.has(r))).toEqual([]);
  });

  it('documents nothing that does not exist', () => {
    const real = new Set([
      ...routesOf(crmRouter, '/crm'),
      ...routesOf(platformRouter, '/platform'),
      ...routesOf(sessionRouter, '/session'),
    ]);
    const stale = [...documented].filter((r) => !real.has(r) && !r.endsWith(' /health'));
    expect(stale).toEqual([]);
  });

  it('tags every operation by area and gives it the standard error responses', () => {
    const ops = Object.entries(spec.paths).flatMap(([path, o]) =>
      Object.values(o).map((op) => ({ path, op: op as { tags?: string[]; responses?: object } })),
    );
    expect(
      ops.filter(({ op }) => !op.tags?.length || op.tags[0] === 'CRM').map((o) => o.path),
    ).toEqual([]);
    expect(ops.filter(({ op }) => !('500' in (op.responses ?? {}))).map((o) => o.path)).toEqual([]);
  });

  it('every JSON request body opens in "Try it out" with a real example, not "string"', () => {
    type Op = { requestBody?: { content?: Record<string, { example?: unknown }> } };
    const missing = Object.entries(spec.paths).flatMap(([path, ops]) =>
      Object.entries(ops)
        .filter(([, op]) => {
          const json = (op as Op).requestBody?.content?.['application/json'];
          return json && json.example === undefined;
        })
        .map(([m]) => `${m.toUpperCase()} ${path}`),
    );
    expect(missing).toEqual([]);
  });

  it('has no example for a route that does not exist', () => {
    expect(Object.keys(REQUEST_EXAMPLES).filter((r) => !documented.has(r))).toEqual([]);
  });
});
