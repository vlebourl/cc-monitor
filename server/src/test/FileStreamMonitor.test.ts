import { FileStreamMonitor } from '../services/FileStreamMonitor';
import { JSONLParser } from '../parsers/JSONLParser';
import { mkdir, writeFile, rmdir } from 'fs/promises';
import path from 'path';
import os from 'os';

describe('FileStreamMonitor', () => {
  let monitor: FileStreamMonitor;
  let testDir: string;

  beforeEach(async () => {
    // Create a temporary test directory
    testDir = path.join(os.tmpdir(), 'cc-monitor-test', Date.now().toString());
    await mkdir(testDir, { recursive: true });

    monitor = new FileStreamMonitor({
      claudeProjectsPath: testDir,
      pollInterval: 100,
      usePolling: true // Use polling for tests for reliability
    });
  });

  afterEach(async () => {
    await monitor.stop();
    // Clean up test directory
    try {
      await rmdir(testDir, { recursive: true });
    } catch {
      // Ignore cleanup errors
    }
  });

  test('should start monitoring successfully', async () => {
    const startPromise = new Promise<void>((resolve) => {
      monitor.once('started', resolve);
    });

    await monitor.start();
    await startPromise;

    expect(monitor.isMonitoring()).toBe(true);
  });

  test('should detect new JSONL files', async () => {
    await monitor.start();

    const sessionDiscoveredPromise = new Promise<any>((resolve) => {
      monitor.once('sessionDiscovered', resolve);
    });

    // Create a test JSONL file
    const testFile = path.join(testDir, 'test-project', 'session-123.jsonl');
    await mkdir(path.dirname(testFile), { recursive: true });
    await writeFile(testFile, '');

    const event = await sessionDiscoveredPromise;

    expect(event.filePath).toBe(testFile);
    expect(event.sessionInfo.sessionId).toBe('session-123');
    expect(event.sessionInfo.projectPath).toBe('test-project');
  });

  test('should parse JSONL messages correctly', async () => {
    await monitor.start();

    const newMessagePromise = new Promise<any>((resolve) => {
      monitor.once('newMessage', resolve);
    });

    // Create test file with JSONL content
    const testFile = path.join(testDir, 'test-project', 'session-456.jsonl');
    await mkdir(path.dirname(testFile), { recursive: true });

    const testMessage = {
      parentUuid: 'parent-123',
      sessionId: 'session-456',
      type: 'user',
      message: {
        role: 'user',
        content: 'Hello Claude!'
      },
      timestamp: new Date().toISOString(),
      cwd: '/test/path'
    };

    await writeFile(testFile, JSON.stringify(testMessage) + '\n');

    const message = await newMessagePromise;

    expect(message.sessionId).toBe('session-456');
    expect(message.type).toBe('user');
    expect(message.message.content).toBe('Hello Claude!');
  });

  test('should handle file changes', async () => {
    await monitor.start();

    // Create initial file
    const testFile = path.join(testDir, 'test-project', 'session-789.jsonl');
    await mkdir(path.dirname(testFile), { recursive: true });
    await writeFile(testFile, '');

    // Wait for initial detection
    await new Promise<void>((resolve) => {
      monitor.once('sessionDiscovered', resolve);
    });

    const newMessagePromise = new Promise<any>((resolve) => {
      monitor.once('newMessage', resolve);
    });

    // Append new content
    const testMessage = {
      sessionId: 'session-789',
      type: 'assistant',
      message: {
        role: 'assistant',
        content: 'Hi there!'
      },
      timestamp: new Date().toISOString()
    };

    await writeFile(testFile, JSON.stringify(testMessage) + '\n', { flag: 'a' });

    const message = await newMessagePromise;
    expect(message.sessionId).toBe('session-789');
    expect(message.type).toBe('assistant');
  });
});

describe('JSONLParser', () => {
  let parser: JSONLParser;

  beforeEach(() => {
    parser = new JSONLParser();
  });

  test('should parse valid JSONL lines', () => {
    const testLine = JSON.stringify({
      sessionId: 'test-session',
      type: 'user',
      message: {
        role: 'user',
        content: 'Test message'
      },
      timestamp: '2025-09-14T12:00:00Z',
      parentUuid: 'parent-123',
      cwd: '/test'
    });

    const result = parser.parseLine(testLine);

    expect(result).not.toBeNull();
    expect(result?.sessionId).toBe('test-session');
    expect(result?.type).toBe('user');
    expect(result?.message.content).toBe('Test message');
  });

  test('should handle malformed JSON gracefully', () => {
    const invalidLine = '{ invalid json }';
    const result = parser.parseLine(invalidLine);
    expect(result).toBeNull();
  });

  test('should parse chunks with multiple lines', () => {
    const line1 = JSON.stringify({
      sessionId: 'test',
      type: 'user',
      message: { role: 'user', content: 'Message 1' },
      timestamp: '2025-09-14T12:00:00Z'
    });

    const line2 = JSON.stringify({
      sessionId: 'test',
      type: 'assistant',
      message: { role: 'assistant', content: 'Message 2' },
      timestamp: '2025-09-14T12:01:00Z'
    });

    const chunk = line1 + '\n' + line2 + '\n';
    const results = parser.parseChunk(chunk);

    expect(results).toHaveLength(2);
    expect(results[0].message.content).toBe('Message 1');
    expect(results[1].message.content).toBe('Message 2');
  });

  test('should determine session state correctly', () => {
    const userMessage = {
      sessionId: 'test',
      type: 'user' as const,
      message: { role: 'user' as const, content: 'Question' },
      timestamp: new Date().toISOString(),
      parentUuid: '',
      cwd: ''
    };

    const assistantMessage = {
      sessionId: 'test',
      type: 'assistant' as const,
      message: { role: 'assistant' as const, content: 'Answer' },
      timestamp: new Date().toISOString(),
      parentUuid: '',
      cwd: ''
    };

    // User message last = Claude working
    expect(JSONLParser.determineSessionState([userMessage])).toBe('working');

    // Assistant message last = waiting for user
    expect(JSONLParser.determineSessionState([assistantMessage])).toBe('waiting');

    // No messages = idle
    expect(JSONLParser.determineSessionState([])).toBe('idle');
  });
});