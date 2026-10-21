import assert from 'node:assert/strict';
import test from 'node:test';
import { buildCallableOptions, REGION, SECRET_NAME } from '../src/config.js';

test('deployed runs enforce App Check in asia-south1 with a managed secret', () => {
  const options = buildCallableOptions({ isEmulator: false, secret: { name: SECRET_NAME } });
  assert.equal(options.region, 'asia-south1');
  assert.equal(REGION, 'asia-south1');
  assert.equal(options.enforceAppCheck, true);
  assert.deepEqual(options.secrets, [{ name: 'GEMINI_API_KEY' }]);
  assert.ok(options.timeoutSeconds * 1000 > 6500, 'function timeout must outlast the provider budget');
});

test('only the emulator relaxes App Check', () => {
  assert.equal(buildCallableOptions({ isEmulator: true, secret: {} }).enforceAppCheck, false);
});
