import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from 'jose';
import { DraftError, Reason } from '../../functions/src/errors.js';

// These caches contain Google's public signing keys only, never users, tokens or request state.
const authKeys = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'), { timeoutDuration: 3000 });
const appKeys = createRemoteJWKSet(new URL('https://firebaseappcheck.googleapis.com/v1/jwks'), { timeoutDuration: 3000 });

export async function verifyUser(token: string, projectId: string, keys: JWTVerifyGetKey = authKeys): Promise<string> {
  try {
    const { payload } = await jwtVerify(token, keys, {
      algorithms: ['RS256'], issuer: `https://securetoken.google.com/${projectId}`, audience: projectId,
      requiredClaims: ['exp', 'iat', 'sub', 'auth_time'],
    });
    const now = Math.floor(Date.now() / 1000);
    if (!payload.sub || payload.sub.length > 128 || typeof payload.iat !== 'number' || payload.iat > now ||
        typeof payload.auth_time !== 'number' || payload.auth_time > now) throw new Error('INVALID_CLAIMS');
    return payload.sub;
  } catch { throw new DraftError(Reason.UNAUTHENTICATED); }
}

export async function verifyApp(token: string, projectNumber: string, appId: string, keys: JWTVerifyGetKey = appKeys): Promise<void> {
  try {
    const { payload } = await jwtVerify(token, keys, {
      algorithms: ['RS256'], issuer: `https://firebaseappcheck.googleapis.com/${projectNumber}`,
      audience: `projects/${projectNumber}`, subject: appId, requiredClaims: ['exp', 'iat', 'sub'],
    });
    if (typeof payload.iat !== 'number' || payload.iat > Math.floor(Date.now() / 1000)) throw new Error('INVALID_CLAIMS');
  } catch { throw new DraftError(Reason.APP_CHECK); }
}
