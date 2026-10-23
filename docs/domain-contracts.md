# Zar domain contracts (D04)

These are the shared models, repository interfaces and saving rules that every feature builds on. The code is the source of truth: shared code is in `core/domain` (pure Kotlin) and `core/data` (Firestore). Read this page before starting a feature that saves or totals money.

## Record kinds: keep them separate

| Kind | Model | Counts in totals? | Changes after saving? |
|---|---|---|---|
| Actual transaction | `Transaction` | Yes, by type (see below) | Never. Use corrections and voids |
| Correction | `Correction` | It changes the transaction it points to | Write-once |
| Undo | `VoidMarker` | It removes the transaction from everything | Write-once and final |
| Transfer | `Transfer` saved as two `Transaction` legs | Never income or spending | Both legs saved and voided together |
| Expected payment | `ExpectedPayment` + `PaymentMatch` | Never | Editable; cancelled instead of deleted |
| Draft | `Draft` with `DraftProposal`s | Never, until the user confirms | Editable while pending |
| Captured evidence link | `EvidenceLink` | No. It links a message to an existing transaction | Write-once |

## Saving rules

1. **Stable IDs.** Create the ID once, when the user starts the action (`RecordIds.newId()`), and reuse it on every retry. For a draft proposal use `draft.recordIdFor(index)`; transfer legs are `<transferId>-out` and `<transferId>-in`.
2. **Write-once facts.** Transactions, corrections, voids and evidence links are created once. Firestore rules deny updates and deletes, so a replayed save can't overwrite an edit or revive an undo.
3. **Edits are corrections.** `CorrectTransactionUseCase` appends a `Correction`. The original, including imported SMS facts, is kept. `LedgerProjector` applies corrections oldest first.
4. **Undo is a void marker.** `VoidTransactionUseCase` voids both transfer legs together. Re-adding something means a new ID.
5. **Transfers are atomic.** `SaveTransferUseCase` writes both legs and any separate transfer-fee expense in one batch.
6. **Captured counterparts link; they don't post.** When an SMS describes money already recorded (e.g. a manual transfer leg), save an `EvidenceLink`, not a new transaction. Each evidence ID links to one transaction only.
7. **Offline first.** Repository saves return once Firestore has the write locally; sync happens later. Don't wait for the server in the UI.

## Money rules

- Fiat amounts are `FiatMoney` in integer minor units (LKR 1,250.50 = `125050`). Crypto amounts are `CryptoAmount` with exact decimals. Never use `Double`.
- Amounts on transactions are always positive. The direction comes from `TransactionType`.
- One account holds one currency. A transaction's amount must use its account's currency.
- Every record stores its `ReportingConversion` to LKR at save time: `SameCurrency`, `Converted` (with the rate used) or `Missing`. Old records keep their historical rate. A corrected amount reuses the same rate.
- Totals return `ReportingTotal(amount, unresolvedCount)`. Show `unresolvedCount > 0` as "missing rate"; never guess.

## Totals rules (`LedgerSummaryCalculator`)

- **Received** = `INCOME` only. Expected payments, unexplained credits (drafts), refunds, transfers and adjustments are excluded.
- **Spent** = `EXPENSE` split into committed, discretionary and unknown. Each expense is reduced by its linked refunds, capped at the purchase and counted in the purchase's period.
- `ADJUSTMENT_OUT` (cash catch-up, reconciliation) counts as **unknown** spending and sets `hasIncompleteData`.
- Voided entries are ignored everywhere.
- Opening balances are opening funds, not income (`AccountBalanceCalculator`). Opening balances can be `Known`, `Approximate` or `Unknown`.
- Expected payments (`ExpectedPaymentProgressCalculator`): matched, remaining and overdue amounts. A receipt is never counted twice across milestones. "Not matched" means no evidence was linked, not "unpaid".
- Dates are calendar days in `Asia/Colombo` (`ZarTime`). Weeks start Monday by default (`weekContaining`).

## Weekly allowance (`AllowanceCalculator`)

An optional early warning for discretionary spending. It starts off, and reads the ledger without ever writing to it.

- **Setting:** one document, `users/{uid}/allowance/weekly`: on/off, an LKR amount, optional category ids and a week start (Monday by default). Switching it off keeps the amount for next time.
- **Counts:** expenses classed `DISCRETIONARY` dated in the current week, in the chosen categories (none chosen means all), net of linked refunds.
- **Never counts:** committed spending (including transfer fees), expenses linked to a bill due date, transfers, goal allocations, income and adjustments.
- **Refunds** reduce the original purchase, capped at it, in the purchase's own week.
- **Left out and reported, never guessed:** expenses with no spending class, expenses with no LKR rate, and cash catch-up outflows. Each is shown as a "Check:" line beside the remaining amount.
- **Remaining** is the allowance minus eligible spending. Negative remaining is shown as "Overspent by". There is no rollover and no carried-over debt.

## Storage

- **Firestore is the primary financial store.** Everything is under `users/{uid}/` with these subcollections: `accounts`, `transactions`, `corrections`, `voids`, `evidenceLinks`, `expectedPayments`, `paymentMatches`, `drafts` (`FirestoreCollections`).
- `firestore.rules` allows only the owner and enforces each collection's schema and write-once behaviour. Tests are in `firestore-tests/` (`npm install && npm test`, needs Java).
- **Encrypted Room is only for capture staging** (L08): raw bank messages stay on the device and are never uploaded. Drafts carry structured fields only, and the rules reject a `rawText` field.
- Implemented now: `LedgerRepository` (Firestore), `UserSessionProvider` (Firebase Auth) and `AccountRepository` (Firestore, D03: `features/accounts`). Interfaces only, to be implemented by their owners: `ExpectedPaymentRepository` (L03/L05), `DraftRepository` (S07). Draft *generation* (D06) is separate and saves nothing: see [ai-quick-notes-service.md](ai-quick-notes-service.md). Use the field names already in `firestore.rules`.

## Where settings live

| Setting | Where it comes from | Changes? |
|---|---|---|
| Reporting currency (LKR) | Fixed domain value, `Currencies.REPORTING`, read through `AppPreferencesRepository`. Not stored | No. Not editable |
| Signed-in email | Firebase Auth session (`ObserveSessionUseCase`) | Follows the session |
| Catch-up reminder (on/off, day, time, skipped week) | This phone only: DataStore (`features/catchup`) | Edited on the reminder screen. Cleared on sign-out (`ResetCatchUpOnSignOut`) |
| Overdue-milestone alerts switch and reminders already shown | This phone only: DataStore `payment_reminders` (`features/reminders`) | Cleared on sign-out (`ResetRemindersOnSignOut`) |
| Dismissed subscription suggestions | This phone only: DataStore `subscription_suggestions` (`features/bills`) | Cleared on sign-out (`ResetSuggestionsOnSignOut`) |
| Bank text shared into Zar | Memory only (`SharedTextInbox`), never written to disk | Cleared once taken and on sign-out |
| Accounts, entries, bills and their reminder choice, everything financial | Firestore under `users/{uid}/` | Synced to the user's Zar account |

Nothing on the Settings or reminder screens is held only in memory. Masking and export settings are not built yet; their stories add their own rows here.

## Decisions to confirm as a team

1. Refunds reduce spending in the **purchase's** month/week, not the refund's.
2. `ADJUSTMENT_OUT` counts as unknown spending; `ADJUSTMENT_IN` affects balances only.
3. One currency per account (a Binance wallet with USDT and BTC becomes two accounts).
4. Transfer fees are a separate `EXPENSE`.
5. `observeLedger()` loads the user's whole ledger and filters in memory. That's fine at assignment scale; it would need paging later.
