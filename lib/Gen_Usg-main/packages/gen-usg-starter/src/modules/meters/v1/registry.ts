export type MeterMode = "counter" | "resource";

export interface MeterDefinition {
  unit: string;
  graceEligible: boolean;
  mode: MeterMode;
}

export interface RegisterMeterInput {
  unit: string;
  graceEligible?: boolean;
  mode?: MeterMode;
}

const registry = new Map<string, MeterDefinition>();

export function registerMeter(code: string, def: RegisterMeterInput): void {
  registry.set(code, {
    unit: def.unit,
    graceEligible: def.graceEligible ?? false,
    mode: def.mode ?? "counter",
  });
}

export function getMeterDefinition(code: string): MeterDefinition | undefined {
  return registry.get(code);
}

export function listRegisteredMeterCodes(): string[] {
  return [...registry.keys()];
}

/** Test-only: clears the module-level registry between test files. */
export function _clearRegistryForTests(): void {
  registry.clear();
}
