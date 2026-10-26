#!/usr/bin/env node
// Benchmark-only fixture. Creates (or reuses) one demo account on the LOCAL Firebase emulators and seeds deterministic
// accounts and transactions so the signed-in Home/Activity/Plans journeys have something to scroll.
//
//   node tools/benchmark-seed.mjs            # auth :9099, firestore :8080, project demo-zar
//
// It talks only to 127.0.0.1 emulators and refuses any other host. There is no production auth bypass: the benchmark
// signs in through the real sign-in screen with the demo credentials below. Firestore is written with the emulator's
// "Bearer owner" admin token, which the emulator accepts and a live project never would.
const AUTH = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';
const STORE = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080';
for (const host of [AUTH, STORE]) {
  if (!/^(127\.0\.0\.1|localhost):\d+$/.test(host)) throw new Error(`refusing non-local host ${host}`);
}
// The app talks to the project in its google-services.json (the emulator keeps each project's data apart), not to the
// "demo-zar" project the instrumented SDK tests use. Only the non-secret project_id is read.
import { readFileSync } from 'node:fs';
const PROJECT = process.env.ZAR_FIREBASE_PROJECT ??
  JSON.parse(readFileSync(new URL('../app/google-services.json', import.meta.url))).project_info.project_id;
export const DEMO_EMAIL = 'benchmark@zar.test';
export const DEMO_PASSWORD = 'benchmark-pass-1';

async function authCall(path, body) {
  const res = await fetch(`http://${AUTH}/identitytoolkit.googleapis.com/v1/${path}?key=fake`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
  });
  return { ok: res.ok, json: await res.json() };
}

async function demoUid() {
  const body = { email: DEMO_EMAIL, password: DEMO_PASSWORD, returnSecureToken: true };
  let r = await authCall('accounts:signUp', body);
  if (!r.ok) r = await authCall('accounts:signInWithPassword', body);
  if (!r.ok) throw new Error(`auth emulator: ${JSON.stringify(r.json)}`);
  return r.json.localId;
}

// Firestore REST typed values.
function enc(v) {
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (Number.isInteger(v)) return { integerValue: String(v) };
  if (v && typeof v === 'object') return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, enc(x)])) } };
  throw new Error(`cannot encode ${v}`);
}

async function put(uid, collection, id, data) {
  const url = `http://${STORE}/v1/projects/${PROJECT}/databases/(default)/documents/users/${uid}/${collection}/${id}`;
  const res = await fetch(url, {
    method: 'PATCH', headers: { 'content-type': 'application/json', authorization: 'Bearer owner' },
    body: JSON.stringify({ fields: Object.fromEntries(Object.entries(data).map(([k, x]) => [k, enc(x)])) }),
  });
  if (!res.ok) throw new Error(`${collection}/${id}: ${res.status} ${await res.text()}`);
}

const lkr = { code: 'LKR', kind: 'FIAT', digits: 2 };
const money = (rupees) => ({ currency: 'LKR', kind: 'FIAT', digits: 2, minor: rupees * 100 });
const day = (offset) => {
  const d = new Date(); d.setDate(d.getDate() - offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const accounts = [
  { id: 'bench-bank', name: 'Commercial Bank', type: 'BANK', opening: 250000 },
  { id: 'bench-cash', name: 'Wallet', type: 'CASH', opening: 20000 },
];
const shops = [
  ['Keells', 'groceries', 'DISCRETIONARY', 6400], ['Uber', 'transport', 'DISCRETIONARY', 1850], ['Dialog', 'bills', 'COMMITTED', 2990],
  ['Coffee Bean', 'coffee-dining', 'DISCRETIONARY', 1250], ['Pharmacy', 'other', 'DISCRETIONARY', 3100], ['Cinema', 'subscriptions', 'DISCRETIONARY', 2200],
];

const uid = await demoUid();
for (const a of accounts) {
  await put(uid, 'accounts', a.id, {
    id: a.id, name: a.name, type: a.type, currency: lkr,
    opening: { status: 'KNOWN', amount: money(a.opening) }, openingDate: day(60), archived: false, updatedAt: Date.now(),
  });
}
let n = 0;
for (let i = 0; i < 48; i++) {
  const [merchant, category, spendingClass, rupees] = shops[i % shops.length];
  const id = `bench-expense-${String(i).padStart(3, '0')}`;
  await put(uid, 'transactions', id, {
    id, accountId: i % 3 === 0 ? 'bench-cash' : 'bench-bank', type: 'EXPENSE', amount: money(rupees + (i % 7) * 100),
    date: day(Math.floor(i / 2)), time: `${String(8 + (i % 12)).padStart(2, '0')}:${String((i * 7) % 60).padStart(2, '0')}`,
    conversion: { status: 'SAME' }, spendingClass, categoryId: category, merchant, source: 'MANUAL', createdAt: 1_790_000_000_000 + i * 1000,
  });
  n++;
}
for (let i = 0; i < 6; i++) {
  const id = `bench-income-${i}`;
  await put(uid, 'transactions', id, {
    id, accountId: 'bench-bank', type: 'INCOME', amount: money(85000 + i * 5000), date: day(i * 5), time: '09:30',
    conversion: { status: 'SAME' }, categoryId: 'salary', merchant: i % 2 ? 'Freelance client' : 'Employer', source: 'MANUAL',
    createdAt: 1_790_000_000_000 + 100_000 + i * 1000,
  });
  n++;
}
console.log(`seeded ${accounts.length} accounts and ${n} transactions for ${DEMO_EMAIL} (${uid})`);
