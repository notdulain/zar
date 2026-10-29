# S08 (SCRUM-42): testing entry, corrections, cash recovery and quick notes

Tested on branch `feature/SCRUM-42-testing` (from `main` at `163df2d`) on 29 Oct 2026.
Screenshots: Pixel 4 emulator (Android 37.1), local Firebase Auth and Firestore emulators, a seeded test user, airplane mode switched on and off from the phone.

## Results

| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest` | **1,253 passed, 0 failed, 0 skipped.** `main` has 1,250, so the 3 new tests are the difference. |
| `cd functions && npm test` | 50 of 50 passed |
| `cd worker && npm run check && npm test` | type check clean, 37 of 37 passed |
| PR #38's five changed test classes, run on its own branch | 38 of 38 passed (see the cross-check below) |

Not run in this session: the instrumented (emulator) test classes, `lintDebug`, and `firestore-tests`.

## Acceptance checks

| Check | What shows it |
| --- | --- |
| Transfers, refunds, correction and explicit cash adjustments across offline reconnect | One phone session offline (screenshots 02 to 13), then reconnected (14). Read-backs: `server-before-reconnect.txt` and `server-after-reconnect.txt`. |
| Ambiguous PickMe messages, rule conflicts, future-only rule application | 3 new unit tests in `CaptureTest`; existing tests named below; screenshots 15 to 18. |
| Missing fields, malformed or failed AI responses, repeated quick-note confirmation | Existing tests named below; screenshots 02 and 19. No new tests were needed. |
| Record evidence and cross-check another member's feature | This folder, and the PR #38 section below. |

### Offline journey, step by step

All four actions were made with airplane mode on. Each is saved on the phone and shown there at once.

| Action | Phone, offline | Screenshots |
| --- | --- | --- |
| Transfer Sampath to Cash, LKR 2,500 | Activity shows both legs and a "You're offline" banner (11 transactions) | 03, 04 |
| Refund LKR 1,000 of the LKR 8,400 Keells Super purchase | Detail shows "Refunded LKR 1,000 · net LKR 7,400" and "Still refundable: LKR 7,400" | 05 to 08 |
| Correct that purchase to LKR 8,600 | "Edited 1 time · originally LKR 8,400"; the original stays on record | 09, 10 |
| Count cash as LKR 22,000 when LKR 23,000 is recorded | A LKR 1,000 cash adjustment; balance becomes LKR 22,000 | 11 to 13 |

Server before reconnect: **10** transactions, none of the above. After reconnect (20 seconds later and again after 10 more): **14**. The four new documents are a refund (LKR 1,000), a reconciliation adjustment (LKR 1,000, out of cash), and the two transfer legs (LKR 2,500, one in and one out). There is exactly **1** correction document (LKR 8,600), and the original `keells` document still says LKR 8,400. Nothing was written twice.

Activity after reconnect (14) shows SPENT LKR 16,380. That matches the arithmetic: 16,180 before, plus 200 for the correction, plus 1,000 for the unexplained adjustment, minus the 1,000 refund.

### PickMe, rules and future-only application

- **New unit tests**, in `CaptureTest`:
  - `aGeneralNameLikePickMeIsNeverFiledEvenWhenARuleExistsForIt`
  - `aPreciseNameOfTheSameBrandIsStillFiled` ("PickMe Food" is filed, "PickMe" is not)
  - `editingARuleOnlyChangesLaterCapturesNeverTheEntryAlreadySaved` (a captured entry keeps its category and rule version 1 after the rule is edited to version 2; only the next capture gets version 2)
- **Existing tests I read and rely on:** `CategoryRuleRulesTest` (generic names, and `rulesOfTheSameWeightThatDisagreeAreAConflictAndFileNothing`), `CaptureTest` (`aSwitchedOffRuleAndDisagreeingRulesLeaveItUnresolved`), `RuleViewModelsTest`.
- **On the phone:** screenshot 15 shows "PickMe is too general to remember". Screenshot 16 states "Nothing already saved changes". Screenshot 17 shows the sheet "Future expenses only". Screenshot 18 shows the entry after saving the rule. Screenshot 18 is weak evidence on its own, since that entry was already Groceries; the unit test above is the real proof.

### Quick notes

- **Offline:** screenshot 02. "You are offline", the note is kept, and nothing is sent.
- **Failed service:** screenshot 19. "This app could not be verified", the note is kept, with "Fill the form" and "Try again". A debug build is refused before anything reaches the AI service, so this run spent nothing. The service's own answer was **not** exercised.
- **Existing tests that cover the rest** (names read from the files, run as part of the 1,253): `QuickNoteViewModelTest` (`aProposalWithAGapCannotBeSavedUntilTheGapIsFilled`, `savingTwiceInARowSavesOnce`, `aSecondTapWhileReadingDoesNotSendTheNoteAgain`, `aFailedReadKeepsTheNoteAndOnlyAnotherTapReadsAgain`), `ConfirmQuickNoteProposalUseCaseTest` (`confirmingTheSameProposalAgainStillLeavesOneRecord`), `CallableQuickNoteDraftRepositoryTest` (`malformedServiceAnswerIsAnInvalidResponse`), `HttpQuickNoteDraftTransportTest` (`handlesHtmlErrorsAndRejectsMalformedOrOversizedSuccesses`), and the Worker tests.

## Cross-check: PR #38 (SCRUM-33, by Luchitha7)

Checked its branch `feature/SCRUM-33-testing` against what its description says.

| Claim | Result |
| --- | --- |
| 24 new unit tests | **Confirmed.** 24 added `@Test` lines in its diff. |
| 1,250 unit tests before the branch | **Confirmed.** `main` gives 1,250 in my run (1,253 with my 3 new tests). |
| New tests pass | **Confirmed for the five changed classes:** `BillViewModelsTest` 7, `FreelanceViewModelsTest` 6, `CryptoViewModelsTest` 5, `IncomeListViewModelTest` 3 and `AutoCaptureTest` 17, which is 38 of 38, 0 failed. |
| Total 1,274 after the branch | Not rerun in full by me. |
| Six ViewModels read their argument from `SavedStateHandle` | **Confirmed in the diff** (`BillViewModels.kt`, `FreelanceViewModels.kt`, `MilestoneViewModel.kt`). |
| Instrumented tests (71), `firestore-tests` (25), lint, the six manual screens | Not rerun. I take these as stated. |

Two observations, neither a failure:

1. For a required id, the new code reads `savedStateHandle.get<String>(...).orEmpty()`. If the argument were ever missing, the screen would open on an empty id and show "not found", where the old `toRoute()` would crash. No test covers a missing argument.
2. The PR says `lintDebug` gives 1 error, `NonObservableLocale` in `PlansHero.kt:213`, already on `main`. I did not run lint, so I can't confirm that.

## Not covered

Physical device, dark mode and TalkBack. The live Firebase project. A real answer from the AI service. Undoing a refund or correction by hand on the phone (the existing unit tests name undo cases, but I did not rerun them as a group or exercise it on the emulator). Instrumented test classes.

## Screenshots

| # | File |
| --- | --- |
| 01 | `01-activity-before-offline-journey.png` |
| 02 | `02-quick-note-offline-note-kept.png` |
| 03 | `03-offline-transfer-form-ready.png` |
| 04 | `04-offline-transfer-saved-on-phone.png` |
| 05 | `05-offline-entry-detail-rule-offer-future-only.png` |
| 06 | `06-offline-refund-form.png` |
| 07 | `07-offline-refund-saved-on-phone.png` |
| 08 | `08-offline-refund-listed.png` |
| 09 | `09-offline-correction-form.png` |
| 10 | `10-offline-correction-saved-history-kept.png` |
| 11 | `11-offline-check-cash.png` |
| 12 | `12-offline-check-cash-difference.png` |
| 13 | `13-offline-cash-adjustment-saved.png` |
| 14 | `14-after-reconnect-activity-synced.png` |
| 15 | `15-pickme-too-general-never-remembered.png` |
| 16 | `16-category-rules-future-only-empty.png` |
| 17 | `17-remember-rule-future-expenses-only.png` |
| 18 | `18-rule-saved-entry-detail.png` |
| 19 | `19-quick-note-failed-read-note-kept.png` |
