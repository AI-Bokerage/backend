import sanitizeHtml from 'sanitize-html';

/**
 * Central sanitization utility for text input.
 * Keep this strict for APIs that should not store/render HTML.
 */
export const sanitizeText = (value: string): string =>
  sanitizeHtml(value, { allowedTags: [], allowedAttributes: {} }).trim();
