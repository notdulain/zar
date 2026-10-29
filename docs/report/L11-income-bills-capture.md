# L11 (SCRUM-34): income, bills and bank capture

Report section for Luchitha's area. Screens in this section were captured on 29 Oct 2026 from the running debug app on a Pixel 8 emulator (API 36) with the Firebase emulators and seeded test data. The short demo is in [L11-demo-script.md](L11-demo-script.md).

## 1. Overview

| Ticket | Jira | PR | What it delivered | Evidence |
|---|---|---|---|---|
| L01 | SCRUM-24 | #3 | Bank SMS compatibility findings | [sms-compatibility.md](../sms-compatibility.md) |
| L02 | SCRUM-25 | #6 | Received income in LKR and USD, rate saved at save time, edit and undo | [L02](../evidence/L02) |
| L03 | SCRUM-26 | #7 | Freelance projects, milestones, linked receipts, "clients owe you" | [L03](../evidence/L03) |
| L04 | SCRUM-27 | #20 | Crypto buy, sell, trade, move, average-cost profit | [L04](../evidence/L04) |
| L05 | SCRUM-28 | #21 | Bills, due dates, matching a payment to a due date | [L05](../evidence/L05) |
| L06 | SCRUM-29 | #24 | Subscription suggestions and cancellation | [L06](../evidence/L06) |
| L07 | SCRUM-30 | #23 | Bill and milestone reminders | [L07](../evidence/L07) |
| L08 | SCRUM-31 | #26 | Automatic SMS capture (off by default) | [L08](../evidence/L08) |
| L09 | SCRUM-32 | #22 | Share and paste SMS capture, review before saving | [L09](../evidence/L09) |
| L10 | SCRUM-33 | #38 | Tests for this area | [L10](../evidence/L10/README.md) |
| L11 | SCRUM-34 | this branch | This report, the demo script and annotated screens | [L11](../evidence/L11) |

Owner of all tickets: Luchitha. Code lives in `features/transactions` (income), `features/freelance`, `features/crypto`, `features/bills`, `features/reminders` and `features/capture`, on top of `core/domain`.

Three rules shape the whole area:

1. **Received means money that arrived.** Expected payments (milestones, bills), unexplained credits and transfers never count as Received.
2. **Nothing is guessed.** A missing LKR rate, an unknown cost or an unreadable SMS stays visible as "missing" or goes to review.
3. **Nothing enters the ledger until the user confirms.** SMS capture only makes drafts.

## 2. Annotated screens

All images are in [docs/evidence/L11](../evidence/L11). The unmarked originals are in [raw](../evidence/L11/raw).

### 2.1 Received income

![Received income, annotated](../evidence/L11/1-income-list-annotated.png)

| # | What it shows |
|---|---|
| 1 | Received this month: LKR 545,000. This is the LKR salary (185,000) plus USD 1,200 at the saved rate of 300 |
| 2 | One record has no LKR rate and is left out of the total, not guessed |
| 3 | USD 450 with no rate: the row says "No LKR rate" |
| 4 | USD 1,200 with a rate. Its LKR value was fixed when it was saved |
| 5 | LKR salary into Commercial Bank |

### 2.2 Freelance project and milestones

![Project and milestones, annotated](../evidence/L11/2-project-milestones-annotated.png)

| # | What it shows |
|---|---|
| 1 | Still owed, in the project's own currency: USD 1,300 |
| 2 | Received 1,200 of USD 2,500 expected |
| 3 | Milestone "Design deposit" has one linked payment, so 300 is left |
| 4 | Milestone "Final files" has nothing linked, so 1,000 is left |

### 2.3 Bills

![Bills list, annotated](../evidence/L11/3-bills-list-annotated.png)

| # | What it shows |
|---|---|
| 1 | Summary line for the week: one bill is due, LKR 1,990 |
| 2 | "Needs a look": Dialog Internet was due 25 Oct and no payment is matched. It does not say "unpaid" |
| 3 | This week: Netflix, LKR 1,990, due 29 Oct |
| 4 | Later: next Dialog Internet due date, 25 Nov |
| 5 | Yearly estimate, LKR 94,080. It is worked out from bill amounts and is not money spent |

### 2.4 Capture sources

![Capture sources, annotated](../evidence/L11/4-capture-settings-annotated.png)

| # | What it shows |
|---|---|
| 1 | Capture status: when something was last captured and how many drafts wait. Silence does not mean nothing happened |
| 2 | "Capture new bank SMS" is **off** by default |
| 3 | Text shared from another app arrives here. Paste also works |
| 4 | Result of reading the message: LKR 800 on 28 Oct, review it to save |

![Waiting drafts and supported banks, annotated](../evidence/L11/5-capture-drafts-supported-annotated.png)

| # | What it shows |
|---|---|
| 1 | Drafts waiting for review |
| 2 | The shared Commercial Bank message as a draft: merchant, date, type, amount |
| 3 | Commercial Bank card purchases and ATM withdrawals: **Supported** |
| 4 | Commercial Bank online transfers: needs review because the time has no AM/PM |

### 2.5 Review before saving

![Review message, annotated](../evidence/L11/6-capture-review-annotated.png)

| # | What it shows |
|---|---|
| 1 | Date and amount read from the SMS |
| 2 | Warning that nothing enters the ledger until the user confirms |
| 3 | Account chosen by the user (the card's last four digits are only a hint) |
| 4 | Type: Expense, Income or Transfer. The user decides |
| 5 | "Counts as": Committed, Discretionary or Unknown |
| 6 | Save entry. Saving closes the draft and adds the entry together |

### 2.6 Home and Needs review

![Home received, annotated](../evidence/L11/7-home-received-annotated.png)

| # | What it shows |
|---|---|
| 1 | Received this month, the same LKR 545,000 as the income screen |
| 2 | Check line: one record without a rate is left out |
| 3 | "1 need review" card that opens Needs review |
| 4 | Spent this month is LKR 0: bills and milestones add nothing to Spent |

![Home clients owe you and next bill, annotated](../evidence/L11/8-home-clients-owe-next-bill-annotated.png)

| # | What it shows |
|---|---|
| 1 | Recent activity shows the three income entries |
| 2 | Clients owe you: USD 1,300, one total per currency, never converted |
| 3 | Next bill due: Netflix, expected LKR 1,990 |

![Needs review, annotated](../evidence/L11/9-needs-review-annotated.png)

| # | What it shows |
|---|---|
| 1 | The USD 450 income with no rate, with a link to open and fix it |
| 2 | Weekly catch-up for the same unresolved item |

More screens for each ticket (partial payments, undo, wrong-currency accounts, reminders, expiry notice) are in the L02 to L09 evidence folders linked in section 1.

## 3. Domain and data

Layers follow the repository rules. Screens and ViewModels are in `presentation`. Models, use cases, calculations and repository interfaces are in `domain` and import nothing from Android or Firebase (`DomainLayerPurityTest` checks this). Repository implementations are in `data`. Hilt binds them in `di/RepositoryModule`. Screens never call Firestore or Room.

### 3.1 What each feature uses

| Feature | Domain (rules, use cases) | Stored in | Data class |
|---|---|---|---|
| Income | `IncomeRules`, `RecordIncomeUseCase`, `EditIncomeUseCase`, `ObserveIncomeUseCase` | `users/{uid}/transactions`, type `INCOME`; edits in `corrections`; undo in `voids` | Shared `LedgerRepository` (Firestore) |
| Freelance | `FreelanceRules`, `FreelanceCalculator`, `SaveProjectUseCase`, `SaveMilestoneUseCase`, `LinkReceiptUseCase`, `ObserveOutstandingUseCase` | Projects in `users/{uid}/projects`. Milestones in `expectedPayments`. Receipt links in `paymentMatches` | `FirestoreProjectRepository` (`ProjectRepository`) |
| Crypto | `CryptoMovementRules`, `CryptoPortfolioCalculator`, `RecordCryptoMovementUseCase`, `SetOpeningCostUseCase` | Movements are transfers (two `transactions` legs). Opening cost in `users/{uid}/cryptoOpeningCosts/{accountId}` | `FirestoreOpeningCostRepository` (`OpeningCostRepository`) |
| Bills | `BillSchedule`, `BillRules`, `BillsCalculator`, `RecurrenceDetector`, `SaveBillUseCase`, `LinkBillPaymentUseCase`, `ConfirmSuggestionUseCase`, `SetBillCancellationUseCase` | Bills in `users/{uid}/bills`. A due date is saved in `expectedPayments` (id `bill-{billId}-{date}`) once a payment is linked | `FirestoreBillRepository` (`BillRepository`); dismissed suggestions in DataStore |
| Reminders | `ReminderPlanner`, `PlanRemindersUseCase`, `MarkRemindersShownUseCase` | This phone only: DataStore `payment_reminders` | `WorkManagerPaymentReminderScheduler`, `PaymentReminderWorker` |
| Capture | `BankSmsParser`, `CaptureTextUseCase`, `CapturedDraftRules`, `ConfirmCapturedDraftUseCase`, `StageIncomingSmsUseCase`, `ProcessStagedSmsUseCase` | Drafts in `users/{uid}/drafts` (structured fields only). Automatic capture stages text in the encrypted Room table `staged_sms` | `FirestoreDraftRepository`; `RoomSmsStagingRepository`; `SmsCipher`; `AutoCaptureWorker` |

Shared models are in `core/domain` and described in [domain-contracts.md](../domain-contracts.md). Firestore access is limited to the owner by `firestore.rules`; 25 rules tests pass (see [L10](../evidence/L10/README.md)).

### 3.2 Money rules

- Amounts are `Money`: integer minor units for fiat (LKR 1,250.50 is `125050`) and exact decimals for crypto. No `Double`.
- Every record stores a `ReportingConversion` to LKR at save time: `SameCurrency`, `Converted` (with the rate) or `Missing`.
- Income form: a rate applies only to foreign income. A blank rate means unknown and is stored as `Missing`. Rates must be positive (`IncomeRules`).
- A corrected amount keeps the stored historical rate. Changing the rate replaces the conversion.
- Totals return an amount and an `unresolvedCount`. When the count is above zero, the screen says a record has no rate.
- Dates are calendar days in `Asia/Colombo`.

### 3.3 What counts as Received

- **Received** is `INCOME` transactions only (`LedgerSummaryCalculator`).
- **Not Received:** milestones and bill due dates (expected payments), unexplained credits (drafts), refunds, transfers (including every crypto movement), opening balances and adjustments.
- Undone entries (void markers) are ignored everywhere.
- Edits are corrections appended to the original. Imported or typed facts are never overwritten.
- A milestone receipt is capped at the amount still unlinked, so one payment is never counted twice across milestones (`FreelanceCalculator`).
- A bill never posts an expense by itself. The user links the real payment to a due date.
- A bill cancelled from a date keeps its earlier due dates. "Planning to cancel" changes nothing until confirmed.

### 3.4 Capture and privacy

- The parser reads shared, pasted or received text on the phone and keeps only structured fields: amount, date, time, card or account hint, merchant and type.
- Shared text is held in memory only (`SharedTextInbox`).
- Automatic capture (`RECEIVE_SMS`) is off until the user turns it on. The receiver component is disabled in the manifest until then. Only messages that parse as a tested bank format are staged.
- Staged text is encrypted with AES-GCM using a key in the Android Keystore. The staging table is `staged_sms` in `CaptureStagingDatabase`.
- A WorkManager job turns staged messages into pending drafts when the phone is online (an extra pass runs every 12 hours). The staged text is deleted once processed.
- Messages not saved within 7 days are deleted and only an expiry notice is kept. Turning capture off discards waiting messages.
- A message staged by one user is never processed for another user.
- Firestore never receives the raw SMS.

## 4. How it connects to Home and Needs review

| Where | What it reads | Source |
|---|---|---|
| Home: Received this month | `INCOME` entries of the month | `ObserveHomeOverviewUseCase` using `LedgerSummaryCalculator`, the same maths as `ObserveIncomeUseCase` |
| Home: Spent this month | `EXPENSE` entries only | Bills, milestones and drafts add nothing until a real expense is saved |
| Home: Clients owe you | Unreceived milestone money, one total per currency | `ObserveHomeCommitmentsUseCase` using `ObserveOutstandingUseCase` |
| Home: Next bill due | Next bill due date, shown as expected | `ObserveHomeCommitmentsUseCase` using `ObserveNextBillUseCase` |
| Home: "need review" card and Needs review | Records that need attention, such as income with a missing rate | `features/catchup` (`UnresolvedRules`, kind `MISSING_RATE`) |
| Capture sources: waiting drafts | Pending drafts from shared, pasted or received SMS | `ObserveCapturedDraftsUseCase` |
| Review message screen | One draft: confirm, change type, or discard | `ConfirmCapturedDraftUseCase` |

How data flows:

1. Home reads the ledger through use cases, so adding income, linking a receipt or undoing an entry updates Received, Clients owe you and Next bill without a refresh, including offline.
2. Income with no rate raises the Home check line and a "Missing rate" item in Needs review. Adding the rate on the entry removes it and the total updates.
3. A captured SMS becomes a draft. In this build the draft is reviewed from **Capture sources** (the Review message screen), not from the Needs review list. The Needs review list shows unresolved ledger records, not drafts.
4. When the user saves the draft, it becomes an expense, income or transfer. Only then do Spent or Received change. A draft never changes Home on its own.
5. Linking a recorded expense to a bill due date settles that due date. The expense counts in Spent once, and the bill due date adds nothing.
