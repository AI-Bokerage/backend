export type UserRole = 'admin' | 'editor' | 'viewer';

export interface JwtPayloadShape {
  sub: string;
  role: UserRole;
  tokenType: 'access' | 'refresh';
}

export interface AuthUser {
  id: string;
  role: UserRole;
}

export interface PaginationQuery {
  page?: number;
  limit?: number;
}
