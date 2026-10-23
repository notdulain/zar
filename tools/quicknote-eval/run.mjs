// Runs every quick-note case against a Gemini model and scores the drafts.
//   node run.mjs [model] [--via firebase|gemini]
// firebase: Firebase AI Logic proxy using the project's Firebase API key from app/google-services.json.
// gemini:   Gemini Developer API directly using GEMINI_API_KEY.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { systemInstruction, userPrompt, responseSchema } from './prompt.mjs';

const args = process.argv.slice(2);
const model = args.find((a) => !a.startsWith('--')) ?? 'gemini-3.5-flash-lite';
const via = args.includes('--via') ? args[args.indexOf('--via') + 1] : 'firebase';
const { today, accounts, cases } = JSON.parse(readFileSync(new URL('./cases.json', import.meta.url)));

function endpoint() {
  if (via === 'gemini') {
    return { url: `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, key: process.env.GEMINI_API_KEY };
  }
  const services = JSON.parse(readFileSync(new URL('../../app/google-services.json', import.meta.url)));
  const project = services.project_info.project_id;
  const client = services.client[0];
  return {
    url: `https://firebasevertexai.googleapis.com/v1beta/projects/${project}/models/${model}:generateContent`,
    key: client.api_key[0].current_key,
    headers: { 'X-Android-Package': client.client_info.android_client_info.package_name },
  };
}

const { url, key, headers = {} } = endpoint();
if (!key) throw new Error('No API key available');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const PACE_MS = Number(process.env.PACE_MS ?? 4500); // free tier allows ~15 requests/minute

async function draft(note, attempt = 0) {
  const body = {
    systemInstruction: { parts: [{ text: systemInstruction() }] },
    contents: [{ role: 'user', parts: [{ text: userPrompt({ note, today, accounts }) }] }],
    generationConfig: {
      temperature: 0,
      responseMimeType: 'application/json',
      responseSchema,
      thinkingConfig: { thinkingLevel: process.env.THINKING ?? 'minimal' },
    },
  };
  const started = performance.now();
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key, ...headers },
    body: JSON.stringify(body),
  });
  const ms = Math.round(performance.now() - started);
  const json = await res.json();
  if (res.status === 429) {
    // Per-minute limits say "retry in Ns"; daily limits do not and are not worth waiting for.
    const wait = Number(/retry in ([\d.]+)s/.exec(json.error?.message ?? '')?.[1]);
    if (attempt < 3 && wait > 0 && wait <= 60) {
      await sleep((wait + 1) * 1000);
      return draft(note, attempt + 1);
    }
    const daily = JSON.stringify(json.error?.details ?? '').includes('PerDay');
    throw new Error(`HTTP 429: ${daily ? 'daily free-tier quota used up' : 'rate limited'}`);
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${json.error?.message ?? JSON.stringify(json).slice(0, 300)}`);
  const text = json.candidates?.[0]?.content?.parts?.map((p) => p.text ?? '').join('') ?? '';
  return { ms, usage: json.usageMetadata ?? {}, output: JSON.parse(text) };
}

const num = (v) => (v == null ? null : Number(String(v).replace(/,/g, '')));
const FIELDS = ['kind', 'transactionType', 'direction', 'amount', 'currency', 'accountId', 'toAccountId', 'date', 'dueDate',
  'spendingClass', 'receivedAmount', 'receivedCurrency', 'title'];

function score(expected, actual) {
  const problems = [];
  if (expected.length !== actual.length) problems.push(`expected ${expected.length} proposals, got ${actual.length}`);
  const remaining = [...actual];
  for (const exp of expected) {
    // Pair with the remaining proposal that matches most expected fields.
    let best = -1, bestHits = -1;
    remaining.forEach((p, i) => {
      const hits = FIELDS.filter((f) => f in exp && same(f, exp[f], p[f])).length;
      if (hits > bestHits) { best = i; bestHits = hits; }
    });
    if (best < 0) { problems.push(`missing ${exp.kind}`); continue; }
    const got = remaining.splice(best, 1)[0];
    for (const f of FIELDS) if (f in exp && !(exp.lenient ?? []).includes(f) && !same(f, exp[f], got[f])) problems.push(`${f}: want ${exp[f]}, got ${got[f] ?? null}`);
    for (const u of exp.uncertain ?? []) if (!(got.uncertainFields ?? []).includes(u)) problems.push(`should flag ${u} as uncertain`);
  }
  return problems;
}

function same(field, want, got) {
  if (['amount', 'receivedAmount'].includes(field)) return num(want) === num(got);
  return (want ?? null) === (got ?? null);
}

const results = [];
for (const c of cases) {
  if (results.length) await sleep(PACE_MS);
  if (results.filter((r) => r.error?.includes('daily')).length >= 2) {
    results.push({ ...c, error: 'skipped: daily quota used up' });
    continue;
  }
  try {
    const r = await draft(c.note);
    const problems = score(c.expect, r.output.proposals ?? []);
    results.push({ ...c, ...r, problems });
    console.log(`${problems.length ? '✗' : '✓'} ${c.id.padEnd(14)} ${String(r.ms).padStart(5)} ms  ${problems.join('; ')}`);
  } catch (e) {
    results.push({ ...c, error: e.message });
    console.log(`! ${c.id.padEnd(14)} ${e.message}`);
  }
}

const ok = results.filter((r) => !r.error);
const latencies = ok.map((r) => r.ms).sort((a, b) => a - b);
const pct = (p) => latencies[Math.min(latencies.length - 1, Math.floor((p / 100) * latencies.length))];
const tokens = (k) => ok.reduce((s, r) => s + (r.usage[k] ?? 0), 0) / Math.max(ok.length, 1);
const summary = {
  model, via, thinking: process.env.THINKING ?? 'minimal', date: new Date().toISOString(), cases: results.length, errors: results.length - ok.length,
  passed: ok.filter((r) => r.problems.length === 0).length,
  latencyMs: { p50: pct(50), p90: pct(90), max: latencies.at(-1) },
  avgTokens: { prompt: Math.round(tokens('promptTokenCount')), output: Math.round(tokens('candidatesTokenCount')),
    thinking: Math.round(tokens('thoughtsTokenCount')), total: Math.round(tokens('totalTokenCount')) },
};
console.log('\n' + JSON.stringify(summary, null, 2));
mkdirSync(new URL('./results/', import.meta.url), { recursive: true });
writeFileSync(new URL(`./results/${model}${process.env.RUN_TAG ? '-' + process.env.RUN_TAG : ''}.json`, import.meta.url), JSON.stringify({ summary, results }, null, 2) + '\n');
