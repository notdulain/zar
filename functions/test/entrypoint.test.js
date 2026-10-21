import assert from 'node:assert/strict';
import test from 'node:test';

// Loads the real firebase-functions SDK to confirm the deployed shape, without deploying or calling Gemini.
process.env.GCLOUD_PROJECT ??= 'demo-zar';

test('createQuickNoteDraft is a callable in asia-south1 bound to the managed secret', async () => {
  const { createQuickNoteDraft } = await import('../index.js');
  const endpoint = createQuickNoteDraft.__endpoint;
  assert.ok(endpoint.callableTrigger, 'must be an onCall trigger');
  assert.deepEqual(endpoint.region, ['asia-south1']);
  assert.deepEqual(endpoint.secretEnvironmentVariables.map((s) => s.key), ['GEMINI_API_KEY']);
  assert.equal(endpoint.timeoutSeconds, 10);
});
