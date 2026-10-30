# U10 — Home, Plans and trust demo

This is a **prepared walkthrough**, not a completed rehearsal. Run it on an emulator with Firebase Auth/Firestore emulators and synthetic records; do not use a personal account. The [report section](U10-home-plans-trust-report.md) contains the source, tests and existing screenshots behind each claim.

Target length: about 3 minutes. **Before presenting**, prepare a fresh synthetic account with an LKR cash account whose known opening balance is LKR 10,000 and an unpaid LKR 5,000 freelance milestone due next week. Do not record a receipt for the milestone. Use the current Colombo date for all entries and set the goal deadline to 14 days from that date. Check the app's loaded state before creating a second goal; Zar supports one savings goal.

| Step | Action to show | Expected observation |
| --- | --- | --- |
| 1. Honest Home totals | Open Home before adding income or an expense. | The LKR 10,000 opening balance does **not** appear as Received. Scheduled/expected money also stays out of Received. Point to the displayed data-coverage wording rather than calling the balance bank-synced. |
| 2. Backed MacBook fund | In Plans, create or edit a MacBook goal of LKR 20,000 due in 14 days. Allocate LKR 8,000 from the cash account. | Allocation and backed progress are LKR 8,000; target shortfall is LKR 12,000; required pace is LKR 6,000 per week. |
| 3. Balance changes backing | Add a dated LKR 6,000 expense from that cash account, mark it discretionary, and choose a category such as Food. Return to Home and Plans. | Home Spent rises by LKR 6,000 while Received stays unchanged. Account balance is LKR 4,000, so backed goal progress falls to LKR 4,000 while allocation stays LKR 8,000. The allocation gap is LKR 4,000 and weekly pace is LKR 8,000. |
| 4. Weekly allowance | Enable a weekly allowance of LKR 8,000 for all categories, including the expense category from step 3. | This week's known eligible expense consumes LKR 6,000 and leaves LKR 2,000. Point out that bills, transfers, allocations and income do not spend the allowance; use an existing coverage-warning fixture if discussing an incomplete week. |
| 5. Privacy | Turn on Hide amounts in Settings, then revisit Home and Plans. | Amounts and the chart are masked. Show amounts again before the export step; the stored values have not changed. |
| 6. Separate exports | Open More → Export. Save actual transactions and expected payments through the system picker. | Two separately named CSV files are offered: `zar-transactions-<date>.csv` for actual entries and `zar-expected-payments-<date>.csv` for expected money. The prepared milestone belongs only in the expected file and must not appear as received income. The deliberate CSV export contains real values even when on-screen masking is enabled. |

If the account has other entries, the exact totals above will differ. Use a fresh fixture or explain the additional entries rather than forcing the expected numbers. A missing LKR rate, unknown backing balance or unclassified expense should produce a warning or unavailable derived value; do not present a guessed total as complete.

## Rehearsal record

Not yet run for U10: no device or emulator was attached when this guide was written on 29 October 2026. Record the device, build commit, date, observed values, and any deviation here after the team performs the walkthrough. Existing emulator screenshots are indexed in the [report](U10-home-plans-trust-report.md#running-app-evidence-and-limits).
