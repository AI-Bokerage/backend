import { GenFmmConfigError } from "../common/errors.ts";

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new GenFmmConfigError(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function optionalEnv(name: string, fallback: string): string {
  return process.env[name] ?? fallback;
}

// Number("garbage") is NaN, and NaN silently disables downstream size/TTL
// checks (NaN comparisons are always false) instead of failing — a config
// error must throw synchronously at factory-call time, never mid-request.
export function intEnv(name: string, fallback: number): number {
  const raw = process.env[name];
  const value = raw === undefined ? fallback : Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    throw new GenFmmConfigError(`Environment variable ${name} must be a positive finite number, got: ${raw}`);
  }
  return value;
}
