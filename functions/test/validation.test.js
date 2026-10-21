import assert from 'node:assert/strict';
import test from 'node:test';
import { validateRequest } from '../src/validation.js';
import { validData } from './helpers.js';

const rejects = (data) => assert.throws(() => validateRequest(data), { reason: 'INVALID_INPUT' });

test('accepts a valid request and trims the note', () => {
  const out = validateRequest(validData({ note: '  hi 100  ' }));
  assert.equal(out.note, 'hi 100');
  assert.equal(out.accounts.length, 2);
});

test('note: blank, over 500 chars, non-string', () => {
  rejects(validData({ note: '' }));
  rejects(validData({ note: ' \n\t ' }));
  rejects(validData({ note: 'a'.repeat(501) }));
  rejects(validData({ note: ['a'] }));
  rejects(validData({ note: undefined }));
  assert.equal(validateRequest(validData({ note: 'a'.repeat(500) })).note.length, 500);
});

test('rejects unknown top-level keys such as balances or raw messages', () => {
  rejects(validData({ rawSms: 'Rs 100 debited' }));
  rejects(validData({ balances: { boc: 1 } }));
});

test('accounts: only id, name, type, currency; no balances', () => {
  const account = { id: 'a', name: 'A', type: 'BANK', currency: 'LKR' };
  rejects(validData({ accounts: [{ ...account, balance: '100' }] }));
  rejects(validData({ accounts: [{ ...account, type: 'WALLET' }] }));
  rejects(validData({ accounts: [{ ...account, currency: 'lkr' }] }));
  rejects(validData({ accounts: [{ ...account, id: 'has space' }] }));
  rejects(validData({ accounts: [{ ...account, name: '' }] }));
  rejects(validData({ accounts: [{ ...account, name: 'n'.repeat(81) }] }));
  rejects(validData({ accounts: [account, account] }));
  rejects(validData({ accounts: 'none' }));
  rejects(validData({ accounts: [null] }));
});

test('accounts: bounded count', () => {
  const many = Array.from({ length: 31 }, (_, i) => ({ id: `a${i}`, name: 'A', type: 'BANK', currency: 'LKR' }));
  rejects(validData({ accounts: many }));
  assert.equal(validateRequest(validData({ accounts: many.slice(0, 30) })).accounts.length, 30);
  assert.deepEqual(validateRequest(validData({ accounts: [] })).accounts, []);
});

test('today must be a real calendar date', () => {
  rejects(validData({ today: '2026-02-30' }));
  rejects(validData({ today: '21-10-2026' }));
  rejects(validData({ today: 20261021 }));
  assert.equal(validateRequest(validData({ today: '2028-02-29' })).today, '2028-02-29');
});
