// WebSocket Protocol definitions for Claude Code Monitor

export interface BaseMessage {
  type: string;
  timestamp: string;
}

// Client to Server Messages
export interface AuthenticateMessage extends BaseMessage {
  type: 'authenticate';
  payload: {
    apiKey: string;
    deviceId?: string;
  };
}

export interface SubscribeMessage extends BaseMessage {
  type: 'subscribe';
  payload: {
    sessionId: string;
  };
}

export interface UnsubscribeMessage extends BaseMessage {
  type: 'unsubscribe';
  payload: {
    sessionId: string;
  };
}

export interface PingMessage extends BaseMessage {
  type: 'ping';
  payload: {};
}

export type ClientMessage = AuthenticateMessage | SubscribeMessage | UnsubscribeMessage | PingMessage;

// Server to Client Messages
export interface ConnectedMessage extends BaseMessage {
  type: 'connected';
  payload: {
    clientId: string;
    serverTime: string;
  };
}

export interface AuthenticatedMessage extends BaseMessage {
  type: 'authenticated';
  payload: {
    success: boolean;
  };
}

export interface AuthenticationFailedMessage extends BaseMessage {
  type: 'authentication_failed';
  payload: {
    error: string;
  };
}

export interface SubscribedMessage extends BaseMessage {
  type: 'subscribed';
  payload: {
    sessionId: string;
  };
}

export interface UnsubscribedMessage extends BaseMessage {
  type: 'unsubscribed';
  payload: {
    sessionId: string;
  };
}

export interface PongMessage extends BaseMessage {
  type: 'pong';
  payload: {
    timestamp: string;
  };
}

export interface ErrorMessage extends BaseMessage {
  type: 'error';
  payload: {
    error: string;
    timestamp: string;
  };
}

export interface SessionMessageWrapper extends BaseMessage {
  type: 'session_message';
  payload: {
    type: 'message' | 'state' | 'termination' | 'error';
    timestamp: string;
    sessionId: string;
    data: any;
  };
}

export interface SessionStatusMessage extends BaseMessage {
  type: 'session_status';
  payload: {
    sessionId: string;
    status: 'active' | 'idle' | 'terminated';
    filePath: string;
    timestamp: string;
  };
}

export interface SessionHistoryStartMessage extends BaseMessage {
  type: 'session_history_start';
  payload: {
    sessionId: string;
  };
}

export interface SessionHistoryEndMessage extends BaseMessage {
  type: 'session_history_end';
  payload: {
    sessionId: string;
  };
}

export interface SessionNotificationMessage extends BaseMessage {
  type: 'session_notification';
  payload: {
    type: 'session_discovered';
    timestamp: string;
    sessionId: string;
    data: {
      projectPath: string;
      filePath: string;
    };
  };
}

export interface DisconnectingMessage extends BaseMessage {
  type: 'disconnecting';
  payload: {
    reason: string;
    sessionId?: string;
  };
}

export type ServerMessage =
  | ConnectedMessage
  | AuthenticatedMessage
  | AuthenticationFailedMessage
  | SubscribedMessage
  | UnsubscribedMessage
  | PongMessage
  | ErrorMessage
  | SessionMessageWrapper
  | SessionStatusMessage
  | SessionHistoryStartMessage
  | SessionHistoryEndMessage
  | SessionNotificationMessage
  | DisconnectingMessage;

// Protocol validation functions
export const validateClientMessage = (message: any): message is ClientMessage => {
  if (!message || typeof message !== 'object') return false;
  if (!message.type || !message.timestamp) return false;

  switch (message.type) {
    case 'authenticate':
      return message.payload && typeof message.payload.apiKey === 'string';
    case 'subscribe':
      return message.payload && typeof message.payload.sessionId === 'string';
    case 'unsubscribe':
      return message.payload && typeof message.payload.sessionId === 'string';
    case 'ping':
      return message.payload !== undefined;
    default:
      return false;
  }
};

export const createServerMessage = <T extends ServerMessage>(
  type: T['type'],
  payload: T['payload']
): T => {
  return {
    type,
    payload,
    timestamp: new Date().toISOString()
  } as T;
};

// WebSocket close codes
export const WS_CLOSE_CODES = {
  NORMAL: 1000,
  UNAUTHORIZED: 4401,
  SESSION_LIMIT_REACHED: 4429,
  INVALID_SESSION: 4404,
  SERVER_ERROR: 4500
} as const;

// Protocol constants
export const PROTOCOL_VERSION = '1.0';
export const MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB
export const HEARTBEAT_INTERVAL = 30000; // 30 seconds
export const CONNECTION_TIMEOUT = 60000; // 60 seconds