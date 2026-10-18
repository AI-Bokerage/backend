import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import { getMeterDefinition } from "./registry.ts";

export function counterKey(tenantId: string, metric: string): string {
  return `genusg:${tenantId}:${metric}`;
}

export function resourceKey(tenantId: string, metric: string): string {
  return `genusg:resource:${tenantId}:${metric}`;
}

export async function readCurrentValue(counterStore: ICounterStore, tenantId: string, metric: string): Promise<number> {
  const def = getMeterDefinition(metric);
  if (def?.mode === "resource") {
    return counterStore.zsumScores(resourceKey(tenantId, metric));
  }
  return (await counterStore.get(counterKey(tenantId, metric))) ?? 0;
}
