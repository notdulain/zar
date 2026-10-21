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

const accounts = [{ id: 'boc' }, { id: 'cash' }];
const parseWith = (obj) => parseProposals(JSON.stringify(obj), { accounts });

test('amounts that are not plain decimals become missing; exact strings are untouched', () => {
  const amounts = ['1,250', '-5', '+5', '1e3', 'NaN', 'Infinity', '12.', '.5', '5 000', 'Rs 100', '０５', '١٢٣', '0x10'];
  for (const amount of amounts) {
    const [p] = parseWith({ proposals: [{ ...base, amount, receivedAmount: amount }] });
    assert.equal(p.amount, null, `${amount} must be dropped`);
    assert.equal(p.receivedAmount, null);
  }
  const [p] = parseWith({ proposals: [{ ...base, amount: '2500', receivedAmount: '0.00012345' }] });
  assert.equal(p.amount, '2500');
  assert.equal(p.receivedAmount, '0.00012345');
});

test('impossible or malformed dates become missing', () => {
  for (const date of ['2026-02-30', '21/10/2026', '2026-13-01', 'tomorrow', '2026-10-21T00:00:00Z']) {
    const [p] = parseWith({ proposals: [{ ...base, date, dueDate: date }] });
    assert.equal(p.date, null, `${date} must be dropped`);
    assert.equal(p.dueDate, null);
  }
  const [p] = parseWith({ proposals: [{ ...base, date: '2026-10-21', dueDate: '2026-11-01' }] });
  assert.equal(p.date, '2026-10-21');
  assert.equal(p.dueDate, '2026-11-01');
});

test('account references outside the supplied list become missing', () => {
  const [p] = parseWith({ proposals: [{ ...base, kind: 'TRANSFER', accountId: 'boc', toAccountId: 'someone-elses-account' }] });
  assert.equal(p.accountId, 'boc');
  assert.equal(p.toAccountId, null);
  const [none] = parseProposals(JSON.stringify({ proposals: [{ ...base, accountId: 'boc' }] }));
  assert.equal(none.accountId, null, 'with no supplied accounts every reference is missing');
});
