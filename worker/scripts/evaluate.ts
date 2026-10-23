// Uses ONLY the repository's made-up evaluation notes. Run: npm run evaluate
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { URL } from 'node:url';
import { createOpenRouterProvider } from '../src/provider';
import { parseProposals } from '../../functions/src/response.js';

const fixture = JSON.parse(readFileSync(new URL('../../tools/quicknote-eval/cases.json', import.meta.url), 'utf8'));
const config = JSON.parse(readFileSync(new URL('../wrangler.jsonc', import.meta.url), 'utf8'));
const apiKey = process.env.OPENROUTER_API_KEY;
if (!apiKey) throw new Error('Set OPENROUTER_API_KEY in worker/.dev.vars (git-ignored) before evaluating.');
const model = process.env.OPENROUTER_MODEL || config.vars.OPENROUTER_MODEL;
const provider = createOpenRouterProvider({ apiKey, model });
const fields = ['kind', 'transactionType', 'direction', 'amount', 'currency', 'accountId', 'toAccountId', 'date', 'dueDate', 'spendingClass', 'receivedAmount', 'receivedCurrency', 'title', 'time'];
type Proposal = Record<string, unknown>;
type Expected = Proposal & { lenient?: string[]; uncertain?: string[] };
const same = (field: string, want: unknown, got: unknown) => ['amount', 'receivedAmount'].includes(field)
  ? (want == null ? null : Number(want)) === (got == null ? null : Number(got)) : (want ?? null) === (got ?? null);

function score(expected: Expected[], actual: Proposal[]): string[] {
  const problems: string[] = [];
  if (expected.length !== actual.length) problems.push(`expected ${expected.length} proposals, got ${actual.length}`);
  const remaining = [...actual];
  for (const exp of expected) {
    let best = -1, hits = -1;
    remaining.forEach((p, i) => {
      const n = fields.filter(f => f in exp && same(f, exp[f], p[f])).length;
      if (n > hits) { best = i; hits = n; }
    });
    if (best < 0) { problems.push(`missing ${exp.kind}`); continue; }
    const got = remaining.splice(best, 1)[0];
    for (const f of fields) if (f in exp && !exp.lenient?.includes(f) && !same(f, exp[f], got[f])) problems.push(`${f}: want ${exp[f]}, got ${got[f] ?? null}`);
    for (const f of exp.uncertain ?? []) if (!(got.uncertainFields as string[] ?? []).includes(f)) problems.push(`should flag ${f} as uncertain`);
  }
  return problems;
}

const results: { id: string; latencyMs: number; problems?: string[]; error?: string }[] = [];
for (const c of fixture.cases) {
  const start = performance.now();
  try {
    const { text } = await provider.generate({ note: c.note, today: fixture.today, accounts: fixture.accounts });
    const proposals = parseProposals(text, { accounts: fixture.accounts });
    results.push({ id: c.id, latencyMs: Math.round(performance.now() - start), problems: score(c.expect, proposals) });
  } catch (error) {
    // Error messages from external libraries must never be echoed (may contain request/key data).
    const reason = error && typeof error === 'object' && 'reason' in error ? String(error.reason) : 'EVALUATION_FAILED';
    results.push({ id: c.id, latencyMs: Math.round(performance.now() - start), error: reason });
  }
  console.log(JSON.stringify(results.at(-1)));
}
const summary = { model, date: new Date().toISOString(), cases: results.length, passed: results.filter(r => !r.error && !r.problems?.length).length, errors: results.filter(r => r.error).length };
console.log(JSON.stringify(summary));
const directory = new URL('../../tools/quicknote-eval/results/', import.meta.url);
mkdirSync(directory, { recursive: true });
writeFileSync(new URL(`openrouter-${model.replaceAll('/', '-')}.json`, directory), JSON.stringify({ summary, results }, null, 2) + '\n');
if (summary.errors) process.exitCode = 1;
