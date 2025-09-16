# Claude Code Remote Monitoring System

A comprehensive real-time monitoring solution for Claude Code sessions, enabling developers to monitor long-running sessions remotely from Android devices.

## Overview

The Claude Code Remote Monitoring System consists of:

- **Server Component**: Node.js/TypeScript backend with real-time file monitoring
- **Android Client**: Kotlin-based mobile app with Material Design 3
- **QR-Code Authentication**: WhatsApp-style secure pairing mechanism
- **Real-Time Streaming**: Sub-2-second latency WebSocket communication

## Key Features

- 🔄 **Real-time JSONL file monitoring** with Chokidar v4
- 📱 **Android app** with Material Design 3 interface
- 🔐 **Secure QR-code authentication** (30-second expiry)
- 🌐 **WebSocket streaming** with exponential backoff reconnection
- 👤 **Single active viewer per session** with force takeover
- 📊 **Session state detection** (working/waiting/idle)
- 💾 **Local message caching** with Room database
- 🛡️ **Robust error handling** and connection resilience

## Architecture

### Server (Node.js/TypeScript)
- **File Monitor**: Watches `~/.claude/projects/**/*.jsonl` files
- **WebSocket Server**: Real-time bidirectional communication
- **Authentication Service**: API key management with TTL
- **Connection Manager**: Session lifecycle and client management

### Android Client (Kotlin)
- **QR Scanner**: ML Kit barcode scanning
- **WebSocket Client**: Ktor-based with auto-reconnection
- **UI Components**: Compose with Material Design 3
- **Local Storage**: Room database for message caching

## Getting Started

### Prerequisites
- Node.js 18+
- Android Studio (for mobile app)
- Claude Code installed locally

### Server Setup

```bash
# Install dependencies
npm install

# Build the server
npm run build

# Start the monitoring server
npm start
```

The server will be available at:
- HTTP API: `http://localhost:3000`
- WebSocket: `ws://localhost:8080`
- Health Check: `http://localhost:3000/health`

### Authentication Flow

1. **Generate QR Code**: `POST /api/auth/qr`
2. **Scan with Android app**: ML Kit captures QR data
3. **Mobile Authentication**: `POST /api/auth/mobile`
4. **WebSocket Connection**: Connect with API key
5. **Session Monitoring**: Subscribe to specific sessions

### API Endpoints

#### Authentication
- `POST /api/auth/qr` - Generate QR code for pairing
- `POST /api/auth/mobile` - Authenticate mobile device
- `POST /api/auth/refresh` - Refresh API key
- `DELETE /api/auth/revoke` - Revoke API key

#### Sessions
- `GET /api/sessions` - List all monitored sessions
- `GET /health` - Server health status
- `GET /api/status` - Detailed server statistics

### WebSocket Protocol

```javascript
// Authentication
{
  "type": "authenticate",
  "payload": { "apiKey": "your-api-key" },
  "timestamp": "2025-09-15T07:00:00.000Z"
}

// Subscribe to session
{
  "type": "subscribe",
  "payload": { "sessionId": "session-uuid" },
  "timestamp": "2025-09-15T07:00:00.000Z"
}

// Real-time message
{
  "type": "message",
  "sessionId": "session-uuid",
  "timestamp": "2025-09-15T07:00:00.000Z",
  "data": {
    "messageType": "user|assistant",
    "content": "Message content...",
    "parentUuid": "parent-message-uuid"
  }
}
```

## Development

### Version Management

**CRITICAL**: Always bump version numbers after any code change:

1. Update `VERSION` file (e.g., `1.1.0` → `1.1.1`)
2. Update `server/package.json` version field
3. Update Android `android/app/build.gradle`:
   - Increment `versionCode` (integer)
   - Update `versionName` (string)
4. Commit and push to trigger GitHub Actions pipeline

**Example version bump:**
```bash
# Update VERSION file to 1.1.1
echo "1.1.1" > VERSION

# Update server/package.json
sed -i 's/"version": "1.1.0"/"version": "1.1.1"/' server/package.json

# Update Android build.gradle
sed -i 's/versionCode 2/versionCode 3/' android/app/build.gradle
sed -i 's/versionName "1.1.0"/versionName "1.1.1"/' android/app/build.gradle

# Commit and push
git add VERSION server/package.json android/app/build.gradle
git commit -m "Bump version to 1.1.1"
git push
```

This ensures proper tracking and allows users to distinguish between different builds.

### Project Structure
```
cc-monitor/
├── server/          # Node.js/TypeScript backend
│   ├── src/
│   │   ├── services/    # Core business logic
│   │   ├── managers/    # Connection & session management
│   │   ├── middleware/  # Express middleware
│   │   ├── routes/      # REST API endpoints
│   │   └── types/       # TypeScript definitions
├── android/         # Android Kotlin app
│   └── app/src/main/java/com/ccmonitor/
├── shared/          # Shared type definitions
└── docs/           # Documentation
```

### Technology Stack

**Server:**
- Node.js 18+ with TypeScript
- Express.js for REST API
- ws library for WebSocket server
- Chokidar v4 for file monitoring
- QR Code generation
- Helmet + CORS for security

**Android:**
- Kotlin with Jetpack Compose
- Material Design 3
- Ktor WebSocket client
- ML Kit Barcode Scanning
- Room database for caching
- Encrypted SharedPreferences

## Testing

The system has been comprehensively tested across all use cases:

- ✅ **TypeScript Compilation**: Strict mode compliance
- ✅ **REST API Endpoints**: All authentication flows
- ✅ **WebSocket Protocol**: Authentication and messaging
- ✅ **File Monitoring**: JSONL file detection and parsing
- ✅ **Android Components**: UI, WebSocket client, caching
- ✅ **Error Handling**: Connection failures and recovery
- ✅ **Security**: API key validation and QR expiry

## Deployment

### Docker (Server)
```bash
# Build Docker image
docker build -t cc-monitor-server ./server

# Run container
docker run -p 3000:3000 -p 8080:8080 cc-monitor-server
```

### Android APK
Build the Android app in Android Studio or use the command line:
```bash
cd android
./gradlew assembleRelease
```

## Configuration

### Environment Variables
- `PORT` - HTTP server port (default: 3000)
- `WS_PORT` - WebSocket server port (default: 8080)
- `CLAUDE_PROJECTS_PATH` - Path to Claude projects directory
- `QR_TOKEN_TTL` - QR code expiry time in ms (default: 30000)
- `API_KEY_TTL` - API key expiry time in ms (default: 30 days)

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Claude Code team for the JSONL session format
- Material Design 3 for mobile UI guidelines
- Chokidar maintainers for robust file watching
- WebSocket community for real-time protocols

---

**Note**: This system monitors Claude Code sessions by reading JSONL files from `~/.claude/projects/`. Ensure Claude Code is configured to write session files to the default location for proper operation.