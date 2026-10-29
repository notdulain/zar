# L11 demo script: income, bills and bank capture

About 5 minutes. It runs on the Android emulator with the Firebase emulators and seeded test data. The report section is [L11-income-bills-capture.md](L11-income-bills-capture.md). Screens below link to the annotated captures in [docs/evidence/L11](../evidence/L11).

## Before the demo: seed (about 5 minutes, not part of the 5)

Use a debug build pointed at the emulators: `./gradlew installDebug -Pzar.useEmulators=true`. Use a test account only. No real names, card numbers or bank messages.

| Step | Where | What to enter |
|---|---|---|
| 1 | More → Accounts | Two accounts: "Commercial Bank" (LKR) and "Wise USD" (USD). Any opening balance |
| 2 | More → Received income → Add income | LKR 185,000, Commercial Bank, category Salary, from "Northwind Ltd" |
| 3 | Add income | USD 1,200, Wise USD, rate **300**, category Freelance, from "Acme" |
| 4 | Add income | USD 450, Wise USD, **leave the rate empty**, category Freelance, from "Globex" |
| 5 | More → Freelance projects → New project | "Logo redesign", client "Acme", USD |
| 6 | Open the project → Add milestone | "Design deposit" USD 1,500 and "Final files" USD 1,000 (due today is fine) |
| 7 | Open "Design deposit" → Link a payment | Link the Acme USD 1,200 income |
| 8 | More → Bills → Add bill | "Dialog Internet", LKR 5,850, monthly, first due earlier this month (for example the 25th) |
| 9 | Add bill | "Netflix", LKR 1,990, monthly, first due **today** |
| 10 | Share a bank SMS (below) | Do not save the draft |

Step 10, from a computer with `adb`. Use a date in the current month.

```bash
adb shell am start -a android.intent.action.SEND -t text/plain \
  -n com.example.zar/.MainActivity \
  --es android.intent.extra.TEXT "'Dear Cardholder, Purchase at CAFE SEVENTY THREE MALABE LK for LKR 800.00 on 28/10/26 10:49 AM has been authorised on your debit card ending #1111.'"
```

This is an anonymized sample from [sms-compatibility.md](../sms-compatibility.md) with the date moved into the current month. On a phone you would long-press the message, then Share, then Zar.

Expected after seeding: Received LKR 545,000, Clients owe you USD 1,300, Spent LKR 0, one draft waiting.

## The demo

| Time | Do | Say | Expected screen |
|---|---|---|---|
| 0:00 | Open Home | "Zar separates money that arrived from money that is only expected. Home shows Received at the top." | Received LKR 545,000 and a check line about one record without a rate. [Annotated Home](../evidence/L11/7-home-received-annotated.png) |
| 0:30 | More → Received income | "Three incomes. The salary is in LKR. The USD 1,200 has a rate of 300 that was saved with it, so it is worth LKR 360,000 and will not change later." | Total LKR 545,000. Rows for Globex, Acme and Northwind. [Annotated income](../evidence/L11/1-income-list-annotated.png) |
| 1:15 | Point at the Globex row | "This one has no rate. Zar does not guess. It says No LKR rate and leaves it out of the total." | "1 record has no LKR rate and is not included". Same image as above, marks 2 and 3 |
| 1:30 | Back to Home, open the "need review" card | "The same record shows up in Needs review so it is not forgotten. Adding the rate there would update the total." | One Missing rate item for Globex. [Annotated Needs review](../evidence/L11/9-needs-review-annotated.png) |
| 2:00 | More → Freelance projects → Logo redesign | "A project has milestones. Milestones are expected money, not income. The Acme payment is linked to the deposit, so USD 300 of it is left." | Still owed USD 1,300. Received 1,200 of USD 2,500. [Annotated project](../evidence/L11/2-project-milestones-annotated.png) |
| 2:45 | Home, scroll to the bottom | "Home reads the same rules. Clients owe you is USD 1,300, one total per currency, never converted. The next bill is Netflix." | Clients owe you USD 1,300. Next bill due Netflix, expected LKR 1,990. [Annotated Home bottom](../evidence/L11/8-home-clients-owe-next-bill-annotated.png) |
| 3:15 | More → Bills | "Bills are expected payments too. Dialog Internet was due on the 25th and nothing is matched, so Zar says it needs a look. It does not say unpaid. Spent stays at zero until a real expense is linked." | One bill due this week, one needing a look, a yearly estimate of LKR 94,080. [Annotated bills](../evidence/L11/3-bills-list-annotated.png) |
| 4:00 | More → Capture sources | "SMS capture is off by default. The safest route is to share a message to Zar. It is read on the phone, and the raw text is never uploaded." | Status shows one message waiting. Automatic capture switch is off. [Annotated capture](../evidence/L11/4-capture-settings-annotated.png) |
| 4:20 | Open the waiting draft | "Commercial Bank card purchases are a validated format. The draft shows the merchant, date and amount. Nothing is in the ledger until I confirm." | Review message with LKR 800 and Cafe Seventy Three. [Annotated review](../evidence/L11/6-capture-review-annotated.png) |
| 4:40 | Optional: choose Expense and tap Save entry, then open Home | "Now it is an expense, and only now does Spent change." | Spent LKR 800. Received is still LKR 545,000 |
| 4:55 | Close | "Limit: the formats are validated on real iPhone-collected messages. Capture was tried on an emulator, not on a real Android phone yet." | none |

If you save the draft in the 4:40 step, the seed must be redone for the next run, because the draft is gone and Spent is no longer zero.

## What to say about limits (if asked)

- Validated: Commercial Bank card and ATM messages and Sampath Bank messages. DFCC and Commercial Bank transfers need a check. Other banks go to manual entry.
- Not done: a test with a real bank SMS on a physical Android phone; a notification-listener route.
- Drafts are reviewed from Capture sources, not from the Needs review list.
- Details: section 6 of the [report](L11-income-bills-capture.md).

## Fallbacks

| If | Then |
|---|---|
| The emulator or app will not start | Walk through the annotated images in [docs/evidence/L11](../evidence/L11) in the order above |
| The Firebase emulators are not running | Start them with `npx -y firebase-tools@latest emulators:start --only auth,firestore` or show the images. The app needs them for sign-in |
| The `adb` share does nothing | Open Capture sources, paste the same text into "Bank SMS text" and tap Read message. Or show [the capture images](../evidence/L11/5-capture-drafts-supported-annotated.png) |
| The draft is missing | The message may have been read before (the same text gives the same draft). Paste a different sample from [sms-compatibility.md](../sms-compatibility.md) |
| Totals look different | Check that the USD 1,200 income has rate 300 and the USD 450 income has none. The report table in section 5.2 lists every expected number |
| Someone asks for automatic capture | Show the off switch and L08 images in [docs/evidence/L08](../evidence/L08): off by default, messages waiting encrypted offline, drafts once online, expiry notice |
| Someone asks about reminders | Show [docs/evidence/L07](../evidence/L07): permission asked when a reminder is chosen, one reminder per bill |
