// src/config/disposable-domains.ts
// ponytail: small hardcoded list, not the original's S3-backed uploadable
// blocklist — good enough for Phase 1; swap for a real fetched/updatable
// list if abuse from disposable-domain signups becomes a measured problem.
export const DISPOSABLE_DOMAINS: ReadonlySet<string> = new Set([
  "mailinator.com",
  "10minutemail.com",
  "guerrillamail.com",
  "tempmail.com",
  "yopmail.com",
]);
