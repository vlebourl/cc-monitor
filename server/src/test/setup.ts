// Jest setup file for server tests
import { beforeEach, afterEach } from '@jest/globals';

beforeEach(() => {
  // Setup code before each test
  jest.clearAllMocks();
});

afterEach(() => {
  // Cleanup code after each test
  jest.resetAllMocks();
});

// Global test utilities
declare global {
  var testTimeout: number;
}

global.testTimeout = 5000;