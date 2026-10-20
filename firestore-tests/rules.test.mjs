import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, getDocs, collection, deleteDoc, updateDoc, writeBatch } from 'firebase/firestore';

let env;
const RULES = new URL("../firestore.rules", import.meta.url);
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-zar',
    firestore: { rules: readFileSync(RULES, 'utf8'), host: '127.0.0.1', port: 8080 },
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

test('owner can create and read a transaction', async () => {
  await assertSucceeds(setDoc(doc(alice(), path('alice', 'transactions', 't1')), tx('t1')));
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
