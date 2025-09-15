# PRP: Claude Code Remote Monitoring System

**Document Version**: 1.0
**Created**: 2025-09-14
**Author**: Claude Code AI
**Status**: Ready for Implementation

---

## Executive Summary

### Problem Statement
Developers using Claude Code for long-running coding tasks need remote visibility into session progress when away from their workstation. Currently, there's no way to monitor Claude Code sessions remotely, creating anxiety about task completion and inability to intervene if needed.

### Solution Overview
A **monorepo project** containing both a standalone monitoring server and Android client application that enables real-time remote monitoring of Claude Code sessions. The system provides QR-code based secure pairing and live streaming of session output via WebSocket connections.

### Business Value
- **Peace of Mind**: Developers can step away during long coding sessions knowing they can monitor progress
- **Increased Productivity**: Reduced context switching and worry about session status
- **Remote Flexibility**: Full visibility into development work from mobile devices
- **GitHub Ready**: Professional project structure ready for open-source distribution

---

## Requirements Analysis

### Functional Requirements

#### Core Features
- **FR-1**: Monitor active Claude Code sessions by reading JSONL files at `~/.claude/projects/<project-path>/<session-uuid>.jsonl`
- **FR-2**: Display real-time session output stream on Android device
- **FR-3**: Detect and display session state (Claude working vs waiting for input)
- **FR-4**: Session selection interface when multiple Claude Code sessions are running
- **FR-5**: QR code authentication for secure server-client pairing (WhatsApp-style)

#### User Experience
- **FR-6**: Single active viewer per session (exclusive connection model)
- **FR-7**: Automatic session discovery and metadata display
- **FR-8**: Connection resilience with automatic reconnection
- **FR-9**: Session termination notification with session selection offering
- **FR-10**: Local caching for reasonable functionality during brief disconnections

#### GitHub Project Features
- **FR-11**: Monorepo structure with server and Android components
- **FR-12**: CI/CD pipelines for automated testing and builds
- **FR-13**: Docker containerization for easy deployment
- **FR-14**: Comprehensive documentation and setup guides

### Non-Functional Requirements

#### Performance
- **NFR-1**: < 2 seconds latency from Claude output to Android display (LAN)
- **NFR-2**: < 10 seconds reconnection time after network interruption
- **NFR-3**: < 5% additional battery drain on Android device
- **NFR-4**: Support monitoring of multi-hour sessions without degradation

#### Security
- **NFR-5**: QR token expiry within 10-30 seconds for secure pairing
- **NFR-6**: API key-based authentication for subsequent connections
- **NFR-7**: Encrypted WebSocket connections (WSS) for remote access
- **NFR-8**: No modification or interference with Claude Code sessions

#### Scalability
- **NFR-9**: Support monitoring multiple concurrent Claude Code sessions
- **NFR-10**: Handle Android client disconnections without affecting sessions
- **NFR-11**: Efficient file monitoring with minimal system resource usage

---

## Technical Architecture

### System Overview

```
┌─────────────────┐    WebSocket/HTTPS    ┌─────────────────┐
│                 │◄────────────────────►│                 │
│  Android Client │                      │  Server Monitor │
│                 │                      │                 │
└─────────────────┘                      └─────────────────┘
                                                   │
                                                   ▼
                                         ┌─────────────────┐
                                         │ Claude Code     │
                                         │ JSONL Sessions  │
                                         │ ~/.claude/...   │
                                         └─────────────────┘
```

### Technology Stack

#### Server Component (Node.js/TypeScript)
- **Framework**: Express.js with TypeScript for robust API development
- **File Monitoring**: Chokidar v4 for cross-platform file watching reliability
- **WebSocket**: ws@^8.14.0 for real-time bidirectional communication
- **Authentication**: QR code generation using qrcode@^1.5.3 library
- **Validation**: ESLint + Prettier for code quality, Jest for testing

#### Android Component (Kotlin)
- **Framework**: Native Android with Kotlin coroutines
- **WebSocket Client**: Ktor v3.0 with OkHttp engine for modern async networking
- **QR Scanning**: ML Kit Barcode Scanning API
- **UI**: Material Design 3 components for consistent experience
- **Architecture**: MVVM pattern with ViewBinding and LiveData

#### Shared Components
- **API Contracts**: TypeScript type definitions shared between server and client
- **Protocol Definitions**: WebSocket message schemas and authentication flows
- **Documentation**: Comprehensive guides and API specifications

### Data Flow Architecture

#### Authentication Flow
```typescript
// QR Authentication Pattern (WhatsApp-style)
class QRAuthSystem {
    async generateAuthQR(): Promise<string> {
        const guestToken = crypto.randomUUID();
        const authUrl = `${BASE_URL}/auth?token=${guestToken}`;

        // Generate QR with 30-second expiry
        const qr = await QrCode.encodeText(authUrl, QrCode.Ecc.MEDIUM);
        this.setupWebSocketAuth(guestToken);

        return qr.toSvgString();
    }
}
```

#### Session Monitoring Flow
```typescript
// File Monitoring Implementation
class FileStreamMonitor {
    setupJSONLWatcher(projectPath: string) {
        const watcher = chokidar.watch(`${projectPath}/**/*.jsonl`, {
            ignored: /(^|[\/\\])\../,
            persistent: true,
            usePolling: false, // true for network drives
            interval: 100
        });

        watcher.on('change', this.handleSessionUpdate.bind(this));
        watcher.on('add', this.handleNewSession.bind(this));
    }
}
```

---

## Implementation Plan

### Phase 1: Core Infrastructure (Week 1-2)

#### Server Development
- **Task 1.1**: Initialize monorepo structure with Nx workspace
  - Create `/server`, `/android`, `/shared` directories
  - Configure TypeScript build system
  - Setup Jest testing framework
  - **Validation**: `npm run build && npm test`

- **Task 1.2**: Implement JSONL file monitoring system
  - Install and configure Chokidar v4: `npm install chokidar@^4.0.0`
  - Create session discovery service
  - Implement real-time JSONL parsing and streaming
  - **Validation**: Monitor actual Claude Code session files

- **Task 1.3**: Build WebSocket server infrastructure
  - Install ws library: `npm install ws@^8.14.0 @types/ws`
  - Implement WebSocket connection management
  - Create message broadcasting system for session updates
  - **Validation**: Test with WebSocket client tool

#### Android Development
- **Task 1.4**: Setup Android project structure
  - Initialize Android project in `/android` directory
  - Configure Gradle build with Kotlin coroutines
  - Add Ktor WebSocket dependencies
  - **Validation**: `./gradlew build`

- **Task 1.5**: Implement WebSocket client
  ```kotlin
  // Ktor WebSocket Client Setup
  val client = HttpClient(OkHttp) {
      install(WebSockets) {
          pingInterval = 30_000
      }
  }
  ```
  - Create connection manager with exponential backoff
  - Implement foreground service for background connections
  - **Validation**: Test connection persistence with server

### Phase 2: Authentication & Security (Week 2)

#### QR Code Authentication
- **Task 2.1**: Server-side QR generation
  - Install QR library: `npm install qrcode@^1.5.3`
  - Implement guest token generation with 30-second expiry
  - Create authentication WebSocket endpoint
  - **Validation**: Generate and validate QR codes

- **Task 2.2**: Android QR scanning
  - Integrate ML Kit Barcode Scanning
  - Implement QR code camera interface
  - Handle authentication token exchange
  - **Validation**: End-to-end QR authentication flow

- **Task 2.3**: API key management
  - Generate persistent API keys after QR authentication
  - Implement token refresh mechanisms
  - Store credentials securely on Android
  - **Validation**: Test authentication persistence across app restarts

### Phase 3: Session Management (Week 3)

#### Session Discovery & Selection
- **Task 3.1**: Session discovery service
  - Scan `~/.claude/projects/` directory structure
  - Parse session metadata from JSONL files
  - Detect active vs inactive sessions using timestamps
  - **Validation**: Display all available sessions correctly

- **Task 3.2**: Android session interface
  - Create session list UI with Material Design
  - Implement session selection and connection
  - Display session metadata (project, activity status)
  - **Validation**: Navigate between multiple sessions

- **Task 3.3**: Single viewer enforcement
  - Implement exclusive connection model (one active viewer per session)
  - Handle connection conflicts gracefully
  - Display connection status to users
  - **Validation**: Test multi-client connection scenarios

### Phase 4: Real-time Streaming (Week 3-4)

#### Message Processing
- **Task 4.1**: JSONL parsing and streaming
  - Parse Claude Code JSONL message format
  - Detect message types (user vs assistant)
  - Stream new messages in real-time to connected clients
  - **Validation**: Monitor actual Claude Code sessions

- **Task 4.2**: Session state detection
  - Analyze message flow to determine Claude working vs waiting states
  - Implement state change notifications
  - Display current session status on Android
  - **Validation**: Verify state detection accuracy

- **Task 4.3**: Android stream display
  - Create scrolling message interface
  - Implement message rendering with timestamps
  - Handle large message volumes efficiently
  - **Validation**: Display multi-hour session streams smoothly

### Phase 5: Resilience & Polish (Week 4)

#### Connection Management
- **Task 5.1**: Reconnection logic
  ```kotlin
  // Android Exponential Backoff Implementation
  suspend fun reconnectWithBackoff() {
      var delay = 1000L
      while (!connected) {
          delay(delay)
          if (attemptConnection()) break
          delay = (delay * 1.5).toLong().coerceAtMost(30000L)
      }
  }
  ```
  - Implement exponential backoff for Android client
  - Handle server restarts gracefully
  - **Validation**: Test network disconnection scenarios

- **Task 5.2**: Session termination handling
  - Detect when Claude Code sessions end or crash
  - Display termination notifications to user
  - Offer session selection when current session ends
  - **Validation**: Handle Claude Code session lifecycle

- **Task 5.3**: Local data caching
  - Cache recent messages locally on Android
  - Implement reasonable offline functionality
  - Sync with server on reconnection
  - **Validation**: Test disconnection/reconnection data consistency

### Phase 6: DevOps & Distribution (Week 5)

#### CI/CD Pipeline
- **Task 6.1**: GitHub Actions setup
  ```yaml
  # .github/workflows/ci.yml
  name: CI
  on: [push, pull_request]
  jobs:
    test-server:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - uses: actions/setup-node@v3
        - run: npm ci && npm test
  ```
  - Configure automated testing for server and Android
  - Setup code quality checks (ESLint, ktlint)
  - **Validation**: All CI checks pass on pull requests

- **Task 6.2**: Docker containerization
  ```dockerfile
  # server/Dockerfile
  FROM node:18-alpine
  WORKDIR /app
  COPY package*.json ./
  RUN npm ci --only=production
  COPY src/ ./src/
  CMD ["npm", "start"]
  ```
  - Create Dockerfile for server deployment
  - Setup docker-compose for development environment
  - **Validation**: `docker-compose up --build`

- **Task 6.3**: Documentation and examples
  - Create comprehensive README with setup instructions
  - Document API endpoints and WebSocket protocol
  - Provide usage examples and troubleshooting guide
  - **Validation**: Fresh environment setup following docs

---

## API Specifications

### REST Endpoints

#### Authentication
```typescript
POST /api/auth/qr
Response: { qrCode: string, guestToken: string }

POST /api/auth/mobile
Body: { guestToken: string, deviceId: string }
Response: { apiKey: string, serverInfo: object }
```

#### Sessions
```typescript
GET /api/sessions
Headers: { Authorization: "Bearer <apiKey>" }
Response: { sessions: SessionMetadata[] }

GET /api/sessions/{sessionId}
Response: { session: SessionDetails, messageCount: number }

GET /api/sessions/{sessionId}/history?limit=100&before=<messageId>
Response: { messages: Message[], hasMore: boolean }
```

### WebSocket Protocol

#### Connection
```typescript
// Connection URL
wss://server:port/ws/sessions/{sessionId}?apiKey=<apiKey>

// Message Types
interface SessionMessage {
  type: 'message' | 'state' | 'termination' | 'error'
  timestamp: string
  sessionId: string
  data: any
}
```

#### Message Flow
```typescript
// Real-time message streaming
{
  type: 'message',
  sessionId: 'uuid',
  timestamp: '2025-09-14T15:04:35.357Z',
  data: {
    messageType: 'user' | 'assistant',
    content: string,
    parentUuid: string
  }
}

// Session state changes
{
  type: 'state',
  sessionId: 'uuid',
  timestamp: string,
  data: {
    state: 'working' | 'waiting' | 'idle',
    lastActivity: string
  }
}
```

---

## Quality Assurance

### Testing Strategy

#### Unit Testing
- **Server**: Jest with 80%+ code coverage requirement
- **Android**: JUnit + Mockito for business logic testing
- **Integration**: Docker Compose with real JSONL file scenarios
- **Validation Commands**:
  ```bash
  # Server testing
  npm test -- --coverage --coverageThreshold='{"global":{"branches":80,"functions":80,"lines":80,"statements":80}}'

  # Android testing
  ./gradlew testDebugUnitTest --continue

  # Integration testing
  docker-compose -f docker-compose.test.yml up --abort-on-container-exit
  ```

#### End-to-End Testing
- **Authentication Flow**: QR generation → mobile scan → API key exchange
- **Session Monitoring**: File changes → WebSocket broadcast → Android display
- **Reconnection**: Network interruption → automatic reconnection → state sync
- **Multi-Session**: Multiple Claude Code sessions → session switching

### Code Quality Standards

#### Server (TypeScript)
```json
// .eslintrc.js
{
  "extends": ["@typescript-eslint/recommended", "prettier"],
  "rules": {
    "@typescript-eslint/no-unused-vars": "error",
    "@typescript-eslint/explicit-function-return-type": "warn"
  }
}
```

#### Android (Kotlin)
```kotlin
// ktlint configuration
android {
    lintOptions {
        abortOnError true
        warningsAsErrors true
    }
}
```

### Performance Requirements

#### Response Time Targets
- **QR Generation**: < 500ms
- **Session Discovery**: < 1 second for up to 50 sessions
- **WebSocket Message Latency**: < 2 seconds (LAN), < 5 seconds (remote)
- **Android App Startup**: < 3 seconds to session list

#### Resource Usage Limits
- **Server Memory**: < 256MB for typical usage (5 concurrent sessions)
- **Android Battery**: < 5% drain per hour of active monitoring
- **Network Bandwidth**: < 10KB/s per active session stream

---

## Risk Analysis

### Technical Risks

#### High Risk
- **Risk**: Android power management killing WebSocket connections
  - **Mitigation**: Foreground service implementation + battery optimization detection
  - **Validation**: Test on Android 13+ with aggressive power management

- **Risk**: JSONL file rotation or concurrent access issues
  - **Mitigation**: File locking detection + graceful handling of file operations
  - **Validation**: Stress test with rapid Claude Code interactions

#### Medium Risk
- **Risk**: WebSocket connection instability on mobile networks
  - **Mitigation**: Exponential backoff reconnection + connection state monitoring
  - **Validation**: Test on cellular/WiFi network transitions

- **Risk**: Large session history impacting performance
  - **Mitigation**: Message pagination + local caching strategies
  - **Validation**: Load test with multi-hour sessions

#### Low Risk
- **Risk**: QR code scanning failures in poor lighting
  - **Mitigation**: Flashlight toggle + manual token entry fallback
  - **Validation**: Test in various lighting conditions

### Security Considerations

#### Data Protection
- **Session Data**: JSONL files contain sensitive development information
- **Network Transport**: All communications encrypted via HTTPS/WSS
- **Authentication Tokens**: Short-lived QR tokens + secure API key storage
- **Local Storage**: Android keystore for sensitive credentials

#### Access Control
- **Network Isolation**: Server only accessible on specified networks
- **Session Isolation**: One active viewer per session enforcement
- **Token Management**: Automatic token revocation on suspicious activity

---

## Deployment Strategy

### Development Environment

#### Local Setup
```bash
# Initialize project
git clone <repository>
cd cc-monitor
npm install

# Start development servers
npm run dev:server    # Server on port 3000
npm run dev:android   # Android development build

# Run tests
npm run test:all      # All components
```

#### Docker Development
```yaml
# docker-compose.dev.yml
version: '3.8'
services:
  server:
    build: ./server
    ports:
      - "3000:3000"
    volumes:
      - ~/.claude:/root/.claude:ro
    environment:
      - NODE_ENV=development

  android-dev:
    build: ./android
    volumes:
      - ./android:/workspace
```

### Production Deployment

#### Server Deployment
- **Option 1**: Docker container on VPS/cloud instance
- **Option 2**: Direct Node.js installation on Linux server
- **Option 3**: Kubernetes deployment for scalability

#### Configuration Management
```typescript
// config/production.ts
export default {
  server: {
    port: process.env.PORT || 3000,
    host: process.env.HOST || '0.0.0.0'
  },
  claudeCode: {
    projectsPath: process.env.CLAUDE_PROJECTS_PATH || '~/.claude/projects'
  },
  security: {
    qrTokenExpiry: 30 * 1000, // 30 seconds
    apiKeyExpiry: 30 * 24 * 60 * 60 * 1000 // 30 days
  }
}
```

---

## Documentation Plan

### User Documentation

#### README.md Structure
```markdown
# Claude Code Remote Monitoring System

## Quick Start
- Prerequisites and system requirements
- Installation instructions for server and Android app
- Basic usage tutorial with screenshots

## Configuration
- Server configuration options
- Android app settings and permissions
- Network setup (LAN vs remote access)

## Troubleshooting
- Common connection issues and solutions
- Android power management configuration
- Network connectivity problems
```

#### API Documentation
- OpenAPI/Swagger specification for REST endpoints
- WebSocket protocol documentation with examples
- Authentication flow diagrams and code samples

### Developer Documentation

#### Architecture Guide
- System architecture overview with diagrams
- Component interaction patterns
- Data flow and state management

#### Contributing Guide
- Code style guidelines and linting setup
- Testing requirements and procedures
- Pull request process and review criteria

---

## Success Metrics

### User Experience Metrics
- **Time to First Connection**: < 60 seconds from app install to monitoring
- **Connection Reliability**: > 95% uptime for established connections
- **User Satisfaction**: Positive feedback on session monitoring capability

### Technical Performance Metrics
- **Latency**: Average < 2 seconds for message delivery (LAN)
- **Resource Usage**: Server < 256MB RAM, Android < 5% battery/hour
- **Error Rate**: < 1% failed connection attempts under normal conditions

### Business Metrics
- **GitHub Project Success**: Stars, forks, and community contributions
- **Documentation Quality**: Clear setup success rate for new users
- **Adoption Rate**: Downloads and active usage tracking

---

## Implementation Confidence Score

**Score: 9/10**

### High Confidence Factors
- ✅ **Technical Feasibility**: All required libraries and patterns are well-established
- ✅ **Clear Requirements**: User needs and technical specifications are well-defined
- ✅ **Proven Architecture**: Similar systems exist with documented implementation patterns
- ✅ **Development Tools**: Comprehensive toolchain available for all components

### Risk Mitigation
- **Android Power Management**: Well-documented workarounds and best practices available
- **WebSocket Reliability**: Established reconnection patterns and connection management
- **File Monitoring**: Chokidar library provides cross-platform reliability guarantees
- **Authentication Security**: WhatsApp-style QR authentication is a proven pattern

### Implementation Readiness
- **Detailed Task Breakdown**: Clear implementation phases with specific deliverables
- **Validation Strategy**: Comprehensive testing approach with measurable success criteria
- **Quality Standards**: Code quality, documentation, and deployment standards defined
- **Risk Management**: Identified risks with specific mitigation strategies

The comprehensive research, clear technical architecture, and detailed implementation plan provide high confidence for successful one-pass implementation using Claude Code.

---

**PRP Status**: ✅ Ready for Implementation
**Next Steps**: Begin Phase 1 implementation with monorepo setup and core infrastructure
**Task Breakdown**: See comprehensive implementation guide at [`docs/tasks/claude-code-remote-monitoring-system.md`](../tasks/claude-code-remote-monitoring-system.md)