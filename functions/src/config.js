export const REGION = 'asia-south1';
export const SECRET_NAME = 'GEMINI_API_KEY';

/** Options for the createQuickNoteDraft callable. App Check is enforced everywhere except the local emulator. */
export function buildCallableOptions({ isEmulator, secret }) {
  return {
    region: REGION,
    enforceAppCheck: !isEmulator,
    secrets: [secret],
    timeoutSeconds: 10,
    memory: '256MiB',
    maxInstances: 10,
  };
}
