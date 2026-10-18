export interface NotificationContent {
  title: string;
  body: string;
  html?: string;
}

export type TemplateBuilder = (data: Record<string, unknown>) => NotificationContent;

const registry = new Map<string, TemplateBuilder>();

const FALLBACK_BUILDER: TemplateBuilder = (data) => ({
  title: "New notification",
  body: typeof data["message"] === "string" ? data["message"] : "You have a new notification.",
});

export function registerTemplate(eventType: string, builder: TemplateBuilder): void {
  registry.set(eventType, builder);
}

export function renderTemplate(eventType: string, data: Record<string, unknown>): NotificationContent {
  const builder = registry.get(eventType) ?? FALLBACK_BUILDER;
  return builder(data);
}

export function listRegisteredEventTypes(): string[] {
  return [...registry.keys()];
}

/** Test-only: clears all registered templates. Not exported from the package's public barrel. */
export function __resetRegistryForTests(): void {
  registry.clear();
}
