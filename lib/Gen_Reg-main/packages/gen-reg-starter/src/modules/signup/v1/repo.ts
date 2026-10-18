// src/modules/signup/v1/repo.ts
import type { PrismaClient } from "../../../../__generated__/prisma/index.js";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import type {
  ISignupSessionRepo,
  SignupSessionRecord,
  CreateSignupSessionInput,
} from "../../../domain/ports/signup-session.repository.port.ts";

const NON_ACTIVE_STATES = [SignupState.ABANDONED, SignupState.PROVISION_FAILED];

export class PrismaSignupSessionRepo implements ISignupSessionRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(input: CreateSignupSessionInput): Promise<SignupSessionRecord> {
    return this.prisma.signupSession.create({ data: input }) as unknown as Promise<SignupSessionRecord>;
  }

  async findById(id: string): Promise<SignupSessionRecord | null> {
    return this.prisma.signupSession.findUnique({ where: { id } }) as unknown as Promise<SignupSessionRecord | null>;
  }

  async findByEmailVerificationTokenHash(hash: string): Promise<SignupSessionRecord | null> {
    return this.prisma.signupSession.findFirst({
      where: { emailVerificationTokenHash: hash },
    }) as unknown as Promise<SignupSessionRecord | null>;
  }

  async findByResumeTokenHash(hash: string): Promise<SignupSessionRecord | null> {
    return this.prisma.signupSession.findFirst({
      where: { resumeTokenHash: hash },
    }) as unknown as Promise<SignupSessionRecord | null>;
  }

  async existsActiveForEmail(email: string): Promise<boolean> {
    const count = await this.prisma.signupSession.count({
      where: { email, state: { notIn: NON_ACTIVE_STATES } },
    });
    return count > 0;
  }

  async existsActiveForSubdomain(subdomain: string): Promise<boolean> {
    const count = await this.prisma.signupSession.count({
      where: { desiredSubdomain: subdomain, state: { notIn: NON_ACTIVE_STATES } },
    });
    return count > 0;
  }

  async updateState(
    id: string,
    state: SignupState,
    patch: Partial<SignupSessionRecord> = {},
  ): Promise<SignupSessionRecord> {
    return this.prisma.signupSession.update({
      where: { id },
      data: { state, ...patch },
    }) as unknown as Promise<SignupSessionRecord>;
  }

  async findExpiredInStates(states: SignupState[], now: Date): Promise<SignupSessionRecord[]> {
    return this.prisma.signupSession.findMany({
      where: { state: { in: states }, expiresAt: { lt: now } },
    }) as unknown as Promise<SignupSessionRecord[]>;
  }

  async findInState(state: SignupState): Promise<SignupSessionRecord[]> {
    return this.prisma.signupSession.findMany({ where: { state } }) as unknown as Promise<SignupSessionRecord[]>;
  }
}
