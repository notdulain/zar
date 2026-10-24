import { DraftError, HTTPS_CODE, Reason, SAFE_MESSAGE } from '../../functions/src/errors.js';
import { createDraftHandler } from '../../functions/src/handler.js';
import { validateRequest } from '../../functions/src/validation.js';
import { verifyApp, verifyUser } from './auth';
import { readJson } from './json';
import { createOpenRouterProvider } from './provider';

export class ServiceError extends Error {
  constructor(public code: string, message: string, public details: { reason: string }) { super(message); }
}

const logger = {
  info: (message: string, fields: Record<string, unknown>) => console.info(JSON.stringify({ message, ...fields })),
  warn: (message: string, fields: Record<string, unknown>) => console.warn(JSON.stringify({ message, ...fields })),
};
const headers = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
const statusFor: Record<string, number> = { UNAUTHENTICATED: 401, APP_CHECK: 403, INVALID_INPUT: 400, RATE_LIMITED: 429, TIMEOUT: 504, PROVIDER_UNAVAILABLE: 503, BAD_RESPONSE: 502 };

export function createWorker(deps = { verifyUser, verifyApp, createProvider: createOpenRouterProvider }) {
  return {
    async fetch(request: Request, env: Env): Promise<Response> {
      const path = new URL(request.url).pathname;
      if (path === '/health' && request.method === 'GET') return Response.json({ status: 'ok', version: 1 }, { headers });
      if (path !== '/v1/quick-note') return new Response(null, { status: 404, headers });
      if (request.method !== 'POST') return new Response(null, { status: 405, headers: { ...headers, Allow: 'POST' } });
      try {
        const auth = request.headers.get('Authorization');
        const token = /^Bearer ([^\s]+)$/i.exec(auth ?? '')?.[1];
        if (!token || token.length > 8192) throw new DraftError(Reason.UNAUTHENTICATED);
        const uid = await deps.verifyUser(token, env.FIREBASE_PROJECT_ID);
        const appToken = request.headers.get('X-Firebase-AppCheck');
        if (!appToken || appToken.length > 8192) throw new DraftError(Reason.APP_CHECK);
        await deps.verifyApp(appToken, env.FIREBASE_PROJECT_NUMBER, env.FIREBASE_APP_ID);
        if (request.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'application/json') throw new DraftError(Reason.INVALID_INPUT);
        let data;
        try { data = validateRequest(await readJson(request, 32 * 1024)); }
        catch { throw new DraftError(Reason.INVALID_INPUT); }
        if (!(await env.NOTE_RATE_LIMITER.limit({ key: `quick-note:${uid}` })).success) throw new DraftError(Reason.RATE_LIMITED);
        const handler = createDraftHandler({
          provider: deps.createProvider({ apiKey: env.OPENROUTER_API_KEY, model: env.OPENROUTER_MODEL }),
          logger, HttpsError: ServiceError, requireAppCheck: true,
        });
        const result = await handler({ data, auth: { uid }, app: appToken ? { appId: env.FIREBASE_APP_ID } : undefined });
        return Response.json(result, { headers });
      } catch (error) {
        const reason: string = error instanceof ServiceError ? error.details.reason : error instanceof DraftError ? error.reason : Reason.PROVIDER_UNAVAILABLE;
        if (!(error instanceof ServiceError)) logger.warn('quick note request rejected', { reason });
        return Response.json({ error: { code: HTTPS_CODE[reason as keyof typeof HTTPS_CODE], reason, message: SAFE_MESSAGE[reason as keyof typeof SAFE_MESSAGE] } }, {
          status: statusFor[reason] ?? 503, headers: { ...headers, ...(reason === Reason.RATE_LIMITED ? { 'Retry-After': '60' } : {}) },
        });
      }
    },
  };
}

export default createWorker() satisfies ExportedHandler<Env>;
