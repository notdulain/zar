# PR #17 review verification

Reviewed the complete diff against main on 21 October 2026. This PR adds no screens, navigation destinations or visible states, so Figma screen matching and UI screenshot evidence do not apply. The S07 review/edit/save screens and caller-held note cleanup remain a separate follow-up; this service never posts financial records.

## Fixes

- Merged current main and resolved `ZarApplication` by retaining both App Check provider installation and weekly reminder scheduling.
- Bound generation to the signed-in user through account loading and service completion. A sign-out/account switch before generation prevents forwarding stale account context; an in-flight old-session result is discarded. Added use-case and repository regressions.
- Reject missing, malformed or unsupported service wire versions before mapping a draft; version 1 remains supported, including Firebase's numeric representation.
- Fixed backend timeout fakes to model referenced I/O using cancellable timers. On the deployment runtime, Node 22, the previous fake left no referenced handles: 43 tests passed and 6 were cancelled. The corrected run executes all 49 tests with zero cancellations.
- Removed the ignored/deprecated `temperature` request parameter. Google's [model guide](https://ai.google.dev/gemini-api/docs/generate-content/whats-new-gemini-3.6) was checked during review: Gemini 3.5 Flash-Lite remains available with minimal thinking, and sampling parameters are ignored for this model. This changes neither the model nor the evaluated prompt/schema; no live model call or paid re-evaluation was made.
- Changed the README backend install recipe to `npm ci` to use the reviewed lockfile.

## Checks

The backend review uses Node **22.23.3**, matching the declared Node 22 deployment major. `npm ci` completed. `npm test` passes **49/49**, with **0 cancelled**. The Functions emulator contract on **demo-zar** passes: unauthenticated request rejected with 401; seven invalid requests rejected with 400; no note content or Secret Manager access in the captured output. No Gemini request, deployment or production secret was used.

The lockfile matches the manifest, uses npm registry resolutions with integrity hashes, and has no unexpected non-registry dependencies. `npm audit --omit=dev` reports **0 production vulnerabilities**. The existing seven development-only Firebase CLI transitive advisories are not changed by this review.

`./gradlew testDebugUnitTest lintDebug assembleDebug compileReleaseKotlin` passes: **575 Android unit tests**, including **73 quick-note tests**, with no failures or skips. Debug packaging and release Kotlin compilation pass. Android lint reports **0 errors** (56 existing dependency/resource advisories). Runtime dependency reports confirm `firebase-appcheck-debug` appears only in debug; release contains Play Integrity and the callable client. Earlier checkpoints in `README.md` and `backend-validation.md` are historical results, not this review's test counts.

## Remaining limits

No live Gemini request, successful attested callable on an Android device, deployment, production App Check registration, billing, quota or cold-start latency was verified. Per-user rate limiting and App Check replay protection are not implemented; App Check and maxInstances remain the existing abuse controls. App Check production enforcement is covered by the function configuration/handler contracts, not by local emulator attestation. S07 must clear caller-held notes on discard, completed save and sign-out and collect refund purchase selection before posting. No durable draft-retention policy was invented.
