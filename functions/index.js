import { initializeApp } from 'firebase-admin/app';
import { logger } from 'firebase-functions';
import { HttpsError, onCall } from 'firebase-functions/https';
import { defineSecret } from 'firebase-functions/params';
import { buildCallableOptions, SECRET_NAME } from './src/config.js';
import { createDraftHandler } from './src/handler.js';
import { createGeminiProvider } from './src/provider.js';

initializeApp();

// Managed secret (Cloud Secret Manager). Set with `firebase functions:secrets:set GEMINI_API_KEY`; never committed.
const geminiApiKey = defineSecret(SECRET_NAME);

// The Functions emulator cannot attest App Check, so only emulator runs relax it. Every deployed run enforces it.
const isEmulator = process.env.FUNCTIONS_EMULATOR === 'true';

export const createQuickNoteDraft = onCall(buildCallableOptions({ isEmulator, secret: geminiApiKey }), (request) => {
  const handle = createDraftHandler({
    provider: createGeminiProvider({ getApiKey: () => geminiApiKey.value() }),
    logger,
    HttpsError,
    requireAppCheck: !isEmulator,
  });
  return handle(request);
});
