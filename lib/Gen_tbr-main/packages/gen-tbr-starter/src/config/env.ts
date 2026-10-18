import { GenTbrConfigError } from "../common/errors.ts";

export function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new GenTbrConfigError(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function optionalEnv(name: string): string | undefined {
  return process.env[name] || undefined;
}
