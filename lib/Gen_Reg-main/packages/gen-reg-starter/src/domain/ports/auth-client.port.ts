// src/domain/ports/auth-client.port.ts
export interface RegisterResult {
  userId: string;
}

export interface IAuthClient {
  register(email: string, password: string): Promise<RegisterResult>;
}
