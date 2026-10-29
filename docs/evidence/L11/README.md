# L11 (SCRUM-34) evidence

Screens captured on 29 Oct 2026 from the debug app (`-Pzar.useEmulators=true`) on a Pixel 8 emulator (API 36), with the Firebase emulators and a seeded test account. The seed steps are in [the demo script](../../report/L11-demo-script.md).

- `raw/`: unmarked screenshots.
- `1-` to `9-`: the same screens with numbered red circles, made by `tools/annotate-screenshot.py`. The numbers are explained in [the report](../../report/L11-income-bills-capture.md), section 2.

## Checks run

| Check | Result |
|---|---|
| Relative links and image paths in `docs/report/L11-income-bills-capture.md`, `docs/report/L11-demo-script.md` and `docs/sms-compatibility.md` | 55 checked, 0 broken |
| Class names written in code font in the report, searched in `app/src` and `firestore.rules` | All found |
| `./gradlew testDebugUnitTest` | 1,274 passed, 0 failed, 0 skipped (same as L10) |
| Changes since the L10 head (`87b2b90`) | Docs, evidence images and `tools/annotate-screenshot.py` only. No app code |
