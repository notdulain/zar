// Runs emulator/contract.mjs inside the Functions emulator (demo-zar only, no Gemini call, no deploy) and then proves
// the emulator output never contained a note. Run with `npm run test:emulator`.
import { spawn } from 'node:child_process';
import { existsSync, rmSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const secretFile = fileURLToPath(new URL('../.secret.local', import.meta.url));
const functionsDir = fileURLToPath(new URL('..', import.meta.url));
const marker = 'EMU-NOTE-SENTINEL-9013';

// A placeholder keeps the emulator from asking Secret Manager for the demo project. The contract only sends requests
// that are rejected before the provider, so this value is never used. An existing local secret is left untouched.
const createdPlaceholder = !existsSync(secretFile);
if (createdPlaceholder) writeFileSync(secretFile, 'GEMINI_API_KEY=emulator-placeholder-not-a-real-key\n');

let output = '';
const child = spawn('npx', ['firebase', 'emulators:exec', '--only', 'functions', '--project', 'demo-zar', '--config', '../firebase.json', 'node emulator/contract.mjs'], {
  cwd: functionsDir,
});
for (const stream of [child.stdout, child.stderr]) {
  stream.on('data', (chunk) => {
    output += chunk;
    process.stdout.write(chunk);
  });
}
child.on('close', (code) => {
  if (createdPlaceholder) rmSync(secretFile, { force: true });
  if (code !== 0) process.exit(code ?? 1);
  if (output.includes(marker)) {
    console.error('FAIL  emulator output contained note content');
    process.exit(1);
  }
  if (/secretmanager\.googleapis\.com/.test(output)) {
    console.error('FAIL  emulator tried to reach Secret Manager');
    process.exit(1);
  }
  console.log('ok  emulator output contains no note content and no Secret Manager access');
});
