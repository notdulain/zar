# U05 backed goal progress

Jira: [SCRUM-48](https://03dulain.atlassian.net/browse/SCRUM-48)

Plans distinguishes money explicitly allocated from money its recorded backing accounts still hold. Known backing is the smaller of each allocation and its account balance, with negative balances treated as zero. Missing, unknown, future-opening, or currency-inconsistent backing accounts leave shortfall and saving pace unavailable. Approximate opening balances are labelled as estimates. Transactions dated after today do not contribute to backing or new allocations.

The optional target date is saved on the goal document. Older goals without the field still load, and removing a date preserves allocations. Weekly pace rounds up to the smallest currency unit: `remaining backed target × 7 / days until target`. A past date asks for a new date. A fully backed goal shows completion without a weekly pace.

The balance date is the oldest last-recorded date across backing accounts, using each account's latest active transaction or opening date. It is not a bank sync timestamp.

## Styling

Plans uses the shared dusk hero, Geist typography, semantic light/dark colors, surface cards and Zar buttons. The hero shows backed and allocated progress; a separate card shows the remaining target, allocation gap, recorded date and weekly pace. Goal and allocation editors use neutral surfaces, rounded fields, scrollable content, shared Save/Cancel controls, and accessible account selection.

## Verification

- Unit tests cover spending, corrections, changed balances, unknown/approximate/currency-inconsistent accounts, future funds, completion, rounding, date persistence and removal.
- Firestore rules tests cover optional date creation/update/removal, invalid values, owner access and allocation limits.
- Compose emulator tests cover backed versus allocated values, creation/allocation actions, date selection/removal, saving controls, dark mode, and a 320dp layout with 200% text.

## Pixel 6a screenshots

These are deterministic Compose fixtures, not real user records.

- [Empty state](screenshots/u05-plans-empty-after.png)
- [Backed amount and allocation gap](screenshots/u05-plans-shortfall.png)
- [Saving pace](screenshots/u05-plans-pace.png)
- [Goal date editor](screenshots/u05-goal-date-editor.png)
- [Dark mode](screenshots/u05-plans-dark.png)
- [Large text and unknown balance](screenshots/u05-plans-large-text.png)

For a manual end-to-end check, create an LKR 10,000 account, set a goal of LKR 20,000 due in 14 days, and allocate LKR 8,000. Backing should be LKR 8,000 and pace LKR 6,000/week. Record an LKR 6,000 expense from that account using Add. Backing should fall to LKR 4,000 while allocation stays LKR 8,000, with an LKR 4,000 gap and LKR 8,000/week pace.
