// Main entry point for Claude Code monitoring server
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import path from 'path';
import { config } from 'dotenv';
import { createServer } from 'http';
import { WebSocketServer } from './services/WebSocketServer';
import { FileStreamMonitor } from './services/FileStreamMonitor';
import { ConnectionManager } from './managers/ConnectionManager';
import { AuthenticationService } from './services/AuthenticationService';
import { createAuthRoutes } from './routes/AuthRoutes';
import { requireAuth, optionalAuth } from './middleware/AuthMiddleware';

// Load package.json for version info
const packageJson = require('../package.json');

// Load environment variables
config();

const app = express();
const PORT = Number(process.env.PORT) || 3000;

// Public URL configuration for reverse proxy support
const PUBLIC_URL = process.env.PUBLIC_URL || `http://localhost:${PORT}`;
const PUBLIC_WS_URL = process.env.PUBLIC_WS_URL || PUBLIC_URL.replace('http', 'ws');

console.log('🌐 Server Configuration:');
console.log(`  Internal HTTP: http://localhost:${PORT}`);
console.log(`  Internal WebSocket: ${PUBLIC_WS_URL}`);
console.log(`  Public URL: ${PUBLIC_URL}`);
console.log(`  Public WebSocket: ${PUBLIC_WS_URL}`);

// Security and performance middleware
app.use(helmet({
  contentSecurityPolicy: false, // Allow inline scripts for QR code generation
}));
app.use(cors());
app.use(compression());
app.use(express.json());

// Serve static files from public directory
app.use(express.static(path.join(__dirname, '../public')));

// Create HTTP server
const httpServer = createServer(app);

// Initialize services
const fileMonitor = new FileStreamMonitor({
  claudeProjectsPath: process.env.CLAUDE_PROJECTS_PATH || undefined,
  usePolling: process.env.USE_POLLING === 'true'
});

const authService = new AuthenticationService({
  baseUrl: PUBLIC_URL,
  qrTokenTTL: Number(process.env.QR_TOKEN_TTL) || 30000,
  apiKeyTTL: Number(process.env.API_KEY_TTL) || 30 * 24 * 60 * 60 * 1000
});

const wsServer = new WebSocketServer({
  server: httpServer,  // Use the same HTTP server
  pingInterval: Number(process.env.PING_INTERVAL) || 30000
});

// Inject auth service into WebSocket server
wsServer.setAuthService(authService);

const connectionManager = new ConnectionManager(wsServer, fileMonitor);

// API Routes

// Authentication routes
app.use('/api/auth', createAuthRoutes(authService));

// Version endpoint for compatibility checks
app.get('/version', (req, res) => {
  res.json({
    version: packageJson.version,
    apiVersion: '1.0',
    protocolVersion: '1.0',
    features: [
      'websocket-connection',
      'qr-authentication',
      'message-display',
      'session-monitoring',
      'file-streaming',
      'real-time-sync'
    ],
    minClientVersion: packageJson.version,
    compatibilityLevel: 'stable'
  });
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    version: packageJson.version,
    services: {
      fileMonitor: fileMonitor.isMonitoring(),
      webSocket: wsServer.isRunning(),
      stats: wsServer.getStats()
    }
  });
});

// API info route (moved to /api to avoid conflict with static files)
app.get('/api', (req, res) => {
  res.json({
    name: 'Claude Code Monitor Server',
    version: packageJson.version,
    description: 'Remote monitoring system for Claude Code sessions',
    endpoints: {
      health: '/health',
      version: '/version',
      sessions: '/api/sessions',
      websocket: PUBLIC_WS_URL,
      qrAuth: '/api/auth/qr'
    }
  });
});

// Sessions API (protected)
app.get('/api/sessions', requireAuth(authService), (req, res) => {
  const monitoredFiles = fileMonitor.getMonitoredFiles();
  const activeSessions = connectionManager.getActiveSessionIds();

  const sessions = monitoredFiles.map(filePath => {
    const sessionId = filePath.split('/').pop()?.replace('.jsonl', '') || '';
    const isActive = activeSessions.includes(sessionId);

    return {
      sessionId,
      filePath,
      isActive,
      stats: connectionManager.getSessionStats(sessionId)
    };
  });

  res.json({
    sessions,
    totalSessions: sessions.length,
    activeSessions: activeSessions.length,
    timestamp: new Date().toISOString()
  });
});

// WebSocket connection info
app.get('/api/websocket', (req, res) => {
  res.json({
    url: PUBLIC_WS_URL,
    status: wsServer.isRunning() ? 'running' : 'stopped',
    stats: wsServer.getStats(),
    protocol: 'Claude Code Monitor Protocol v1.0'
  });
});

// Server status
app.get('/api/status', (req, res) => {
  res.json({
    server: 'running',
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
    memory: process.memoryUsage(),
    services: {
      fileMonitor: {
        running: fileMonitor.isMonitoring(),
        monitoredFiles: fileMonitor.getMonitoredFiles().length
      },
      webSocket: {
        running: wsServer.isRunning(),
        stats: wsServer.getStats()
      },
      connectionManager: {
        activeSessions: connectionManager.getActiveSessionIds().length,
        sessions: connectionManager.getAllSessionStats()
      }
    }
  });
});

// Error handling middleware
app.use((error: Error, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('Server error:', error);
  res.status(500).json({
    error: 'Internal server error',
    timestamp: new Date().toISOString()
  });
});

// Start services
async function startServer() {
  try {
    console.log('Starting Claude Code Monitor Server...');

    // Start file monitoring
    console.log('Starting authentication service...');
    // Auth service starts automatically
    console.log('✓ Authentication service started');

    console.log('Starting file monitor...');
    await fileMonitor.start();
    console.log('✓ File monitor started');

    // Start HTTP server with WebSocket
    httpServer.listen(PORT, async () => {
      console.log(`✓ HTTP server started on port ${PORT}`);

      // Start WebSocket server on the same HTTP server
      console.log('Starting WebSocket server...');
      await wsServer.start();
      console.log(`✓ WebSocket server started on the same port`);

      console.log('');
      console.log('Services running:');
      console.log(`  HTTP API (internal): http://localhost:${PORT}`);
      console.log(`  HTTP API (public): ${PUBLIC_URL}`);
      console.log(`  WebSocket (internal): ${PUBLIC_WS_URL}`);
      console.log(`  WebSocket (public): ${PUBLIC_WS_URL}`);
      console.log(`  Health Check: ${PUBLIC_URL}/health`);
      console.log('');
      console.log('Ready to monitor Claude Code sessions! 🚀');
    });

  } catch (error) {
    console.error('Failed to start server:', error);
    process.exit(1);
  }
}

// Graceful shutdown
async function shutdown(signal: string) {
  console.log(`\nReceived ${signal}, shutting down gracefully...`);

  try {
    // Stop authentication service
    console.log('Stopping authentication service...');
    authService.shutdown();

    // Stop WebSocket server
    console.log('Stopping WebSocket server...');
    await wsServer.stop();

    // Stop file monitor
    console.log('Stopping file monitor...');
    await fileMonitor.stop();

    // Close HTTP server
    httpServer.close(() => {
      console.log('✓ All services stopped');
      process.exit(0);
    });

  } catch (error) {
    console.error('Error during shutdown:', error);
    process.exit(1);
  }
}

// Handle shutdown signals
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

// Start the server
if (require.main === module) {
  startServer();
}

export { app, httpServer, wsServer, fileMonitor, connectionManager };