// The endpoint is public configuration, safe to commit. No key or token is read by this script.
import { readFileSync, writeFileSync } from 'node:fs';
const url = new URL(process.argv[2]);
if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) throw new Error('Supply the HTTPS workers.dev URL printed by wrangler deploy.');
url.pathname = '/v1/quick-note';
const path = new URL('../../gradle.properties', import.meta.url);
const old = readFileSync(path, 'utf8');
const line = `zar.quickNoteEndpoint=${url.href}`;
const next = /^zar\.quickNoteEndpoint=.*$/m.test(old) ? old.replace(/^zar\.quickNoteEndpoint=.*$/m, line) : `${old.trimEnd()}\n\n# Public Cloudflare endpoint. The OpenRouter key is stored only in Worker secrets.\n${line}\n`;
writeFileSync(path, next);
console.log(`Configured Android quick notes: ${url.href}`);
