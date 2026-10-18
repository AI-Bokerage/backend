// src/modules/signup/v1/repo.test.ts
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { PrismaClient } from "../../../../__generated__/prisma/index.js";
import { startTestPostgres } from "../../../../tests/support/postgres-container.ts";
import { PrismaSignupSessionRepo } from "./repo.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type { StartedPostgreSqlContainer } from "@testcontainers/postgresql";

describe("PrismaSignupSessionRepo", () => {
  let container: StartedPostgreSqlContainer;
  let prisma: PrismaClient;
  let repo: PrismaSignupSessionRepo;

  beforeAll(async () => {
    const started = await startTestPostgres();
    container = started.container;
    prisma = new PrismaClient({ datasources: { db: { url: started.databaseUrl } } });
    repo = new PrismaSignupSessionRepo(prisma);
  }, 60_000);

  afterAll(async () => {
    await prisma.$disconnect();
    await container.stop();
  });

  function baseInput(overrides: Partial<Parameters<typeof repo.create>[0]> = {}) {
    return {
      email: "founder@example.com",
      desiredSubdomain: "acme",
      authUserId: "11111111-1111-1111-1111-111111111111",
      emailVerificationTokenHash: "hash-abc",
      resumeTokenHash: "resume-abc",
      expiresAt: new Date(Date.now() + 86_400_000),
      ...overrides,
    };
  }

  it("creates a session in STARTED state", async () => {
    const session = await repo.create(baseInput());
    expect(session.state).toBe(SignupState.STARTED);
    expect(session.email).toBe("founder@example.com");
  });

  it("finds a session by id", async () => {
    const created = await repo.create(baseInput({ email: "findme@example.com", desiredSubdomain: "findme" }));
    const found = await repo.findById(created.id);
    expect(found?.email).toBe("findme@example.com");
  });

  it("finds a session by email verification token hash", async () => {
    await repo.create(baseInput({ email: "token@example.com", desiredSubdomain: "tokentest", emailVerificationTokenHash: "unique-hash-1" }));
    const found = await repo.findByEmailVerificationTokenHash("unique-hash-1");
    expect(found?.email).toBe("token@example.com");
  });

  it("reports existsActiveForEmail true for a STARTED session, false after it's ABANDONED", async () => {
    const session = await repo.create(baseInput({ email: "active@example.com", desiredSubdomain: "activetest" }));
    expect(await repo.existsActiveForEmail("active@example.com")).toBe(true);

    await repo.updateState(session.id, SignupState.ABANDONED);
    expect(await repo.existsActiveForEmail("active@example.com")).toBe(false);
  });

  it("reports existsActiveForSubdomain the same way", async () => {
    await repo.create(baseInput({ email: "sub@example.com", desiredSubdomain: "subtest" }));
    expect(await repo.existsActiveForSubdomain("subtest")).toBe(true);
    expect(await repo.existsActiveForSubdomain("never-used-slug")).toBe(false);
  });

  it("updateState applies a patch alongside the state change", async () => {
    const session = await repo.create(baseInput({ email: "patch@example.com", desiredSubdomain: "patchtest" }));
    const updated = await repo.updateState(session.id, SignupState.EMAIL_VERIFIED, { emailVerifiedAt: new Date() });
    expect(updated.state).toBe(SignupState.EMAIL_VERIFIED);
    expect(updated.emailVerifiedAt).not.toBeNull();
  });

  it("findExpiredInStates only returns sessions past expiresAt in the given states", async () => {
    const expired = await repo.create(baseInput({
      email: "expired@example.com", desiredSubdomain: "expiredtest", expiresAt: new Date(Date.now() - 1000),
    }));
    await repo.create(baseInput({
      email: "notexpired@example.com", desiredSubdomain: "notexpiredtest", expiresAt: new Date(Date.now() + 86_400_000),
    }));

    const found = await repo.findExpiredInStates([SignupState.STARTED], new Date());
    expect(found.map((s) => s.id)).toContain(expired.id);
    expect(found.map((s) => s.email)).not.toContain("notexpired@example.com");
  });

  it("findInState returns only sessions in that exact state", async () => {
    const session = await repo.create(baseInput({ email: "instate@example.com", desiredSubdomain: "instatetest" }));
    await repo.updateState(session.id, SignupState.PROVISIONING);

    const found = await repo.findInState(SignupState.PROVISIONING);
    expect(found.map((s) => s.id)).toContain(session.id);
  });
});
