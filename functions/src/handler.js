import { DraftError, HTTPS_CODE, Reason, SAFE_MESSAGE } from './errors.js';
import { parseProposals } from './response.js';
import { validateRequest } from './validation.js';

export const WIRE_VERSION = 1;

/**
 * The createQuickNoteDraft request handler, independent of Firebase so it can be tested with fake providers.
 *
 * Privacy: the note, the prompt and the model's answer exist only as local variables of one call. Nothing is
 * written to storage, and the only thing logged is `{ outcome, reason?, latencyMs, tokens }`.
 *
 * @param provider      `{ generate({note, today, accounts}) -> {text, usage} }`
 * @param logger        `{ info(message, fields), warn(message, fields) }`
 * @param requireAppCheck false only for the local emulator, where App Check cannot be attested
 * @param HttpsError    firebase-functions HttpsError, injected so this module stays testable
 */
export function createDraftHandler({ provider, logger, HttpsError, requireAppCheck = true, now = Date.now }) {
  return async function handle(request) {
    const started = now();
    try {
      // Auth and App Check come before any validation or provider work.
      if (!request?.auth?.uid) throw new DraftError(Reason.UNAUTHENTICATED);
      if (requireAppCheck && !request.app) throw new DraftError(Reason.APP_CHECK);

      const input = validateRequest(request.data);
      const { text, usage } = await provider.generate(input);
      const proposals = parseProposals(text, input);

      logger.info('quick note draft generated', {
        outcome: 'success', latencyMs: now() - started, proposals: proposals.length, ...usage,
      });
      return { version: WIRE_VERSION, proposals };
    } catch (error) {
      const reason = error instanceof DraftError ? error.reason : Reason.PROVIDER_UNAVAILABLE;
      logger.warn('quick note draft failed', { outcome: 'failure', reason, latencyMs: now() - started });
      throw new HttpsError(HTTPS_CODE[reason], SAFE_MESSAGE[reason], { reason });
    }
  };
}
