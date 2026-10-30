# S09 demo script: Add, Activity and cash catch-up

About 5 minutes. It runs on the Android emulator with the Firebase emulators and seeded test data. The report section is [S09-add-activity-catch-up.md](S09-add-activity-catch-up.md). The emulator recording does not demonstrate a successful quick-note response; use S07 stills for that part unless a separately verified install is ready. A recording of the first two parts (add and correct an expense, then the cash catch-up) is [demo-add-activity-catch-up.mp4](../evidence/S09/demo-add-activity-catch-up.mp4): one continuous take of about 71 seconds, not edited. The quick-note part is not in it; use the [S07 stills](../evidence/S07). Screens below link to the annotated captures in [docs/evidence/S09](../evidence/S09).

## Before the demo: seed (about 5 minutes, not part of the 5)

Use a debug build pointed at the emulators: `./gradlew installDebug -Pzar.useEmulators=true`. Use a fresh test account so earlier entries and catch-up preferences do not change the expected totals. No real names, card numbers or bank messages. Use today for every seed transaction below and the first day of the current month for every opening balance, so a month boundary does not exclude part of the seed from Activity.

| Step | Where | What to enter |
|---|---|---|
| 1 | More → Accounts → Add account | "Cash" (cash, LKR, opening balance 20,000 on the first of this month), "Sampath" (bank, LKR, opening 50,000 on the first of this month) and "Payoneer" (freelance, USD, opening 100 on the first of this month) |
| 2 | Add → Manual entry → Expense | Note "Keells Super", LKR 8,400, Cash, Groceries, Discretionary, today |
| 3 | Add → Expense | Note "PickMe", LKR 1,250, Cash, Transport, Discretionary, today |
| 4 | Add → Expense | Note "Corner shop", LKR 650, Cash, **no category**, counts as Unknown, today |
| 5 | Add → Expense | Note "Hosting", **USD** 40, Payoneer, Bills, today, **leave the rate empty** |
| 6 | Add → Expense | Note "Dialog Internet", LKR 5,850, Sampath, Bills, Committed, today |
| 7 | Add → Income tab | Note "Client payment", LKR 125,000, Sampath, Freelance, today |
| 8 | Add → Transfer | LKR 10,000 from Sampath to Cash, **fee LKR 30**, today |

The report screenshots were captured on data created by a script with these values, plus one expense in September (Netflix, LKR 2,190) that does not touch October's totals. The historical captures use October dates. A fresh run should use the current month; the optional prior-month Netflix expense is unnecessary for the expected current-month totals.

Expected after seeding: Cash recorded LKR 19,700. Activity shows Received 125,000 and Spent 16,180 (8,400 + 1,250 + 650 + 5,850 + the 30 fee), with the line "1 record has no LKR rate and is not included". Home shows "2 need review".

## The demo

| Time | Do | Say | Expected screen |
|---|---|---|---|
| 0:00 | Open Home, tap Add | "Everything starts from Add. A quick note, or the manual form." | Two rows: Quick note, Manual entry. [Annotated](../evidence/S09/1-add-sheet-annotated.png) |
| 0:20 | Manual entry: amount 1450, Sampath, Coffee & dining, Save | "Account, category, and whether it counts as committed or discretionary. Zar suggests but I decide." | Form filled. [Annotated](../evidence/S09/2-manual-expense-annotated.png). Home Spent becomes LKR 17,630 |
| 0:50 | Open Activity, tap the new row | "Activity lists every record. This one is saved and synced." | New row on top. Status "Saved · synced". [Annotated Activity](../evidence/S09/4-activity-annotated.png) |
| 1:10 | Edit, change 1450 to 1500, Save correction | "I don't overwrite it. The original stays on record and a correction is added." | "Edited 1 time · originally LKR 1,450". [Annotated](../evidence/S09/6-edit-correction-annotated.png) |
| 1:40 | Optional: Void entry | "Undo is a void marker. The record stays, but leaves every total." | Spent drops back. Skip if short of time |
| 1:50 | Back to Home, tap "2 need review" | "Missing entries happen. The weekly catch-up shows what is still open, without guessing." | Needs review with a missing rate and an uncategorised expense. Tap Start catch-up. [Annotated](../evidence/S09/8-catchup-review-annotated.png) |
| 2:20 | Continue to counting. Type 19200 | "I count the cash in my wallet. Zar says 19,700, I counted 19,200." | Difference minus LKR 500. [Annotated](../evidence/S09/9-catchup-count-annotated.png) |
| 2:50 | Review the adjustment | "It saves at most one adjustment, and says what it is: unclassified, no category. It is not counted as food or transport." | [Annotated](../evidence/S09/10-catchup-adjustment-annotated.png) |
| 3:15 | Save adjustment | "Cash is now correct. Totals stay marked incomplete, because the LKR 500 is still unexplained." | "Cash is LKR 19,200", 3 items left. [Annotated](../evidence/S09/11-catchup-done-annotated.png) |
| 3:40 | Open the adjustment | "It tells me why it shows as unknown. I can explain it later." | Cash adjustment, "Cash count was LKR 500 short" |
| 4:00 | Add → Quick note. Type "paid 1250 at keells super with cash, and the electricity bill 4500 is due tomorrow". Tap Interpret | "A quick note is read once. The note itself is not saved." | Reading, then "Check before saving". [Stills](../evidence/S07) |
| 4:20 | Point at the two records | "It found two things: a payment, and a bill that is expected, not paid. The bill does not change my balance until I pay it." | A paid Keells Super record and an expected electricity bill |
| 4:35 | Pick the Cash account, Save the first | "It never picks my account. I choose, then confirm each record." | The saved record shows on Home. [Stills](../evidence/S07) |
| 4:50 | Close | "Limit: tested on an emulator, not on a real phone yet." | none |

If you void the entry in the 1:40 step, the totals in later steps are lower by that amount. Use a fresh test account and seed it again for a clean second run, because the saved ledger history cannot be deleted.

## What to say about limits (if asked)

- Ledger history is write-once. To reverse an edit, append another correction to its transaction; individual corrections cannot be voided. A transaction or balance adjustment can be voided subject to link/refund restrictions.
- A quick-note read needs the verified app and a connection. Offline, the note is kept and nothing is sent.
- Tested on an emulator. Not on a physical phone, with dark mode, or with TalkBack. Details: section 6 of the [report](S09-add-activity-catch-up.md).

## Fallbacks

| If | Then |
|---|---|
| The emulator or app will not start | Play the [recording](../evidence/S09/demo-add-activity-catch-up.mp4) or walk through the annotated images in [docs/evidence/S09](../evidence/S09) in the order above |
| The Firebase emulators are not running | Start them with `npx -y firebase-tools@latest emulators:start --only auth,firestore`. The app needs them for sign-in |
| Quick note says "This app could not be verified" | This happened in the recorded debug setup; service access depends on the install's App Check and authentication configuration. Say so, show that the note is kept and "Fill the form" is offered, then show the [S07 stills](../evidence/S07) |
| The phone is offline | Show the offline captures in [docs/evidence/S08](../evidence/S08/README.md): transfer, refund, correction and cash adjustment saved on the phone, then synced |
| Totals look different | Check the seed table. Cash recorded must be LKR 19,700 before the catch-up |
| Someone asks about a refund | Open Keells Super, tap Refund, show "Up to LKR 8,400". [Annotated](../evidence/S09/7-refund-annotated.png) |
