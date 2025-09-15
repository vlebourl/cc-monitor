// Shared utilities for Claude Code monitoring system

export const generateId = (): string => {
  return Math.random().toString(36).substr(2, 9);
};

export const formatTimestamp = (date: Date): string => {
  return date.toISOString();
};

export const parseTimestamp = (timestamp: string): Date => {
  return new Date(timestamp);
};

export const isValidSessionId = (sessionId: string): boolean => {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  return uuidRegex.test(sessionId);
};

export const sanitizePath = (path: string): string => {
  return path.replace(/[^a-zA-Z0-9\/\-_.]/g, '');
};