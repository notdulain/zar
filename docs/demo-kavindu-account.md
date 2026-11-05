# Kavindu Silva live demo account

The assignment persona is represented by **synthetic records in a normal Firebase Auth account**: `kavindu.silva.demo@example.com`. The shipped app has no hard-coded Kavindu data. `KavinduDemoSeed` is an explicitly invoked Android instrumentation fixture that writes through the app's production repositories and use cases; it is not part of the release APK. The generated password is in the local ignored `.env.kavindu-demo` file, never in Git.

## Before seeding

1. Use the team's Android `google-services.json` for Firebase project `zar-app-35602` and package `com.example.zar`. The older local file for `zar-app-2a967` has no default Firestore database and cannot support the live demo. Keep this file ignored by Git.
2. Confirm that the live project has Firebase Email/Password Authentication, a default Firestore database, and the repository's Firestore security rules deployed. The account can only read and write its own `/users/{uid}` records.
3. Build a normal debug APK and Android test APK **without** `-Pzar.useEmulators=true`. Use an unlocked Android emulator or device. The app's live Firebase project comes from `google-services.json`.
4. Keep the dedicated account credentials in an ignored local `.env.kavindu-demo` file. Never commit or put its password in the report.

The fixture accepts `-e live true` only for this email address. It checks for existing live records before writing and reads back representative records from the server afterward. A failed run must be investigated before it is repeated because ledger entries are write-once.

On 5 November 2026, the fixture passed against live `zar-app-35602` after the supplied Android configuration was installed. A fresh sign-in in the normal app loaded Home, Plans, Crypto, Freelance, and Bills. The [running-app screenshots](evidence/kavindu-live-demo/README.md) record the observations and their limits.

`KavinduDemoNovemberSeed` adds the first five days of November (two receipts, six expenses) so the current month is not empty. Run it once after `KavinduDemoSeed`, with the same `-e live true`, email and password arguments; it refuses to run twice.

## What the records show

| Assignment situation | Demo data |
| --- | --- |
| Variable salary and multiple income sources | October salary LKR 132,500; LKR 45,000 freelance payment; Google AdSense USD 42; USD 15 client tip with a missing LKR rate to show incomplete reporting |
| Freelance milestones | Lanka Bakers and Ceylon Tea Traders projects; one matched paid milestone, one overdue/open milestone, and one upcoming milestone |
| Crypto gains and losses | USDT purchase in October; ETH bought and sold at a realized gain in Q1 2026; BNB bought and sold at a realized loss in Q3 2026 |
| Fragmented spending | Commercial Bank, Sampath Savings, cash, Payoneer USD, and crypto accounts; rent, utilities, gym, subscriptions, PickMe, Uber Eats, groceries, and cash spending |
| Coffee and dining surprise | Seven coffee/dining expenses total exactly LKR 24,000 in October |
| Savings and goal | After the gym debit, Sampath Savings holds LKR 11,200; the MacBook Pro M4 goal targets LKR 490,000 by September 2027 and allocates LKR 11,200 |
| Spending clarity | Committed, discretionary, and unknown expenses are present; a weekly discretionary allowance is enabled |

## Demo route

Sign in as the dedicated account. Select **October 2026** in Home to show the complete salary, income, spending, coverage, and goal story. Open Plans for the MacBook shortfall and allowance. Open Activity to show actual entries, then Freelance for unpaid milestones, Bills for recurring commitments, and Crypto for the Q1 gain/Q3 loss. The current month is November 2026, so a current-month Home view correctly does not pretend October's salary has arrived again. Expected freelance money is not counted as received income, and crypto transfers are not counted as spending.

The PDF's requirements document, schema, architecture diagram, wireframes, APK, and live presentation are separate deliverables; creating an account does not replace them.
