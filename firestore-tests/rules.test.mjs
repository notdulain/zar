import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, getDocs, collection, deleteDoc, updateDoc, writeBatch, deleteField } from 'firebase/firestore';

let env;
const RULES = new URL("../firestore.rules", import.meta.url);
// `firebase emulators:exec` exports the emulator address; fall back to the default port for a bare run.
const [emulatorHost, emulatorPort] = (process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080').split(':');
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-zar',
    firestore: { rules: readFileSync(RULES, 'utf8'), host: emulatorHost, port: Number(emulatorPort) },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();

const lkr = (minor) => ({ currency: 'LKR', kind: 'FIAT', digits: 2, minor });
const tx = (id, over = {}) => ({
  id, accountId: 'bank', type: 'EXPENSE', amount: lkr(100000), date: '2026-10-10',
  conversion: { status: 'SAME' }, spendingClass: 'DISCRETIONARY', source: 'MANUAL', createdAt: 1790000000000, ...over,
});
const usdIncome = (id) => ({
  id, accountId: 'payoneer', type: 'INCOME', amount: { currency: 'USD', kind: 'FIAT', digits: 2, minor: 10000 }, date: '2026-10-10',
  conversion: { status: 'CONVERTED', amount: lkr(3000000), rate: { base: { currency: 'USD', kind: 'FIAT', digits: 2, minor: 0 },
    quote: lkr(0), rate: '300', asOf: 1790000000000, source: 'MANUAL' } },
  source: 'MANUAL', createdAt: 1790000000000,
});
const path = (uid, col, id) => `users/${uid}/${col}/${id}`;

// D08: valid owner fixtures make the negative ownership cases meaningful: the exact same
// create payload is accepted for Alice after Bob and the signed-out client are refused.
const millis = 1790000000000;
const ownershipCases = [
  ['accounts', 'bank', { id: 'bank', name: 'Test bank', type: 'BANK', currency: { code: 'LKR', kind: 'FIAT', digits: 2 }, opening: { status: 'UNKNOWN' }, openingDate: '2026-10-01', archived: false, updatedAt: millis }],
  ['transactions', 't1', tx('t1')],
  ['corrections', 'c1', { id: 'c1', transactionId: 't1', changes: { note: 'Corrected' }, correctedAt: millis }],
  ['voids', 't1', { transactionId: 't1', voidedAt: millis }],
  ['evidenceLinks', 'e1', { evidenceId: 'e1', transactionId: 't1', linkedAt: millis }],
  ['expectedPayments', 'p1', { id: 'p1', direction: 'OUT', amount: lkr(1000), dueDate: '2026-10-30', title: 'Test bill', source: { type: 'QUICK_NOTE' }, status: 'OPEN', createdAt: millis }],
  ['paymentMatches', 'p1_t1', { expectedPaymentId: 'p1', transactionId: 't1', amount: lkr(1000), matchedAt: millis }],
  ['projects', 'p1', { id: 'p1', name: 'Test project', currency: { code: 'LKR', kind: 'FIAT', digits: 2 }, status: 'ACTIVE', createdAt: millis }],
  ['goals', 'g1', { id: 'g1', name: 'Test goal', target: lkr(10000), allocations: [], updatedAt: millis }],
  ['planPreferences', 'main', { id: 'main', mainGoalId: 'g1', updatedAt: millis }],
  ['allowance', 'weekly', { id: 'weekly', enabled: false, categoryIds: [], weekStart: 'MONDAY', updatedAt: millis }],
  ['cryptoOpeningCosts', 'wallet', { accountId: 'wallet', cost: lkr(1000), approximate: false, updatedAt: millis }],
  ['bills', 'b1', { id: 'b1', name: 'Test bill', accountId: 'bank', amount: lkr(1000), frequency: 'MONTHLY', firstDue: '2026-10-30', status: 'ACTIVE', createdAt: millis }],
  ['categoryRules', 'r1', { id: 'r1', merchantKey: 'test store', displayName: 'Test store', categoryId: 'groceries', enabled: true, version: 1, createdAt: millis, updatedAt: millis }],
  ['expenseCategories', 'custom-test-category', { id: 'custom-test-category', name: 'Pets', nameKey: 'pets', source: 'CUSTOM', createdAt: millis }],
  ['drafts', 'd1', { id: 'd1', source: 'SMS', status: 'PENDING_REVIEW', createdAt: millis, proposals: [{ kind: 'ACTUAL_TRANSACTION', type: 'EXPENSE', amount: lkr(1000), uncertainFields: [] }] }],
];

for (const [col, id, payload] of ownershipCases) {
  test(`D08 ownership: ${col} refuses foreign and signed-out create/read/list/update/delete`, async () => {
    for (const db of [bob(), anon()]) await assertFails(setDoc(doc(db, path('alice', col, id)), payload));
    await assertSucceeds(setDoc(doc(alice(), path('alice', col, id)), payload));
    await assertSucceeds(getDoc(doc(alice(), path('alice', col, id))));
    for (const db of [bob(), anon()]) {
      const ref = doc(db, path('alice', col, id));
      await assertFails(getDoc(ref));
      await assertFails(getDocs(collection(db, `users/alice/${col}`)));
      await assertFails(updateDoc(ref, payload));
      await assertFails(deleteDoc(ref));
    }
  });
}

test('D08 evidence links are typed, schema bounded, ID-bound and write-once', async () => {
  const link = { evidenceId: 'e1', transactionId: 't1', linkedAt: millis };
  const ref = doc(alice(), path('alice', 'evidenceLinks', 'e1'));
  await assertSucceeds(setDoc(ref, link));
  await assertFails(setDoc(ref, link));
  await assertFails(updateDoc(ref, { transactionId: 't2' }));
  await assertFails(deleteDoc(ref));
  for (const over of [{ evidenceId: 'other' }, { transactionId: 5 }, { linkedAt: '1790000000000' }, { rawText: 'private SMS' }]) {
    await assertFails(setDoc(doc(alice(), path('alice', 'evidenceLinks', 'e2')), { ...link, evidenceId: 'e2', ...over }));
  }
});

test('D08 transaction types and raw-message schema pollution are rejected', async () => {
  const invalid = [{ amount: '1000' }, { amount: { ...lkr(1000), minor: '1000' } }, { date: millis }, { createdAt: '1790000000000' }, { accountId: 5 }, { source: true }, { rawText: 'private SMS' }, { rawMessage: 'private SMS' }];
  for (const [i, over] of invalid.entries()) {
    const id = `invalid-${i}`;
    await assertFails(setDoc(doc(alice(), path('alice', 'transactions', id)), tx(id, over)));
  }
});

test('D08 corrections and voids reject malformed values and cannot be changed or deleted', async () => {
  const c = { id: 'c1', transactionId: 't1', changes: { amount: lkr(1000) }, correctedAt: millis };
  const ref = doc(alice(), path('alice', 'corrections', 'c1'));
  await assertSucceeds(setDoc(ref, c));
  await assertFails(updateDoc(ref, { changes: { amount: lkr(2000) } }));
  await assertFails(deleteDoc(ref));
  await assertFails(setDoc(doc(alice(), path('alice', 'corrections', 'bad')), { ...c, id: 'bad', changes: { rawText: 'private SMS' } }));
  await assertFails(setDoc(doc(alice(), path('alice', 'voids', 't2')), { transactionId: 't1', voidedAt: millis }));
  await assertFails(setDoc(doc(alice(), path('alice', 'voids', 't2')), { transactionId: 't2', voidedAt: '1790000000000' }));
});

test('D08 unknown collections, nested paths and root documents remain denied', async () => {
  for (const db of [alice(), bob(), anon()]) {
    for (const location of ['users/alice', 'users/alice/unknown/test', 'users/alice/transactions/t1/private/test', 'transactions/t1']) {
      await assertFails(setDoc(doc(db, location), { fixture: true }));
      await assertFails(getDoc(doc(db, location)));
    }
  }
});

test('owner can create and read a transaction', async () => {
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'transactions', 't1')), tx('t1')));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'transactions', 'timed')), tx('timed', { time: '09:05' })));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'transactions', 'u1')), usdIncome('u1')));
  await assertSucceeds(getDocs(collection(alice(), 'users/alice/transactions')));
});

test('other users and signed-out users get nothing', async () => {
  await assertFails(setDoc(doc(bob(), path('alice', 'transactions', 't1')), tx('t1')));
  await assertFails(setDoc(doc(anon(), path('alice', 'transactions', 't1')), tx('t1')));
  await env.withSecurityRulesDisabled(async (c) => setDoc(doc(c.firestore(), path('alice', 'transactions', 't1')), tx('t1')));
  await assertFails(getDoc(doc(bob(), path('alice', 'transactions', 't1'))));
  await assertFails(getDocs(collection(bob(), 'users/alice/transactions')));
  await assertFails(getDocs(collection(anon(), 'users/alice/transactions')));
});

test('replayed save cannot overwrite; delete is denied', async () => {
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'transactions', 't1')), tx('t1')));
  await assertFails(setDoc(doc(alice(), path('alice', 'transactions', 't1')), tx('t1', { amount: lkr(1) })));
  await assertFails(setDoc(doc(alice(), path('alice', 'transactions', 't1')), tx('t1')));
  await assertFails(deleteDoc(doc(alice(), path('alice', 'transactions', 't1'))));
});

test('void marker is durable', async () => {
  const v = { transactionId: 't1', voidedAt: 1790000000001 };
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'voids', 't1')), v));
  await assertFails(deleteDoc(doc(alice(), path('alice', 'voids', 't1'))));
  await assertFails(setDoc(doc(alice(), path('alice', 'voids', 't1')), { ...v, reason: 'x' }));
});

test('transfer batch succeeds, and a replay of the whole batch fails as a unit', async () => {
  const leg = (id, type, acc) => { const t = tx(id, { type, transferId: 'tr', accountId: acc }); delete t.spendingClass; return t; };
  const db = alice();
  const commit = () => { const b = writeBatch(db);
    b.set(doc(db, path('alice', 'transactions', 'tr-out')), leg('tr-out', 'TRANSFER_OUT', 'bank'));
    b.set(doc(db, path('alice', 'transactions', 'tr-in')), leg('tr-in', 'TRANSFER_IN', 'cash'));
    return b.commit(); };
  await assertSucceeds(commit());
  await assertFails(commit());
});

test('invalid transactions are rejected', async () => {
  const p = (id) => doc(alice(), path('alice', 'transactions', id));
  await assertFails(setDoc(p('a'), tx('a', { amount: lkr(0) })));                 // non-positive
  await assertFails(setDoc(p('b'), tx('b', { extra: 'x' })));                     // schema pollution
  await assertFails(setDoc(p('c'), tx('wrong-id')));                              // id mismatch
  await assertFails(setDoc(p('d'), tx('d', { type: 'INCOME' })));                 // class on income
  await assertFails(setDoc(p('e'), tx('e', { note: 'x'.repeat(501) })));          // oversized
  await assertFails(setDoc(p('f'), tx('f', { amount: { ...lkr(100), minor: 1.5 } }))); // float money
  const refund = tx('g', { type: 'REFUND' }); delete refund.spendingClass;
  await assertFails(setDoc(p('g'), refund));                                      // refund without link
  await assertSucceeds(setDoc(p('h'), { ...refund, id: 'h', refundOf: 't1' }));
  await assertSucceeds(setDoc(p('i'), tx('i', { amount: { currency: 'BTC', kind: 'CRYPTO', digits: 8, quantity: '0.00012345' }, conversion: { status: 'MISSING', target: lkr(0) } })));
  await assertFails(setDoc(p('j'), tx('j', { amount: { currency: 'BTC', kind: 'CRYPTO', digits: 8, quantity: '0.000' } })));
  await assertFails(setDoc(p('bad-time'), tx('bad-time', { time: '25:61' })));
});

test('corrections are write-once and validated', async () => {
  const c = { id: 'c1', transactionId: 't1', changes: { amount: lkr(5000), note: null }, correctedAt: 1790000000002 };
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'corrections', 'c1')), c));
  await assertFails(setDoc(doc(alice(), path('alice', 'corrections', 'c1')), c));
  await assertFails(setDoc(doc(alice(), path('alice', 'corrections', 'c2')), { ...c, id: 'c2', changes: {} }));
  await assertFails(setDoc(doc(alice(), path('alice', 'corrections', 'c3')), { ...c, id: 'c3', changes: { accountId: 'x' } }));
});

test('expected payments: editable, cancel is final, never deleted', async () => {
  const e = { id: 'm1', direction: 'IN', amount: { currency: 'USD', kind: 'FIAT', digits: 2, minor: 50000 }, dueDate: '2026-10-20',
    title: 'Milestone 1', source: { type: 'FREELANCE_MILESTONE', projectId: 'p1', milestoneId: 'm1' }, status: 'OPEN', createdAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'expectedPayments', 'm1'));
  await assertSucceeds(setDoc(r, e));
  await assertSucceeds(updateDoc(r, { title: 'Milestone one' }));
  await assertFails(updateDoc(r, { direction: 'OUT' }));
  await assertSucceeds(updateDoc(r, { status: 'CANCELLED' }));
  await assertFails(updateDoc(r, { status: 'OPEN' }));
  await assertFails(deleteDoc(r));
});

test('payment matches: idempotent ID, create/delete only', async () => {
  const m = { expectedPaymentId: 'm1', transactionId: 't1', amount: lkr(100), matchedAt: 1790000000000 };
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'paymentMatches', 'm1_t1')), m));
  await assertFails(setDoc(doc(alice(), path('alice', 'paymentMatches', 'other')), m));
  await assertFails(updateDoc(doc(alice(), path('alice', 'paymentMatches', 'm1_t1')), { amount: lkr(5) }));
  await assertSucceeds(deleteDoc(doc(alice(), path('alice', 'paymentMatches', 'm1_t1'))));
});

test('drafts: no raw text; final once confirmed', async () => {
  const d = { id: 'd1', source: 'SMS', status: 'PENDING_REVIEW', createdAt: 1790000000000,
    proposals: [{ kind: 'ACTUAL_TRANSACTION', type: 'EXPENSE', amount: lkr(1000), uncertainFields: ['CATEGORY'] }] };
  const r = doc(alice(), path('alice', 'drafts', 'd1'));
  await assertFails(setDoc(doc(alice(), path('alice', 'drafts', 'd2')), { ...d, id: 'd2', rawText: 'Your a/c debited' }));
  await assertSucceeds(setDoc(r, d));
  await assertSucceeds(updateDoc(r, { status: 'CONFIRMED' }));
  await assertFails(updateDoc(r, { status: 'PENDING_REVIEW' }));
});

test('accounts: currency fixed, no delete, user doc locked', async () => {
  const a = { id: 'bank', name: 'BOC', type: 'BANK', currency: { code: 'LKR', kind: 'FIAT', digits: 2 },
    opening: { status: 'KNOWN', amount: lkr(1120000) }, openingDate: '2026-10-01', archived: false, updatedAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'accounts', 'bank'));
  await assertSucceeds(setDoc(r, a));
  await assertSucceeds(updateDoc(r, { archived: true, updatedAt: 1790000000001 }));
  await assertFails(updateDoc(r, { currency: { code: 'USD', kind: 'FIAT', digits: 2 } }));
  await assertFails(deleteDoc(r));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'accounts', 'binance')), { ...a, id: 'binance', type: 'CRYPTO',
    currency: { code: 'USDT', kind: 'CRYPTO', digits: 6 }, opening: { status: 'UNKNOWN' } }));
  await assertFails(setDoc(doc(alice(), 'users/alice'), { name: 'x' }));
  await assertFails(setDoc(doc(alice(), 'users/alice/secrets/x'), { a: 1 }));
});

test('projects: currency fixed, archive instead of delete, owner only', async () => {
  const p = { id: 'p1', name: 'Website redesign', client: 'Acme', currency: { code: 'USD', kind: 'FIAT', digits: 2 },
    status: 'ACTIVE', createdAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'projects', 'p1'));
  await assertSucceeds(setDoc(r, p));
  const { client, ...noClient } = p;
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'projects', 'p2')), { ...noClient, id: 'p2' }));
  await assertSucceeds(updateDoc(r, { name: 'Website v2', status: 'ARCHIVED' }));
  await assertFails(updateDoc(r, { currency: { code: 'LKR', kind: 'FIAT', digits: 2 } }));
  await assertFails(updateDoc(r, { createdAt: 1790000000001 }));
  await assertFails(updateDoc(r, { status: 'DONE' }));
  await assertFails(setDoc(doc(alice(), path('alice', 'projects', 'p3')), { ...p, id: 'other' }));
  await assertFails(setDoc(doc(alice(), path('alice', 'projects', 'p4')), { ...p, id: 'p4', name: '' }));
  await assertFails(setDoc(doc(alice(), path('alice', 'projects', 'p5')), { ...p, id: 'p5', rate: '300' }));
  await assertFails(deleteDoc(r));
  await assertFails(getDoc(doc(bob(), path('alice', 'projects', 'p1'))));
  await assertFails(setDoc(doc(bob(), path('alice', 'projects', 'p6')), { ...p, id: 'p6' }));
});

test('goals: owner can explicitly allocate money without changing ledger data', async () => {
  const goal = { id: 'macbook-fund', name: 'MacBook fund', target: lkr(49000000), allocations: [],
    updatedAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'goals', 'macbook-fund'));

  await assertSucceeds(setDoc(r, goal));
  await assertSucceeds(updateDoc(r, {
    allocations: [{ accountId: 'savings', amount: lkr(1120000) }],
    targetDate: '2027-06-01',
    updatedAt: 1790000000001,
  }));
  await assertSucceeds(getDoc(r));

  await assertFails(updateDoc(r, { id: 'other' }));
  await assertFails(updateDoc(r, { allocations: [{ accountId: 'savings', amount: lkr(0) }] }));
  await assertFails(updateDoc(r, { targetDate: 'next summer' }));
  await assertFails(updateDoc(r, { targetDate: null }));
  await assertSucceeds(updateDoc(r, { targetDate: deleteField() }));
  await assertFails(updateDoc(r, { allocations: [{ accountId: 'savings', amount: { ...lkr(100), currency: 'USD' } }] }));
  await assertFails(setDoc(doc(alice(), path('alice', 'goals', 'bad-id')), { ...goal }));
  await assertFails(deleteDoc(r));
  await assertFails(getDoc(doc(bob(), path('alice', 'goals', 'macbook-fund'))));
  await assertFails(setDoc(doc(bob(), path('alice', 'goals', 'other')), { ...goal, id: 'other' }));
});

test('goals: allocation boundary and update schema stay enforced', async () => {
  const goal = { id: 'macbook-fund', name: 'MacBook fund', target: lkr(49000000), allocations: [], updatedAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'goals', goal.id));
  const allocations = Array.from({ length: 10 }, (_, i) => ({ accountId: `account-${i}`, amount: lkr(100) }));
  await assertSucceeds(setDoc(r, { ...goal, allocations }));
  await assertSucceeds(updateDoc(r, { allocations, updatedAt: 1790000000001 }));
  await assertFails(updateDoc(r, { allocations: [{ accountId: 'savings', amount: { ...lkr(100), digits: 3 } }] }));
  await assertFails(updateDoc(r, { allocations: [{ accountId: 'savings', amount: { ...lkr(100), extra: true } }] }));
  const crypto = { currency: 'USDT', kind: 'CRYPTO', digits: 6, quantity: '5' };
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'goals', 'crypto')), {
    ...goal, id: 'crypto', target: crypto,
    allocations: [{ accountId: 'wallet', amount: { ...crypto, quantity: '1.5' } }],
  }));
  await assertFails(updateDoc(r, { allocations: [...allocations, { accountId: 'extra', amount: lkr(100) }] }));
  await assertFails(updateDoc(r, { name: 'x'.repeat(81) }));
  await assertFails(updateDoc(r, { unexpectedField: true }));
  await assertFails(updateDoc(doc(bob(), path('alice', 'goals', goal.id)), { name: 'Changed' }));
  await assertFails(getDoc(doc(anon(), path('alice', 'goals', goal.id))));
});

test('goals: several plans coexist under distinct IDs and a retried create is the same document', async () => {
  const a = { id: 'a1b2c3d4-0000-4000-8000-000000000001', name: 'Laptop', target: lkr(49000000), allocations: [], updatedAt: 1790000000000 };
  const b = { ...a, id: 'a1b2c3d4-0000-4000-8000-000000000002', name: 'Phone', target: lkr(9000000) };
  const legacy = { ...a, id: 'macbook-fund', name: 'MacBook fund',
    allocations: [{ accountId: 'savings', amount: lkr(1120000) }] };
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'goals', legacy.id)), legacy));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'goals', a.id)), a));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'goals', b.id)), b));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'goals', b.id)), b)); // retry of the same create
  const legacySnap = await getDoc(doc(alice(), path('alice', 'goals', legacy.id)));
  assert.equal(legacySnap.data().allocations.length, 1);
  await assertFails(deleteDoc(doc(alice(), path('alice', 'goals', a.id))));
});

test('planPreferences: one owner-only main-plan pointer, no delete, nothing else writable', async () => {
  const pref = { id: 'main', mainGoalId: 'macbook-fund', updatedAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'planPreferences', 'main'));
  await assertSucceeds(setDoc(r, pref));
  await assertSucceeds(updateDoc(r, { mainGoalId: 'a1b2c3d4-0000-4000-8000-000000000002', updatedAt: 1790000000001 }));
  await assertSucceeds(getDoc(r));
  await assertFails(updateDoc(r, { mainGoalId: '' }));
  await assertFails(updateDoc(r, { mainGoalId: 'x'.repeat(65) }));
  await assertFails(updateDoc(r, { extra: true }));
  await assertFails(setDoc(r, { id: 'main', updatedAt: 1790000000002 }));
  await assertFails(setDoc(doc(alice(), path('alice', 'planPreferences', 'other')), { ...pref, id: 'other' }));
  await assertFails(deleteDoc(r));
  await assertFails(getDoc(doc(bob(), path('alice', 'planPreferences', 'main'))));
  await assertFails(setDoc(doc(bob(), path('alice', 'planPreferences', 'main')), pref));
  await assertFails(getDoc(doc(anon(), path('alice', 'planPreferences', 'main'))));
});

test('allowance: one owner-only setting, LKR amount, no delete', async () => {
  const setting = { id: 'weekly', enabled: true, amount: lkr(800000), categoryIds: ['coffee-dining'],
    weekStart: 'MONDAY', updatedAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'allowance', 'weekly'));

  await assertSucceeds(setDoc(r, setting));
  await assertSucceeds(updateDoc(r, { enabled: false, updatedAt: 1790000000001 }));
  await assertSucceeds(updateDoc(r, { categoryIds: [], weekStart: 'SUNDAY', updatedAt: 1790000000002 }));
  await assertSucceeds(getDoc(r));
  // Off with no amount saved yet is fine; on without an amount is not.
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'allowance', 'weekly')), {
    id: 'weekly', enabled: false, categoryIds: [], weekStart: 'MONDAY', updatedAt: 1790000000003 }));
  await assertFails(setDoc(r, { id: 'weekly', enabled: true, categoryIds: [], weekStart: 'MONDAY', updatedAt: 1790000000004 }));

  await assertFails(setDoc(r, { ...setting, amount: lkr(0) }));
  await assertFails(setDoc(r, { ...setting, amount: { ...lkr(800000), currency: 'USD' } }));
  await assertFails(setDoc(r, { ...setting, weekStart: 'FUNDAY' }));
  await assertFails(setDoc(r, { ...setting, categoryIds: Array.from({ length: 9 }, (_, i) => `c${i}`) }));
  await assertFails(setDoc(r, { ...setting, categoryIds: [''] }));
  await assertFails(setDoc(r, { ...setting, unexpectedField: true }));
  await assertFails(setDoc(r, { ...setting, id: 'other' }));
  await assertFails(setDoc(doc(alice(), path('alice', 'allowance', 'second')), { ...setting, id: 'second' }));
  await assertFails(deleteDoc(r));
  await assertFails(getDoc(doc(bob(), path('alice', 'allowance', 'weekly'))));
  await assertFails(setDoc(doc(bob(), path('alice', 'allowance', 'weekly')), setting));
  await assertFails(getDoc(doc(anon(), path('alice', 'allowance', 'weekly'))));
});

test('expense categories: owner-only, custom ids, safe names, never changed or deleted', async () => {
  const cat = (id, over = {}) => ({ id, name: 'Pets', nameKey: 'pets', source: 'CUSTOM', createdAt: 1790000000000, ...over });
  const at = (id) => doc(alice(), path('alice', 'expenseCategories', id));
  const id = 'custom-5f2c9a1e-7d44-4c0b-9c1d-0a1b2c3d4e5f';

  await assertSucceeds(setDoc(at(id), cat(id)));
  await assertSucceeds(getDoc(at(id)));
  await assertSucceeds(getDocs(collection(alice(), 'users/alice/expenseCategories')));
  // A retry or offline replay of the same create is allowed and changes nothing.
  await assertSucceeds(setDoc(at(id), cat(id)));
  // Unicode, spaces inside and a 40-character name are fine.
  await assertSucceeds(setDoc(at('custom-unicode-1'), cat('custom-unicode-1', { name: 'කෑම & 🐶 Fees', nameKey: 'කෑම & 🐶 fees' })));
  await assertSucceeds(setDoc(at('custom-long-name1'), cat('custom-long-name1', { name: 'x'.repeat(40), nameKey: 'x'.repeat(40) })));

  // Never edited, renamed or deleted: an expense's categoryId must keep resolving.
  await assertFails(setDoc(at(id), cat(id, { name: 'Dogs', nameKey: 'dogs' })));
  await assertFails(updateDoc(at(id), { createdAt: 1790000000001 }));
  await assertFails(deleteDoc(at(id)));

  // Reserved ids: built-in, income and transfer-fee ids are never creatable; id must match the document.
  for (const reserved of ['groceries', 'other', 'coffee-dining', 'season-ticket', 'salary', 'freelance', 'transfer-fee', 'custom-']) {
    await assertFails(setDoc(at(reserved), cat(reserved)));
  }
  await assertFails(setDoc(at('custom-short'), cat('custom-short')));
  await assertFails(setDoc(at('custom-bad_id_1'), cat('custom-bad_id_1')));
  await assertFails(setDoc(at('custom-mismatch-1'), cat('custom-another-id')));

  // Malformed payloads.
  const bad = (name, over) => assertFails(setDoc(at('custom-bad-' + name), cat('custom-bad-' + name, over)));
  await bad('empty', { name: '', nameKey: '' });
  await bad('long', { name: 'x'.repeat(81), nameKey: 'x'.repeat(81) });
  await bad('key-long', { nameKey: 'x'.repeat(161) });
  await bad('untrimmed', { name: ' Pets' });
  await bad('newline', { name: 'Pet\nfood', nameKey: 'pet food' });
  await bad('tab', { name: 'Pet\tfood', nameKey: 'pet food' });
  await bad('nul', { name: 'Pet\u0000food', nameKey: 'pet food' });
  await bad('linesep', { name: 'Pet\u2028food', nameKey: 'pet food' });
  await bad('upperkey', { nameKey: 'Pets' });
  await bad('keyspace', { nameKey: 'pets ' });
  await bad('keynewline', { nameKey: 'pet\nfood' });
  await bad('keytab', { nameKey: 'pet\tfood' });
  await bad('keydouble', { nameKey: 'pet  food' });
  await bad('source', { source: 'BUILT_IN' });
  const noSource = cat('custom-bad-nosource'); delete noSource.source;
  await assertFails(setDoc(at('custom-bad-nosource'), noSource));
  await bad('extra', { extra: true });
  await bad('nonstring', { name: 7 });
  await bad('created', { createdAt: 0 });
  await bad('createdstr', { createdAt: '1790000000000' });

  // Owner isolation: another user and a signed-out user can neither read nor write Alice's categories.
  await assertFails(getDoc(doc(bob(), path('alice', 'expenseCategories', id))));
  await assertFails(getDocs(collection(bob(), 'users/alice/expenseCategories')));
  await assertFails(setDoc(doc(bob(), path('alice', 'expenseCategories', 'custom-bobs-attempt')), cat('custom-bobs-attempt')));
  await assertFails(getDoc(doc(anon(), path('alice', 'expenseCategories', id))));
  await assertFails(setDoc(doc(anon(), path('alice', 'expenseCategories', 'custom-anon-attempt')), cat('custom-anon-attempt')));
  // Bob has his own catalog, and the same id under his scope does not touch Alice's.
  await assertSucceeds(setDoc(doc(bob(), path('bob', 'expenseCategories', id)), cat(id, { name: 'Bob pets' , nameKey: 'bob pets' })));
  await assertSucceeds(getDoc(doc(alice(), path('alice', 'expenseCategories', id))));
  // Outside the user scope there is nothing.
  await assertFails(setDoc(doc(alice(), `expenseCategories/${id}`), cat(id)));
});

test('an expense can reference a category before the category document exists (offline ordering)', async () => {
  // The ledger rules never look a category up, so an expense saved offline right after its new category
  // is accepted whatever order the writes reach the server.
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'transactions', 'e1')), tx('e1', { categoryId: 'custom-5f2c9a1e-7d44-4c0b-9c1d-0a1b2c3d4e5f' })));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'expenseCategories', 'custom-5f2c9a1e-7d44-4c0b-9c1d-0a1b2c3d4e5f')), {
    id: 'custom-5f2c9a1e-7d44-4c0b-9c1d-0a1b2c3d4e5f', name: 'Pets', nameKey: 'pets', source: 'CUSTOM', createdAt: 1790000000000 }));
});

test('crypto opening costs: LKR only, owner only, can be cleared', async () => {
  const c = { accountId: 'binance-btc', cost: lkr(30000000), approximate: true, updatedAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'cryptoOpeningCosts', 'binance-btc'));
  await assertSucceeds(setDoc(r, c));
  await assertSucceeds(updateDoc(r, { cost: lkr(32000000), approximate: false, updatedAt: 1790000000001 }));
  await assertFails(setDoc(doc(alice(), path('alice', 'cryptoOpeningCosts', 'other')), c));
  await assertFails(setDoc(doc(alice(), path('alice', 'cryptoOpeningCosts', 'usd')), { ...c, accountId: 'usd',
    cost: { currency: 'USD', kind: 'FIAT', digits: 2, minor: 100 } }));
  await assertFails(setDoc(doc(alice(), path('alice', 'cryptoOpeningCosts', 'zero')), { ...c, accountId: 'zero', cost: lkr(0) }));
  await assertFails(setDoc(doc(alice(), path('alice', 'cryptoOpeningCosts', 'extra')), { ...c, accountId: 'extra', rate: '300' }));
  await assertFails(getDoc(doc(bob(), path('alice', 'cryptoOpeningCosts', 'binance-btc'))));
  await assertFails(deleteDoc(doc(bob(), path('alice', 'cryptoOpeningCosts', 'binance-btc'))));
  await assertSucceeds(deleteDoc(r));
});

test('captured drafts: structured fields only, confirmed once', async () => {
  const d = { id: 'capture-0011aabb', source: 'SHARED_TEXT', status: 'PENDING_REVIEW', createdAt: 1790000000000,
    proposals: [{ kind: 'ACTUAL_TRANSACTION', type: 'EXPENSE', amount: lkr(80000), date: '2026-10-11', spendingClass: 'UNKNOWN',
      merchant: 'CAFE SEVENTY THREE MALABE LK', uncertainFields: ['TYPE'] }] };
  const r = doc(alice(), path('alice', 'drafts', d.id));
  await assertSucceeds(setDoc(r, d));
  await assertSucceeds(setDoc(r, d));
  await assertFails(setDoc(doc(alice(), path('alice', 'drafts', 'capture-raw')), { ...d, id: 'capture-raw',
    proposals: [{ ...d.proposals[0], rawText: 'Dear Cardholder, Purchase at ...' }] }));
  await assertFails(updateDoc(r, { status: 'BROKEN' }));
  await assertFails(updateDoc(r, { createdAt: d.createdAt + 1 }));
  await assertFails(updateDoc(r, { rawText: 'private message', status: 'CONFIRMED' }));
  const batch = writeBatch(r.firestore);
  batch.set(doc(r.firestore, path('alice', 'transactions', 'capture-record')), tx('capture-record', { source: 'SHARED_TEXT' }));
  batch.update(r, { status: 'CONFIRMED' });
  await assertSucceeds(batch.commit());
  await assertFails(setDoc(r, d));
  await assertFails(getDoc(doc(bob(), path('alice', 'drafts', d.id))));
});

test('bills: stop instead of delete, created date fixed, owner only', async () => {
  const b = { id: 'b1', name: 'Dialog Internet', accountId: 'bank', amount: lkr(585000), frequency: 'MONTHLY',
    firstDue: '2026-10-31', status: 'ACTIVE', createdAt: 1790000000000 };
  const r = doc(alice(), path('alice', 'bills', 'b1'));
  await assertSucceeds(setDoc(r, b));
  await assertSucceeds(updateDoc(r, { amount: lkr(600000), status: 'STOPPED' }));
  await assertSucceeds(updateDoc(r, { reminderDaysBefore: 1 }));
  await assertFails(updateDoc(r, { reminderDaysBefore: 2 }));
  await assertSucceeds(updateDoc(r, { cancellation: 'INTENDED' }));
  await assertFails(updateDoc(r, { cancellation: 'CONFIRMED' }));
  await assertSucceeds(updateDoc(r, { cancellation: 'CONFIRMED', cancelledFrom: '2026-11-01' }));
  await assertFails(updateDoc(r, { cancellation: 'INTENDED', cancelledFrom: '2026-11-01' }));
  await assertFails(updateDoc(r, { createdAt: 1790000000001 }));
  await assertFails(updateDoc(r, { frequency: 'DAILY' }));
  await assertFails(setDoc(doc(alice(), path('alice', 'bills', 'b2')), { ...b, id: 'other' }));
  await assertFails(setDoc(doc(alice(), path('alice', 'bills', 'b3')), { ...b, id: 'b3', amount: lkr(0) }));
  await assertFails(setDoc(doc(alice(), path('alice', 'bills', 'b4')), { ...b, id: 'b4', firstDue: '31/10/2026' }));
  await assertFails(deleteDoc(r));
  await assertFails(getDoc(doc(bob(), path('alice', 'bills', 'b1'))));
  const occ = { id: 'bill-b1-2026-10-31', direction: 'OUT', amount: lkr(585000), dueDate: '2026-10-31', title: 'Dialog Internet',
    source: { type: 'BILL_OCCURRENCE', billId: 'b1', occurrenceDate: '2026-10-31' }, accountId: 'bank', status: 'OPEN', createdAt: 1790000000000 };
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'expectedPayments', occ.id)), occ));
});

test('transactions: the category rule that filled the category is both-or-neither, on expenses only', async () => {
  const p = (id) => doc(alice(), path('alice', 'transactions', id));
  await assertSucceeds(setDoc(p('r1'), tx('r1', { categoryId: 'groceries', appliedRuleId: 'rule-1', appliedRuleVersion: 2 })));
  await assertFails(setDoc(p('r2'), tx('r2', { appliedRuleId: 'rule-1' })));                       // version missing
  await assertFails(setDoc(p('r3'), tx('r3', { appliedRuleVersion: 2 })));                         // rule ID missing
  await assertFails(setDoc(p('r4'), tx('r4', { appliedRuleId: 'rule-1', appliedRuleVersion: 0 }))); // versions start at 1
  await assertFails(setDoc(p('r5'), tx('r5', { appliedRuleId: 'rule-1', appliedRuleVersion: '2' })));
  const income = tx('r6', { type: 'INCOME', appliedRuleId: 'rule-1', appliedRuleVersion: 1 });
  delete income.spendingClass;
  await assertFails(setDoc(p('r6'), income));                                                      // never on income
});

const rule = (id, over = {}) => ({
  id, merchantKey: 'keells super', displayName: 'Keells Super', categoryId: 'groceries', enabled: true, version: 1,
  createdAt: 1790000000000, updatedAt: 1790000000000, ...over,
});

test('category rules: owner only, start at version 1, every edit raises the version, never deleted', async () => {
  const r = doc(alice(), path('alice', 'categoryRules', 'r1'));
  await assertSucceeds(setDoc(r, rule('r1')));
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'categoryRules', 'r2')), rule('r2', { accountId: 'cash' })));
  await assertSucceeds(getDoc(r));
  await assertFails(getDoc(doc(bob(), path('alice', 'categoryRules', 'r1'))));
  await assertFails(setDoc(doc(bob(), path('alice', 'categoryRules', 'rb')), rule('rb')));
  await assertFails(setDoc(doc(anon(), path('alice', 'categoryRules', 'ra')), rule('ra')));
  // edits: category, account, enabled, with a higher version
  await assertSucceeds(updateDoc(r, { categoryId: 'coffee-dining', enabled: false, version: 2, updatedAt: 1790000000500 }));
  await assertFails(updateDoc(r, { enabled: true, updatedAt: 1790000000600 }));                    // same version
  await assertFails(updateDoc(r, { enabled: true, version: 2, updatedAt: 1790000000600 }));        // version not raised
  await assertFails(updateDoc(r, { version: 1, enabled: true }));                                 // lowered
  await assertFails(updateDoc(r, { merchantKey: 'keells', version: 3 }));                         // the name is fixed
  await assertFails(updateDoc(r, { displayName: 'Keells', version: 3 }));
  await assertFails(updateDoc(r, { createdAt: 1, version: 3 }));
  await assertFails(deleteDoc(r));
});

test('category rules: invalid documents are rejected', async () => {
  const p = (id) => doc(alice(), path('alice', 'categoryRules', id));
  await assertFails(setDoc(p('a'), rule('a', { version: 2 })));                  // a new rule starts at 1
  await assertFails(setDoc(p('b'), rule('wrong-id')));                           // id mismatch
  await assertFails(setDoc(p('c'), rule('c', { extra: 'x' })));                  // schema pollution
  await assertFails(setDoc(p('d'), rule('d', { enabled: 'yes' })));
  await assertFails(setDoc(p('e'), rule('e', { merchantKey: '' })));
  const noCategory = rule('f');
  delete noCategory.categoryId;
  await assertFails(setDoc(p('f'), noCategory));                          // a category is required
  await assertFails(setDoc(p('g'), rule('g', { createdAt: 0 })));
});

test('corrections can set and clear time but reject malformed values', async () => {
  const write = (id, time) => setDoc(doc(alice(), path('alice', 'corrections', id)), { id, transactionId: 't1', changes: { time }, correctedAt: 1790000000001 });
  await assertSucceeds(write('set-time', '18:30'));
  await assertSucceeds(write('clear-time', null));
  await assertFails(write('invalid-time', '25:61'));
  await assertFails(write('seconds', '18:30:59'));
});

test('account corrections require an owned active account in the stored currency', async () => {
  const db = alice();
  const account = (id, code = 'LKR', archived = false) => ({ id, name: id, type: 'BANK', currency: { code, kind: 'FIAT', digits: 2 }, opening: { status: 'UNKNOWN' }, openingDate: '2026-10-01', archived, updatedAt: 1790000000000 });
  await setDoc(doc(db, path('alice', 'transactions', 'move')), tx('move'));
  await setDoc(doc(db, path('alice', 'accounts', 'cash')), account('cash'));
  await setDoc(doc(db, path('alice', 'accounts', 'usd')), account('usd', 'USD'));
  await setDoc(doc(db, path('alice', 'accounts', 'archived')), account('archived', 'LKR', true));
  await setDoc(doc(bob(), path('bob', 'accounts', 'bob-only')), account('bob-only'));
  const write = (id, accountId, extra = {}, transactionId = 'move') => setDoc(doc(db, path('alice', 'corrections', id)), { id, transactionId, changes: { accountId, ...extra }, correctedAt: 1790000000001 });
  await assertSucceeds(write('move-cash', 'cash', { time: '18:30' }));
  await assertFails(write('move-foreign', 'usd'));
  await assertFails(write('move-archived', 'archived'));
  await assertFails(write('move-missing', 'missing'));
  await assertFails(write('move-bob', 'bob-only'));
  await assertFails(write('move-wrong-currency', 'cash', { amount: { ...lkr(100), currency: 'USD' } }));
  const refund = tx('refund', { type: 'REFUND', refundOf: 'move' });
  delete refund.spendingClass;
  await setDoc(doc(db, path('alice', 'transactions', 'refund')), refund);
  await assertFails(write('move-refund', 'cash', {}, 'refund'));
  await assertFails(updateDoc(doc(db, path('alice', 'transactions', 'move')), { accountId: 'cash' }));
});
