// src/common/slug.ts
export const SLUG_MIN_LEN = 3;
export const SLUG_MAX_LEN = 63;
export const SLUG_REGEX = /^[a-z][a-z0-9-]*[a-z0-9]$/;

export const RESERVED_SUBDOMAINS: ReadonlySet<string> = new Set([
  "www", "api", "app", "admin", "mail", "ftp", "localhost", "staging", "dev", "test", "docs", "status",
]);

export function toSlug(raw: string): string {
  return raw
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

export function isValidSlug(slug: string): boolean {
  return slug.length >= SLUG_MIN_LEN && slug.length <= SLUG_MAX_LEN && SLUG_REGEX.test(slug);
}
