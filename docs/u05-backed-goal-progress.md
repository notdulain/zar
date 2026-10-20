# U05 backed goal progress

Jira: [SCRUM-48](https://03dulain.atlassian.net/browse/SCRUM-48)

Plans now distinguishes money the user allocated from money the recorded backing account still holds. For each allocation, known backing is the smaller of the allocation and the account balance, with negative balances treated as zero. The goal shows both progress bars, the allocation gap, the remaining target amount, and the oldest last-recorded date among backing accounts. A missing, unknown, or currency-inconsistent account leaves the shortfall and saving pace unavailable; an approximate opening balance is labelled as an estimate.

The optional target date is saved on the goal document. Existing goals without that field still load. When a date is set, weekly pace rounds up to the smallest currency unit: `remaining backed target × 7 / days until target`. A past date asks the user to update it. Only recorded transactions through today affect backing; expected or future dated income is excluded.

## Manual check on a Pixel 6a emulator

1. Create an LKR savings account with a known opening balance of LKR 10,000.
2. On Plans, create a MacBook goal with target LKR 20,000 and a target date 14 days away. Allocate LKR 8,000 from Savings. Verify both bars show LKR 8,000 and pace is LKR 6,000 per week.
3. Edit the Savings account opening balance to LKR 4,000. Verify allocated remains LKR 8,000, backed falls to LKR 4,000, the allocation gap becomes LKR 4,000, and pace becomes LKR 8,000 per week.
4. Change the goal date to yesterday. Verify Plans asks for a new target date instead of showing a weekly pace.

The current Add sheet does not yet save expenses. Tests cover later expenses, transaction corrections, unknown and approximate valuations, currency errors, and future dated income using in-memory records. The manual path above only needs the existing Accounts and Plans forms.

The date on the card is the latest recorded transaction date (or the account opening date if no transaction exists), not a bank sync timestamp.

## Pixel 6a evidence

- [Plans empty state](screenshots/u05-plans-empty-after.png)
- [Goal editor with target date](screenshots/u05-goal-date-editor.png)

These images show the empty state and date control. The backed amount and pace cases are verified by the domain and ViewModel tests; the emulator account had no saved goal, so no user data was created for a screenshot.
