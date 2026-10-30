# L11 (SCRUM-34) evidence

Screens captured on 29 Oct 2026 from the debug app (`-Pzar.useEmulators=true`) on a Pixel 8 emulator (API 36), with the Firebase emulators and a seeded test account. The seed steps are in [the demo script](../../report/L11-demo-script.md).

- `raw/`: unmarked screenshots.
- `1-` to `9-`: the same screens with numbered red circles, made by `tools/annotate-screenshot.py`. The numbers are explained in [the report](../../report/L11-income-bills-capture.md), section 2.

## Original author checks (29 Oct 2026)

| Check | Result |
|---|---|
| Relative links and image paths in `docs/report/L11-income-bills-capture.md`, `docs/report/L11-demo-script.md` and `docs/sms-compatibility.md` | 55 checked, 0 broken |
| Class names written in code font in the report, searched in `app/src` and `firestore.rules` | All found |
| `./gradlew testDebugUnitTest` | 1,274 passed, 0 failed, 0 skipped (same as L10) |
| Changes since the L10 head (`87b2b90`) | Docs, evidence images and `tools/annotate-screenshot.py` only. No app code |

## PR review checks (30 Oct 2026)

- Integrated main after parent PR #38 merged, and retargeted PR #40 to main.
- Checked the report against the ledger calculator, capture review UI/use cases, staging expiry, Firestore writes and route/build configuration. Corrected Spent/refund rules, transfer evidence linking, merchant editing and staged-text retention claims.
- Checked 56 relative links and image paths across the report, demo, SMS compatibility document and this README: zero broken.
- Decoded and visually inspected all 20 PNGs (9 annotated, 11 raw). They are historical author captures, not fresh review captures. Home's check line is visibly truncated and the fixed bottom navigation covers part of the Spent card; these images demonstrate the seeded data, not visual parity or a clipping-free UI.
- Ran the annotation CLI on a raw screenshot with two markers and `--width 360`; it produced a valid 360 × 800 image. The tool requires Python 3 and Pillow (`python3 -m pip install Pillow`).
- Checked all 71 class/enum names written individually in code font in the report against `app/src`: all found.
- No Android, instrumentation or Firestore tests were rerun for this docs/script-only diff. Earlier counts above are author results; PR #38's separate 30 Oct review recorded 1,291 passing unit tests after integrating main.
- No physical-phone demo or live Figma comparison was performed in this review.
