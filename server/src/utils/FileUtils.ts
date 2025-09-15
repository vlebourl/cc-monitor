import { access, stat, readFile } from 'fs/promises';
import { createReadStream } from 'fs';
import { createInterface } from 'readline';
import path from 'path';

export class FileUtils {
  /**
   * Check if a file exists and is accessible
   */
  static async fileExists(filePath: string): Promise<boolean> {
    try {
      await access(filePath);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Get file size safely
   */
  static async getFileSize(filePath: string): Promise<number> {
    try {
      const stats = await stat(filePath);
      return stats.size;
    } catch {
      return 0;
    }
  }

  /**
   * Get file modification time
   */
  static async getModifiedTime(filePath: string): Promise<Date | null> {
    try {
      const stats = await stat(filePath);
      return stats.mtime;
    } catch {
      return null;
    }
  }

  /**
   * Read file lines with optional offset and limit
   */
  static async readLines(
    filePath: string,
    options: {
      start?: number;
      end?: number;
      maxLines?: number;
    } = {}
  ): Promise<string[]> {
    const lines: string[] = [];

    const fileStream = createReadStream(filePath, {
      encoding: 'utf8',
      start: options.start,
      end: options.end
    });

    const rl = createInterface({
      input: fileStream,
      crlfDelay: Infinity
    });

    let lineCount = 0;
    const maxLines = options.maxLines || Infinity;

    for await (const line of rl) {
      if (lineCount >= maxLines) {
        break;
      }
      lines.push(line);
      lineCount++;
    }

    return lines;
  }

  /**
   * Tail a file from a specific position
   */
  static async tailFrom(filePath: string, startPosition: number): Promise<string[]> {
    const lines: string[] = [];

    try {
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
          lines.push(line);
        }
      }
    } catch (error) {
      throw new Error(`Failed to tail file from position ${startPosition}: ${error}`);
    }

    return lines;
  }

  /**
   * Extract project name from file path
   */
  static extractProjectName(filePath: string, claudeProjectsPath: string): string {
    const relativePath = path.relative(claudeProjectsPath, filePath);
    const pathParts = relativePath.split(path.sep);

    // Return the first directory name as project name
    return pathParts[0] || path.basename(filePath, '.jsonl');
  }

  /**
   * Extract session ID from JSONL filename
   */
  static extractSessionId(filePath: string): string {
    return path.basename(filePath, '.jsonl');
  }

  /**
   * Check if a file has been recently modified
   */
  static async isRecentlyModified(filePath: string, thresholdMinutes: number = 5): Promise<boolean> {
    const modTime = await this.getModifiedTime(filePath);
    if (!modTime) return false;

    const now = new Date();
    const diffMinutes = (now.getTime() - modTime.getTime()) / (1000 * 60);
    return diffMinutes <= thresholdMinutes;
  }

  /**
   * Safe JSON parse with error handling
   */
  static safeJsonParse<T>(jsonString: string, defaultValue: T | null = null): T | null {
    try {
      return JSON.parse(jsonString) as T;
    } catch {
      return defaultValue;
    }
  }

  /**
   * Normalize file path for cross-platform compatibility
   */
  static normalizePath(filePath: string): string {
    return path.normalize(filePath).replace(/\\/g, '/');
  }

  /**
   * Get relative path with consistent separators
   */
  static getRelativePath(from: string, to: string): string {
    return this.normalizePath(path.relative(from, to));
  }

  /**
   * Validate that a path is within the allowed directory
   */
  static isPathAllowed(filePath: string, allowedBasePath: string): boolean {
    const normalizedFilePath = path.resolve(filePath);
    const normalizedBasePath = path.resolve(allowedBasePath);

    return normalizedFilePath.startsWith(normalizedBasePath);
  }
}