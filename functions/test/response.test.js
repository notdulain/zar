import assert from 'node:assert/strict';
import test from 'node:test';
import { parseProposals } from '../src/response.js';

const base = { kind: 'ACTUAL_TRANSACTION', uncertainFields: [] };
const parse = (obj) => parseProposals(JSON.stringify(obj));
const fails = (text) => assert.throws(() => parseProposals(text), { reason: 'BAD_RESPONSE' });

test('accepts an empty list and normalises omitted fields to null', () => {
  assert.deepEqual(parse({ proposals: [] }), []);
  const [p] = parse({ proposals: [base] });
  assert.equal(p.amount, null);
  assert.equal(p.transactionType, null);
  assert.deepEqual(p.uncertainFields, []);
});

test('rejects invalid JSON, wrong top-level shape and too many proposals', () => {
  fails('not json');
  fails('[]');
  fails('null');
  fails(JSON.stringify({ proposals: 'x' }));
  fails(JSON.stringify({ proposals: Array.from({ length: 6 }, () => base) }));
  assert.equal(parse({ proposals: Array.from({ length: 5 }, () => base) }).length, 5);
});

test('rejects unsupported kinds, enums and field types', () => {
  fails(JSON.stringify({ proposals: [{ ...base, kind: 'POST_TRANSACTION' }] }));
  fails(JSON.stringify({ proposals: [{ ...base, transactionType: 'GIFT' }] }));
  fails(JSON.stringify({ proposals: [{ ...base, spendingClass: 'LUXURY' }] }));
  fails(JSON.stringify({ proposals: [{ ...base, direction: 'SIDEWAYS' }] }));
  fails(JSON.stringify({ proposals: [{ ...base, amount: 100 }] }));
  fails(JSON.stringify({ proposals: [{ ...base, uncertainFields: ['SECRET'] }] }));
  fails(JSON.stringify({ proposals: [{ ...base, uncertainFields: 'AMOUNT' }] }));
  fails(JSON.stringify({ proposals: [null] }));
});

test('drops unknown fields so only the known contract is returned', () => {
  const [p] = parse({ proposals: [{ ...base, injected: 'x', execute: true }] });
  assert.ok(!('injected' in p) && !('execute' in p));
});

test('bounds free text and treats oversized identifiers as missing', () => {
  const [p] = parse({ proposals: [{ ...base, note: 'n'.repeat(1000), merchant: 'm'.repeat(200), amount: '9'.repeat(100), accountId: 'a'.repeat(200) }] });
  assert.equal(p.note.length, 300);
  assert.equal(p.merchant.length, 80);
  assert.equal(p.amount, null);
  assert.equal(p.accountId, null);
});

test('de-duplicates uncertain fields and blank strings become null', () => {
  const [p] = parse({ proposals: [{ ...base, merchant: '   ', uncertainFields: ['AMOUNT', 'AMOUNT'] }] });
  assert.equal(p.merchant, null);
  assert.deepEqual(p.uncertainFields, ['AMOUNT']);
});
