import { DraftError, Reason } from '../../functions/src/errors.js';
import { responseSchema, systemInstruction, userPrompt } from '../../functions/src/prompt.js';
import { readJson } from './json';

export const OPENROUTER_URL = 'https://openrouter.ai/api/v1/chat/completions';
export const PROVIDER_TIMEOUT_MS = 6500;
type Schema = { type: string; nullable?: boolean; properties?: Record<string, Schema>; items?: Schema; enum?: (string | null)[]; description?: string; required?: string[] };

/** Convert Gemini's schema dialect to strict JSON Schema without changing the draft fields or prompt. */
export function toJsonSchema(schema: Schema): Record<string, unknown> {
  const { nullable, properties, items, required: _required, ...rest } = schema;
  const converted: Record<string, unknown> = { ...rest, type: nullable ? [schema.type.toLowerCase(), 'null'] : schema.type.toLowerCase() };
  if (nullable && schema.enum) converted.enum = [...schema.enum, null];
  if (properties) {
    converted.properties = Object.fromEntries(Object.entries(properties).map(([name, child]) => [name, toJsonSchema(child)]));
    converted.required = Object.keys(properties);
    converted.additionalProperties = false;
  }
  if (items) converted.items = toJsonSchema(items);
  return converted;
}

export const jsonSchema = toJsonSchema(responseSchema);
export type DraftInput = { note: string; today: string; accounts: { id: string; name: string; type: string; currency: string }[] };
type Completion = { error?: unknown; choices?: { finish_reason?: string; message?: { content?: unknown; refusal?: unknown } }[]; usage?: { prompt_tokens?: number; completion_tokens?: number } };

export function createOpenRouterProvider({ apiKey, model, fetchImpl = fetch, timeoutMs = PROVIDER_TIMEOUT_MS }: {
  apiKey: string; model: string; fetchImpl?: typeof fetch; timeoutMs?: number;
}) {
  return {
    async generate(input: DraftInput) {
      if (!apiKey || !model) throw new DraftError(Reason.PROVIDER_UNAVAILABLE);
      try {
        const response = await fetchImpl(OPENROUTER_URL, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}`, 'X-Title': 'Zar Quick Notes' },
          body: JSON.stringify({
            model, messages: [{ role: 'system', content: systemInstruction() }, { role: 'user', content: userPrompt(input) }],
            response_format: { type: 'json_schema', json_schema: { name: 'zar_quick_note', strict: true, schema: jsonSchema } },
            provider: { require_parameters: true, data_collection: 'deny' },
            reasoning: { effort: 'minimal', exclude: true },
            max_tokens: 2048, stream: false,
          }),
          signal: AbortSignal.timeout(timeoutMs),
        });
        if (!response.ok) {
          await response.body?.cancel();
          const reason = response.status === 429 ? Reason.RATE_LIMITED : [408, 504].includes(response.status) ? Reason.TIMEOUT : Reason.PROVIDER_UNAVAILABLE;
          throw new DraftError(reason);
        }
        let json: Completion;
        try { json = await readJson(response, 64 * 1024) as Completion; }
        catch (error) {
          if (error instanceof Error && ['TimeoutError', 'AbortError'].includes(error.name)) throw error;
          throw new DraftError(Reason.BAD_RESPONSE);
        }
        const candidate = json?.choices?.[0];
        if (json?.error || candidate?.finish_reason !== 'stop' || candidate.message?.refusal || typeof candidate.message?.content !== 'string' || !candidate.message.content.trim()) {
          throw new DraftError(Reason.BAD_RESPONSE);
        }
        const count = (n: unknown) => typeof n === 'number' && Number.isSafeInteger(n) && n >= 0 ? n : undefined;
        return { text: candidate.message.content, usage: { promptTokens: count(json.usage?.prompt_tokens), outputTokens: count(json.usage?.completion_tokens) } };
      } catch (error) {
        if (error instanceof DraftError) throw error;
        if (error instanceof Error && ['TimeoutError', 'AbortError'].includes(error.name)) throw new DraftError(Reason.TIMEOUT);
        throw new DraftError(Reason.PROVIDER_UNAVAILABLE);
      }
    },
  };
}
