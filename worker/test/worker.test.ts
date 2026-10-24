import test from 'node:test';
import assert from 'node:assert/strict';
import { createWorker } from '../src/index';
import { createOpenRouterProvider } from '../src/provider';
import { DraftError, Reason } from '../../functions/src/errors.js';
import { validData } from '../../functions/test/helpers.js';

const env: Env = {
  FIREBASE_PROJECT_ID: 'zar-app-35602', FIREBASE_PROJECT_NUMBER: '588119468948', FIREBASE_APP_ID: 'android-app',
  OPENROUTER_MODEL: 'test/model', OPENROUTER_API_KEY: 'test-secret',
  NOTE_RATE_LIMITER: { limit: async () => ({ success: true }) },
};
function setup({ authError = false, appError = false, rateLimited = false, providerBody = '{"proposals":[]}' } = {}) {
  let providerCalls = 0;
  const worker = createWorker({
    verifyUser: async () => { if (authError) throw new DraftError(Reason.UNAUTHENTICATED); return 'verified-user'; },
    verifyApp: async () => { if (appError) throw new DraftError(Reason.APP_CHECK); },
    createProvider: options => createOpenRouterProvider({ ...options, fetchImpl: async () => {
      providerCalls++;
      return Response.json({ choices: [{ finish_reason: 'stop', message: { content: providerBody } }] });
    } }),
  });
  return { worker, calls: () => providerCalls, env: { ...env, NOTE_RATE_LIMITER: { limit: async ({ key }: { key: string }) => { assert.equal(key, 'quick-note:verified-user'); return { success: !rateLimited }; } } } };
}
const request = (body: unknown = validData(), extra: Record<string, string> = {}) => new Request('https://zar.example/v1/quick-note', {
  method: 'POST', headers: { Authorization: 'Bearer test-id-token', 'X-Firebase-AppCheck': 'test-app-token', 'Content-Type': 'application/json', ...extra }, body: JSON.stringify(body),
});

test('keeps the Android version 1 draft contract and sanitizes unknown account IDs', async () => {
  const s = setup({ providerBody: JSON.stringify({ proposals: [{ kind: 'ACTUAL_TRANSACTION', transactionType: 'EXPENSE', amount: '850', currency: 'LKR', accountId: 'unknown' }] }) });
  const response = await s.worker.fetch(request(), s.env);
  assert.equal(response.status, 200);
  const body = await response.json() as { version: number; proposals: { accountId: unknown; amount: string }[] };
  assert.equal(body.version, 1);
  assert.equal(body.proposals[0].accountId, null);
  assert.equal(body.proposals[0].amount, '850');
  assert.equal(response.headers.get('Cache-Control'), 'no-store');
  assert.equal(s.calls(), 1);
});
for (const [name, options, headers, expected] of [
  ['missing auth', {}, { Authorization: '' }, 401], ['bad auth', { authError: true }, {}, 401],
  ['missing App Check', {}, { 'X-Firebase-AppCheck': '' }, 403], ['bad App Check', { appError: true }, {}, 403],
  ['rate limit', { rateLimited: true }, {}, 429],
] as const) {
  test(`${name} never calls OpenRouter`, async () => {
    const s = setup(options);
    const r = await s.worker.fetch(request(validData(), headers), s.env);
    assert.equal(r.status, expected);
    assert.equal(s.calls(), 0);
    assert.ok(!(await r.text()).includes('test-secret'));
  });
}
test('rejects balances, oversized requests and invalid dates before the provider', async () => {
  for (const data of [validData({ balance: '5000' }), validData({ note: 'x'.repeat(33_000) }), validData({ today: '2026-02-30' })]) {
    const s = setup();
    assert.equal((await s.worker.fetch(request(data), s.env)).status, 400);
    assert.equal(s.calls(), 0);
  }
});
test('rejects malformed model output instead of creating a draft', async () => {
  const s = setup({ providerBody: 'not-json' });
  const r = await s.worker.fetch(request(), s.env);
  assert.equal(r.status, 502);
  assert.equal((await r.json() as { error: { reason: string } }).error.reason, 'BAD_RESPONSE');
});
test('health, unknown route and wrong method never call the provider', async () => {
  const s = setup();
  assert.equal((await s.worker.fetch(new Request('https://zar.example/health'), s.env)).status, 200);
  assert.equal((await s.worker.fetch(new Request('https://zar.example/missing'), s.env)).status, 404);
  assert.equal((await s.worker.fetch(new Request('https://zar.example/v1/quick-note'), s.env)).status, 405);
  assert.equal(s.calls(), 0);
});
