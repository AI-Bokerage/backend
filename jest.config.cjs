/** @type {import('jest').Config} */
module.exports = {
  // Compile only, no type-check per file: `pnpm typecheck` checks types once, and per-file
  // checking ran the CI runner out of memory.
  transform: { '^.+\\.ts$': ['ts-jest', { isolatedModules: true }] },
  testEnvironment: 'node',
  roots: ['<rootDir>/tests'],
  moduleFileExtensions: ['ts', 'js', 'json'],
  collectCoverageFrom: ['**/*.ts', '!dist/**', '!**/*.d.ts', '!**/index.ts'],
  coverageDirectory: 'coverage',
  clearMocks: true,
  setupFiles: ['<rootDir>/tests/setup-env.ts'],
};
