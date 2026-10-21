# D06 verification record

What was run for SCRUM-19 (authenticated quick-note drafts), when, and what was **not** verified. Run on 21 October 2026 on a macOS dev machine (Node 26.7, Gradle's configured JDK, Firebase CLI 15.x via `functions/` dev dependency). Backend follow-up detail is in [backend-validation.md](backend-validation.md).

## Commands and results

| Check | Command | Result |
|---|---|---|
| Backend unit and contract tests | `cd functions && npm ci && npm test` | 49 passed, 0 failed |
| Functions emulator contract | `cd functions && npm run test:emulator` | passed: unauthenticated request rejected (401); seven invalid requests rejected (400 `INVALID_INPUT`); no note content or Secret Manager access in output |
| Android unit tests | `./gradlew testDebugUnitTest assembleDebug compileReleaseKotlin` | 406 passed, 0 failed (68 in `features/quicknote`; includes `DomainLayerPurityTest`) |
| Debug build | `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| Release compile | `./gradlew compileReleaseKotlin` | BUILD SUCCESSFUL (Play Integrity installer only) |
| Dependencies per variant | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` (and `debugRuntimeClasspath`) | `firebase-appcheck-debug` appears in debug only |
| OpenSpec | `openspec validate authenticated-quick-note-drafts --strict` | valid |
| OpenSpec stays untracked | `git check-ignore -v openspec` | ignored by `.gitignore` |

The initial subagent build used ignored local Firebase configuration. The final parent build used an ignored `local.properties` and no `app/google-services.json`; compilation succeeds without runtime Firebase initialization. Neither configuration file is committed.

## What the tests cover

- **Auth and App Check** (`functions/test/handler.test.js`, `config.test.js`): missing user, missing App Check, emulator-only relaxation, and that the provider is never called on a rejected request. `enforceAppCheck` is true unless `FUNCTIONS_EMULATOR`.
- **Input bounds** (`validation.test.js`): blank, 501 characters, non-text, unknown keys (balances, raw messages), account shape and count, impossible dates.
- **Provider** (`provider.test.js`, fake `fetch`): D05 request (model, temperature 0, minimal thinking, schema, key only in the header), 429, 5xx, 403/404, timeout, real abort on a slow provider, blocked/truncated/empty answers.
- **Response validation** (`response.test.js`): invalid JSON, bad shape, more than five proposals, unknown kinds and enums, unknown fields dropped, size limits.
- **Privacy** (`handler.test.js`, `sideEffects.test.js`): log output is inspected for note, merchant, account and user identifiers; error messages are content-free; server code contains no storage, queue or `console` use.
- **Prompt parity** (`promptSync.test.js`): deployed prompt and schema equal `tools/quicknote-eval/prompt.mjs`.
- **App mapping** (`QuickNoteDraftMapperTest`, `AmountGroundingTest`): actual, expected and transfer proposals; fiat-to-crypto transfer; no received-equals-sent default across currencies; unknown and same-account references; currency mismatch and explicit invalid currency with a known account; exact money (signs, exponents, separators, precision, overflow); grounding with k, lakh, mn, Sinhala, Tamil, Arabic-Indic and full-width digits; range midpoint/endpoints become missing unless independently stated; expense-only spending class; refund flag; valid dates without invented calendar cutoffs; malformed optional values; invalid top level.
- **Use case and repository** (`GenerateQuickNoteDraftUseCaseTest`, `CallableQuickNoteDraftRepositoryTest`, `CallableErrorMapperTest`): input errors, minimal account context, typed auth and network failures, unexpected exceptions without their text, cancellation propagation including a shorter caller deadline, eight-second total budget including account context (and success at 7,999 ms for the repository call), fresh draft identity, signed-out short circuit, error-code mapping.

## Not verified

- **No live Gemini call.** The provider is tested against a fake `fetch` only. D05 measured accuracy and latency; D06 did not re-measure them.
- **Nothing deployed.** The secret `GEMINI_API_KEY` was not created, App Check was not registered, and no function was deployed. The final emulator wrapper avoids Secret Manager access using a temporary placeholder; the contract script does not need a real key.
- **No device or emulator run of the app.** App Check (Play Integrity and debug) and the callable client were compiled, not exercised. `FirebaseQuickNoteDraftTransport` and `AppCheckInstaller` have no unit tests because they wrap Firebase classes; their error mapping is covered through `CallableErrorMapper`.
- **No billing or quota check** on the Gemini account.
- **SmartRail agents** were not consulted: the local server was unreachable (exit 3). No persisted-draft retention period was invented; generated drafts live only in the caller's memory.
- **Emulator test scope.** The Functions emulator decodes but does not verify tokens, so it proves routing, error shape and pre-provider rejection, not production token verification (that is Firebase's own code with `enforceAppCheck: true`).

## Final PR review

The subsequent review integrated current main, fixed session-change handling and wire-version validation, and reran backend checks on Node 22. See [pr-review.md](pr-review.md) for the final 575 Android tests, 49 backend tests and emulator/dependency checks. Counts and Node 26 notes above describe the earlier checkpoint. No screens changed.
