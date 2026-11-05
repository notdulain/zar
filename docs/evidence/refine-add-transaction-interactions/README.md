# Interaction refinements — final PR evidence

All screenshots are captured from the running debug app on the Pixel_6a emulator (API 37, 1080 × 2400) after merging `origin/main` at `8aa7f9c`. Compose instrumentation supplies synthetic fixtures; no live user data is used.

The initial pending work is committed as `c83f878`. Merge and lint fixes are in `185dcc6`. The screenshot commit contains the final captures and this report.

Validation after the merge:

- Android debug app and instrumentation APK: build passed.
- Unit suite: 1,514 passed; zero failures, errors, or skipped tests.
- Android lint: passed (warnings remain).
- Firestore rules: 48 passed on the local Firestore emulator.
- Focused emulator UI run: 63 passed, zero failures. Three screenshot-refresh checks were then rerun after improving capture readiness and hiding the keyboard.

The UI run covers Home balances/privacy/order, Home and Activity month navigation, hero collapse, Add press/haptic/reduced-motion behavior, New plan entry/swipe/form behavior, Add selectors and sheet height, category icon/removal flows, date/calendar gestures, minute selection, Quick note routing, and Add/New plan dwell behavior.

Screenshots show Add transaction and its date/time pickers, category states, Home accounts and month selection, Activity month selection, Plans/More heroes, and New plan. These show rendered states; controlled-clock UI assertions verify the animations between them.

Not covered: a complete visual/accessibility matrix, live Firebase writes and offline/reconnect for every flow, fresh Figma verification, or physical 120 Hz/haptic-strength verification of the final merged build.

## Capture index

| File | Rendered state |
| --- | --- |
| 01-add-expense.png | Expense form with the shared selectors |
| 02-add-income.png | Income tab |
| 03-add-transfer.png | Transfer tab |
| 04-date-picker.png | Calendar opened from Add transaction |
| 05-date-month-chooser.png | Month chooser opened from that calendar |
| 06-time-picker.png | Clock dial opened from Add transaction |
| 07-category-icons.png | New category icon picker, light theme |
| 08-category-icons-dark.png | New category icon picker, dark theme |
| 09-category-grid.png | Category grid with removal controls |
| 10-category-delete-confirm.png | Named category-removal confirmation |
| 11-home-accounts.png | Account balances and Clients owe you below the other cards |
| 12-home-month-picker.png | Home month picker |
| 13-activity-month-picker.png | Activity month picker with other filters retained |
| 14-plans-hero.png | Plans hero and grouped rows |
| 15-more-hero.png | More hero and rows |
| 16-new-plan.png | New plan sheet after its upward entrance |

Some collapse fixtures constrain the app content to a fixed phone-size viewport; the PNGs retain the full emulator window.
