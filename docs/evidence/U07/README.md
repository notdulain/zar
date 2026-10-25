# U07 hide amounts evidence

Captured on 24 October 2026 from the Pixel 6a Android emulator (API 37). Images 1–2 are full-window screenshots of the production Home composable with fixed, nonzero fixture values in `HideAmountsUiTest`. Images 3–6 are from the running debug app signed into a disposable account on local Firebase Auth and Firestore emulators. No live account data appears in these images.

| Image | State shown |
|---|---|
| `01-home-hidden.png` | Home hero amount and chart masked. |
| `02-commitments-hidden.png` | Spending split, client debt, overdue value and next bill masked. |
| `03-activity-empty-hidden.png` | Activity totals masked with no transactions. |
| `04-plans-empty-hidden.png` | Empty Plans backing value masked. |
| `05-goal-editor-hidden.png` | Prefilled target amount shown as dots in the goal editor (author capture, before the fixed-width field mask below). |
| `06-settings-hidden.png` | Settings hide-amounts switch on. |

The device walkthrough also checked Home after force-stop and relaunch: it still displayed masked values and a “Show amounts” control. The Settings switch was toggled off and on, and its checked state followed both changes. Eight privacy instrumentation tests passed, including initial state, switching, Home commitments and the empty Plans placeholder. The unit suite passed 946 tests.

Exported CSV amounts remain real values because export is a deliberate action (U08 export merged in PR #33 and writes raw amounts, not the display formatters). Notifications posted before the preference changes keep their existing text. Screen readers receive the masked bullet text through the shared formatters. A physical device, TalkBack pronunciation and a frame-by-frame launch recording were not checked.

## Review captures (`review/`)

Captured on 25 October 2026 on the Pixel 6a emulator (API 37) after merging `main` and the review fix, with the debug app showing its locally cached test entries.

| Image | State shown |
|---|---|
| `review/01-home-hidden.png` | Home hero masked and chart replaced. |
| `review/02-home-cards-hidden.png` | Spending split, recent rows and client debt masked with real entries. |
| `review/03-activity-hidden.png` | Activity totals and rows masked. |
| `review/04-entry-detail-hidden.png` | Entry detail amount masked; name, date and account readable. |
| `review/05-edit-entry-hidden.png` | Prefilled amount field shows a fixed `••••`, no longer one dot per digit. |
