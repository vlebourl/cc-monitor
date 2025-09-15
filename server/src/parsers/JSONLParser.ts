import { ClaudeCodeMessage } from '../types/ClaudeCodeTypes';

export class JSONLParser {
  private lineBuffer = '';

  /**
   * Parse incremental JSONL data chunk
   */
  parseChunk(chunk: string): ClaudeCodeMessage[] {
    this.lineBuffer += chunk;
    const lines = this.lineBuffer.split('\n');

    // Keep the last incomplete line in buffer
    this.lineBuffer = lines.pop() || '';

    const messages: ClaudeCodeMessage[] = [];

    for (const line of lines) {
      if (line.trim()) {
        try {
          const message = this.parseLine(line);
          if (message) {
            messages.push(message);
          }
        } catch (error) {
          // Skip malformed lines but emit error
          console.warn('Failed to parse JSONL line:', error);
        }
      }
    }

    return messages;
  }

  /**
   * Parse a single JSONL line
   */
  parseLine(line: string): ClaudeCodeMessage | null {
    try {
      const parsed = JSON.parse(line.trim());

      // Validate structure
      if (!this.isValidClaudeCodeMessage(parsed)) {
        return null;
      }

      return {
        parentUuid: parsed.parentUuid || '',
        sessionId: parsed.sessionId,
        type: parsed.type,
        message: {
          role: parsed.message.role,
          content: parsed.message.content
        },
        timestamp: parsed.timestamp,
        cwd: parsed.cwd || ''
      };
    } catch {
      return null;
    }
  }

  /**
   * Parse multiple lines at once
   */
  parseLines(lines: string[]): ClaudeCodeMessage[] {
    const messages: ClaudeCodeMessage[] = [];

    for (const line of lines) {
      if (line.trim()) {
        const message = this.parseLine(line);
        if (message) {
          messages.push(message);
        }
      }
    }

    return messages;
  }

  /**
   * Validate that an object matches Claude Code message format
   */
  private isValidClaudeCodeMessage(obj: any): boolean {
    return (
      obj &&
      typeof obj.sessionId === 'string' &&
      (obj.type === 'user' || obj.type === 'assistant') &&
      obj.message &&
      typeof obj.message.role === 'string' &&
      typeof obj.message.content === 'string' &&
      typeof obj.timestamp === 'string'
    );
  }

  /**
   * Get any remaining buffered content
   */
  flush(): ClaudeCodeMessage[] {
    if (this.lineBuffer.trim()) {
      const messages = [this.parseLine(this.lineBuffer)].filter(Boolean) as ClaudeCodeMessage[];
      this.lineBuffer = '';
      return messages;
    }
    return [];
  }

  /**
   * Clear the internal buffer
   */
  reset(): void {
    this.lineBuffer = '';
  }

  /**
   * Extract session metadata from messages
   */
  static extractSessionMetadata(messages: ClaudeCodeMessage[]) {
    if (messages.length === 0) {
      return null;
    }

    const firstMessage = messages[0]!;
    const lastMessage = messages[messages.length - 1]!;

    return {
      sessionId: firstMessage.sessionId,
      projectPath: firstMessage.cwd,
      messageCount: messages.length,
      firstActivity: firstMessage.timestamp,
      lastActivity: lastMessage.timestamp,
      userMessages: messages.filter(m => m.type === 'user').length,
      assistantMessages: messages.filter(m => m.type === 'assistant').length
    };
  }

  /**
   * Determine session state based on message flow
   */
  static determineSessionState(messages: ClaudeCodeMessage[]): 'working' | 'waiting' | 'idle' {
    if (messages.length === 0) {
      return 'idle';
    }

    const lastMessage = messages[messages.length - 1]!;
    const lastTimestamp = new Date(lastMessage.timestamp);
    const now = new Date();
    const minutesSinceLastActivity = (now.getTime() - lastTimestamp.getTime()) / (1000 * 60);

    // If no activity for more than 5 minutes, consider idle
    if (minutesSinceLastActivity > 5) {
      return 'idle';
    }

    // If last message was from user, Claude should be working
    if (lastMessage.type === 'user') {
      return 'working';
    }

    // If last message was from assistant, waiting for user input
    return 'waiting';
  }
}