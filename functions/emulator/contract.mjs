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

// Notes carry this marker so emulator/run.mjs can prove the function never logs note content.
const marker = 'EMU-NOTE-SENTINEL-9013';
const valid = { note: `lunch 850 card ${marker}`, today: '2026-10-21', accounts: [] };

const anon = await call(valid);
assert.equal(anon.status, 401);
assert.equal(anon.body.error.status, 'UNAUTHENTICATED');
console.log('ok  unauthenticated request rejected');

const rejected = [
  { ...valid, note: '   ' },
  { ...valid, note: marker + 'x'.repeat(500) },
  { ...valid, note: 42 },
  { ...valid, today: '2026-02-30' },
  { ...valid, rawSms: 'balance 5' },
  { ...valid, accounts: [{ id: 'a', name: 'A', type: 'BANK', currency: 'LKR', balance: '5' }] },
  { ...valid, accounts: Array.from({ length: 31 }, (_, i) => ({ id: `a${i}`, name: 'A', type: 'BANK', currency: 'LKR' })) },
];
for (const bad of rejected) {
  const out = await call(bad, idToken);
  assert.equal(out.status, 400);
  assert.equal(out.body.error.status, 'INVALID_ARGUMENT');
  assert.equal(out.body.error.details.reason, 'INVALID_INPUT');
}
console.log(`ok  ${rejected.length} blank, oversized, malformed and balance-carrying requests rejected before any provider call`);
