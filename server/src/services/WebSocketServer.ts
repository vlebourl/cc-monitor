import WebSocket, { WebSocketServer as WSServer } from 'ws';
import { EventEmitter } from 'events';
import { IncomingMessage } from 'http';
import { URL } from 'url';
import { WebSocketMessage, SessionMessage } from '../types/ClaudeCodeTypes';

export interface WebSocketClient {
  id: string;
  socket: WebSocket;
  sessionId?: string | undefined;
  lastPing: Date;
  authenticated: boolean;
  apiKey?: string | undefined;
  deviceId?: string | undefined;
}

export interface WebSocketServerOptions {
  port?: number;
  host?: string;
  pingInterval?: number;
  connectionTimeout?: number;
}

export class WebSocketServer extends EventEmitter {
  private server: WSServer | null = null;
  private clients = new Map<string, WebSocketClient>();
  private sessionClients = new Map<string, Set<string>>();
  private pingTimer?: NodeJS.Timeout;
  private options: Required<WebSocketServerOptions>;

  constructor(options: WebSocketServerOptions = {}) {
    super();

    this.options = {
      port: options.port || 8080,
      host: options.host || 'localhost',
      pingInterval: options.pingInterval || 30000, // 30 seconds
      connectionTimeout: options.connectionTimeout || 60000 // 60 seconds
    };
  }

  async start(): Promise<void> {
    this.server = new WSServer({
      port: this.options.port,
      host: this.options.host,
      verifyClient: this.verifyClient.bind(this)
    });

    this.server.on('connection', this.handleConnection.bind(this));
    this.server.on('error', this.handleServerError.bind(this));

    // Start ping interval
    this.startPingInterval();

    this.emit('started', {
      port: this.options.port,
      host: this.options.host
    });
  }

  async stop(): Promise<void> {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
    }

    // Close all client connections
    for (const client of this.clients.values()) {
      client.socket.close();
    }

    if (this.server) {
      this.server.close();
      this.server = null;
    }

    this.clients.clear();
    this.sessionClients.clear();

    this.emit('stopped');
  }

  private verifyClient(info: { origin: string; secure: boolean; req: IncomingMessage }): boolean {
    // Basic origin verification - can be enhanced based on requirements
    return true;
  }

  private handleConnection(socket: WebSocket, request: IncomingMessage): void {
    const clientId = this.generateClientId();
    const url = new URL(request.url!, `http://${request.headers.host}`);
    const sessionId = url.searchParams.get('sessionId');
    const apiKey = url.searchParams.get('apiKey');

    const client: WebSocketClient = {
      id: clientId,
      socket,
      sessionId: sessionId || undefined,
      lastPing: new Date(),
      authenticated: false,
      apiKey: apiKey || undefined
    };

    this.clients.set(clientId, client);

    // Setup socket event handlers
    socket.on('message', (data) => this.handleMessage(clientId, data));
    socket.on('close', () => this.handleDisconnection(clientId));
    socket.on('error', (error) => this.handleClientError(clientId, error));
    socket.on('pong', () => this.handlePong(clientId));

    // If session ID is provided, add to session clients
    if (sessionId && this.isAuthenticated(client)) {
      this.addClientToSession(clientId, sessionId);
    }

    this.emit('clientConnected', { clientId, sessionId, apiKey });

    // Send welcome message
    this.sendToClient(clientId, {
      type: 'connected',
      payload: {
        clientId,
        serverTime: new Date().toISOString()
      },
      timestamp: new Date().toISOString()
    });
  }

  private handleMessage(clientId: string, data: WebSocket.RawData): void {
    const client = this.clients.get(clientId);
    if (!client) return;

    try {
      const message = JSON.parse(data.toString()) as WebSocketMessage;
      this.emit('messageReceived', { clientId, message });

      // Handle different message types
      switch (message.type) {
        case 'authenticate':
          this.handleAuthentication(clientId, message.payload);
          break;
        case 'subscribe':
          this.handleSubscription(clientId, message.payload);
          break;
        case 'unsubscribe':
          this.handleUnsubscription(clientId, message.payload);
          break;
        case 'ping':
          this.sendToClient(clientId, {
            type: 'pong',
            payload: { timestamp: new Date().toISOString() },
            timestamp: new Date().toISOString()
          });
          break;
        default:
          this.sendError(clientId, `Unknown message type: ${message.type}`);
      }
    } catch (error) {
      this.sendError(clientId, 'Invalid JSON message');
    }
  }

  private handleDisconnection(clientId: string): void {
    const client = this.clients.get(clientId);
    if (!client) return;

    // Remove from session clients
    if (client.sessionId) {
      this.removeClientFromSession(clientId, client.sessionId);
    }

    this.clients.delete(clientId);
    this.emit('clientDisconnected', { clientId, sessionId: client.sessionId });
  }

  private handleClientError(clientId: string, error: Error): void {
    this.emit('clientError', { clientId, error });
  }

  private handleServerError(error: Error): void {
    this.emit('error', error);
  }

  private handlePong(clientId: string): void {
    const client = this.clients.get(clientId);
    if (client) {
      client.lastPing = new Date();
    }
  }

  private handleAuthentication(clientId: string, payload: any): void {
    const client = this.clients.get(clientId);
    if (!client) return;

    // TODO: Implement proper API key validation
    const isValid = this.validateApiKey(payload.apiKey);

    if (isValid) {
      client.authenticated = true;
      client.apiKey = payload.apiKey;

      this.sendToClient(clientId, {
        type: 'authenticated',
        payload: { success: true },
        timestamp: new Date().toISOString()
      });

      this.emit('clientAuthenticated', { clientId, apiKey: payload.apiKey });
    } else {
      this.sendToClient(clientId, {
        type: 'authentication_failed',
        payload: { error: 'Invalid API key' },
        timestamp: new Date().toISOString()
      });
    }
  }

  private handleSubscription(clientId: string, payload: any): void {
    const client = this.clients.get(clientId);
    if (!client || !client.authenticated) {
      this.sendError(clientId, 'Authentication required');
      return;
    }

    const sessionId = payload.sessionId;
    if (!sessionId) {
      this.sendError(clientId, 'Session ID required');
      return;
    }

    const forceTakeover = payload.forceTakeover === true;

    // Check for existing viewers
    const existingClients = this.sessionClients.get(sessionId);
    if (existingClients && existingClients.size > 0) {
      if (!forceTakeover) {
        // Get existing client info for error message
        const existingClientIds = Array.from(existingClients);
        const existingClient = existingClientIds.length > 0 ? this.clients.get(existingClientIds[0]!) : null;

        this.sendToClient(clientId, {
          type: 'session_occupied',
          payload: {
            sessionId,
            existingViewer: existingClient?.deviceId ?? 'Unknown device',
            message: 'Session already has an active viewer. Use forceTakeover to disconnect existing viewer.',
            canTakeOver: true
          },
          timestamp: new Date().toISOString()
        });
        return;
      } else {
        // Force takeover - disconnect existing clients
        for (const existingClientId of existingClients) {
          const existingClient = this.clients.get(existingClientId);
          if (existingClient) {
            // Notify existing client they're being disconnected
            this.sendToClient(existingClientId, {
              type: 'session_taken_over',
              payload: {
                sessionId,
                newViewer: client.apiKey ?? 'Another device',
                message: 'Session viewing has been taken over by another device'
              },
              timestamp: new Date().toISOString()
            });

            // Unsubscribe existing client
            this.removeClientFromSession(existingClientId, sessionId);
            if (existingClient.sessionId === sessionId) {
              existingClient.sessionId = undefined;
            }

            this.emit('clientForcedUnsubscribe', {
              clientId: existingClientId,
              sessionId,
              takenOverBy: clientId
            });
          }
        }
      }
    }

    // Subscribe new client
    this.addClientToSession(clientId, sessionId);
    client.sessionId = sessionId;

    this.sendToClient(clientId, {
      type: 'subscribed',
      payload: {
        sessionId,
        tookOver: forceTakeover && existingClients && existingClients.size > 0
      },
      timestamp: new Date().toISOString()
    });

    this.emit('clientSubscribed', { clientId, sessionId, forceTakeover });
  }

  private handleUnsubscription(clientId: string, payload: any): void {
    const client = this.clients.get(clientId);
    if (!client) return;

    const sessionId = payload.sessionId || client.sessionId;
    if (sessionId) {
      this.removeClientFromSession(clientId, sessionId);
      client.sessionId = undefined;

      this.sendToClient(clientId, {
        type: 'unsubscribed',
        payload: { sessionId },
        timestamp: new Date().toISOString()
      });

      this.emit('clientUnsubscribed', { clientId, sessionId });
    }
  }

  private addClientToSession(clientId: string, sessionId: string): void {
    if (!this.sessionClients.has(sessionId)) {
      this.sessionClients.set(sessionId, new Set());
    }
    this.sessionClients.get(sessionId)!.add(clientId);
  }

  private removeClientFromSession(clientId: string, sessionId: string): void {
    const clients = this.sessionClients.get(sessionId);
    if (clients) {
      clients.delete(clientId);
      if (clients.size === 0) {
        this.sessionClients.delete(sessionId);
      }
    }
  }

  private startPingInterval(): void {
    this.pingTimer = setInterval(() => {
      const now = new Date();

      for (const [clientId, client] of this.clients.entries()) {
        const timeSinceLastPing = now.getTime() - client.lastPing.getTime();

        if (timeSinceLastPing > this.options.connectionTimeout) {
          // Client is unresponsive, close connection
          client.socket.close();
          this.handleDisconnection(clientId);
        } else if (client.socket.readyState === WebSocket.OPEN) {
          // Send ping
          client.socket.ping();
        }
      }
    }, this.options.pingInterval);
  }

  private generateClientId(): string {
    return Math.random().toString(36).substr(2, 9);
  }

  // Inject authentication service
  private authService?: any;

  setAuthService(authService: any): void {
    this.authService = authService;
  }

  private validateApiKey(apiKey: string): boolean {
    if (!this.authService) {
      // Fallback for when auth service is not set
      return typeof apiKey === 'string' && apiKey.length > 0;
    }
    return this.authService.validateApiKey(apiKey);
  }

  private isAuthenticated(client: WebSocketClient): boolean {
    return client.apiKey !== undefined && this.validateApiKey(client.apiKey);
  }

  // Public methods for broadcasting

  public sendToClient(clientId: string, message: WebSocketMessage): boolean {
    const client = this.clients.get(clientId);
    if (!client || client.socket.readyState !== WebSocket.OPEN) {
      return false;
    }

    try {
      client.socket.send(JSON.stringify(message));
      return true;
    } catch (error) {
      this.emit('sendError', { clientId, error });
      return false;
    }
  }

  public broadcastToSession(sessionId: string, message: SessionMessage): number {
    const clients = this.sessionClients.get(sessionId);
    if (!clients) return 0;

    let sentCount = 0;
    const wsMessage: WebSocketMessage = {
      type: 'session_message',
      payload: message,
      timestamp: new Date().toISOString()
    };

    for (const clientId of clients) {
      if (this.sendToClient(clientId, wsMessage)) {
        sentCount++;
      }
    }

    return sentCount;
  }

  public broadcast(message: WebSocketMessage, excludeClient?: string): number {
    let sentCount = 0;

    for (const [clientId, client] of this.clients.entries()) {
      if (excludeClient && clientId === excludeClient) continue;

      if (this.sendToClient(clientId, message)) {
        sentCount++;
      }
    }

    return sentCount;
  }

  private sendError(clientId: string, error: string): void {
    this.sendToClient(clientId, {
      type: 'error',
      payload: { error, timestamp: new Date().toISOString() },
      timestamp: new Date().toISOString()
    });
  }

  // Status methods

  public getConnectedClients(): WebSocketClient[] {
    return Array.from(this.clients.values());
  }

  public getSessionClients(sessionId: string): string[] {
    const clients = this.sessionClients.get(sessionId);
    return clients ? Array.from(clients) : [];
  }

  public isRunning(): boolean {
    return this.server !== null;
  }

  public getStats() {
    return {
      totalClients: this.clients.size,
      activeSessions: this.sessionClients.size,
      authenticatedClients: Array.from(this.clients.values()).filter(c => c.authenticated).length
    };
  }
}