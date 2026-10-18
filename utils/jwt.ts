import jwt, { type SignOptions } from 'jsonwebtoken';

import { env } from '../config/env';
import type { JwtPayloadShape } from '../types/common';

const getSecret = (tokenType: JwtPayloadShape['tokenType']): string =>
  tokenType === 'access' ? env.jwt.accessSecret : env.jwt.refreshSecret;

const getExpiry = (tokenType: JwtPayloadShape['tokenType']): SignOptions['expiresIn'] =>
  tokenType === 'access'
    ? (env.jwt.accessExpiresIn as SignOptions['expiresIn'])
    : (env.jwt.refreshExpiresIn as SignOptions['expiresIn']);

export const signToken = (
  payload: Omit<JwtPayloadShape, 'tokenType'>,
  tokenType: 'access' | 'refresh',
): string =>
  jwt.sign({ ...payload, tokenType }, getSecret(tokenType), {
    expiresIn: getExpiry(tokenType),
  });

export const verifyToken = (token: string, tokenType: 'access' | 'refresh'): JwtPayloadShape =>
  jwt.verify(token, getSecret(tokenType)) as JwtPayloadShape;
