import { PrismaClient } from '@prisma/client';
import mongoose from 'mongoose';

import { env } from './env';
import { logger } from './logger';

let prismaClient: PrismaClient | null = null;

export const getPrismaClient = (): PrismaClient => {
  if (!prismaClient) {
    prismaClient = new PrismaClient();
  }
  return prismaClient;
};

export const connectPrisma = async (): Promise<void> => {
  const client = getPrismaClient();
  await client.$connect();
  logger.info('Connected to PostgreSQL via Prisma');
};

export const disconnectPrisma = async (): Promise<void> => {
  if (!prismaClient) return;
  await prismaClient.$disconnect();
  logger.info('Disconnected Prisma client');
};

export const connectMongoose = async (): Promise<void> => {
  await mongoose.connect(env.mongodbUri);
  logger.info('Connected to MongoDB via Mongoose');
};

export const disconnectMongoose = async (): Promise<void> => {
  if (mongoose.connection.readyState === 0) return;
  await mongoose.disconnect();
  logger.info('Disconnected Mongoose client');
};

export const connectDatabases = async (): Promise<void> => {
  if (env.dbClient === 'prisma') {
    await connectPrisma();
    return;
  }

  if (env.dbClient === 'mongoose') {
    await connectMongoose();
    return;
  }

  await Promise.all([connectPrisma(), connectMongoose()]);
};

export const disconnectDatabases = async (): Promise<void> => {
  await Promise.all([disconnectPrisma(), disconnectMongoose()]);
};
