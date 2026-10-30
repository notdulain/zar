# 4.1 Firebase ownership and type rules

Ran the complete rules suite against the local Firestore emulator, project `demo-zar`, loading this checkout's `firestore.rules` through `initializeTestEnvironment`. No rules or deployed services changed.

Added 19 cases to the existing 25:

- Fifteen collection cases each refuse foreign and signed-out creates, then accept the exact same owner payload. After creation they accept the owner read and refuse foreign/signed-out get, list, update and delete. Collections: accounts, transactions, corrections, voids, evidenceLinks, expectedPayments, paymentMatches, projects, goals, allowance, cryptoOpeningCosts, bills, categoryRules, expenseCategories, drafts.
- Evidence links: valid create, immutable/replayed write, delete, ID mismatch, wrong transaction/timestamp types and raw-text field rejection.
- Transactions: wrong amount/minor/date/timestamp/account/source types and raw message field rejection.
- Corrections and voids: immutable corrections, schema pollution, mismatched transaction ID and wrong timestamp type.
- Default denial of unknown collections, nested paths, root user documents and top-level financial documents.

Existing tests retain coverage for raw text inside draft proposals and updates, terminal draft status, atomic transfer replay, money precision, correction account ownership/currency, bill cancellation and other feature schemas.

```bash
cd firestore-tests
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node --test rules.test.mjs
```

Result: **44 tests passed, 0 failed, 0 skipped** (3.23 seconds). SDK versions: Firebase 11.10.0, rules-unit-testing 4.0.1, reused from the existing local installation because the disk was nearly full. The emulator was already running from this task; running `npm test` instead starts it through `emulators:exec`. The direct command runs the same complete test file and uploads this checkout's rules. Run it separately from Android integration tests: its `beforeEach` clears demo data.

No production defect demonstrated by these cases. These are emulator schema/ownership checks, not live-project or production App Check verification. Date validation in the current rules checks the stored string format; calendar validity is handled by domain parsing, not newly guaranteed by these cases.
