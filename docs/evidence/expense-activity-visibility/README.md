# Saved expense visibility: reproduction and cause

Local OpenSpec change `fix-expense-activity-visibility`. Test fixture data only (Firebase Auth and Firestore emulators, anonymous test user, `demo-zar`). No live project was read or written.

## Trace of one expense (LKR 500, today, Wallet, Food)

| Boundary | Result |
| --- | --- |
| Manual form -> `RecordExpenseUseCase` -> `ManualEntryRules.newExpense` | One `Transaction` with the stable ID created when the screen opened. |
| `FirestoreLedgerRepository.saveTransaction` -> local Firestore cache | Accepted at once, online or offline. The call returns without waiting for the server. |
| Snapshot listener -> `LedgerDocumentMapper.transactionFromMap` | Mapper accepts the submitted shape; the entry read back equals the saved one. |
| `LedgerProjector` -> `ActivityQuery` (this month, All accounts, no search) | Included exactly once. The query filters date, account, void state and search, never type. |
| `LedgerSummaryCalculator` | Spent includes it; Received counts income only. |

Read/projection/filter code is correct for an accepted record (`LedgerWriteOutcomeTest.anExpenseSavedOfflineAppearsOnceImmediatelyAndStaysAfterReconnect` on the emulator, plus the unit tests added with this change).

## Demonstrated defect

`LedgerWriteOutcomeTest.reproduction_aServerRejectedExpenseIsShownThenSilentlyDropped` (real `FirestoreLedgerRepository`, emulator rules):

1. Save returns normally, so the form reports success and closes.
2. The expense appears in the next local ledger emission.
3. When the server refuses the write, Firestore rolls the local copy back and the row disappears.
4. The only trace is `Log.w("... was rejected")`. The user sees neither an error nor the entry again.

That is exactly "I saved an expense and it is not in Activity". The failing boundary is the repository write: `.set(...).logFailure(...)` drops the rejection.

## What is not proven

Which rule refused the user's real expense. A current manual expense passes the rules in this repository. Since `4865c6d` (20 Oct) every manual expense carries `time`, and later saves can carry `appliedRuleId`; a deployed ruleset older than those commits would refuse them. The deployed ruleset was not read here (no live calls). Compare it with `firestore.rules` before concluding; deploying the current rules is a separate, authorised step.
