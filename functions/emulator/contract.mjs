// Contract check against the real Functions emulator (no Gemini call, no deploy). Run with `npm run test:emulator`.
// The emulator decodes but does not verify tokens, so an unsigned token stands in for a signed-in user.
import assert from 'node:assert/strict';

const project = process.env.GCLOUD_PROJECT ?? 'demo-zar';
const url = `http://127.0.0.1:5001/${project}/asia-south1/createQuickNoteDraft`;

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const idToken = `${b64({ alg: 'none', typ: 'JWT' })}.${b64({ iss: `https://securetoken.google.com/${project}`, aud: project, sub: 'emu-user', user_id: 'emu-user', iat: 1, exp: 4102444800, firebase: { sign_in_provider: 'password' } })}.`;

async function call(data, token) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: JSON.stringify({ data }),
  });
  return { status: res.status, body: await res.json() };
}

const valid = { note: 'lunch 850 card', today: '2026-10-21', accounts: [] };

const anon = await call(valid);
assert.equal(anon.status, 401);
assert.equal(anon.body.error.status, 'UNAUTHENTICATED');
console.log('ok  unauthenticated request rejected');

for (const bad of [{ ...valid, note: '   ' }, { ...valid, note: 'x'.repeat(501) }, { ...valid, accounts: [{ id: 'a', name: 'A', type: 'BANK', currency: 'LKR', balance: '5' }] }]) {
  const out = await call(bad, idToken);
  assert.equal(out.status, 400);
  assert.equal(out.body.error.status, 'INVALID_ARGUMENT');
  assert.equal(out.body.error.details.reason, 'INVALID_INPUT');
}
console.log('ok  blank, oversized and balance-carrying requests rejected before any provider call');
