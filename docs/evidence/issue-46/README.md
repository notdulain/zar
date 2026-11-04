# Issue #46: today's exchange rate on income and expenses

What was run, and what it showed. The feature is described in [docs/exchange-rates.md](../../exchange-rates.md).

Captured on 4 Nov 2026 from the running debug app on the Pixel 4 emulator (Android 37.1) with the local Firebase Auth and Firestore emulators and a throwaway test user (Cash and Sampath in LKR, Payoneer in USD). The emulator ran headless with 2 GB, as in [S09](../S09/README.md); screenshots come from `adb exec-out screencap`, resized to 720 pixels wide. No real names, accounts or messages.

The rate in these runs is **a real answer from the provider** (ExchangeRate-API open endpoint), fetched from the emulator over the internet: USD to LKR 332.444906, published 4 Nov 2026 00:02:31 UTC, which the form shows in Sri Lanka time as "4 Nov, 05:32".

## Screens

| File | State | What it shows |
|---|---|---|
| `01-expense-usd-link-available.png` | Expense, Payoneer (USD) | The new rate row on the expense form: `1 USD = ? LKR`, the **Use today's rate** link and the hint. Before this change a foreign-currency expense had no rate field. |
| `02-expense-failed-offline.png` | Expense, phone offline | Wi-Fi and data switched off, link tapped. The link becomes **Try again** and the message says "Couldn't get today's rate. Type one, or leave it empty." The offline banner is the form's existing one. |
| `03-expense-filled-provider.png` | Expense, back online | **Try again** tapped. The field holds 332.4449, the hint says when the provider published it and that it can be changed, and the required link "Rates By Exchange Rate API" is shown. |
| `04-income-usd-link-available.png` | Income, Payoneer (USD) | The same row on the income form. |
| `05-income-filled.png` | Income, link tapped | The provider's rate filled in. |
| `06-income-typed-over.png` | Income, 330 typed over it | "You typed this rate. Today's rate is 332.4449." The link to fill in today's rate again stays. |
| `07-income-earlier-date.png` | Income, date set to Yesterday | The fetched rate is gone, there is no link, and the hint says today's rate isn't offered for an earlier date. |
| `08-activity-totals.png` | Activity, November | Received LKR 166,027.96 and Spent LKR 13,297.80, built from the three records below, so their rates are in the totals. |
| `09-income-edit-saved-provider-rate.png` | Edit income (saved with the provider's rate) | "Saved from today's rate, updated 4 Nov, 05:32." and the attribution link. No fetch link, because the rate is already the provider's. |

## What was stored

Read back from the Firestore emulator with a small script after the steps above (amounts in minor units):

| Record | Amount | Conversion | Rate | Source | Rate time | LKR value |
|---|---|---|---|---|---|---|
| Expense, 4 Nov, Groceries | USD 40 | CONVERTED | 332.4449 | **PROVIDER** | 2026-11-04T00:02:31Z (the provider's time) | 13,297.80 |
| Income, 4 Nov, Freelance | USD 400 | CONVERTED | 332.4449 | **PROVIDER** | 2026-11-04T00:02:31Z | 132,977.96 |
| Income, 3 Nov, Freelance | USD 100 | CONVERTED | **330.5** (typed as `330,5`) | MANUAL | time of saving | 33,050.00 |
| Correction of the 4 Nov income | | CONVERTED | 331 | MANUAL | | |

- `330,5` was stored as 330.5, not 3305: the comma fix.
- The USD 400 income's original record keeps 332.4449 and PROVIDER. Changing its rate to 331 in the edit form added a correction (331, MANUAL) and did not touch the original, as the write-once ledger requires.
- The two LKR values above match the amounts asserted in the unit tests (`IncomeFormViewModelRateTest`, `ManualEntryViewModelTest`).

## Tests run for this change

- Unit tests (JVM): the whole suite, 143 classes and 1,431 tests, 0 failures, 0 errors, 0 skipped.
- Firestore rules tests: 48 of 48, including a new case for a rate with source `PROVIDER`, and one that refuses an unknown source and a comma in the rate.

## Not covered

- **Dark mode, large fonts, TalkBack** and a **physical phone**. Emulator only.
- **An answer that is cached** was not observed on the phone. It is covered by `ExchangeRateApiRepositoryTest` (one request, then the second answer from the cache; a new request after the provider's next update).
- **The provider's "too many requests" answer (HTTP 429)** was not triggered on purpose. It is covered by a unit test against a mock server: nothing is sent for 20 minutes.
- **The expense form's earlier-date and typed-over states** were not captured. They share the row and the logic with income and are covered by `ManualEntryViewModelTest`.
- **Instrumented (on-device) tests** were not run.
- **Adding a rate to an expense that was already saved** is still not possible.
- **The saved record's rate is only visible in the income edit form.** The Activity detail screen does not show rates.
- **The Figma frames** (section 29, Z198 to Z210) show the rate as a row with a label; the app keeps the existing layout of the field (label with the link on the first line, the number below). The wording, states and colours match.

## How it was run

The sequence in `.claude/emulator-setup.md`: Gradle daemons stopped, a debug APK built with `-Pzar.useEmulators=true` and the Firestore port in `FirebaseModule.kt` temporarily set to 8081 (port 8080 is taken on this machine), then reverted and not committed; Firebase emulators (Auth 9099, Firestore 8081); the phone headless with `-memory 2048 -no-window -no-audio -gpu swiftshader_indirect`; animations off; the test user seeded over REST.
