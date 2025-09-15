import { Request, Response, NextFunction } from 'express';
import { AuthenticationService } from '../services/AuthenticationService';

// Extend Request interface to include auth info
declare global {
  namespace Express {
    interface Request {
      apiKey?: string;
      deviceId?: string | undefined;
      authenticated?: boolean;
    }
  }
}

export interface AuthMiddlewareOptions {
  required?: boolean;
  headerName?: string;
}

/**
 * Create authentication middleware
 */
export function createAuthMiddleware(
  authService: AuthenticationService,
  options: AuthMiddlewareOptions = {}
): (req: Request, res: Response, next: NextFunction) => void {
  const {
    required = true,
    headerName = 'authorization'
  } = options;

  return (req: Request, res: Response, next: NextFunction) => {
    try {
      // Extract API key from Authorization header
      const authHeader = req.headers[headerName.toLowerCase()];
      let apiKey: string | undefined;

      if (typeof authHeader === 'string') {
        // Support both "Bearer <token>" and just "<token>" formats
        apiKey = authHeader.startsWith('Bearer ')
          ? authHeader.replace('Bearer ', '')
          : authHeader;
      } else if (Array.isArray(authHeader)) {
        // Handle case where header is an array
        apiKey = authHeader[0]?.startsWith('Bearer ')
          ? authHeader[0].replace('Bearer ', '')
          : authHeader[0];
      }

      // Check if API key is provided
      if (!apiKey) {
        if (required) {
          return res.status(401).json({
            success: false,
            error: 'Authentication required',
            message: `Missing ${headerName} header with API key`,
            timestamp: new Date().toISOString()
          });
        } else {
          // Optional auth - continue without authentication
          req.authenticated = false;
          return next();
        }
      }

      // Validate API key
      const isValid = authService.validateApiKey(apiKey);

      if (!isValid) {
        return res.status(401).json({
          success: false,
          error: 'Invalid API key',
          message: 'The provided API key is invalid, expired, or revoked',
          timestamp: new Date().toISOString()
        });
      }

      // Get API key info to extract device ID
      const keyInfo = authService.getApiKeyInfo(apiKey);

      // Attach auth info to request
      req.apiKey = apiKey;
      if (keyInfo?.deviceId) {
        req.deviceId = keyInfo.deviceId;
      }
      req.authenticated = true;

      next();

    } catch (error) {
      res.status(500).json({
        success: false,
        error: 'Authentication error',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  };
}

/**
 * Middleware to require authentication
 */
export function requireAuth(authService: AuthenticationService) {
  return createAuthMiddleware(authService, { required: true });
}

/**
 * Middleware for optional authentication
 */
export function optionalAuth(authService: AuthenticationService) {
  return createAuthMiddleware(authService, { required: false });
}

/**
 * Middleware to extract API key from query parameter (for WebSocket upgrade)
 */
export function extractApiKeyFromQuery(
  authService: AuthenticationService
): (req: Request, res: Response, next: NextFunction) => void {
  return (req: Request, res: Response, next: NextFunction) => {
    try {
      // Check for API key in query parameter (common for WebSocket connections)
      const apiKey = req.query.apiKey as string;

      if (!apiKey) {
        return res.status(401).json({
          success: false,
          error: 'Authentication required',
          message: 'Missing apiKey query parameter',
          timestamp: new Date().toISOString()
        });
      }

      // Validate API key
      const isValid = authService.validateApiKey(apiKey);

      if (!isValid) {
        return res.status(401).json({
          success: false,
          error: 'Invalid API key',
          message: 'The provided API key is invalid, expired, or revoked',
          timestamp: new Date().toISOString()
        });
      }

      // Get API key info
      const keyInfo = authService.getApiKeyInfo(apiKey);

      // Attach auth info to request
      req.apiKey = apiKey;
      if (keyInfo?.deviceId) {
        req.deviceId = keyInfo.deviceId;
      }
      req.authenticated = true;

      return next();

    } catch (error) {
      return res.status(500).json({
        success: false,
        error: 'Authentication error',
        message: error instanceof Error ? error.message : 'Unknown error',
        timestamp: new Date().toISOString()
      });
    }
  };
}

/**
 * Middleware to validate API key for WebSocket upgrades
 */
export function validateWebSocketAuth(authService: AuthenticationService) {
  return (apiKey: string): { valid: boolean; deviceId?: string; error?: string } => {
    try {
      if (!apiKey) {
        return { valid: false, error: 'Missing API key' };
      }

      const isValid = authService.validateApiKey(apiKey);

      if (!isValid) {
        return { valid: false, error: 'Invalid, expired, or revoked API key' };
      }

      const keyInfo = authService.getApiKeyInfo(apiKey);

      const result: { valid: boolean; deviceId?: string; error?: string } = {
        valid: true
      };

      if (keyInfo?.deviceId) {
        result.deviceId = keyInfo.deviceId;
      }

      return result;

    } catch (error) {
      return {
        valid: false,
        error: error instanceof Error ? error.message : 'Authentication error'
      };
    }
  };
}