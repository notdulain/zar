import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import test from 'node:test';

// D06 never persists notes or drafts. Guard the server source against storage/analytics SDKs and content logging.
const sources = ['index.js', ...readdirSync(new URL('../src', import.meta.url)).map((f) => `src/${f}`)];
const text = sources.map((f) => ({ f, code: readFileSync(new URL(`../${f}`, import.meta.url), 'utf8') }));

test('server code has no storage, queue or analytics imports', () => {
  const forbidden = /firebase-admin\/(firestore|database|storage|messaging)|@google-cloud\/(firestore|storage|pubsub|tasks)|getFirestore|console\.(log|info|warn|error)/;
  for (const { f, code } of text) assert.ok(!forbidden.test(code), `${f} must not use storage or console logging`);
});

test('the secret value is only read inside a request, never logged', () => {
  const index = text.find(({ f }) => f === 'index.js').code;
  assert.match(index, /geminiApiKey\.value\(\)/);
  assert.ok(!/logger\.[a-z]+\([^)]*apiKey/i.test(index));
});
