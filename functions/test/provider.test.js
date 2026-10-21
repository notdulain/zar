import assert from 'node:assert/strict';
import test from 'node:test';
import { createGeminiProvider, ENDPOINT, MODEL } from '../src/provider.js';
import { fakeFetch, geminiBody, NOTE_SENTINEL } from './helpers.js';

const input = { note: `lunch 850 ${NOTE_SENTINEL}`, today: '2026-10-21', accounts: [] };
const make = (fetchImpl, extra = {}) => createGeminiProvider({ getApiKey: () => 'test-key', fetchImpl, ...extra });
const failsWith = (promise, reason) => assert.rejects(promise, { reason });

test('sends the D05 request: model, temperature 0, minimal thinking, schema, key in header only', async () => {
  const fetchImpl = fakeFetch({ body: geminiBody('{"proposals":[]}') });
  const out = await make(fetchImpl).generate(input);
  assert.equal(out.text, '{"proposals":[]}');
  assert.deepEqual(out.usage, { promptTokens: 887, outputTokens: 163 });

  const [{ url, init }] = fetchImpl.calls;
  assert.equal(url, `${ENDPOINT}/models/${MODEL}:generateContent`);
  assert.equal(MODEL, 'gemini-3.5-flash-lite');
  assert.equal(init.headers['x-goog-api-key'], 'test-key');
  assert.ok(!url.includes('test-key'));
  const body = JSON.parse(init.body);
  assert.equal(body.generationConfig.temperature, 0);
  assert.equal(body.generationConfig.thinkingConfig.thinkingLevel, 'minimal');
  assert.equal(body.generationConfig.responseMimeType, 'application/json');
  assert.ok(body.generationConfig.responseSchema.properties.proposals);
  assert.ok(!init.body.includes('test-key'));
  // The note is quoted JSON data inside the user turn, not part of the system instruction.
  assert.ok(!body.systemInstruction.parts[0].text.includes(NOTE_SENTINEL));
  assert.ok(JSON.parse(body.contents[0].parts[0].text).note.includes(NOTE_SENTINEL));
  assert.ok(init.signal instanceof AbortSignal);
});

test('maps HTTP failures to safe reasons', async () => {
  for (const [status, reason] of [[429, 'RATE_LIMITED'], [408, 'TIMEOUT'], [504, 'TIMEOUT'], [500, 'PROVIDER_UNAVAILABLE'], [403, 'PROVIDER_UNAVAILABLE'], [404, 'PROVIDER_UNAVAILABLE']]) {
    await failsWith(make(fakeFetch({ status, body: { error: { message: NOTE_SENTINEL } } })).generate(input), reason);
  }
});

test('failure errors never carry provider text or the note', async () => {
  const error = await make(fakeFetch({ status: 429, body: { error: { message: NOTE_SENTINEL } } })).generate(input).catch((e) => e);
  assert.ok(!JSON.stringify({ m: error.message, c: error.cause }).includes(NOTE_SENTINEL));
  const thrown = await make(fakeFetch({ throws: new Error(`connect ${NOTE_SENTINEL}`) })).generate(input).catch((e) => e);
  assert.equal(thrown.reason, 'PROVIDER_UNAVAILABLE');
  assert.ok(!thrown.message.includes(NOTE_SENTINEL));
});

test('a network abort or timeout is a TIMEOUT', async () => {
  const timeout = Object.assign(new Error('x'), { name: 'TimeoutError' });
  await failsWith(make(fakeFetch({ throws: timeout })).generate(input), 'TIMEOUT');
});

test('a real slow provider is aborted by the time budget', async () => {
  const slow = (url, init) => new Promise((_, reject) => init.signal.addEventListener('abort', () => reject(init.signal.reason)));
  await failsWith(make(slow, { timeoutMs: 20 }).generate(input), 'TIMEOUT');
});

test('empty, blocked, truncated or malformed envelopes are BAD_RESPONSE', async () => {
  const cases = [
    { candidates: [] },
    {},
    geminiBody('{"proposals":[]}', { promptFeedback: { blockReason: 'SAFETY' } }),
    { candidates: [{ content: { parts: [{ text: '{"prop' }] }, finishReason: 'MAX_TOKENS' }] },
    { candidates: [{ content: { parts: [] }, finishReason: 'STOP' }] },
  ];
  for (const body of cases) await failsWith(make(fakeFetch({ body })).generate(input), 'BAD_RESPONSE');
  const notJson = { ok: true, status: 200, json: async () => { throw new SyntaxError('bad'); } };
  await failsWith(make(async () => notJson).generate(input), 'BAD_RESPONSE');
});

test('a response body that stalls past the budget is aborted, not awaited forever', async () => {
  const stalled = async (url, init) => ({
    ok: true,
    status: 200,
    json: () => new Promise((_, reject) => init.signal.addEventListener('abort', () => reject(init.signal.reason))),
  });
  await failsWith(make(stalled, { timeoutMs: 20 }).generate(input), 'TIMEOUT');
});

test('the default provider budget stays below the 8 s client budget', async () => {
  const { PROVIDER_TIMEOUT_MS } = await import('../src/provider.js');
  assert.equal(PROVIDER_TIMEOUT_MS, 6500);
});

test('a failing secret lookup is a safe provider failure that never reaches the network', async () => {
  const fetchImpl = fakeFetch({ body: geminiBody('{"proposals":[]}') });
  const provider = createGeminiProvider({ getApiKey: () => { throw new Error(`no secret ${NOTE_SENTINEL}`); }, fetchImpl });
  const error = await provider.generate(input).catch((e) => e);
  assert.equal(error.reason, 'PROVIDER_UNAVAILABLE');
  assert.ok(!error.message.includes(NOTE_SENTINEL));
  assert.equal(fetchImpl.calls.length, 0);
});

test('a candidate with only non-text parts is BAD_RESPONSE, not a crash', async () => {
  const body = { candidates: [{ content: { parts: [{ inlineData: {} }] }, finishReason: 'STOP' }] };
  await failsWith(make(fakeFetch({ body })).generate(input), 'BAD_RESPONSE');
});
