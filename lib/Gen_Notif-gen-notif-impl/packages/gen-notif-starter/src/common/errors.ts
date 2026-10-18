export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = new.target.name;
  }
}

export class GenNotifConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenNotifConfigError";
  }
}

export class NotificationNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "NOTIFICATION_NOT_FOUND", `Notification "${id}" not found`);
  }
}

export class NotificationForbiddenError extends AppError {
  constructor() {
    super(403, "NOTIFICATION_FORBIDDEN", "Access to this notification is denied");
  }
}

export class PreferenceNotFoundError extends AppError {
  constructor() {
    super(404, "PREFERENCE_NOT_FOUND", "Notification preference not found");
  }
}

export class WebhookEndpointNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "WEBHOOK_ENDPOINT_NOT_FOUND", `Webhook endpoint "${id}" not found`);
  }
}

export class SmsNotConfiguredError extends AppError {
  constructor() {
    super(501, "SMS_NOT_CONFIGURED", "No SMS provider is configured — supply an ISmsSender override");
  }
}

export class ChannelDispatchError extends AppError {
  constructor(channel: string, cause: string) {
    super(502, "CHANNEL_DISPATCH_ERROR", `Failed to dispatch via "${channel}": ${cause}`);
  }
}
