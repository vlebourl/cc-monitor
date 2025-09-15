import { EventEmitter } from 'events';
import { WebSocketServer, WebSocketClient } from '../services/WebSocketServer';
import { FileStreamMonitor } from '../services/FileStreamMonitor';
import { ClaudeCodeMessage, SessionMessage } from '../types/ClaudeCodeTypes';

export interface ConnectionManagerOptions {
  maxClientsPerSession?: number;
  sessionTimeout?: number;
}

export class ConnectionManager extends EventEmitter {
  private wsServer: WebSocketServer;
  private fileMonitor: FileStreamMonitor;
  private options: Required<ConnectionManagerOptions>;
  private activeSessions = new Map<string, { lastActivity: Date; clients: Set<string> }>();

  constructor(
    wsServer: WebSocketServer,
    fileMonitor: FileStreamMonitor,
    options: ConnectionManagerOptions = {}
  ) {
    super();

    this.wsServer = wsServer;
    this.fileMonitor = fileMonitor;
    this.options = {
      maxClientsPerSession: options.maxClientsPerSession || 1, // Single viewer per session
      sessionTimeout: options.sessionTimeout || 300000 // 5 minutes
    };

    this.setupEventHandlers();
  }

  private setupEventHandlers(): void {
    // WebSocket server events
    this.wsServer.on('clientConnected', this.handleClientConnected.bind(this));
    this.wsServer.on('clientDisconnected', this.handleClientDisconnected.bind(this));
    this.wsServer.on('clientSubscribed', this.handleClientSubscribed.bind(this));
    this.wsServer.on('clientUnsubscribed', this.handleClientUnsubscribed.bind(this));

    // File monitor events
    this.fileMonitor.on('newMessage', this.handleNewMessage.bind(this));
    this.fileMonitor.on('existingMessage', this.handleExistingMessage.bind(this));
    this.fileMonitor.on('sessionDiscovered', this.handleSessionDiscovered.bind(this));
    this.fileMonitor.on('sessionTerminated', this.handleSessionTerminated.bind(this));
    this.fileMonitor.on('sessionInactive', this.handleSessionInactive.bind(this));

    // Cleanup timer for inactive sessions
    setInterval(this.cleanupInactiveSessions.bind(this), 60000); // Every minute
  }

  private handleClientConnected({ clientId, sessionId, apiKey }: any): void {
    this.emit('clientConnected', { clientId, sessionId, apiKey });

    // Send current session status if sessionId is provided
    if (sessionId) {
      this.sendSessionStatus(clientId, sessionId);
    }
  }

  private handleClientDisconnected({ clientId, sessionId }: any): void {
    this.emit('clientDisconnected', { clientId, sessionId });

    if (sessionId) {
      const session = this.activeSessions.get(sessionId);
      if (session) {
        session.clients.delete(clientId);
        if (session.clients.size === 0) {
          this.activeSessions.delete(sessionId);
          this.emit('sessionInactive', { sessionId });
        }
      }
    }
  }

  private handleClientSubscribed({ clientId, sessionId }: any): void {
    // Check if session has reached max clients
    const sessionClients = this.wsServer.getSessionClients(sessionId);
    if (sessionClients.length > this.options.maxClientsPerSession) {
      this.wsServer.sendToClient(clientId, {
        type: 'error',
        payload: { error: 'Session viewer limit reached' },
        timestamp: new Date().toISOString()
      });
      return;
    }

    // Track active session
    if (!this.activeSessions.has(sessionId)) {
      this.activeSessions.set(sessionId, {
        lastActivity: new Date(),
        clients: new Set()
      });
    }

    this.activeSessions.get(sessionId)!.clients.add(clientId);

    // Send recent session history
    this.sendSessionHistory(clientId, sessionId);

    this.emit('clientSubscribed', { clientId, sessionId });
  }

  private handleClientUnsubscribed({ clientId, sessionId }: any): void {
    const session = this.activeSessions.get(sessionId);
    if (session) {
      session.clients.delete(clientId);
      if (session.clients.size === 0) {
        this.activeSessions.delete(sessionId);
        this.emit('sessionInactive', { sessionId });
      }
    }

    this.emit('clientUnsubscribed', { clientId, sessionId });
  }

  private handleNewMessage(message: ClaudeCodeMessage): void {
    const sessionId = message.sessionId;

    // Update session activity
    const session = this.activeSessions.get(sessionId);
    if (session) {
      session.lastActivity = new Date();
    }

    // Broadcast to session clients
    const sessionMessage: SessionMessage = {
      type: 'message',
      timestamp: message.timestamp,
      sessionId: message.sessionId,
      data: {
        messageType: message.type,
        content: message.message.content,
        parentUuid: message.parentUuid
      }
    };

    const sentCount = this.wsServer.broadcastToSession(sessionId, sessionMessage);

    this.emit('messageForwarded', { sessionId, message, sentCount });

    // Determine and broadcast session state
    this.broadcastSessionState(sessionId, [message]);
  }

  private handleExistingMessage(message: ClaudeCodeMessage): void {
    // Similar to new message but mark as historical
    const sessionId = message.sessionId;

    const sessionMessage: SessionMessage = {
      type: 'message',
      timestamp: message.timestamp,
      sessionId: message.sessionId,
      data: {
        messageType: message.type,
        content: message.message.content,
        parentUuid: message.parentUuid,
        historical: true
      }
    };

    this.wsServer.broadcastToSession(sessionId, sessionMessage);
  }

  private handleSessionDiscovered({ filePath, sessionInfo }: any): void {
    const sessionId = sessionInfo.sessionId;

    this.emit('sessionDiscovered', { sessionId, sessionInfo });

    // Notify clients about new session
    const notification: SessionMessage = {
      type: 'session_discovered',
      timestamp: new Date().toISOString(),
      sessionId: sessionId,
      data: {
        projectPath: sessionInfo.projectPath,
        filePath: filePath
      }
    };

    // Broadcast to all authenticated clients (not session-specific)
    this.wsServer.broadcast({
      type: 'session_notification',
      payload: notification,
      timestamp: new Date().toISOString()
    });
  }

  private handleSessionTerminated({ sessionInfo }: any): void {
    const sessionId = sessionInfo.sessionId;

    this.emit('sessionTerminated', { sessionId, sessionInfo });

    // Notify session clients
    const terminationMessage: SessionMessage = {
      type: 'termination',
      timestamp: new Date().toISOString(),
      sessionId: sessionId,
      data: {
        reason: 'Session ended',
        projectPath: sessionInfo.projectPath
      }
    };

    this.wsServer.broadcastToSession(sessionId, terminationMessage);

    // Clean up session tracking
    this.activeSessions.delete(sessionId);
  }

  private handleSessionInactive({ sessionInfo, reason, lastActivity }: any): void {
    const sessionId = sessionInfo.sessionId;

    this.emit('sessionInactive', { sessionId, sessionInfo, reason, lastActivity });

    // Notify session clients about inactivity
    const inactivityMessage: SessionMessage = {
      type: 'inactivity',
      timestamp: new Date().toISOString(),
      sessionId: sessionId,
      data: {
        reason: reason === 'inactivity_timeout' ? 'Session inactive for more than 10 minutes' : reason,
        lastActivity: lastActivity,
        projectPath: sessionInfo.projectPath
      }
    };

    this.wsServer.broadcastToSession(sessionId, inactivityMessage);

    // Don't automatically clean up - let clients decide if they want to stay connected
    const session = this.activeSessions.get(sessionId);
    if (session && session.clients.size === 0) {
      this.activeSessions.delete(sessionId);
    }
  }

  private sendSessionStatus(clientId: string, sessionId: string): void {
    // Check if session is being monitored
    const monitoredFiles = this.fileMonitor.getMonitoredFiles();
    const sessionFile = monitoredFiles.find(file => file.includes(sessionId));

    if (!sessionFile) {
      this.wsServer.sendToClient(clientId, {
        type: 'error',
        payload: { error: 'Session not found' },
        timestamp: new Date().toISOString()
      });
      return;
    }

    // Send session status
    this.wsServer.sendToClient(clientId, {
      type: 'session_status',
      payload: {
        sessionId,
        status: 'active',
        filePath: sessionFile,
        timestamp: new Date().toISOString()
      },
      timestamp: new Date().toISOString()
    });
  }

  private async sendSessionHistory(clientId: string, sessionId: string): Promise<void> {
    // TODO: Implement session history retrieval
    // This would read the last N messages from the JSONL file

    this.wsServer.sendToClient(clientId, {
      type: 'session_history_start',
      payload: { sessionId },
      timestamp: new Date().toISOString()
    });

    // Send history end marker
    setTimeout(() => {
      this.wsServer.sendToClient(clientId, {
        type: 'session_history_end',
        payload: { sessionId },
        timestamp: new Date().toISOString()
      });
    }, 100);
  }

  private broadcastSessionState(sessionId: string, recentMessages: ClaudeCodeMessage[]): void {
    // Determine session state from recent messages
    let state: 'working' | 'waiting' | 'idle' = 'idle';

    if (recentMessages.length > 0) {
      const lastMessage = recentMessages[recentMessages.length - 1]!;
      if (lastMessage.type === 'user') {
        state = 'working'; // Claude should be working on user's request
      } else {
        state = 'waiting'; // Waiting for user input
      }
    }

    const stateMessage: SessionMessage = {
      type: 'state',
      timestamp: new Date().toISOString(),
      sessionId: sessionId,
      data: {
        state,
        lastActivity: recentMessages.length > 0 ? recentMessages[recentMessages.length - 1]!.timestamp : new Date().toISOString()
      }
    };

    this.wsServer.broadcastToSession(sessionId, stateMessage);
  }

  private cleanupInactiveSessions(): void {
    const now = new Date();
    const inactiveSessions: string[] = [];

    for (const [sessionId, session] of this.activeSessions.entries()) {
      const timeSinceActivity = now.getTime() - session.lastActivity.getTime();

      if (timeSinceActivity > this.options.sessionTimeout) {
        inactiveSessions.push(sessionId);
      }
    }

    // Clean up inactive sessions
    for (const sessionId of inactiveSessions) {
      this.activeSessions.delete(sessionId);
      this.emit('sessionTimeout', { sessionId });

      // Notify clients
      const timeoutMessage: SessionMessage = {
        type: 'timeout',
        timestamp: new Date().toISOString(),
        sessionId,
        data: { reason: 'Session inactive for too long' }
      };

      this.wsServer.broadcastToSession(sessionId, timeoutMessage);
    }
  }

  // Public methods

  public getActiveSessionIds(): string[] {
    return Array.from(this.activeSessions.keys());
  }

  public getSessionStats(sessionId: string) {
    const session = this.activeSessions.get(sessionId);
    if (!session) return null;

    return {
      sessionId,
      clientCount: session.clients.size,
      lastActivity: session.lastActivity,
      clients: Array.from(session.clients)
    };
  }

  public getAllSessionStats() {
    const stats: any[] = [];

    for (const sessionId of this.activeSessions.keys()) {
      const sessionStats = this.getSessionStats(sessionId);
      if (sessionStats) {
        stats.push(sessionStats);
      }
    }

    return stats;
  }

  public disconnectSessionClients(sessionId: string, reason: string): void {
    const sessionClients = this.wsServer.getSessionClients(sessionId);

    for (const clientId of sessionClients) {
      this.wsServer.sendToClient(clientId, {
        type: 'disconnecting',
        payload: { reason, sessionId },
        timestamp: new Date().toISOString()
      });

      // Close connection after a brief delay
      setTimeout(() => {
        const client = this.wsServer.getConnectedClients().find(c => c.id === clientId);
        if (client) {
          client.socket.close(1000, reason);
        }
      }, 1000);
    }

    this.activeSessions.delete(sessionId);
  }

  public forceSessionCleanup(sessionId: string): void {
    // Clean up file monitor tracking
    this.fileMonitor.forceSessionCleanup(sessionId);

    // Disconnect all session clients
    this.disconnectSessionClients(sessionId, 'Session cleanup requested');

    this.emit('sessionForceCleaned', { sessionId, timestamp: new Date().toISOString() });
  }
}