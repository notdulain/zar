import test from 'node:test';
import assert from 'node:assert/strict';
import { createOpenRouterProvider, jsonSchema, OPENROUTER_URL } from '../src/provider';
import { validData } from '../../functions/test/helpers.js';

const completion = (content: unknown = '{"proposals":[]}', extra = {}) => ({ choices: [{ finish_reason: 'stop', message: { content } }], usage: { prompt_tokens: 900, completion_tokens: 120 }, ...extra });
const provider = (body: unknown, status = 200) => createOpenRouterProvider({ apiKey: 'test-key', model: 'test/model', fetchImpl: async () => Response.json(body, { status }) });

test('sends the unchanged prompt with strict JSON Schema and no training-permitted providers', async () => {
  const impl = createOpenRouterProvider({ apiKey: 'test-key', model: 'google/gemini-3.5-flash-lite', fetchImpl: async (url, init) => {
    assert.equal(url, OPENROUTER_URL);
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer test-key');
    const body = JSON.parse(String(init?.body));
    assert.equal(body.model, 'google/gemini-3.5-flash-lite');
    assert.equal(JSON.parse(body.messages[1].content).note, validData().note);
    assert.deepEqual(body.provider, { require_parameters: true, data_collection: 'deny' });
    assert.equal(body.response_format.json_schema.strict, true);
    assert.ok(init?.signal);
    return Response.json(completion());
  } });
  assert.deepEqual(await impl.generate(validData()), { text: '{"proposals":[]}', usage: { promptTokens: 900, outputTokens: 120 } });
  const properties = jsonSchema.properties as Record<string, { items: { additionalProperties: boolean; required: string[]; properties: Record<string, unknown> } }>;
  assert.equal(properties.proposals.items.additionalProperties, false);
  assert.equal(properties.proposals.items.required.length, Object.keys(properties.proposals.items.properties).length);
  assert.deepEqual(properties.proposals.items.properties.amount, { type: ['string', 'null'], description: 'Plain decimal, no separators' });
});

for (const [status, reason] of [[401, 'PROVIDER_UNAVAILABLE'], [402, 'PROVIDER_UNAVAILABLE'], [404, 'PROVIDER_UNAVAILABLE'], [429, 'RATE_LIMITED'], [503, 'PROVIDER_UNAVAILABLE'], [504, 'TIMEOUT']] as const) {
  test(`maps HTTP ${status} without forwarding provider error text`, async () => {
    await assert.rejects(() => provider({ error: { message: validData().note } }, status).generate(validData()), { reason, message: reason });
  });
}
for (const body of [completion(null), completion(''), completion('{}', { choices: [{ finish_reason: 'length', message: { content: '{}' } }] }), { error: { message: 'private' } }]) {
  test('rejects incomplete/refused/empty completion', async () => {
    await assert.rejects(() => provider(body).generate(validData()), { reason: 'BAD_RESPONSE' });
  });
}
test('bounds provider responses even with no content-length', async () => {
  await assert.rejects(() => provider(completion('x'.repeat(70_000))).generate(validData()), { reason: 'BAD_RESPONSE' });
});
test('maps deadline and network errors without exposing their text', async () => {
  for (const [name, reason] of [['TimeoutError', 'TIMEOUT'], ['AbortError', 'TIMEOUT'], ['TypeError', 'PROVIDER_UNAVAILABLE']]) {
    const impl = createOpenRouterProvider({ apiKey: 'test', model: 'test', fetchImpl: async () => { throw Object.assign(new Error(validData().note), { name }); } });
    await assert.rejects(() => impl.generate(validData()), { reason, message: reason });
  }
});
