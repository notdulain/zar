import { existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const args = ['node_modules/wrangler/bin/wrangler.js', 'deploy'];
if (existsSync('.dev.vars')) args.push('--secrets-file', '.dev.vars');
const result = spawnSync(process.execPath, args, { stdio: 'inherit' });
process.exit(result.status ?? 1);
