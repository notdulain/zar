# AI quick notes: feasibility (D05)

The question behind this ticket: can a typed rough note ("dialog bill eka 2450 gewwa card eken") reliably become an editable draft, without leaking private money data or skipping the review step?

**Answer: yes.** In testing, Gemini 3.5 Flash-Lite matched every expected field in 93% of drafts across English, Sinhala, Tamil and mixed notes. It costs well under one rupee per note and answers in about 2.5 s. There are two conditions: the project needs the paid tier, and the call needs a small backend. Details below.

## Recommendation

**Launch setup (decided: `zar-app-35602` moved to the Blaze plan on 14 October 2026):**

1. The app calls a **callable Cloud Function** (`createQuickNoteDraft`, region `asia-south1`).
2. The function **requires a signed-in Firebase user and App Check**. Callable functions verify both tokens automatically.
3. It rejects notes over 500 characters, then calls **Gemini 3.5 Flash-Lite** on the paid Gemini API, with the prompt and schema from `tools/quicknote-eval/prompt.mjs`, temperature 0 and minimal thinking.
4. It returns proposals only. **It never writes to Firestore**; the reviewed app flow saves records (S07).
5. It never logs the note or the response; only latency, token counts and error codes.

**Why not call Gemini straight from the app (Firebase AI Logic)?** It works, and these tests ran through it. But Firebase AI Logic can only check App Check, not which user is signed in, so it can't meet D06's "validate the user's Firebase authentication". Its AI monitoring also stores prompts in Cloud Logging by default.

**Gemini API billing:** new accounts are on **Prepay**, so Gemini answers only while prepaid credit is available. Buy credit in AI Studio (Billing, Buy credits; minimum $5). Until credit is added, every request fails with "prepayment credits are depleted", even on Blaze. $5 covers roughly 7,000 notes on Flash-Lite.

**Fallback if billing is unavailable:** use Firebase AI Logic from the app on the free tier, with App Check enforced and the AI monitoring prompt log excluded. Use it **only with made-up demo notes**: on the free tier, Google may use prompts to improve its products, and human reviewers may read them. App Check becomes mandatory for Firebase AI Logic on 2 November 2026.

## Measured results

The test set is 20 notes in `tools/quicknote-eval/cases.json`:
- 9 English
- 3 Sinhala script or romanised Sinhala, plus 1 Sinhala/English mix
- 1 Tamil script and 1 Tanglish
- ranges, "k" and "lakh" amounts, no-account notes, expected vs actual, transfers, a crypto buy, a refund, a note with no money, a prompt injection, and an ambiguous-tense note.

`node run.mjs <model>` scores each draft field by field. Raw outputs are in `tools/quicknote-eval/results/`.

| Model, thinking | Passed | p50 / p90 latency | Avg tokens in / out |
|---|---|---|---|
| gemini-3.5-flash-lite, minimal (run 1) | 20 / 20 | 2.5 s / 2.9 s | 887 / 163 |
| gemini-3.5-flash-lite, minimal (run 2) | 17 / 20 | 2.5 s / 3.2 s | 887 / 165 |
| gemini-3.5-flash-lite, low | 19 / 20 | 2.5 s / 3.4 s | 887 / 130 |
| gemini-3.5-flash-lite, low, **paid tier** | 19 / 20 | 1.9 s / 2.4 s | 887 / 137 |
| gemini-3.8-flash, low, **paid tier** | 19 / 20 | 4.2 s / 5.8 s | 887 / 106 (+94 thinking) |

Across the four Flash-Lite runs, 75 of 80 drafts matched every expected field (94%). On the paid tier it was also faster, at about 1.9 s. Gemini 3.8 Flash was no more accurate: it dropped both amounts of the crypto buy. It is also twice as slow and costs about twice as much per note (about $0.0014), so **Flash-Lite is the recommended model**. The other 4 still produced usable drafts with one wrong or empty field, which the user fixes during review.

Prompt v1 passed 13 of 17 completed notes. It dropped a transfer amount, guessed the midpoint of "2-3k" and didn't map "card". Prompt v2 fixed these. Later runs vary because the model is not fully deterministic even at temperature 0.

**Where it is reliable:** amounts in all scripts, "k" and "lakh", currency symbols (Rs, රු, ரூ, $), past vs future tense, splitting "got 150, other 150 later" into one actual and one expected proposal, transfers between named accounts, empty output for a note with no money, and ignoring "ignore all rules…".

**Where it wobbles** (these are why the review step matters):
- Mapping "my card" to the one card account: sometimes it leaves the account empty and flags it. Once, it guessed the card account for a note that named no account, but it flagged ACCOUNT as uncertain.
- The received side of crypto buys (50 USDT): sometimes dropped.
- Vague due dates ("next week"): sometimes a date, sometimes empty.
- "netflix 1500 this month" can read as paid or due; both readings get flagged.

**Cost on the paid tier:** Flash-Lite charges $0.30 per million input tokens and $2.50 per million output tokens. That's about 887 × 0.30/1M + 163 × 2.50/1M ≈ **$0.0007 per note**, around LKR 0.2. 1,000 notes cost about $0.70. Cloud Functions' no-cost quota covers the function calls.

**Paid tier verified 14 October 2026** with $5 of Gemini credit on `zar-app-35602`; both models answered with no rate limiting.

**Free-tier limits seen:** Flash-Lite allows 15 requests per minute and handled about 80 requests in a day without hitting a daily limit. Gemini 3.8 Flash allows 20 requests per day per project, so the free tier can't support it beyond a single tester.

## Privacy handling

- The app sends a note only when the user taps **Interpret**, and sends no account balances. It includes the account list (id, name, type, currency) so the model can match account names.
- On the paid tier, Google doesn't use prompts to improve products. The function stores and logs no note text. Turn off the AI monitoring prompt log (exclusion filter `resource.type="firebasevertexai.googleapis.com/Model"`) if Firebase AI Logic is ever used.
- Drafts saved to Firestore keep structured fields only. The rules already reject a `rawText` field.
- Raw bank SMS never goes to the model. That's the capture flow, which stays on the device (L08).

## Draft contract (to agree with Sanuk, S07)

The model returns `{ proposals: [...] }`. The app maps each proposal onto the existing `DraftProposal` types in `core/domain/model/Draft.kt`:

| Model `kind` | Domain type | Fields used |
|---|---|---|
| `ACTUAL_TRANSACTION` | `DraftProposal.ActualTransaction` | `transactionType` (INCOME, EXPENSE, REFUND), `amount` + `currency` → `Money`, `accountId`, `date`, `spendingClass`, `category` → category id, `merchant`, `note` |
| `EXPECTED` | `DraftProposal.Expected` | `direction`, `amount` + `currency`, `dueDate`, `title`, `accountId` |
| `TRANSFER` | `DraftProposal.TransferBetweenAccounts` | `accountId` → from, `toAccountId` → to, `amount`/`currency` → sent, `receivedAmount`/`receivedCurrency` → received (defaults to sent) |

How missing and uncertain fields work:

- **Missing** fields are empty and come from `missingFields`, which the domain computes; the model doesn't report them. The Save button stays disabled until the user fills them.
- **Uncertain** fields come from the model's `uncertainFields` (`DraftField` values). The UI highlights them, but they don't block saving.
- Several proposals from one note appear as separate cards. Each confirmed card saves with `draft.recordIdFor(index)`, so pressing save twice saves once.
- Refunds still need the user to pick the original purchase (`refundOf`); the model can't know it.

Guards the app adds before showing a draft, because model output is untrusted:
- Drop any `accountId` not in the user's list.
- Drop `spendingClass` unless it's an expense.
- Parse amounts with `Money.parse` and treat failures as missing.
- Mark an amount uncertain if its digits don't appear in the note after expanding k and lakh.
- Treat any error or timeout (8 s) as "couldn't interpret, enter manually", and keep the note text in the manual form.

## Out of scope

Whisper and voice input are not part of the launch. No feasibility failure was found, so there's nothing to escalate.

## Re-running the tests

```bash
cd tools/quicknote-eval
node run.mjs gemini-3.5-flash-lite                       # Firebase AI Logic, key from app/google-services.json
THINKING=low RUN_TAG=low node run.mjs gemini-3.8-flash   # other models or thinking levels
```

You need Node 20+, Firebase AI Logic switched on in the project, and the Generative Language API terms accepted.
