# L10 (SCRUM-33): testing income, bills, currencies, crypto and SMS capture

Tested on branch `feature/SCRUM-33-testing` on 29 Oct 2026, using a Pixel 8 emulator on API 36 with the Firebase emulators (auth and Firestore).

## What was missing

The money maths in these features already had strong tests: income totals, bill due dates, currency conversion, crypto cost basis and the SMS parser. What was missing was the screen state, meaning what each screen shows:

- while it is loading;
- when there is nothing yet;
- when loading fails;
- when a save fails because the phone is offline.

The bills and freelance screen ViewModels could not run in unit tests at all, because `toRoute()` needs an Android `Bundle`.

## What changed

| Commit | What |
| --- | --- |
| `refactor(bills,freelance)` | The bill, bill form, due date, project, project form and milestone ViewModels now read their screen argument with `savedStateHandle.get(Route::field.name)`. This is the same pattern other screens already use. Navigation still passes the same arguments. |
| `test(bills)` | 7 tests: the list loading and then empty, a load failure, empty form problems, a failed save keeping the input with a retry saving one bill, edit fill, editing a missing bill, and a failed cancellation |
| `test(freelance)` | 6 tests: the list empty, a load failure, the overdue alerts switch, a missing name, a failed save with a retry creating one project, and a fixed currency when editing or a missing project |
| `test(crypto)` | 5 tests: an empty wallet, a load failure, an opening cost that is invalid, offline, or saved, a movement missing accounts, and a failed buy with a retry recording it once |
| `test(income)` | 3 tests: mixed LKR and USD income with a missing rate counted as unresolved, an empty month, and a load failure followed by a retry |
| `test(capture)` | 3 tests: the automatic capture switch states, and resuming only for the account that turned it on |

That is 24 new unit tests.

## Coverage by feature

| Feature | Unit tests | Instrumented tests |
| --- | --- | --- |
| Income (rules, use cases, list) | 25, in `IncomeRulesTest`, `IncomeUseCasesTest`, `IncomeListViewModelTest` and others | `IncomeSheetParityUiTest` 19, `IncomeCorrectionUiTest` 1 |
| Bills, subscriptions, due dates | 41, in 4 files including `BillViewModelsTest` | `BillsUiTest` 12 |
| Reminders | 9 | none |
| Freelance and milestones | 25, in 4 files including `FreelanceViewModelsTest` | none |
| Currencies | `CurrencyConverterTest` 5, `MoneyTest` 6, `MoneyFormatTest` 6 | none |
| Crypto | 23, in 3 files including `CryptoViewModelsTest` | none |
| SMS parser | `BankSmsParserTest` 13 | none |
| Capture, manual and automatic | 100, in 8 files including `AutoCaptureTest` 17 | `CaptureUiTest` 23, `CaptureRepositoryTest` 10, `AutoCaptureTest` 6 |
| Firestore rules | none | 25 rules tests in `firestore-tests` |

## Results

| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest` | **1,274 passed, 0 failed.** There were 1,250 before this branch. |
| `cd firestore-tests && npm test` | **25 out of 25 passed** |
| Instrumented runs: `CaptureUiTest`, `BillsUiTest`, `AutoCaptureTest`, `CaptureRepositoryTest`, `IncomeSheetParityUiTest`, `IncomeCorrectionUiTest` | **All 71 passed** |
| `./gradlew lintDebug` | 1 error, already on `main`. It is not in any of these files: `NonObservableLocale` in `features/plans/presentation/screen/PlansHero.kt:213`. |

## Manual check after the refactor

I signed in to a test account on the emulator with one LKR bill and one USD project, then opened every screen whose argument reading changed.

| Screen | Result | Screenshot |
| --- | --- | --- |
| Bill detail (Dialog Internet) | Correct amount, schedule, account and payments | `1-bill-detail.png` |
| Bill due date (25 Oct) | Opens "Payment for 25 Oct", shows LKR 5,850 expected | `2-bill-due-date.png` |
| Edit bill | Form filled with the saved bill | `3-bill-edit.png` |
| Project detail (Logo redesign) | Opens, still owed USD 0, no milestones | `4-project-detail.png` |
| New milestone | Opens for that project | `5-new-milestone.png` |
| Edit project | Filled with name, client and USD | `6-project-edit.png` |

## Known issues and gaps

- **Flaky test:** `CaptureUiTest.missingAccountLeavesManualRecoveryInstructionsVisible` failed once in a mixed batch run. It passed when run alone and when the whole class ran. This looks like timing, not a bug in the app.
- **Not covered here:**
  - a physical phone receiving a real bank SMS (the emulator SMS was used for L08);
  - dark mode and TalkBack passes;
  - live Firebase instead of the emulators.

## PR review validation (30 Oct 2026)

- Merged current `main` (`a8ac464`) into this branch before validation.
- Strengthened bill, project and crypto retry tests to compare IDs across failed and successful write attempts. Counting only successful records did not prove that the failed attempt reused its ID.
- Cancelled the income test ViewModel scopes after assertions.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: passed; 1,291 unit tests, zero failures, errors or skips. The total includes tests added to main since the original author run.
- `lintDebug`: one error and 82 warnings. The error remains the unchanged `NonObservableLocale` in `PlansHero.kt:213`, also present on main.
- Inspected all six original screenshots. They remain author-provided runtime evidence; this review changed no screen layout or styling and captured no new screenshots.
- This review did not rerun instrumentation, Firestore emulator tests, or live Figma comparison. The earlier emulator results above belong to the author run, not this review. No visual parity claim is made.
