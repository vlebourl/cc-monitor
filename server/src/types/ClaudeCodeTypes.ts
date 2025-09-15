// Shared types for Claude Code monitoring system

export interface ClaudeCodeMessage {
  parentUuid: string;
  sessionId: string;
  type: 'user' | 'assistant';
  message: {
    role: 'user' | 'assistant';
    content: string;
  };
  timestamp: string;
  cwd: string;
}

export interface SessionMetadata {
  sessionId: string;
  projectPath: string;
  projectName: string;
  lastActivity: string;
  messageCount: number;
  status: SessionStatus;
}

export type SessionStatus = 'active' | 'idle' | 'terminated';

export type SessionState = 'working' | 'waiting' | 'idle';

export interface SessionMessage {
  type: 'message' | 'state' | 'termination' | 'error' | 'session_discovered' | 'timeout' | 'inactivity';
  timestamp: string;
  sessionId: string;
  data: any;
}

export interface MessageData {
  messageType: 'user' | 'assistant';
  content: string;
  parentUuid: string;
}

export interface StateData {
  state: SessionState;
  lastActivity: string;
}

export interface WebSocketMessage {
  type: string;
  payload: any;
  timestamp: string;
}

export interface ErrorResponse {
  error: string;
  message: string;
  timestamp: string;
}