import { GenNotifConfigError } from "../common/errors.ts";

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new GenNotifConfigError(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function optionalEnv(name: string, fallback: string): string {
  return process.env[name] ?? fallback;
}
