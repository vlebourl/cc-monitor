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
    type: 'message' | 'state' | 'termination' | 'error';
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
export interface AuthenticationRequest {
    guestToken: string;
    deviceId: string;
}
export interface AuthenticationResponse {
    apiKey: string;
    serverInfo: {
        version: string;
        features: string[];
    };
}
export interface QRResponse {
    qrCode: string;
    guestToken: string;
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
//# sourceMappingURL=index.d.ts.map