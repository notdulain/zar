import test from 'node:test';
import assert from 'node:assert/strict';
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from 'jose';
import { verifyApp, verifyUser } from '../src/auth';

const { privateKey, publicKey } = await generateKeyPair('RS256');
const jwk = { ...await exportJWK(publicKey), kid: 'test' };
const keys = createLocalJWKSet({ keys: [jwk] });
const now = Math.floor(Date.now() / 1000);
const sign = (overrides = {}) => new SignJWT({ sub: 'user-1', aud: 'zar-app-35602', iss: 'https://securetoken.google.com/zar-app-35602', iat: now, exp: now + 3600, auth_time: now - 10, ...overrides }).setProtectedHeader({ alg: 'RS256', kid: 'test' }).sign(privateKey);

test('accepts a signed Firebase ID token for this project', async () => {
  assert.equal(await verifyUser(await sign(), 'zar-app-35602', keys), 'user-1');
});

for (const [label, claims] of Object.entries({
  'wrong project': { aud: 'other-project' }, 'wrong issuer': { iss: 'https://evil.example' },
  expired: { exp: now - 60 }, 'future issue time': { iat: now + 3600 },
  'future authentication': { auth_time: now + 3600 }, 'empty uid': { sub: '' },
  'missing authentication': { auth_time: undefined }, 'overlong uid': { sub: 'x'.repeat(129) },
})) {
  test(`rejects Firebase auth: ${label}`, async () => {
    await assert.rejects(() => sign(claims).then(t => verifyUser(t, 'zar-app-35602', keys)), { reason: 'UNAUTHENTICATED' });
  });
}

test('rejects an unsigned emulator token on the deployed verification path', async () => {
  const unsigned = `${Buffer.from('{"alg":"none"}').toString('base64url')}.${Buffer.from('{"sub":"user-1"}').toString('base64url')}.`;
  await assert.rejects(() => verifyUser(unsigned, 'zar-app-35602', keys), { reason: 'UNAUTHENTICATED' });
});

const appClaims = { iss: 'https://firebaseappcheck.googleapis.com/588119468948', aud: ['projects/588119468948'], sub: 'android-app-id' };
test('accepts a signed App Check token for the configured project and Android app', async () => {
  await verifyApp(await sign(appClaims), '588119468948', 'android-app-id', keys);
});
for (const claims of [{ aud: ['projects/other'] }, { sub: 'other-app' }, { iss: 'https://evil.example' }, { exp: now - 10 }]) {
  test(`rejects App Check: ${JSON.stringify(claims)}`, async () => {
    await assert.rejects(async () => verifyApp(await sign({ ...appClaims, ...claims }), '588119468948', 'android-app-id', keys), { reason: 'APP_CHECK' });
  });
}
