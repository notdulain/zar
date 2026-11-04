// Local-only acceptance fixture. Never targets a live Firebase service.
import './benchmark-seed.mjs';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
const project = JSON.parse(readFileSync(new URL('../app/google-services.json', import.meta.url))).project_info.project_id;
const require = createRequire(new URL('../firestore-tests/package.json', import.meta.url));
const { initializeTestEnvironment } = require('@firebase/rules-unit-testing');
const env = await initializeTestEnvironment({ projectId: project, firestore: { host: '127.0.0.1', port: 8080, rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') } });
await env.cleanup(); // Upload matching rules to the local app project; never clear its fixture data.
const auth = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake', {
  method: 'POST', headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ email: 'benchmark@zar.test', password: 'benchmark-pass-1', returnSecureToken: true }),
});
if (!auth.ok) throw new Error('Run benchmark-seed.mjs first');
const { localId: uid } = await auth.json();
function enc(x) {
  if (typeof x === 'string') return { stringValue: x };
  if (typeof x === 'number') return { integerValue: String(x) };
  if (Array.isArray(x)) return { arrayValue: { values: x.map(enc) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(x).map(([k,v])=>[k,enc(v)])) } };
}
const money = minor => ({ currency: 'LKR', kind: 'FIAT', digits: 2, minor });
for (const [collection,id,body] of [
  ['goals','macbook-fund',{ id:'macbook-fund', name:'Laptop fund', target:money(100000000), allocations:[{accountId:'bench-bank',amount:money(300000)}], updatedAt:1790000000000 }],
  ['goals','demo-phone',{id:'demo-phone',name:'New phone',target:money(36900000),allocations:[{accountId:'bench-bank',amount:money(200000)}],updatedAt:1790000000001}],
  ['planPreferences','main',{id:'main',mainGoalId:'demo-phone',updatedAt:1790000000002}],
]) {
  const res = await fetch(`http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents/users/${uid}/${collection}/${id}`, {
    method:'PATCH',headers:{'content-type':'application/json',authorization:'Bearer owner'},body:JSON.stringify({fields:Object.fromEntries(Object.entries(body).map(([k,v])=>[k,enc(v)]))}),
  });
  if(!res.ok) throw new Error(`${collection}/${id}: ${res.status}`);
}
console.log('Seeded legacy + secondary goal and explicit main selection on localhost only');
