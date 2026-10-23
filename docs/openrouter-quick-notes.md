# Quick notes: Cloudflare Worker + OpenRouter

The Android app calls an authenticated Cloudflare Worker. The Worker calls OpenRouter with the server's key,
validates the output, and returns the same version 1 proposals used by the review screen. Generating a draft
does not save any transaction. Firebase Authentication and Firestore continue to serve the rest of Zar.

This replaces the Firebase callable + Secret Manager + direct Gemini deployment described in
[the original D06 service document](ai-quick-notes-service.md). Quick-note generation no longer needs Blaze,
Secret Manager, a Gemini API key, AI Studio credit, a custom domain, a Pages project, or a running tunnel.
The existing `functions/` entrypoint is retained as legacy code; do not deploy it for the new Android client.

## One-time setup

Requirements: Node 22.12+ (or a supported newer version), an existing Cloudflare account, and an OpenRouter
key with credit. `cloudflared` manages tunnels; this deployment uses **Wrangler**, installed in `worker/`.
Stay on Workers Free; this service uses no paid storage. OpenRouter charges separately for model usage.

From the repository root:

```sh
cd worker
npm ci
npx wrangler login
npx wrangler whoami
python3 scripts/set-key.py
npm run check
npm test
npx wrangler deploy --dry-run
npm run deploy
```

Enter the OpenRouter key at the hidden terminal prompt. The helper writes `worker/.dev.vars` privately with
owner-only file permissions; this file is git-ignored. `npm run deploy` passes that file directly to Wrangler
for an encrypted Worker secret, never to the Android app or `wrangler.jsonc`. The first deployment needs the
secrets file because the Worker does not exist yet. Later key rotations can also use
`npx wrangler secret put OPENROUTER_API_KEY`. Use a dedicated Zar key with a small spending limit in OpenRouter.
If Cloudflare offers several accounts, select the account containing Zar's Worker.

Wrangler prints the deployment's HTTPS `workers.dev` URL. Configure and build Android with:

```sh
npm run configure:android -- https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev
cd ..
./gradlew installDebug
```

The configuration script writes only the public endpoint to `gradle.properties`; commit that change so other
developers use the same backend. Alternatively override `-Pzar.quickNoteEndpoint=https://.../v1/quick-note`.
A build with no URL can still run/manual-entry, but AI generation returns ServiceUnavailable without sending
the note. The build validates configured URLs. Release builds require HTTPS.

The deployed Zar endpoint is `https://zar-quick-notes.03dulain.workers.dev/v1/quick-note`, already configured
in the repository's `gradle.properties`.

The service has two routes:

- `GET /health`: returns `{ "status": "ok", "version": 1 }`, without making a paid model call.
- `POST /v1/quick-note`: requires `Authorization: Bearer <Firebase ID token>`, `X-Firebase-AppCheck: <token>`,
  and a JSON body containing only `note`, `today`, and `accounts`.

## Firebase authentication and App Check

The Worker validates Firebase RS256 signatures, token expiration, issuer, audience, issue/authentication time,
and nonempty user ID against Google's public keys. It validates App Check separately for this project number
and exact Android app ID. No Firebase service-account key or Google billing account is needed for public-key
verification. There is no production bypass for missing App Check or unsigned Auth emulator tokens.

In Firebase for `zar-app-35602`:

1. Enable the Firebase App Check API if disabled.
2. Register the Android app in App Check. Production uses Play Integrity; complete its signing/Play setup.
3. For a debug emulator, register its debug token from Logcat (`DebugAppCheckProvider`) under App Check's
   Manage debug tokens. Keep that token private. Restart the app after registration/backoff.
4. If Firebase reports `INVALID_REFRESH_TOKEN`, sign out and sign back in. A cached user is not proof that
   the backend will accept their session.

Auth/Firestore emulator builds remain useful for other features, but their unsigned Auth tokens cannot call
the deployed Worker. Unit tests inject verified identities; production verification is never relaxed for tests.

## Model and privacy

`worker/wrangler.jsonc` pins `google/gemini-3.5-flash-lite`, the OpenRouter ID for Zar's evaluated Gemini model.
Changing models requires rerunning the multilingual evaluation. There is no automatic model fallback.

The adapter converts the existing Gemini schema into strict JSON Schema, retains the original system prompt,
asks for minimal reasoning, caps output at 2,048 tokens, and accepts only complete non-refused answers. Routing
requires parameter support and `data_collection: "deny"`. Backend and app independently validate the result.
Provider support/privacy can vary; OpenRouter account prompt logging should remain off.

The provider deadline is 6.5 seconds within the app's existing 8-second total budget. A timeout leaves the note
available for retry or manual entry. Requests/responses are bounded to 32/64 KiB. The app cancels its HTTP call
when the screen cancels and never follows redirects with its Firebase tokens.

Each verified UID is limited to approximately 10 requests/minute per Cloudflare location. This reduces abuse;
it is not a global hard spending cap. Use the OpenRouter key's spending limit for that. Validation and token
checks run before any model call. Logs contain only outcome, reason, duration, proposal count and token counts;
no note, account identifiers, user IDs, tokens, API keys, prompts or model text. Cloudflare invocation logs are
disabled, and responses specify `Cache-Control: no-store`. The Worker writes no notes or drafts to storage.

## Verification and evaluation

```sh
cd worker
npm run types
npm run check
npm test
npx wrangler deploy --dry-run
cd ../functions
npm ci
npm test
cd ..
./gradlew testDebugUnitTest --tests 'com.example.zar.features.quicknote.*' assembleDebug
```

For the live provider evaluation, copy `worker/.dev.vars.example` to `worker/.dev.vars`, enter the key privately
in that ignored file, and run `npm run evaluate` from `worker/`. This spends OpenRouter credit on the 20 made-up
notes in `tools/quicknote-eval/cases.json` (English, Sinhala, Tamil and mixed notes). The runner uses the exact
production adapter and response validator and reports timing, errors, and field-level matches. It writes only
case IDs, differences and summary metrics under `tools/quicknote-eval/results/openrouter-*.json`; no keys/tokens.
Changing the model through `OPENROUTER_MODEL` is supported for evaluating a candidate before changing deployment.

Deployment success, `/health`, unit tests and model evaluation are different checks: a successful deploy/health
check does not prove the key, billing, Firebase session or App Check registration is working. Final verification
must open Quick note in an ordinary signed-in build, interpret a made-up note, inspect the draft, and check that
generation alone added no records. Do not save a test transaction to a real account without intending to keep it.

## Troubleshooting

- **401 / UNAUTHENTICATED:** sign in again; ensure the app and Worker target the same Firebase project.
- **403 / APP_CHECK:** enable/register App Check, including the emulator's debug token; keep enforcement enabled.
- **429 / RATE_LIMITED:** wait a minute; either the Worker or OpenRouter rate limit was reached.
- **503 / PROVIDER_UNAVAILABLE:** check `wrangler secret list`, the configured model, and OpenRouter key balance.
  Provider details are deliberately not forwarded to the app/logs.
- **504 / TIMEOUT:** retry or use manual entry; compare evaluation latency before changing the budgets.
- **502 / BAD_RESPONSE:** the model produced incomplete/invalid JSON or an unsupported proposal; evaluate the model.

References: [Worker secrets](https://developers.cloudflare.com/workers/configuration/secrets/),
[Workers Free pricing](https://developers.cloudflare.com/workers/platform/pricing/),
[OpenRouter structured outputs](https://openrouter.ai/docs/guides/features/structured-outputs),
[Firebase token verification](https://firebase.google.com/docs/auth/admin/verify-id-tokens),
[App Check verification](https://firebase.google.com/docs/app-check/custom-resource-backend).
