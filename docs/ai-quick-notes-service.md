# AI quick-note drafts: service and app integration (D06)

The question behind this ticket: how does a typed note become an editable draft without leaking private money data or skipping review?

**Answer:** the app calls one authenticated backend function. It returns *suggestions only*. The app maps them to the shared `Draft` types, and the review flow (S07) decides what gets saved. Decisions on hosting, model and cost are in [ai-quick-notes-feasibility.md](ai-quick-notes-feasibility.md) (D05). They are unchanged here.

## What exists

| Piece | Where |
|---|---|
| Callable `createQuickNoteDraft` (asia-south1) | `functions/` |
| Prompt and output schema (mirror of `tools/quicknote-eval/prompt.mjs`) | `functions/src/prompt.js` |
| Domain contract, use case, typed errors | `app/.../features/quicknote/domain` |
| Callable client, response mapper, repository | `app/.../features/quicknote/data` |
| Hilt bindings | `di/RepositoryModule`, `di/FirebaseModule` |
| App Check (production and debug) | `core/data/remote/AppCheckInstaller` in `src/release` and `src/debug` |

No UI was added. S07 (SCRUM-41) owns the review and save screens.

## Wire contract

Request (`data` of the callable). Unknown keys are rejected, so balances or raw messages cannot be forwarded by accident.

```json
{
  "note": "dialog bill eka 2450 gewwa card eken",
  "today": "2026-10-21",
  "accounts": [{ "id": "boc", "name": "BOC Savings", "type": "BANK", "currency": "LKR" }]
}
```

Limits: note 1 to 500 characters after trimming; at most 30 accounts; account name up to 80 characters; `type` one of `BANK`, `CASH`, `FREELANCE`, `CRYPTO`; `today` a real `yyyy-MM-dd` date.

Response:

```json
{ "version": 1, "proposals": [ { "kind": "ACTUAL_TRANSACTION", "amount": "2450", "currency": "LKR", "accountId": "boc",
  "transactionType": "EXPENSE", "date": "2026-10-21", "spendingClass": "COMMITTED", "category": "bills",
  "uncertainFields": [] } ] }
```

Zero to five proposals. Zero is a success ("no money movement"). Every field is validated on the server (kinds, enums, sizes) and again in the app. An answer that is not valid JSON, has an unknown kind or more than five proposals fails the whole request.

### Failures

The server sends a Firebase error code plus `details.reason`. Messages are fixed text.

| `reason` | Code | App error | Retry? |
|---|---|---|---|
| `UNAUTHENTICATED` | `unauthenticated` | `NotSignedIn` | no |
| `APP_CHECK` | `failed-precondition` | `AppNotVerified` | no |
| `INVALID_INPUT` | `invalid-argument` | `InvalidInput(REJECTED)` | no |
| `RATE_LIMITED` | `resource-exhausted` | `RateLimited` | yes |
| `TIMEOUT` | `deadline-exceeded` | `TimedOut` | yes |
| `PROVIDER_UNAVAILABLE` | `unavailable` | `ServiceUnavailable` | yes |
| `BAD_RESPONSE` | `internal` | `InvalidResponse` | yes |
| none (no network) | `unavailable` | `Offline` | yes |

App Check failures are normally rejected by Firebase before our code runs and arrive without a `reason`. Those map from the Firebase code (`unauthenticated`, `permission-denied`).

Every failure leaves manual entry usable. The caller still holds the user's text.

## Security and privacy

- **Auth + App Check.** `onCall` is deployed with `enforceAppCheck: true`; the handler also rejects a missing user or app token before validating or calling Gemini. Only local emulator runs (`FUNCTIONS_EMULATOR=true`) relax App Check, because the emulator cannot attest.
- **Secret.** `GEMINI_API_KEY` is a managed secret (Cloud Secret Manager). It is read inside a request after auth and validation, sent only in the `x-goog-api-key` header, and never returned or logged. The app has no provider key.
- **No storage.** The function has no Firestore, Storage, queue or analytics code (a test guards this). The app keeps no copy: the repository returns the draft and holds nothing. The `Draft` has no raw-text field.
- **No content logging.** The only log lines are `{outcome, reason, latencyMs, proposals, promptTokens, outputTokens}`. Provider error bodies and exception messages are dropped, because they can echo the note. Tests check log output for note, merchant, account and user identifiers.
- **Prompt injection.** The note travels as JSON data in the user turn, not in the system instruction. Output is validated regardless of what the model says.
- **No SMS.** Raw bank messages never go through this service.

## Model

Gemini 3.5 Flash-Lite (`gemini-3.5-flash-lite`), `generateContent` REST (v1beta), `thinkingLevel: minimal`, schema-constrained JSON, 2,048 output tokens, 6.5 s provider budget inside the app's 8 s. The model and evaluated prompt are unchanged. The current Google guide says sampling parameters such as `temperature` are deprecated and ignored for this model, so the service omits them; D05's historical harness still sent temperature 0. Google's [model guide](https://ai.google.dev/gemini-api/docs/generate-content/whats-new-gemini-3.6), checked on 21 October 2026, lists this model for production use with `minimal` thinking. This documentation check is not a live API test. If the model is retired or unavailable to the account, requests fail as `PROVIDER_UNAVAILABLE`. The service does **not** switch models on its own; changing it needs a new evaluation (`tools/quicknote-eval`).

## Setup (one-time, by an owner of `zar-app-35602`)

Not done by this change. Nothing was deployed.

1. Add Gemini prepaid credit (see D05) and create an API key.
2. `firebase functions:secrets:set GEMINI_API_KEY` (paste the key when prompted).
3. Firebase console, Security, App Check: register the Android app with **Play Integrity** (needs the app linked to Google Play and the SHA-256 of the signing certificate). Watch the metrics, then enforce for Cloud Functions. The function already rejects calls without a token, so enforce only after a release build with App Check is in testers' hands.
4. Debug builds: run the app once and copy the debug token printed in Logcat (`DebugAppCheckProvider`) into App Check, Manage debug tokens. Never commit it. Release builds do not contain the debug provider.
5. `cd functions && npm ci`, then `npx firebase deploy --only functions:createQuickNoteDraft`.

### Local development

```bash
cd functions
npm ci
npm test                 # unit and contract tests, no network, no Gemini
npm run test:emulator    # starts the Functions emulator, checks auth and input rejection, shuts down
```

The emulator contract uses `demo-zar` and creates a placeholder `.secret.local` only when none exists. It removes that placeholder afterward, preserves any existing local secret, and checks emulator output for note content and Secret Manager access. It sends only rejected requests and makes no live Gemini call.

To try the whole flow locally, put a throwaway key in `functions/.secret.local` (`GEMINI_API_KEY=...`, git-ignored), run `npm run serve`, and build the app with `./gradlew installDebug -Pzar.useEmulators=true`. Debug builds then use the Auth (9099), Firestore (8080) and Functions (5001) emulators. That calls the real Gemini API with your key and costs a little: use made-up notes.

## Using it from S07

```kotlin
@HiltViewModel
class QuickNoteViewModel @Inject constructor(
    private val generate: GenerateQuickNoteDraftUseCase,
) : ViewModel() {
    // The caller owns the text. Keep it in memory only.
    fun interpret(note: String) = viewModelScope.launch {
        generate(note)
            .onSuccess { draft -> /* show draft.proposals as cards */ }
            .onFailure { error ->
                // error is a QuickNoteDraftError; keep `note` and open the manual form with it.
                val canRetry = (error as? QuickNoteDraftError)?.isRetriable == true
            }
    }
}
```

`GenerateQuickNoteDraftUseCase.invoke(note: String): Result<Draft>` does the following.
- It trims and validates the note.
- It captures the signed-in user, reads their non-archived accounts and sends only id, name, type and currency. If the user signs out or changes while accounts load, it sends nothing; if the session changes during generation, the late result is discarded. The repository also guards its own in-flight result.
- It calls the repository. Coroutine cancellation is rethrown and never turned into a result.
- Its eight-second deadline covers waiting for accounts and generation together; a caller's shorter deadline still cancels the operation.

The returned `Draft` has `source = QUICK_NOTE`, `status = PENDING_REVIEW`, a new `DraftId`, `createdAt` and structured proposals. Use `draft.recordIdFor(index)` when saving a confirmed proposal so a double tap saves once.

### What S07 must do

- **Hold the note yourself, in memory.** It is not stored anywhere by this feature. Clear it when the user discards, when saving finishes and on sign-out (add a `SignOutHandler`). On any failure, pass it into the manual form.
- **Persist only what the user confirms,** through the existing save use cases. Generation never saves a draft or a record. Whether and how long to keep unconfirmed drafts is S07's decision; D06 set no retention period.
- **Show `missingFields` and `uncertainFields`.** Missing blocks Save. Uncertain is highlighted only.
- **Refunds.** `proposal.requiresPurchaseSelection` is true for a `REFUND`. The user must pick the original purchase (`refundOf`); the draft contract has no field for it.
- **Transfers.** A cross-currency transfer arrives with `received = null` unless the note stated it. `AMOUNT` is then missing; ask the user. The received amount equals the sent amount only when the destination account is known and uses the same currency.
- **Expenses** always have a `spendingClass` (`UNKNOWN` when the model gave none). Income, refunds and transfers never do.
- **Categories** arrive as a short lowercase word (`food`, `bills`) in `categoryId`, not as an id from a category list. Map it or ignore it.
- **Dates.** A date the model did not give stays missing; actual and transfer dates after today are cleared. S07 may prefill today as a visible default.

## How model output is checked in the app

`QuickNoteDraftMapper` treats the answer as untrusted:

- Unknown account ids become missing; a transfer from an account to itself clears both sides.
- Amounts must match `\d+(\.\d+)?`, parse exactly through `Money.parse` and be positive. Too many decimals, signs, exponents, separators and overflow become missing. There is no floating point.
- The amount's currency must equal a known account's currency, otherwise the amount is cleared. A missing currency falls back to the known account, never to LKR. An explicitly invalid currency is cleared rather than replaced.
- An amount inferred only from a numeric range or alternatives (such as `2-3k` or `1000 or 1500`) becomes missing, including guessed endpoints. An independently stated amount in the same note remains usable.
- **Grounding.** An amount not written in the note is kept but `AMOUNT` is flagged uncertain. The note is scanned for numbers in any script's digits (Latin, Sinhala, Tamil, Arabic-Indic, full-width) with `k`, `thousand`, `lakh/lakhs/lac/lk`, `mn/m/million` and `cr/crore` applied. Number words and magnitude words that are not listed ("ලක්ෂ", "ஆயிரம்") are not understood, so those amounts are flagged for review. That is safe, just noisy.
- Only `INCOME`, `EXPENSE` and `REFUND` are accepted as types. Spending class applies to expenses only.
- An invalid top level (not a map, missing/unsupported wire `version`, no `proposals` list, more than five proposals, unknown kind) is `InvalidResponse`. Version 1 is the supported contract.

## Limits to be honest about

- Accuracy is the D05 result (about 94% of drafts fully correct on 20 notes). The review step exists for the rest. Nothing in D06 changes the model's accuracy.
- The first request after idle can be slower because of a function cold start. The 8 s app budget includes it, and a miss falls back to manual entry. Cold-start time was not measured.
- The server has no per-user rate limit beyond `maxInstances: 10` and App Check. Add one if cost or abuse appears.
- The Android App Check provider and Play Integrity registration need a real device or Play-linked build to confirm end to end. That was not done here.
