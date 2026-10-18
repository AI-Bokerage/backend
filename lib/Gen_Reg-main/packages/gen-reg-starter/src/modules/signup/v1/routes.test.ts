// src/modules/signup/v1/routes.test.ts
import { describe, it, expect, vi } from "vitest";
import express from "express";
import "express-async-errors";
import request from "supertest";
import { signupRoutes } from "./routes.ts";
import type { SignupController } from "./controller.ts";

function fakeController(): SignupController {
  return {
    startSignup: vi.fn((_req, res) => res.status(201).json({ ok: true })),
    checkSubdomain: vi.fn((_req, res) => res.status(200).json({ ok: true })),
    resumeSignup: vi.fn((_req, res) => res.status(200).json({ ok: true })),
  } as unknown as SignupController;
}

describe("signupRoutes", () => {
  it("mounts POST /signup without a captcha middleware when none is supplied", async () => {
    const controller = fakeController();
    const app = express();
    app.use(express.json());
    app.use(signupRoutes(controller));

    const res = await request(app).post("/signup").send({});
    expect(res.status).toBe(201);
    expect(controller.startSignup).toHaveBeenCalledOnce();
  });

  it("runs the supplied captcha middleware before POST /signup's controller", async () => {
    const controller = fakeController();
    const order: string[] = [];
    const captchaMiddleware = vi.fn((_req, _res, next) => {
      order.push("captcha");
      next();
    });
    (controller.startSignup as ReturnType<typeof vi.fn>).mockImplementation((_req, res) => {
      order.push("controller");
      res.status(201).json({ ok: true });
    });

    const app = express();
    app.use(express.json());
    app.use(signupRoutes(controller, captchaMiddleware));

    await request(app).post("/signup").send({});

    expect(captchaMiddleware).toHaveBeenCalledOnce();
    expect(order).toEqual(["captcha", "controller"]);
  });

  it("does not run the captcha middleware on check-subdomain or resume", async () => {
    const controller = fakeController();
    const captchaMiddleware = vi.fn((_req, _res, next) => next());
    const app = express();
    app.use(express.json());
    app.use(signupRoutes(controller, captchaMiddleware));

    await request(app).get("/signup/check-subdomain?value=acme");
    await request(app).get("/signup/resume?token=abc");

    expect(captchaMiddleware).not.toHaveBeenCalled();
  });
});
