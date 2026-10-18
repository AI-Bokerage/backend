process.env.NODE_ENV = 'test';
process.env.PORT = '3001';
process.env.APP_NAME = 'Enterprise Backend Template Test';
process.env.API_PREFIX = '/api/v1';
process.env.ALLOWED_ORIGINS = 'http://localhost:3000';
process.env.LOG_LEVEL = 'error';
process.env.JWT_ACCESS_SECRET = 'test_access_secret_123456';
process.env.JWT_REFRESH_SECRET = 'test_refresh_secret_123456';
process.env.JWT_ACCESS_EXPIRES_IN = '15m';
process.env.JWT_REFRESH_EXPIRES_IN = '7d';
process.env.BCRYPT_SALT_ROUNDS = '10';
process.env.DB_CLIENT = 'prisma';
process.env.DATABASE_URL = 'postgresql://postgres:postgres@localhost:5432/template_db';
process.env.MONGODB_URI = 'mongodb://localhost:27017/template_db';

// modules/platform
process.env.INTERNAL_SERVICE_SECRET = 'test-internal-secret';
process.env.AUTH_JWT_PUBLIC_KEY = [
  '-----BEGIN PUBLIC KEY-----',
  'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArjcOkeJc1ZklqDmn2/fr',
  'EepdFjOnhIFRo85cDqBsVBx7nvedJsDgg1P8IQKtHXLBm/unG7buaCqQLfSA0TkG',
  'GReqPunt2aARQdVCh4iKPHnrp2Xn98D4+lhEsUHqR/9U6xy0f6abB1zYx6fj+Gv3',
  'I56T8oeu1INb+S+sq7YWEFEqRZzsvy/h4jHFsxF4D4eFUPAHcfO+fGUz0Gfs2Nke',
  'IujxYfGykR+7Q9d7Baon1IOaar6vlPlUtFahek4vGKZdzpxjcs3KnPH+6gyqRs7Q',
  'XxJwl6KWBTqgYT7dOOgJUVjSbYgBRZn3zy7k0XJEmw0qW7SJqzSbrqdzN3y3jKLe',
  'tQIDAQAB',
  '-----END PUBLIC KEY-----',
].join('\n');
