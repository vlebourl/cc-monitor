import * as chokidar from 'chokidar';
import { EventEmitter } from 'events';
import { readFile, stat, access } from 'fs/promises';
import { createReadStream } from 'fs';
import { createInterface } from 'readline';
import path from 'path';
import os from 'os';
import { ClaudeCodeMessage } from '../types/ClaudeCodeTypes';

export interface FileMonitorOptions {
  claudeProjectsPath?: string | undefined;
  pollInterval?: number | undefined;
  usePolling?: boolean | undefined;
}

export class FileStreamMonitor extends EventEmitter {
  private watcher: chokidar.FSWatcher | null = null;
  private filePositions = new Map<string, number>();
  private lastActivity = new Map<string, Date>();
  private inactivityTimer?: NodeJS.Timeout | undefined;
  private options: {
    claudeProjectsPath: string;
    pollInterval: number;
    usePolling: boolean;
  };

  constructor(options: FileMonitorOptions = {}) {
    super();

    this.options = {
      claudeProjectsPath: options.claudeProjectsPath || path.join(os.homedir(), '.claude', 'projects'),
      pollInterval: options.pollInterval || 1000,
      usePolling: options.usePolling || false
    };
  }

  async start(): Promise<void> {
    try {
      // Check if Claude projects directory exists
      await access(this.options.claudeProjectsPath);
    } catch {
      throw new Error(`Claude projects directory not found: ${this.options.claudeProjectsPath}`);
    }

    const pattern = path.join(this.options.claudeProjectsPath, '**/*.jsonl');

    const watchOptions: any = {
      ignored: /(^|[\/\\])\\../, // ignore dotfiles
      persistent: true,
      ignoreInitial: false
    };

    if (this.options.usePolling !== undefined) {
      watchOptions.usePolling = this.options.usePolling;
    }

    if (this.options.pollInterval !== undefined) {
      watchOptions.interval = this.options.pollInterval;
    }

    this.watcher = chokidar.watch(pattern, watchOptions);

    this.watcher
      .on('add', this.handleFileAdded.bind(this))
      .on('change', this.handleFileChanged.bind(this))
      .on('unlink', this.handleFileRemoved.bind(this))
      .on('error', this.handleError.bind(this));

    // Start inactivity monitoring
    this.startInactivityMonitoring();

    this.emit('started');
  }

  async stop(): Promise<void> {
    if (this.watcher) {
      await this.watcher.close();
      this.watcher = null;
    }

    if (this.inactivityTimer) {
      clearInterval(this.inactivityTimer);
      this.inactivityTimer = undefined;
    }

    this.filePositions.clear();
    this.lastActivity.clear();
    this.emit('stopped');
  }

  private async handleFileAdded(filePath: string): Promise<void> {
    try {
      const stats = await stat(filePath);
      this.filePositions.set(filePath, stats.size);

      const sessionInfo = this.extractSessionInfo(filePath);
      this.emit('sessionDiscovered', { filePath, sessionInfo });

      // Read existing content if file is not empty
      if (stats.size > 0) {
        await this.readExistingContent(filePath);
      }
    } catch (error) {
      this.emit('error', error);
    }
  }

  private async handleFileChanged(filePath: string): Promise<void> {
    try {
      const currentPosition = this.filePositions.get(filePath) || 0;
      const stats = await stat(filePath);

      // Update last activity
      this.lastActivity.set(filePath, new Date());

      // Handle file truncation (session restart)
      if (stats.size < currentPosition) {
        this.filePositions.set(filePath, 0);
        await this.readNewContent(filePath, 0);
        return;
      }

      // Read new content from last position
      if (stats.size > currentPosition) {
        await this.readNewContent(filePath, currentPosition);
        this.filePositions.set(filePath, stats.size);
      }
    } catch (error) {
      this.emit('error', error);
    }
  }

  private handleFileRemoved(filePath: string): void {
    this.filePositions.delete(filePath);
    this.lastActivity.delete(filePath);
    const sessionInfo = this.extractSessionInfo(filePath);
    this.emit('sessionTerminated', {
      filePath,
      sessionInfo,
      reason: 'file_removed',
      timestamp: new Date().toISOString()
    });
  }

  private handleError(error: unknown): void {
    this.emit('error', error);
  }

  private async readExistingContent(filePath: string): Promise<void> {
    const fileStream = createReadStream(filePath, { encoding: 'utf8' });
    const rl = createInterface({
      input: fileStream,
      crlfDelay: Infinity
    });

    for await (const line of rl) {
      if (line.trim()) {
        try {
          const message = this.parseJSONLLine(line, filePath);
          if (message) {
            this.emit('existingMessage', message);
          }
        } catch (error) {
          this.emit('parseError', { error, line, filePath });
        }
      }
    }
  }

  private async readNewContent(filePath: string, startPosition: number): Promise<void> {
    const fileStream = createReadStream(filePath, {
      encoding: 'utf8',
      start: startPosition
    });

    const rl = createInterface({
      input: fileStream,
      crlfDelay: Infinity
    });

    for await (const line of rl) {
      if (line.trim()) {
        try {
          const message = this.parseJSONLLine(line, filePath);
          if (message) {
            this.emit('newMessage', message);
          }
        } catch (error) {
          this.emit('parseError', { error, line, filePath });
        }
      }
    }
  }

  private parseJSONLLine(line: string, filePath: string): ClaudeCodeMessage | null {
    try {
      const parsed = JSON.parse(line);

      // Validate required fields
      if (!parsed.sessionId || !parsed.type || !parsed.message || !parsed.timestamp) {
        return null;
      }

      return {
        parentUuid: parsed.parentUuid || '',
        sessionId: parsed.sessionId,
        type: parsed.type,
        message: parsed.message,
        timestamp: parsed.timestamp,
        cwd: parsed.cwd || ''
      };
    } catch {
      return null;
    }
  }

  private extractSessionInfo(filePath: string): { sessionId: string; projectPath: string } {
    const fileName = path.basename(filePath, '.jsonl');
    const projectPath = path.dirname(path.relative(this.options.claudeProjectsPath, filePath));

    return {
      sessionId: fileName,
      projectPath: projectPath
    };
  }

  private startInactivityMonitoring(): void {
    this.inactivityTimer = setInterval(() => {
      const now = new Date();
      const inactiveThreshold = 10 * 60 * 1000; // 10 minutes

      for (const [filePath, lastActivity] of this.lastActivity.entries()) {
        const timeSinceActivity = now.getTime() - lastActivity.getTime();

        if (timeSinceActivity > inactiveThreshold) {
          const sessionInfo = this.extractSessionInfo(filePath);
          this.emit('sessionInactive', {
            filePath,
            sessionInfo,
            reason: 'inactivity_timeout',
            lastActivity: lastActivity.toISOString(),
            timestamp: new Date().toISOString()
          });

          // Remove from tracking to avoid repeated notifications
          this.lastActivity.delete(filePath);
        }
      }
    }, 60000); // Check every minute
  }

  getMonitoredFiles(): string[] {
    return Array.from(this.filePositions.keys());
  }

  isMonitoring(): boolean {
    return this.watcher !== null;
  }

  getSessionActivity(): Map<string, Date> {
    return new Map(this.lastActivity);
  }

  forceSessionCleanup(sessionId: string): void {
    const filesToCleanup: string[] = [];

    for (const filePath of this.filePositions.keys()) {
      const sessionInfo = this.extractSessionInfo(filePath);
      if (sessionInfo.sessionId === sessionId) {
        filesToCleanup.push(filePath);
      }
    }

    for (const filePath of filesToCleanup) {
      this.filePositions.delete(filePath);
      this.lastActivity.delete(filePath);

      const sessionInfo = this.extractSessionInfo(filePath);
      this.emit('sessionTerminated', {
        filePath,
        sessionInfo,
        reason: 'forced_cleanup',
        timestamp: new Date().toISOString()
      });
    }
  }
}