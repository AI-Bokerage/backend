import { Router } from "express";
import type { NotificationController } from "./controller.ts";

export function notificationsRoutes(controller: NotificationController): Router {
  const router = Router();
  router.get("/unread", controller.listUnread);
  router.patch("/:id/read", controller.markRead);
  return router;
}
