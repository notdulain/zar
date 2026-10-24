# OpenRouter migration verification

Verified on 24 October 2026 (Asia/Colombo) using Pixel 6a Android 17 emulator and the ordinary debug app.

- Worker: `https://zar-quick-notes.03dulain.workers.dev`
- Deployment version: `82e791fd-984e-436b-822c-6385e40aab4f`
- Model: `google/gemini-3.5-flash-lite` through OpenRouter.
- The OpenRouter key is installed as a Cloudflare encrypted secret. The local copy is git-ignored with mode 0600.
- Firebase App Check API enabled and this emulator's existing debug token registered; enforcement retained.
- Real Firebase-issued App Check token exchanged and verified with the production Worker verification code.
- Deployed `/health`: HTTP 200. Unauthenticated `/v1/quick-note`: HTTP 401 with safe typed error.
- Worker type check and deployment dry run passed. 37 Worker unit tests passed.
- 49 existing backend contract/validation tests passed.
- 133 Android quick-note JVM tests passed, including 6 HTTP transport tests; `installDebug` succeeded.
- [Live model evaluation](../../../tools/quicknote-eval/results/openrouter-google-gemini-3.5-flash-lite.json):
  20/20 expected drafts matched, 0 provider errors, median 1.60 s, p90 2.133 s, maximum 3.735 s in one run.
- In the running app, the made-up note `test lunch 850 cash` produced one expense proposal for LKR 850,
  account Wallet, dated 24 October. The existing review screen displayed it before any save.
- The test proposal was discarded. No test transaction was saved. Home still displayed LKR 0 spent.

The emulator initially had broken DNS. Restarting it with `-dns-server 1.1.1.1,8.8.8.8` restored validated
internet; app data was preserved. No app connectivity checks were weakened.

Screenshots from the live app:

1. [Write a made-up note](01-write-note.png)
2. [Review the real Worker/OpenRouter result](02-live-review.png)

Not tested: physical Android device, release Play Integrity attestation, sustained load, and arbitrary new
models. This 20-case sample is one evaluation run, not a guarantee that every future note is interpreted correctly.
