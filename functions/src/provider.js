import { DraftError, Reason } from './errors.js';
import { responseSchema, systemInstruction, userPrompt } from './prompt.js';

// D05 model and prompt are unchanged. Sampling parameters are deprecated/ignored by this model.
export const MODEL = 'gemini-3.5-flash-lite';
export const ENDPOINT = 'https://generativelanguage.googleapis.com/v1beta';
// The Android client gives up after 8 s, so the provider call must end sooner to return a typed error.
export const PROVIDER_TIMEOUT_MS = 6500;
const MAX_OUTPUT_TOKENS = 2048;

/**
 * Gemini Developer API (generateContent, v1beta) provider. `getApiKey()` reads the managed secret, and only after
 * auth and input checks pass; the key only ever goes into the x-goog-api-key header. Failures are reduced to DraftError reasons: provider
 * response bodies and exception messages can echo the note, so they are dropped.
 *
 * Returns { text, usage } where text is the model's JSON string (validated later by parseProposals).
 */
export function createGeminiProvider({ getApiKey, fetchImpl = fetch, timeoutMs = PROVIDER_TIMEOUT_MS, model = MODEL }) {
  return {
    async generate({ note, today, accounts }) {
      const body = {
        systemInstruction: { parts: [{ text: systemInstruction() }] },
        contents: [{ role: 'user', parts: [{ text: userPrompt({ note, today, accounts }) }] }],
        generationConfig: {
          maxOutputTokens: MAX_OUTPUT_TOKENS,
          responseMimeType: 'application/json',
          responseSchema,
          thinkingConfig: { thinkingLevel: 'minimal' },
        },
      };

      let response;
      try {
        response = await fetchImpl(`${ENDPOINT}/models/${model}:generateContent`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'x-goog-api-key': getApiKey() },
          body: JSON.stringify(body),
          signal: AbortSignal.timeout(timeoutMs),
        });
      } catch (error) {
        if (error?.name === 'TimeoutError' || error?.name === 'AbortError') throw new DraftError(Reason.TIMEOUT);
        throw new DraftError(Reason.PROVIDER_UNAVAILABLE);
      }

      if (!response.ok) throw new DraftError(reasonForStatus(response.status));

      let json;
      try {
        json = await response.json();
      } catch (error) {
        if (error?.name === 'TimeoutError' || error?.name === 'AbortError') throw new DraftError(Reason.TIMEOUT);
        throw new DraftError(Reason.BAD_RESPONSE);
      }

      const candidate = json?.candidates?.[0];
      // Blocked, truncated or empty answers cannot be trusted as a complete draft list.
      if (json?.promptFeedback?.blockReason || !candidate || candidate.finishReason !== 'STOP') {
        throw new DraftError(Reason.BAD_RESPONSE);
      }
      const parts = candidate.content?.parts;
      const text = Array.isArray(parts) ? parts.map((p) => (typeof p?.text === 'string' ? p.text : '')).join('') : '';
      if (text.length === 0) throw new DraftError(Reason.BAD_RESPONSE);

      return { text, usage: tokenUsage(json.usageMetadata) };
    },
  };
}

function reasonForStatus(status) {
  if (status === 429) return Reason.RATE_LIMITED;
  if (status === 408 || status === 504) return Reason.TIMEOUT;
  // 400/401/403/404 mean our own key, billing or model configuration is wrong; 5xx is the provider. Either way the
  // user can only fall back to manual entry.
  return Reason.PROVIDER_UNAVAILABLE;
}

function tokenUsage(meta) {
  const count = (v) => (Number.isInteger(v) && v >= 0 ? v : undefined);
  return { promptTokens: count(meta?.promptTokenCount), outputTokens: count(meta?.candidatesTokenCount) };
}
