import { Router, Request, Response } from 'express';
import { AuthenticationService } from '../services/AuthenticationService';

export function createAuthRoutes(authService: AuthenticationService): Router {
  const router = Router();

  /**
   * Generate QR code for mobile authentication
   * POST /api/auth/qr
   */
  router.post('/qr', async (req: Request, res: Response) => {
    try {
      const result = await authService.generateAuthQR();

      res.json({
        success: true,
        data: {
          qrCode: result.qrCode,
          guestToken: result.guestToken,
          expiresIn: 30, // seconds
          instructions: 'Scan this QR code with your Android app within 30 seconds'
        },
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      res.status(500).json({
        success: false,
        error: 'Failed to generate QR code',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  });

  /**
   * Authenticate mobile device with guest token
   * POST /api/auth/mobile
   */
  router.post('/mobile', async (req: Request, res: Response) => {
    try {
      const { guestToken, deviceId } = req.body;

      if (!guestToken || !deviceId) {
        return res.status(400).json({
          success: false,
          error: 'Missing required fields',
          message: 'guestToken and deviceId are required',
          timestamp: new Date().toISOString()
        });
      }

      const result = await authService.authenticateMobile(guestToken, deviceId);

      return res.json({
        success: true,
        data: {
          apiKey: result.apiKey,
          serverInfo: result.serverInfo,
          message: 'Device authenticated successfully'
        },
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      const statusCode = error instanceof Error && error.message.includes('Invalid') ? 401 :
                        error instanceof Error && error.message.includes('expired') ? 410 :
                        error instanceof Error && error.message.includes('used') ? 409 : 500;

      return res.status(statusCode).json({
        success: false,
        error: 'Authentication failed',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  });

  /**
   * Refresh API key
   * POST /api/auth/refresh
   */
  router.post('/refresh', (req: Request, res: Response) => {
    try {
      const apiKey = req.headers.authorization?.replace('Bearer ', '');

      if (!apiKey) {
        return res.status(401).json({
          success: false,
          error: 'Missing API key',
          message: 'Authorization header with Bearer token required',
          timestamp: new Date().toISOString()
        });
      }

      const result = authService.refreshApiKey(apiKey);

      if (!result) {
        return res.status(401).json({
          success: false,
          error: 'Invalid or expired API key',
          message: 'Cannot refresh the provided API key',
          timestamp: new Date().toISOString()
        });
      }

      return res.json({
        success: true,
        data: {
          apiKey: result.apiKey,
          expiresAt: result.expiresAt,
          message: 'API key refreshed successfully'
        },
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      return res.status(500).json({
        success: false,
        error: 'Failed to refresh API key',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  });

  /**
   * Revoke API key
   * POST /api/auth/revoke
   */
  router.post('/revoke', (req: Request, res: Response) => {
    try {
      const apiKey = req.headers.authorization?.replace('Bearer ', '');

      if (!apiKey) {
        return res.status(401).json({
          success: false,
          error: 'Missing API key',
          message: 'Authorization header with Bearer token required',
          timestamp: new Date().toISOString()
        });
      }

      const revoked = authService.revokeApiKey(apiKey);

      if (!revoked) {
        return res.status(404).json({
          success: false,
          error: 'API key not found',
          message: 'The provided API key does not exist',
          timestamp: new Date().toISOString()
        });
      }

      return res.json({
        success: true,
        data: {
          message: 'API key revoked successfully'
        },
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      return res.status(500).json({
        success: false,
        error: 'Failed to revoke API key',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  });

  /**
   * Get API key information
   * GET /api/auth/info
   */
  router.get('/info', (req: Request, res: Response) => {
    try {
      const apiKey = req.headers.authorization?.replace('Bearer ', '');

      if (!apiKey) {
        return res.status(401).json({
          success: false,
          error: 'Missing API key',
          message: 'Authorization header with Bearer token required',
          timestamp: new Date().toISOString()
        });
      }

      const info = authService.getApiKeyInfo(apiKey);

      if (!info) {
        return res.status(404).json({
          success: false,
          error: 'API key not found',
          message: 'The provided API key does not exist',
          timestamp: new Date().toISOString()
        });
      }

      return res.json({
        success: true,
        data: {
          deviceId: info.deviceId,
          createdAt: info.createdAt,
          expiresAt: info.expiresAt,
          lastUsed: info.lastUsed,
          revoked: info.revoked,
          valid: authService.validateApiKey(apiKey)
        },
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      return res.status(500).json({
        success: false,
        error: 'Failed to get API key info',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  });

  /**
   * Get authentication statistics (admin endpoint)
   * GET /api/auth/stats
   */
  router.get('/stats', (req: Request, res: Response) => {
    try {
      const stats = authService.getStats();
      const activeKeys = authService.getActiveApiKeys();

      return res.json({
        success: true,
        data: {
          ...stats,
          activeKeys: activeKeys.map(key => ({
            keyPrefix: key.keyPrefix,
            deviceId: key.deviceId,
            createdAt: key.createdAt,
            lastUsed: key.lastUsed
          }))
        },
        timestamp: new Date().toISOString()
      });

    } catch (error) {
      return res.status(500).json({
        success: false,
        error: 'Failed to get auth stats',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  });

  /**
   * Simple authentication endpoint for QR code scanning
   * GET /auth?token=<guestToken>
   */
  router.get('/', (req: Request, res: Response) => {
    const { token } = req.query;

    if (!token || typeof token !== 'string') {
      return res.status(400).json({
        success: false,
        error: 'Missing token parameter',
        message: 'Token parameter is required',
        timestamp: new Date().toISOString()
      });
    }

    // Return a simple page that mobile apps can parse
    return res.json({
      success: true,
      data: {
        guestToken: token,
        message: 'Use this token to authenticate your mobile device',
        instructions: 'Send this token along with your device ID to /api/auth/mobile'
      },
      timestamp: new Date().toISOString()
    });
  });

  return router;
}