# Task Breakdown: Claude Code Remote Monitoring System

**Generated**: 2025-09-14
**Source PRP**: docs/prps/claude-code-remote-monitoring-system.md
**Total Tasks**: 22
**Estimated Duration**: 5-6 weeks

---

## PRP Analysis Summary

### Feature Overview
**Claude Code Remote Monitoring System** - A monorepo project containing both a standalone monitoring server (Node.js/TypeScript) and Android client application that enables real-time remote monitoring of Claude Code sessions via QR-code based authentication and WebSocket streaming.

### Key Technical Requirements
- **Monorepo Structure**: Server (Node.js/TypeScript) + Android (Kotlin) components
- **File Monitoring**: Real-time JSONL file monitoring using Chokidar v4
- **Authentication**: WhatsApp-style QR code pairing with 30-second token expiry
- **Communication**: WebSocket-based real-time streaming architecture
- **Session Management**: Single active viewer per session enforcement
- **Deployment**: Docker containerization + CI/CD pipeline setup
- **Performance**: <2s latency, <10s reconnection time, <5% battery drain

### Validation Requirements
- Monitor actual Claude Code sessions at `~/.claude/projects/<project-path>/<session-uuid>.jsonl`
- End-to-end authentication flow testing (QR → scan → API key → session access)
- Network resilience testing (disconnection/reconnection scenarios)
- Multi-session handling and exclusive viewer enforcement
- Performance benchmarking against specified NFR targets

---

## Task Complexity Assessment

### Overall Complexity: **Complex**
- **Justification**: Multi-platform system (server + mobile) with real-time requirements, authentication security, and file system monitoring
- **Technology Integration**: 5+ major technology stacks (Node.js, TypeScript, Kotlin, WebSockets, file monitoring)
- **Cross-platform Concerns**: Android power management, network reliability, file system access patterns

### Integration Points
1. **File System → Server**: JSONL file monitoring and parsing
2. **Server → Android**: WebSocket real-time communication
3. **Authentication Flow**: QR generation → mobile scan → API key exchange
4. **Session Lifecycle**: Claude Code session events → Android display updates
5. **Network Resilience**: Connection management across mobile/WiFi transitions

### Technical Challenges
- **Android Power Management**: Maintaining WebSocket connections with aggressive battery optimization
- **File Access Patterns**: Handling concurrent access to active JSONL files
- **Real-time Streaming**: Sub-2-second latency requirements for message delivery
- **Authentication Security**: Time-sensitive QR token management
- **Session State Detection**: Analyzing JSONL patterns to determine Claude working vs waiting states

---

## Phase Organization

### Phase 1: Foundation Infrastructure (Week 1-2)
**Objective**: Establish monorepo structure and core file monitoring capabilities
- **Deliverables**:
  - Working monorepo with TypeScript build system
  - JSONL file monitoring service with Chokidar
  - WebSocket server infrastructure
  - Basic Android project with WebSocket client
- **Milestone**: Server can monitor and stream Claude Code session changes to WebSocket clients

### Phase 2: Authentication & Security (Week 2)
**Objective**: Implement QR-based authentication system
- **Deliverables**:
  - QR code generation with time-based tokens
  - Android QR scanning with ML Kit
  - API key management and persistence
- **Milestone**: Complete authentication flow from QR scan to authenticated API access

### Phase 3: Session Management (Week 3)
**Objective**: Multi-session discovery and exclusive viewer enforcement
- **Deliverables**:
  - Session discovery service scanning ~/.claude/projects/
  - Android session selection interface
  - Single viewer per session enforcement
- **Milestone**: Users can select and switch between multiple Claude Code sessions

### Phase 4: Real-time Streaming & State Detection (Week 3-4)
**Objective**: Real-time message streaming with session state awareness
- **Deliverables**:
  - JSONL message parsing and real-time streaming
  - Session state detection (working/waiting/idle)
  - Android message display interface
- **Milestone**: Real-time session monitoring with state indication

### Phase 5: Resilience & Polish (Week 4)
**Objective**: Connection reliability and user experience optimization
- **Deliverables**:
  - Exponential backoff reconnection logic
  - Session termination handling
  - Local caching for brief disconnections
- **Milestone**: Robust connection management handling network interruptions

### Phase 6: DevOps & Distribution (Week 5)
**Objective**: Production-ready deployment and documentation
- **Deliverables**:
  - CI/CD pipelines with automated testing
  - Docker containerization
  - Comprehensive documentation
- **Milestone**: Production-ready deployment with complete documentation

---

## Detailed Task Breakdown

### Phase 1: Foundation Infrastructure

#### Task 1.1: Initialize Monorepo Structure
- **Priority**: Critical
- **Dependencies**: None (starting task)
- **Estimated Duration**: 1-2 days

**Acceptance Criteria**:
```gherkin
Given I want to set up the project foundation
When I initialize the monorepo structure
Then the following should be available:
  - /server directory with TypeScript configuration
  - /android directory with Kotlin/Gradle setup
  - /shared directory for common type definitions
  - Root package.json with workspace configuration
  - Jest testing framework configured for server
  - ESLint + Prettier for code quality

AND When I run build commands
Then `npm run build` should compile TypeScript successfully
AND `npm test` should run Jest tests
AND `./gradlew build` should build Android project
```

**Implementation Details**:
- Create monorepo structure: `/server`, `/android`, `/shared`
- Configure TypeScript with strict mode enabled
- Setup Jest with coverage reporting (80% minimum)
- Configure ESLint with @typescript-eslint/recommended rules
- Initialize Android project with Kotlin coroutines and Ktor dependencies

**Files to Create/Modify**:
- `package.json` (root workspace configuration)
- `server/package.json` (server dependencies)
- `server/tsconfig.json` (TypeScript configuration)
- `server/jest.config.js` (Jest testing configuration)
- `android/build.gradle` (Android project configuration)
- `.eslintrc.js` (Code quality rules)

**Manual Testing Steps**:
1. Run `npm install` in root directory
2. Execute `npm run build` - should complete without errors
3. Execute `npm test` - should run Jest tests successfully
4. Navigate to android directory and run `./gradlew build`
5. Verify all build outputs are generated correctly

---

#### Task 1.2: Implement JSONL File Monitoring System
- **Priority**: Critical
- **Dependencies**: Task 1.1 (monorepo setup)
- **Estimated Duration**: 2-3 days

**Acceptance Criteria**:
```gherkin
Given Claude Code is running and creating JSONL files
When the file monitoring service is active
Then it should detect new JSONL files in ~/.claude/projects/
AND it should stream file changes in real-time
AND it should parse JSONL message format correctly
AND it should handle file rotation and concurrent access gracefully

AND Given a JSONL file receives new content
When the monitoring service detects the change
Then it should emit parsed messages within 500ms
AND it should maintain message order consistency
```

**Rule-Based Checklist**:
- [ ] Chokidar v4 installed and configured for cross-platform compatibility
- [ ] File watcher monitors `~/.claude/projects/**/*.jsonl` pattern
- [ ] JSONL parser handles malformed lines gracefully
- [ ] File lock detection prevents read conflicts
- [ ] Message ordering preserved during concurrent file operations
- [ ] Error handling for missing or deleted files
- [ ] Performance testing with multiple active sessions

**Implementation Details**:
- Install Chokidar v4: `npm install chokidar@^4.0.0`
- Create FileStreamMonitor class with configurable polling options
- Implement JSONL line-by-line parsing with error recovery
- Add file existence checks and graceful degradation
- Implement message queuing to maintain order during high-frequency updates

**Files to Create/Modify**:
- `server/src/services/FileStreamMonitor.ts`
- `server/src/parsers/JSONLParser.ts`
- `server/src/types/SessionMessage.ts`
- `server/src/utils/FileUtils.ts`

**Manual Testing Steps**:
1. Start a Claude Code session to generate JSONL files
2. Start the monitoring service
3. Verify service detects existing JSONL files
4. Interact with Claude Code and verify real-time change detection
5. Test file rotation scenarios (Claude Code session restart)
6. Verify error handling with corrupted JSONL content

---

#### Task 1.3: Build WebSocket Server Infrastructure
- **Priority**: Critical
- **Dependencies**: Task 1.2 (file monitoring)
- **Estimated Duration**: 2 days

**Acceptance Criteria**:
```gherkin
Given the WebSocket server is running
When multiple clients attempt to connect
Then it should handle concurrent connections efficiently
AND it should broadcast session updates to connected clients
AND it should maintain connection state per session
AND it should handle client disconnections gracefully

AND Given a session update occurs
When the update is broadcast via WebSocket
Then all connected clients should receive the update within 2 seconds (LAN)
AND message format should follow the defined protocol schema
```

**Rule-Based Checklist**:
- [ ] WebSocket server using ws@^8.14.0 library
- [ ] Connection manager tracking client sessions
- [ ] Message broadcasting system for session updates
- [ ] Heartbeat/ping-pong for connection health monitoring
- [ ] Graceful connection termination handling
- [ ] Error handling for malformed WebSocket messages
- [ ] Protocol validation for incoming messages

**Implementation Details**:
- Install ws library: `npm install ws@^8.14.0 @types/ws`
- Create WebSocketServer class with connection management
- Implement message broadcasting with client session tracking
- Add WebSocket message validation using TypeScript interfaces
- Implement ping/pong heartbeat with 30-second intervals

**Files to Create/Modify**:
- `server/src/services/WebSocketServer.ts`
- `server/src/managers/ConnectionManager.ts`
- `server/src/protocols/WebSocketProtocol.ts`
- `server/src/types/WebSocketMessage.ts`

**Manual Testing Steps**:
1. Start WebSocket server on specified port
2. Connect multiple WebSocket clients (using websocat or browser tools)
3. Trigger session updates from file monitoring
4. Verify all clients receive broadcast messages
5. Test connection drops and reconnection scenarios
6. Validate message format compliance with protocol

---

#### Task 1.4: Setup Android Project Structure
- **Priority**: Critical
- **Dependencies**: Task 1.1 (monorepo setup)
- **Estimated Duration**: 1-2 days

**Acceptance Criteria**:
```gherkin
Given the Android project is initialized
When I build the project
Then it should compile successfully with Kotlin coroutines
AND Ktor WebSocket dependencies should be properly configured
AND MVVM architecture components should be set up
AND Material Design 3 components should be available

AND When I run the basic WebSocket client test
Then it should connect to a test WebSocket server successfully
```

**Rule-Based Checklist**:
- [ ] Android project targeting API 24+ (Android 7.0)
- [ ] Kotlin coroutines configured with proper scope management
- [ ] Ktor WebSocket client dependencies added
- [ ] Material Design 3 components integrated
- [ ] ViewBinding enabled for type-safe view access
- [ ] MVVM architecture with ViewModel and LiveData
- [ ] Internet and camera permissions declared

**Implementation Details**:
- Initialize Android project in `/android` directory
- Configure Gradle build with Kotlin coroutines 1.7+
- Add Ktor dependencies: implementation("io.ktor:ktor-client-websockets")
- Setup Material Design 3: implementation("com.google.android.material:material:1.10.0")
- Configure ViewBinding and data binding

**Files to Create/Modify**:
- `android/build.gradle` (project-level configuration)
- `android/app/build.gradle` (app-level dependencies)
- `android/app/src/main/AndroidManifest.xml` (permissions)
- `android/app/src/main/res/values/themes.xml` (Material Design theme)

**Manual Testing Steps**:
1. Navigate to android directory
2. Run `./gradlew build` - should complete successfully
3. Run `./gradlew testDebugUnitTest` for unit tests
4. Import project in Android Studio and verify no build errors
5. Run basic app to ensure Material Design components render correctly

---

#### Task 1.5: Implement Android WebSocket Client
- **Priority**: Critical
- **Dependencies**: Task 1.4 (Android setup), Task 1.3 (WebSocket server)
- **Estimated Duration**: 2-3 days

**Acceptance Criteria**:
```gherkin
Given the Android WebSocket client is implemented
When it connects to the server
Then it should establish a persistent WebSocket connection
AND it should implement exponential backoff for reconnection
AND it should run in a foreground service for background operation
AND it should handle connection state changes properly

AND Given the connection is lost
When the client attempts reconnection
Then it should retry with exponential backoff (1s, 1.5s, 2.25s, ...)
AND maximum retry interval should not exceed 30 seconds
```

**Rule-Based Checklist**:
- [ ] Ktor WebSocket client configured with OkHttp engine
- [ ] Exponential backoff reconnection logic implemented
- [ ] Foreground service for maintaining connections in background
- [ ] Connection state management with LiveData
- [ ] Proper coroutine scope management (ViewModelScope, ServiceScope)
- [ ] WebSocket message parsing and handling
- [ ] Error handling for connection failures

**Implementation Details**:
- Create WebSocket client using Ktor with OkHttp engine
- Implement connection manager with exponential backoff algorithm
- Create foreground service for background WebSocket maintenance
- Use StateFlow/LiveData for connection state management
- Implement proper coroutine cancellation handling

**Files to Create/Modify**:
- `android/app/src/main/java/com/ccmonitor/services/WebSocketService.kt`
- `android/app/src/main/java/com/ccmonitor/network/WebSocketClient.kt`
- `android/app/src/main/java/com/ccmonitor/viewmodels/ConnectionViewModel.kt`
- `android/app/src/main/java/com/ccmonitor/utils/ConnectionManager.kt`

**Manual Testing Steps**:
1. Start the WebSocket server from Task 1.3
2. Build and run the Android app
3. Initiate WebSocket connection from the app
4. Verify connection establishment in server logs
5. Test disconnection/reconnection by stopping/starting server
6. Verify exponential backoff behavior in logs

---

### Phase 2: Authentication & Security

#### Task 2.1: Implement Server-side QR Code Generation
- **Priority**: Critical
- **Dependencies**: Task 1.3 (WebSocket server)
- **Estimated Duration**: 2 days

**Acceptance Criteria**:
```gherkin
Given the authentication service is running
When a QR code is requested
Then it should generate a unique guest token with 30-second expiry
AND the QR code should contain a valid authentication URL
AND the token should be tracked for WebSocket authentication
AND expired tokens should be automatically cleaned up

AND Given the QR code is scanned within the expiry window
When the mobile client uses the guest token
Then it should successfully authenticate and receive an API key
```

**Rule-Based Checklist**:
- [ ] QR code library (qrcode@^1.5.3) installed and configured
- [ ] Guest token generation using crypto.randomUUID()
- [ ] Token expiry management with 30-second TTL
- [ ] QR code generation with proper error correction level
- [ ] Authentication endpoint for guest token validation
- [ ] Automatic cleanup of expired tokens
- [ ] API key generation after successful guest token validation

**Implementation Details**:
- Install QR library: `npm install qrcode@^1.5.3`
- Create AuthenticationService with token management
- Implement QR code generation with SVG format output
- Create REST endpoint: `POST /api/auth/qr`
- Add token cleanup job with 1-minute interval

**Files to Create/Modify**:
- `server/src/services/AuthenticationService.ts`
- `server/src/routes/AuthRoutes.ts`
- `server/src/utils/TokenManager.ts`
- `server/src/types/AuthTypes.ts`

**Manual Testing Steps**:
1. Start the server with authentication service
2. Make POST request to `/api/auth/qr`
3. Verify QR code SVG is returned with guest token
4. Test token expiry by waiting 30+ seconds and attempting authentication
5. Verify expired tokens are cleaned up automatically
6. Test concurrent QR generation requests

---

#### Task 2.2: Implement Android QR Code Scanning
- **Priority**: Critical
- **Dependencies**: Task 2.1 (QR generation), Task 1.5 (WebSocket client)
- **Estimated Duration**: 2-3 days

**Acceptance Criteria**:
```gherkin
Given the Android QR scanner is active
When a valid authentication QR code is scanned
Then it should extract the guest token from the QR URL
AND it should initiate the authentication flow with the server
AND it should handle QR scanning errors gracefully
AND it should provide visual feedback during scanning

AND Given a QR code scan is successful
When the authentication request is made
Then it should receive a valid API key for subsequent requests
AND the API key should be stored securely using Android Keystore
```

**Rule-Based Checklist**:
- [ ] ML Kit Barcode Scanning API integrated
- [ ] Camera permission handling with user prompts
- [ ] QR code URL parsing and validation
- [ ] Authentication flow implementation with server
- [ ] API key secure storage using Android Keystore
- [ ] Visual feedback for successful/failed scans
- [ ] Error handling for camera access issues

**Implementation Details**:
- Integrate ML Kit: implementation("com.google.mlkit:barcode-scanning:17.2.0")
- Create QR scanner activity with camera preview
- Implement URL parsing to extract guest tokens
- Create authentication flow with server API calls
- Store API keys securely using EncryptedSharedPreferences

**Files to Create/Modify**:
- `android/app/src/main/java/com/ccmonitor/activities/QRScanActivity.kt`
- `android/app/src/main/java/com/ccmonitor/services/AuthenticationService.kt`
- `android/app/src/main/java/com/ccmonitor/utils/SecureStorage.kt`
- `android/app/src/main/java/com/ccmonitor/viewmodels/AuthViewModel.kt`

**Manual Testing Steps**:
1. Generate QR code from server (Task 2.1)
2. Launch Android app QR scanner
3. Point camera at QR code and verify detection
4. Confirm authentication flow completes successfully
5. Verify API key is stored and can be retrieved
6. Test camera permission denial scenarios

---

#### Task 2.3: Implement API Key Management System
- **Priority**: High
- **Dependencies**: Task 2.2 (QR scanning)
- **Estimated Duration**: 1-2 days

**Acceptance Criteria**:
```gherkin
Given a mobile device has completed QR authentication
When it receives an API key
Then the key should have a 30-day expiry period
AND it should be validated on every WebSocket connection
AND it should support automatic refresh before expiry
AND revoked keys should be blocked immediately

AND Given an API key is near expiry
When the mobile client makes requests
Then it should automatically refresh the key
AND the old key should remain valid during the refresh window
```

**Rule-Based Checklist**:
- [ ] API key generation with 30-day TTL
- [ ] Key validation middleware for WebSocket connections
- [ ] Automatic key refresh mechanism
- [ ] Key revocation capability
- [ ] Secure key storage on Android (Keystore)
- [ ] Key expiry monitoring and notifications
- [ ] Graceful handling of revoked/expired keys

**Implementation Details**:
- Create API key generation using JWT or custom token format
- Implement validation middleware for WebSocket authentication
- Add automatic refresh logic on Android client
- Create key revocation endpoint and blacklist management
- Implement secure token storage using Android Keystore

**Files to Create/Modify**:
- `server/src/middleware/AuthMiddleware.ts`
- `server/src/services/ApiKeyService.ts`
- `android/app/src/main/java/com/ccmonitor/auth/ApiKeyManager.kt`
- `server/src/routes/AuthRoutes.ts` (add key refresh endpoint)

**Manual Testing Steps**:
1. Complete QR authentication flow to receive API key
2. Use API key to establish WebSocket connection
3. Test key validation by using invalid/expired keys
4. Test automatic refresh mechanism before expiry
5. Test key revocation scenarios
6. Verify secure storage of keys on Android

---

### Phase 3: Session Management

#### Task 3.1: Implement Session Discovery Service
- **Priority**: Critical
- **Dependencies**: Task 1.2 (file monitoring)
- **Estimated Duration**: 2-3 days

**Acceptance Criteria**:
```gherkin
Given Claude Code sessions exist in ~/.claude/projects/
When the session discovery service scans the directory
Then it should find all .jsonl files
AND it should extract session metadata (project, session ID, timestamps)
AND it should detect active vs inactive sessions based on recent activity
AND it should handle missing or corrupted session files gracefully

AND Given session metadata is extracted
When the information is requested via API
Then it should return session list with current status
AND active sessions should be marked clearly
AND inactive sessions should show last activity timestamp
```

**Rule-Based Checklist**:
- [ ] Directory scanning of ~/.claude/projects/ structure
- [ ] JSONL metadata extraction (project name, session UUID, timestamps)
- [ ] Active session detection using file modification times
- [ ] Session status determination (active/idle/terminated)
- [ ] Error handling for corrupted or missing files
- [ ] Efficient scanning for large numbers of sessions (50+ sessions)
- [ ] Caching mechanism for session metadata

**Implementation Details**:
- Create SessionDiscoveryService that scans ~/.claude/projects/
- Parse JSONL files to extract session metadata and last activity
- Implement session status detection using file timestamps
- Create REST endpoint: `GET /api/sessions`
- Add caching layer for session metadata with TTL

**Files to Create/Modify**:
- `server/src/services/SessionDiscoveryService.ts`
- `server/src/types/SessionMetadata.ts`
- `server/src/routes/SessionRoutes.ts`
- `server/src/utils/SessionStatusDetector.ts`

**Manual Testing Steps**:
1. Create multiple Claude Code sessions to generate JSONL files
2. Start the session discovery service
3. Make GET request to `/api/sessions`
4. Verify all sessions are detected with correct metadata
5. Test with inactive/terminated sessions
6. Performance test with 20+ concurrent sessions

---

#### Task 3.2: Build Android Session Selection Interface
- **Priority**: Critical
- **Dependencies**: Task 3.1 (session discovery), Task 2.3 (API key management)
- **Estimated Duration**: 3 days

**Acceptance Criteria**:
```gherkin
Given the Android app is authenticated
When the session list is loaded
Then it should display all available Claude Code sessions
AND each session should show project name, status, and last activity
AND active sessions should be visually distinguished
AND users should be able to select and connect to sessions
AND the interface should handle empty session lists gracefully

AND Given a user selects a session
When they tap to connect
Then the app should establish WebSocket connection to that session
AND display connection status to the user
```

**Rule-Based Checklist**:
- [ ] RecyclerView with session list adapter
- [ ] Material Design 3 components for session cards
- [ ] Session status indicators (active/idle/terminated)
- [ ] Pull-to-refresh functionality for session updates
- [ ] Empty state handling when no sessions exist
- [ ] Loading states during session discovery
- [ ] Error handling for API failures

**Implementation Details**:
- Create SessionListActivity with RecyclerView
- Design session card layout with Material Design 3 components
- Implement session selection and connection logic
- Add pull-to-refresh using SwipeRefreshLayout
- Create ViewModels for session list management

**Files to Create/Modify**:
- `android/app/src/main/java/com/ccmonitor/activities/SessionListActivity.kt`
- `android/app/src/main/java/com/ccmonitor/adapters/SessionListAdapter.kt`
- `android/app/src/main/java/com/ccmonitor/viewmodels/SessionListViewModel.kt`
- `android/app/src/main/res/layout/activity_session_list.xml`
- `android/app/src/main/res/layout/item_session_card.xml`

**Manual Testing Steps**:
1. Authenticate using QR code scanning
2. Navigate to session list screen
3. Verify all available sessions are displayed correctly
4. Test session selection and connection initiation
5. Test pull-to-refresh functionality
6. Test empty state when no sessions exist

---

#### Task 3.3: Enforce Single Active Viewer Per Session
- **Priority**: High
- **Dependencies**: Task 3.2 (session interface), Task 1.3 (WebSocket server)
- **Estimated Duration**: 2 days

**Acceptance Criteria**:
```gherkin
Given a session already has an active viewer
When another client attempts to connect to the same session
Then it should reject the connection with an appropriate message
AND the existing viewer should be notified of the connection attempt
AND the new client should receive clear feedback about the conflict

AND Given the active viewer disconnects
When the disconnection is detected
Then the session should become available for new connections immediately
AND any queued connection attempts should be processed
```

**Rule-Based Checklist**:
- [ ] Session connection tracking per WebSocket connection
- [ ] Exclusive access enforcement logic
- [ ] Connection conflict detection and rejection
- [ ] Notification system for connection attempts
- [ ] Automatic cleanup when viewers disconnect
- [ ] Queue management for pending connection attempts
- [ ] Clear error messages for rejected connections

**Implementation Details**:
- Extend ConnectionManager to track session-specific connections
- Implement connection rejection logic with appropriate WebSocket error codes
- Add notification system for active viewers when conflicts occur
- Create connection queue system for handling multiple connection attempts

**Files to Create/Modify**:
- `server/src/managers/ConnectionManager.ts` (extend for session tracking)
- `server/src/services/SessionAccessControl.ts`
- `server/src/types/ConnectionConflict.ts`
- `android/app/src/main/java/com/ccmonitor/handlers/ConnectionConflictHandler.kt`

**Manual Testing Steps**:
1. Connect first client to a specific session
2. Attempt to connect second client to same session
3. Verify second client is rejected with clear error message
4. Verify first client receives notification about connection attempt
5. Disconnect first client and verify session becomes available
6. Test rapid connection/disconnection scenarios

---

### Phase 4: Real-time Streaming & State Detection

#### Task 4.1: Implement JSONL Message Parsing and Real-time Streaming
- **Priority**: Critical
- **Dependencies**: Task 3.3 (single viewer), Task 1.2 (file monitoring)
- **Estimated Duration**: 3 days

**Acceptance Criteria**:
```gherkin
Given a Claude Code session is active and generating JSONL messages
When new messages are written to the JSONL file
Then they should be parsed and streamed to connected clients within 500ms
AND message types should be correctly identified (user vs assistant)
AND message content should be preserved exactly as written
AND streaming should handle high-frequency message updates

AND Given the WebSocket client receives a streamed message
When it processes the message
Then it should maintain message order consistency
AND handle partial message scenarios gracefully
```

**Rule-Based Checklist**:
- [ ] Real-time JSONL line parsing with incremental reading
- [ ] Message type detection (user, assistant, system)
- [ ] Streaming optimization for high-frequency updates
- [ ] Message order preservation during concurrent access
- [ ] Error handling for malformed JSONL lines
- [ ] Buffer management for large message content
- [ ] Performance optimization for sub-500ms latency

**Implementation Details**:
- Enhance FileStreamMonitor to parse JSONL incrementally
- Implement message type classification based on Claude Code format
- Add streaming buffer management to handle message bursts
- Create WebSocket message protocol for real-time streaming
- Optimize file reading to minimize I/O overhead

**Files to Create/Modify**:
- `server/src/parsers/JSONLParser.ts` (enhance for real-time parsing)
- `server/src/services/MessageStreamingService.ts`
- `server/src/types/ClaudeCodeMessage.ts`
- `server/src/protocols/StreamingProtocol.ts`

**Manual Testing Steps**:
1. Start Claude Code session and monitor JSONL generation
2. Connect Android client to session
3. Interact with Claude Code to generate various message types
4. Verify messages appear on Android within 500ms
5. Test high-frequency interactions (rapid back-and-forth)
6. Monitor message order consistency during rapid updates

---

#### Task 4.2: Implement Session State Detection
- **Priority**: High
- **Dependencies**: Task 4.1 (message streaming)
- **Estimated Duration**: 2-3 days

**Acceptance Criteria**:
```gherkin
Given messages are being streamed from a Claude Code session
When the message flow is analyzed
Then the system should detect when Claude is working vs waiting for input
AND state changes should be communicated to connected clients
AND state detection should handle edge cases (long responses, interruptions)
AND idle sessions should be detected after inactivity periods

AND Given the session state changes
When the state update is sent to clients
Then the Android app should update UI to reflect the current state
AND users should have clear visual indication of Claude's activity status
```

**Rule-Based Checklist**:
- [ ] Message flow analysis for state determination
- [ ] State change detection (working/waiting/idle)
- [ ] Timing-based state transitions (e.g., idle after 5 minutes)
- [ ] Edge case handling for long responses or interruptions
- [ ] State persistence across server restarts
- [ ] Visual state indicators on Android UI
- [ ] State change notifications with timestamps

**Implementation Details**:
- Create SessionStateAnalyzer to analyze message patterns
- Implement state machine for tracking session status
- Add timing logic for idle state detection
- Create state change notification system via WebSocket
- Design UI indicators for different session states

**Files to Create/Modify**:
- `server/src/services/SessionStateAnalyzer.ts`
- `server/src/types/SessionState.ts`
- `server/src/utils/StateMachine.ts`
- `android/app/src/main/java/com/ccmonitor/ui/StateIndicatorView.kt`

**Manual Testing Steps**:
1. Connect to active Claude Code session
2. Observe state changes during various interaction patterns:
   - User asks question → Claude responds (working → waiting)
   - Long Claude responses (working state persistence)
   - Session idle for extended periods
3. Verify state indicators update correctly on Android
4. Test edge cases like interrupted responses

---

#### Task 4.3: Build Android Real-time Message Display
- **Priority**: Critical
- **Dependencies**: Task 4.2 (state detection), Task 3.2 (session interface)
- **Estimated Duration**: 3 days

**Acceptance Criteria**:
```gherkin
Given the Android app is connected to a session
When messages are streamed from the server
Then they should be displayed in a scrolling interface
AND message timestamps should be shown accurately
AND user vs assistant messages should be visually distinguished
AND the interface should handle large volumes of messages efficiently
AND auto-scroll should follow new messages while preserving manual scroll position

AND Given messages are displayed
When the session state changes
Then appropriate visual indicators should update
AND users should see clear feedback about Claude's current activity
```

**Rule-Based Checklist**:
- [ ] RecyclerView with message adapter for efficient scrolling
- [ ] Message bubble design following Material Design patterns
- [ ] Timestamp formatting and display
- [ ] Auto-scroll behavior with manual scroll preservation
- [ ] Session state indicator prominently displayed
- [ ] Performance optimization for 1000+ message sessions
- [ ] Message content formatting (code blocks, lists, etc.)

**Implementation Details**:
- Create MessageDisplayActivity with RecyclerView
- Design message bubble layouts for user vs assistant messages
- Implement auto-scroll with smart scroll position management
- Create session state indicator widget
- Optimize RecyclerView for large datasets using DiffUtil

**Files to Create/Modify**:
- `android/app/src/main/java/com/ccmonitor/activities/MessageDisplayActivity.kt`
- `android/app/src/main/java/com/ccmonitor/adapters/MessageAdapter.kt`
- `android/app/src/main/java/com/ccmonitor/viewmodels/MessageDisplayViewModel.kt`
- `android/app/src/main/res/layout/activity_message_display.xml`
- `android/app/src/main/res/layout/item_message_user.xml`
- `android/app/src/main/res/layout/item_message_assistant.xml`

**Manual Testing Steps**:
1. Connect to active session with existing message history
2. Verify messages load and display correctly
3. Test auto-scroll behavior with new incoming messages
4. Manually scroll up and verify auto-scroll doesn't interfere
5. Test with session containing 500+ messages
6. Verify state indicator updates with session activity

---

### Phase 5: Resilience & Polish

#### Task 5.1: Implement Connection Resilience with Exponential Backoff
- **Priority**: High
- **Dependencies**: Task 4.3 (message display)
- **Estimated Duration**: 2 days

**Acceptance Criteria**:
```gherkin
Given the Android client loses connection to the server
When it attempts to reconnect
Then it should use exponential backoff starting at 1 second
AND maximum retry interval should not exceed 30 seconds
AND it should continue retrying until connection is restored
AND users should see clear connection status indicators

AND Given the connection is restored
When the client reconnects successfully
Then it should sync any missed messages
AND resume normal operation seamlessly
```

**Rule-Based Checklist**:
- [ ] Exponential backoff algorithm (1s, 1.5s, 2.25s, ..., max 30s)
- [ ] Network state monitoring for intelligent reconnection
- [ ] Connection status UI with clear indicators
- [ ] Message sync after reconnection
- [ ] Retry limit handling with user notification
- [ ] Background reconnection during app backgrounding
- [ ] Battery optimization considerations

**Implementation Details**:
- Enhance WebSocketClient with exponential backoff logic
- Create NetworkStateMonitor for connection awareness
- Implement connection status UI components
- Add message synchronization logic after reconnection
- Handle Android power management restrictions

**Files to Create/Modify**:
- `android/app/src/main/java/com/ccmonitor/network/ReconnectionManager.kt`
- `android/app/src/main/java/com/ccmonitor/utils/NetworkStateMonitor.kt`
- `android/app/src/main/java/com/ccmonitor/ui/ConnectionStatusView.kt`
- `android/app/src/main/java/com/ccmonitor/services/WebSocketService.kt` (enhance)

**Manual Testing Steps**:
1. Establish connection and begin monitoring session
2. Disconnect network/stop server to simulate connection loss
3. Observe exponential backoff behavior in logs
4. Restore connection and verify automatic reconnection
5. Test during app backgrounding scenarios
6. Verify UI shows appropriate connection status

---

#### Task 5.2: Handle Session Termination and Cleanup
- **Priority**: Medium
- **Dependencies**: Task 5.1 (connection resilience)
- **Estimated Duration**: 1-2 days

**Acceptance Criteria**:
```gherkin
Given a Claude Code session ends or crashes
When the termination is detected
Then connected clients should be notified immediately
AND the session should be marked as terminated
AND users should be offered to select another active session
AND the WebSocket connection should be closed gracefully

AND Given session termination is detected
When the notification is sent to Android clients
Then users should see clear indication that the session ended
AND they should be redirected to session selection automatically
```

**Rule-Based Checklist**:
- [ ] Session termination detection via file monitoring
- [ ] WebSocket termination message protocol
- [ ] Graceful connection cleanup
- [ ] Automatic redirection to session selection
- [ ] Session status updates in session list
- [ ] Cleanup of server-side resources
- [ ] User notification with clear messaging

**Implementation Details**:
- Extend FileStreamMonitor to detect session termination
- Create session termination notification protocol
- Implement automatic redirection logic on Android
- Add server-side resource cleanup for terminated sessions

**Files to Create/Modify**:
- `server/src/services/SessionLifecycleManager.ts`
- `server/src/types/SessionTermination.ts`
- `android/app/src/main/java/com/ccmonitor/handlers/SessionTerminationHandler.kt`
- `android/app/src/main/java/com/ccmonitor/viewmodels/SessionLifecycleViewModel.kt`

**Manual Testing Steps**:
1. Connect to active Claude Code session
2. Terminate Claude Code process to simulate crash
3. Verify termination detection and notification
4. Verify Android app redirects to session selection
5. Test graceful session ending (normal Claude Code exit)
6. Verify server cleanup of terminated session resources

---

#### Task 5.3: Implement Local Message Caching
- **Priority**: Medium
- **Dependencies**: Task 5.2 (session termination)
- **Estimated Duration**: 2 days

**Acceptance Criteria**:
```gherkin
Given the Android client is receiving messages
When messages arrive via WebSocket
Then they should be cached locally using Room database
AND cached messages should persist across app restarts
AND cache should have reasonable size limits (e.g., last 500 messages per session)
AND old messages should be automatically purged

AND Given the client reconnects after brief disconnection
When it establishes connection
Then it should display cached messages immediately
AND sync with server for any missed messages
```

**Rule-Based Checklist**:
- [ ] Room database setup for message caching
- [ ] Message entity with proper indexing
- [ ] Cache size management with automatic purging
- [ ] Message sync logic after reconnection
- [ ] Offline message display capabilities
- [ ] Cache cleanup for terminated sessions
- [ ] Database migration strategy

**Implementation Details**:
- Setup Room database with Message entity
- Implement MessageRepository for cache management
- Create sync logic to merge cached and server messages
- Add cache size limits and automatic cleanup
- Implement offline message display functionality

**Files to Create/Modify**:
- `android/app/src/main/java/com/ccmonitor/database/MessageDatabase.kt`
- `android/app/src/main/java/com/ccmonitor/entities/MessageEntity.kt`
- `android/app/src/main/java/com/ccmonitor/repositories/MessageRepository.kt`
- `android/app/src/main/java/com/ccmonitor/utils/MessageSyncManager.kt`

**Manual Testing Steps**:
1. Connect to session and receive several messages
2. Disconnect from network briefly
3. Verify cached messages remain visible
4. Reconnect and verify message sync
5. Restart app and verify messages persist
6. Test cache size limits with large message volumes

---

### Phase 6: DevOps & Distribution

#### Task 6.1: Setup CI/CD Pipeline with GitHub Actions
- **Priority**: Medium
- **Dependencies**: All development tasks complete
- **Estimated Duration**: 2 days

**Acceptance Criteria**:
```gherkin
Given code is pushed to the repository
When GitHub Actions workflow is triggered
Then it should run all tests for both server and Android components
AND code quality checks (ESLint, ktlint) should pass
AND build artifacts should be generated successfully
AND test coverage reports should be published

AND Given a pull request is created
When the CI pipeline runs
Then all checks must pass before merge is allowed
AND test results should be clearly visible in PR status
```

**Rule-Based Checklist**:
- [ ] GitHub Actions workflow for server testing (Jest)
- [ ] GitHub Actions workflow for Android testing (JUnit)
- [ ] Code quality checks integration (ESLint, ktlint)
- [ ] Build artifact generation and storage
- [ ] Test coverage reporting with coverage gates
- [ ] Branch protection rules requiring CI success
- [ ] Parallel job execution for efficiency

**Implementation Details**:
- Create .github/workflows/ci.yml for continuous integration
- Configure separate jobs for server and Android testing
- Add code quality gates with failure conditions
- Setup test coverage reporting with codecov or similar
- Configure branch protection requiring CI success

**Files to Create/Modify**:
- `.github/workflows/ci.yml`
- `.github/workflows/android-ci.yml`
- `server/.eslintrc.js` (ensure CI-compatible rules)
- `android/build.gradle` (add test reporting)

**Manual Testing Steps**:
1. Create feature branch and push changes
2. Verify CI workflow triggers automatically
3. Check all test suites run and pass
4. Test with failing tests to verify CI catches issues
5. Create pull request and verify status checks
6. Test merge protection with failing CI

---

#### Task 6.2: Create Docker Containerization
- **Priority**: Medium
- **Dependencies**: Task 6.1 (CI/CD setup)
- **Estimated Duration**: 1-2 days

**Acceptance Criteria**:
```gherkin
Given the server application is containerized
When Docker image is built
Then it should include all necessary dependencies
AND the container should start successfully
AND it should expose the correct ports for WebSocket and HTTP
AND it should handle environment variable configuration

AND Given docker-compose is used for development
When containers are started with docker-compose up
Then the complete development environment should be available
AND the server should be accessible from host machine
```

**Rule-Based Checklist**:
- [ ] Multi-stage Dockerfile for production optimization
- [ ] Proper Node.js base image (alpine for size)
- [ ] Environment variable configuration
- [ ] Port exposure (3000 for HTTP, WebSocket)
- [ ] docker-compose.yml for development environment
- [ ] Volume mounting for development file watching
- [ ] Health check configuration

**Implementation Details**:
- Create Dockerfile with multi-stage build (development/production)
- Use Node.js Alpine image for smaller container size
- Configure environment variables for runtime configuration
- Create docker-compose.yml for easy development setup
- Add health check endpoint and Docker health check

**Files to Create/Modify**:
- `server/Dockerfile`
- `docker-compose.yml`
- `docker-compose.dev.yml`
- `server/src/routes/HealthRoutes.ts`

**Manual Testing Steps**:
1. Build Docker image: `docker build -t cc-monitor-server ./server`
2. Run container: `docker run -p 3000:3000 cc-monitor-server`
3. Verify server accessibility from host
4. Test docker-compose: `docker-compose up --build`
5. Verify complete development environment works
6. Test environment variable configuration

---

#### Task 6.3: Create Comprehensive Documentation
- **Priority**: High
- **Dependencies**: All implementation tasks complete
- **Estimated Duration**: 2-3 days

**Acceptance Criteria**:
```gherkin
Given the project is complete
When documentation is created
Then it should include clear installation instructions
AND setup guide for both server and Android components
AND API documentation with examples
AND troubleshooting guide for common issues
AND architecture overview with diagrams

AND Given a new user follows the documentation
When they complete the setup process
Then they should have a working monitoring system
AND be able to successfully monitor Claude Code sessions
```

**Rule-Based Checklist**:
- [ ] Comprehensive README.md with quick start guide
- [ ] Installation instructions for all platforms
- [ ] API documentation with OpenAPI/Swagger spec
- [ ] WebSocket protocol documentation
- [ ] Android app usage guide with screenshots
- [ ] Troubleshooting section for common issues
- [ ] Architecture diagrams and system overview
- [ ] Contributing guidelines for developers

**Implementation Details**:
- Create detailed README.md with step-by-step setup
- Document all API endpoints with request/response examples
- Create WebSocket protocol specification
- Add troubleshooting guide for connection issues, Android permissions, etc.
- Include architecture diagrams and system flow documentation

**Files to Create/Modify**:
- `README.md` (comprehensive project documentation)
- `docs/api/REST-API.md` (REST endpoint documentation)
- `docs/api/WebSocket-Protocol.md` (WebSocket message protocol)
- `docs/setup/Installation.md` (detailed installation guide)
- `docs/troubleshooting/Common-Issues.md` (troubleshooting guide)
- `docs/architecture/System-Overview.md` (architecture documentation)

**Manual Testing Steps**:
1. Follow README.md setup instructions on fresh environment
2. Verify all installation steps work correctly
3. Test API documentation examples
4. Validate troubleshooting guide solutions
5. Review documentation completeness and clarity
6. Get feedback from fresh user following docs

---

## Implementation Recommendations

### Suggested Team Structure
- **Full-stack Developer (Lead)**: Tasks 1.1-1.3, 2.1, 3.1, 4.1-4.2, 6.1-6.3
- **Android Developer**: Tasks 1.4-1.5, 2.2-2.3, 3.2, 4.3, 5.1-5.3
- **DevOps Engineer**: Tasks 6.1-6.2 (can be handled by lead if needed)

### Optimal Task Sequencing
**Week 1-2**: Foundation (Tasks 1.1-1.5) - Critical path items
**Week 2**: Authentication (Tasks 2.1-2.3) - Parallel after WebSocket setup
**Week 3**: Session Management (Tasks 3.1-3.3) - Sequential dependency
**Week 3-4**: Streaming (Tasks 4.1-4.3) - Some parallel opportunities
**Week 4**: Polish (Tasks 5.1-5.3) - Can be done in parallel
**Week 5**: DevOps (Tasks 6.1-6.3) - Final integration phase

### Parallelization Opportunities
- **Tasks 1.4-1.5** (Android setup) can run parallel with **Tasks 1.2-1.3** (server)
- **Tasks 2.2-2.3** (Android auth) can run parallel with **Task 3.1** (session discovery)
- **Tasks 5.1-5.3** (resilience features) can be developed in parallel
- **Tasks 6.1-6.2** (CI/CD and Docker) can run parallel with **Task 6.3** (documentation)

### Resource Allocation Suggestions
- **Critical Path Focus**: Prioritize Tasks 1.1-1.3, 2.1, 3.1, 4.1 as they block other work
- **Android Development**: Requires dedicated Android expertise for Tasks 1.4-1.5, 2.2-2.3, 3.2, 4.3, 5.1-5.3
- **Testing Strategy**: Plan integration testing during Phase 4 when both components communicate
- **Documentation**: Allocate sufficient time for comprehensive documentation as it's essential for open-source success

---

## Critical Path Analysis

### Tasks on Critical Path
1. **Task 1.1** (Monorepo Setup) → **Task 1.2** (File Monitoring) → **Task 1.3** (WebSocket Server)
2. **Task 2.1** (QR Generation) → **Task 3.1** (Session Discovery) → **Task 4.1** (Message Streaming)
3. **Task 4.1** (Streaming) → **Task 4.2** (State Detection) → **Task 4.3** (Android Display)

### Potential Bottlenecks
- **File Monitoring Complexity**: Task 1.2 may require additional time for handling Claude Code's specific JSONL format and concurrent access patterns
- **Android WebSocket Reliability**: Task 1.5 and Task 5.1 are critical for user experience but dependent on Android power management behavior
- **Real-time Performance**: Task 4.1 has strict latency requirements (<2s) that may require optimization iterations

### Schedule Optimization Suggestions
- **Start Android Development Early**: Begin Task 1.4 immediately after Task 1.1 to maximize parallel development
- **Prototype File Monitoring**: Create simple prototype of Task 1.2 early to validate approach with actual Claude Code sessions
- **Plan Integration Testing**: Schedule dedicated integration testing time during Week 4 when all components are functional
- **Buffer Time for Polish**: Tasks in Phase 5 are essential for user experience - don't compress this phase

---

<function_calls>
<invoke name="TodoWrite">
<parameter name="todos">[{"content": "Analyze PRP document complexity and identify key technical requirements", "status": "completed", "activeForm": "Analyzing PRP document complexity and identifying key technical requirements"}, {"content": "Assess system architecture and integration points for task decomposition", "status": "completed", "activeForm": "Assessing system architecture and integration points for task decomposition"}, {"content": "Break down the system into logical phases and work packages", "status": "completed", "activeForm": "Breaking down the system into logical phases and work packages"}, {"content": "Define detailed tasks with acceptance criteria and dependencies", "status": "completed", "activeForm": "Defining detailed tasks with acceptance criteria and dependencies"}, {"content": "Create comprehensive task breakdown document with Given-When-Then criteria", "status": "completed", "activeForm": "Creating comprehensive task breakdown document with Given-When-Then criteria"}, {"content": "Generate implementation recommendations and critical path analysis", "status": "completed", "activeForm": "Generating implementation recommendations and critical path analysis"}]