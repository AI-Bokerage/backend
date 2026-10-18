// src/infra/auth-client/auth-client.ts
import { SignupEmailAlreadyRegisteredError, GenAuthRegistrationError } from "../../common/errors.ts";
import type { IAuthClient, RegisterResult } from "../../domain/ports/auth-client.port.ts";

export class HttpAuthClient implements IAuthClient {
  constructor(private readonly baseUrl: string) {}

  async register(email: string, password: string): Promise<RegisterResult> {
    const res = await fetch(`${this.baseUrl}/api/v1/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });

    if (res.status === 409) {
      throw new SignupEmailAlreadyRegisteredError(email);
    }
    if (res.status !== 201) {
      throw new GenAuthRegistrationError(`Gen_Auth register failed: ${res.status} ${await res.text()}`);
    }

    const body = (await res.json()) as { userId: string };
    return { userId: body.userId };
  }
}
