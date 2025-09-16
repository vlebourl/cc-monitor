import { EventEmitter } from 'events';
import * as crypto from 'crypto';
import QRCode from 'qrcode';

// Load package.json for version info
const packageJson = require('../../package.json');

export interface GuestToken {
  token: string;
  expires: Date;
  deviceId?: string;
  used: boolean;
  createdAt: Date;
}

export interface ApiKey {
  key: string;
  deviceId: string;
  createdAt: Date;
  expiresAt: Date;
  lastUsed?: Date | undefined;
  revoked: boolean;
}

export interface AuthenticationOptions {
  qrTokenTTL?: number; // QR token time-to-live in milliseconds
  apiKeyTTL?: number; // API key time-to-live in milliseconds
  baseUrl?: string; // Base URL for QR code generation
  cleanupInterval?: number; // Cleanup interval for expired tokens
}

export class AuthenticationService extends EventEmitter {
  private guestTokens = new Map<string, GuestToken>();
  private apiKeys = new Map<string, ApiKey>();
  private cleanupTimer?: NodeJS.Timeout | undefined;
  private options: Required<AuthenticationOptions>;

  constructor(options: AuthenticationOptions = {}) {
    super();

    this.options = {
      qrTokenTTL: options.qrTokenTTL || 30 * 1000, // 30 seconds
      apiKeyTTL: options.apiKeyTTL || 30 * 24 * 60 * 60 * 1000, // 30 days
      baseUrl: options.baseUrl || 'http://localhost:3000',
      cleanupInterval: options.cleanupInterval || 60 * 1000 // 1 minute
    };

    this.startCleanupTimer();
  }

  /**
   * Generate QR code with guest token for mobile authentication
   */
  async generateAuthQR(): Promise<{ qrCode: string; qrDataURL: string; guestToken: string }> {
    const guestToken = this.generateGuestToken();
    const authUrl = `${this.options.baseUrl}/auth?token=${guestToken.token}`;

    console.log(`🔑 [AUTH] Generated new guest token: ${guestToken.token}`);
    console.log(`🔗 [AUTH] Auth URL: ${authUrl}`);
    console.log(`⏰ [AUTH] Token expires at: ${guestToken.expires.toISOString()}`);

    try {
      // Generate QR code as SVG string
      const qrCodeSvg = await QRCode.toString(authUrl, {
        type: 'svg',
        errorCorrectionLevel: 'M',
        width: 256,
        margin: 2,
        color: {
          dark: '#000000',
          light: '#ffffff'
        }
      });

      // Generate QR code as data URL for easier client display
      const qrDataURL = await QRCode.toDataURL(authUrl, {
        errorCorrectionLevel: 'M',
        width: 280,
        margin: 2,
        color: {
          dark: '#2c3e50',
          light: '#ffffff'
        }
      });

      this.guestTokens.set(guestToken.token, guestToken);

      this.emit('qrGenerated', {
        guestToken: guestToken.token,
        expiresIn: this.options.qrTokenTTL
      });

      return {
        qrCode: qrCodeSvg,
        qrDataURL: qrDataURL,
        guestToken: guestToken.token
      };

    } catch (error) {
      this.emit('error', { type: 'qr_generation_failed', error });
      throw new Error(`Failed to generate QR code: ${error}`);
    }
  }

  /**
   * Authenticate mobile device using guest token
   */
  async authenticateMobile(guestToken: string, deviceId: string): Promise<{ apiKey: string; serverInfo: object }> {
    console.log(`🔐 [AUTH] Authentication attempt - Token: ${guestToken}, Device: ${deviceId}`);
    console.log(`🗂️  [AUTH] Available guest tokens: ${Array.from(this.guestTokens.keys()).join(', ')}`);

    const token = this.guestTokens.get(guestToken);

    if (!token) {
      console.log(`❌ [AUTH] Invalid guest token: ${guestToken}`);
      this.emit('authenticationFailed', { reason: 'invalid_token', guestToken });
      throw new Error('Invalid guest token');
    }

    if (token.used) {
      console.log(`❌ [AUTH] Guest token already used: ${guestToken}`);
      this.emit('authenticationFailed', { reason: 'token_already_used', guestToken });
      throw new Error('Guest token already used');
    }

    if (new Date() > token.expires) {
      console.log(`❌ [AUTH] Guest token expired: ${guestToken} (expired: ${token.expires.toISOString()})`);
      this.guestTokens.delete(guestToken);
      this.emit('authenticationFailed', { reason: 'token_expired', guestToken });
      throw new Error('Guest token expired');
    }

    // Mark token as used
    token.used = true;
    token.deviceId = deviceId;

    // Generate API key
    const apiKey = this.generateApiKey(deviceId);
    this.apiKeys.set(apiKey.key, apiKey);

    // Clean up used guest token
    this.guestTokens.delete(guestToken);

    const serverInfo = {
      version: packageJson.version,
      features: ['session_monitoring', 'real_time_streaming', 'state_detection'],
      protocol: 'Claude Code Monitor Protocol v1.0',
      websocketUrl: this.options.baseUrl.replace('http', 'ws')
    };

    console.log(`✅ [AUTH] Authentication successful - Generated API key: ${apiKey.key.substring(0, 8)}...`);
    console.log(`📡 [AUTH] WebSocket URL: ${serverInfo.websocketUrl}`);
    console.log(`📱 [AUTH] Device registered: ${deviceId}`);

    this.emit('deviceAuthenticated', {
      deviceId,
      apiKey: apiKey.key,
      expiresAt: apiKey.expiresAt
    });

    return {
      apiKey: apiKey.key,
      serverInfo
    };
  }

  /**
   * Validate API key for WebSocket connections
   */
  validateApiKey(apiKey: string): boolean {
    console.log(`🔍 [AUTH] Validating API key: ${apiKey ? apiKey.substring(0, 8) + '...' : 'null'}`);

    const key = this.apiKeys.get(apiKey);

    if (!key) {
      console.log(`❌ [AUTH] API key not found: ${apiKey ? apiKey.substring(0, 8) + '...' : 'null'}`);
      console.log(`📋 [AUTH] Available API keys: ${Array.from(this.apiKeys.keys()).map(k => k.substring(0, 8) + '...').join(', ')}`);
      return false;
    }

    if (key.revoked) {
      console.log(`❌ [AUTH] API key revoked: ${apiKey.substring(0, 8)}...`);
      return false;
    }

    if (new Date() > key.expiresAt) {
      console.log(`❌ [AUTH] API key expired: ${apiKey.substring(0, 8)}... (expired: ${key.expiresAt.toISOString()})`);
      this.revokeApiKey(apiKey);
      return false;
    }

    // Update last used timestamp
    key.lastUsed = new Date();

    console.log(`✅ [AUTH] API key validated successfully: ${apiKey.substring(0, 8)}...`);
    return true;
  }

  /**
   * Refresh API key (extend expiration)
   */
  refreshApiKey(apiKey: string): { apiKey: string; expiresAt: Date } | null {
    const key = this.apiKeys.get(apiKey);

    if (!key || key.revoked || new Date() > key.expiresAt) {
      return null;
    }

    // Extend expiration
    key.expiresAt = new Date(Date.now() + this.options.apiKeyTTL);
    key.lastUsed = new Date();

    this.emit('apiKeyRefreshed', {
      deviceId: key.deviceId,
      apiKey: key.key,
      expiresAt: key.expiresAt
    });

    return {
      apiKey: key.key,
      expiresAt: key.expiresAt
    };
  }

  /**
   * Revoke API key
   */
  revokeApiKey(apiKey: string): boolean {
    const key = this.apiKeys.get(apiKey);

    if (!key) {
      return false;
    }

    key.revoked = true;

    this.emit('apiKeyRevoked', {
      deviceId: key.deviceId,
      apiKey: key.key,
      revokedAt: new Date()
    });

    return true;
  }

  /**
   * Get API key information
   */
  getApiKeyInfo(apiKey: string): Omit<ApiKey, 'key'> | null {
    const key = this.apiKeys.get(apiKey);

    if (!key) {
      return null;
    }

    const result: Omit<ApiKey, 'key'> = {
      deviceId: key.deviceId,
      createdAt: key.createdAt,
      expiresAt: key.expiresAt,
      revoked: key.revoked
    };

    if (key.lastUsed) {
      result.lastUsed = key.lastUsed;
    }

    return result;
  }

  /**
   * List all active API keys
   */
  getActiveApiKeys(): Array<Omit<ApiKey, 'key'> & { keyPrefix: string }> {
    const activeKeys: Array<Omit<ApiKey, 'key'> & { keyPrefix: string }> = [];

    for (const [key, apiKey] of this.apiKeys.entries()) {
      if (!apiKey.revoked && new Date() <= apiKey.expiresAt) {
        const keyInfo: Omit<ApiKey, 'key'> & { keyPrefix: string } = {
          keyPrefix: key.substring(0, 8) + '...',
          deviceId: apiKey.deviceId,
          createdAt: apiKey.createdAt,
          expiresAt: apiKey.expiresAt,
          revoked: apiKey.revoked
        };

        if (apiKey.lastUsed) {
          keyInfo.lastUsed = apiKey.lastUsed;
        }

        activeKeys.push(keyInfo);
      }
    }

    return activeKeys;
  }

  /**
   * Generate a new guest token
   */
  private generateGuestToken(): GuestToken {
    const token = crypto.randomUUID();
    const now = new Date();
    const expires = new Date(now.getTime() + this.options.qrTokenTTL);

    return {
      token,
      expires,
      used: false,
      createdAt: now
    };
  }

  /**
   * Generate a new API key
   */
  private generateApiKey(deviceId: string): ApiKey {
    const key = crypto.randomBytes(32).toString('hex');
    const now = new Date();
    const expiresAt = new Date(now.getTime() + this.options.apiKeyTTL);

    return {
      key,
      deviceId,
      createdAt: now,
      expiresAt,
      revoked: false
    };
  }

  /**
   * Start cleanup timer for expired tokens and keys
   */
  private startCleanupTimer(): void {
    this.cleanupTimer = setInterval(() => {
      this.cleanupExpiredTokens();
      this.cleanupExpiredApiKeys();
    }, this.options.cleanupInterval);
  }

  /**
   * Clean up expired guest tokens
   */
  private cleanupExpiredTokens(): void {
    const now = new Date();
    let cleanedCount = 0;

    for (const [token, guestToken] of this.guestTokens.entries()) {
      if (now > guestToken.expires) {
        this.guestTokens.delete(token);
        cleanedCount++;
      }
    }

    if (cleanedCount > 0) {
      this.emit('tokensCleanedUp', { count: cleanedCount, type: 'guest_tokens' });
    }
  }

  /**
   * Clean up expired API keys
   */
  private cleanupExpiredApiKeys(): void {
    const now = new Date();
    let cleanedCount = 0;

    for (const [key, apiKey] of this.apiKeys.entries()) {
      if (now > apiKey.expiresAt) {
        this.apiKeys.delete(key);
        cleanedCount++;
      }
    }

    if (cleanedCount > 0) {
      this.emit('tokensCleanedUp', { count: cleanedCount, type: 'api_keys' });
    }
  }

  /**
   * Get statistics about tokens and keys
   */
  getStats() {
    const activeGuestTokens = Array.from(this.guestTokens.values()).filter(
      t => !t.used && new Date() <= t.expires
    ).length;

    const activeApiKeys = Array.from(this.apiKeys.values()).filter(
      k => !k.revoked && new Date() <= k.expiresAt
    ).length;

    return {
      guestTokens: {
        total: this.guestTokens.size,
        active: activeGuestTokens,
        expired: this.guestTokens.size - activeGuestTokens
      },
      apiKeys: {
        total: this.apiKeys.size,
        active: activeApiKeys,
        expired: this.apiKeys.size - activeApiKeys
      }
    };
  }

  /**
   * Shutdown the authentication service
   */
  shutdown(): void {
    if (this.cleanupTimer) {
      clearInterval(this.cleanupTimer);
      this.cleanupTimer = undefined;
    }

    this.guestTokens.clear();
    this.apiKeys.clear();

    this.emit('shutdown');
  }
}