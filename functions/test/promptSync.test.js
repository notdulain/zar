import assert from 'node:assert/strict';
import test from 'node:test';
import * as server from '../src/prompt.js';
import * as evaluation from '../../tools/quicknote-eval/prompt.mjs';

// The deployed prompt must be the one that was evaluated in D05.
test('server prompt and schema match the evaluation harness', () => {
  assert.equal(server.systemInstruction(), evaluation.systemInstruction());
  assert.deepEqual(server.responseSchema, evaluation.responseSchema);
  assert.deepEqual(server.DRAFT_FIELDS, evaluation.DRAFT_FIELDS);
  const input = { note: 'n', today: '2026-10-21', accounts: [] };
  assert.equal(server.userPrompt(input), evaluation.userPrompt(input));
});
