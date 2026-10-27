# Pixel 6a round 2: running-app evidence

Captured from the **running debug app on `emulator-5554`** (1080x2400 @ 420dpi, 60 Hz, Android 17 emulator), synthetic data only:
the local Firebase Auth/Firestore emulators and `node tools/benchmark-seed.mjs` (demo user `benchmark@zar.test`, 2 accounts, 54
transactions), signed in through the real sign-in screen. Build: `-Pzar.useEmulators=true` (`BuildConfig.USE_FIREBASE_EMULATORS` checked
true before sign-in). Script: `tools/record-round2.py` (refuses any serial that is not `emulator-*`; the physical phone was not attached
and was never touched). Both themes, screenshots by `screencap`, clips by `screenrecord`. Tested commit: `2ea5e0d` plus the Plans list
and sign-in message edits that follow it in the branch history; the frames were captured after those edits.

Figma: file `bBBJuQz1Cg8N34FcisKBs9`, section 26 `626:19643`; compared Plans P2 `630:20302` and sign-in Z41 `269:3558` / Z42 `269:3587` by eye
against these frames (positions, 420dp hero, gradient, button colours match; differences below).

| Frame / clip | What it shows |
| --- | --- |
| `auth-idle/keyboard/error-*.png` | Sign-in on the dusk gradient: idle, keyboard up (form does not move), wrong-password message. Light button white, Dark button accent blue. |
| `home-rest/scroll-1/scroll-2/end-floating-nav-*.png`, `home-collapse-*.mp4` | Whole top card collapsing into the status bar and flattening; last rows scroll behind the floating Add with no painted band. |
| `nav-activity-selected-*.png`, `nav-stationary-taps-*.mp4` | Four equal slots; only the capsule moves, including 12 rapid taps. |
| `pager-swipe-*.mp4`, `pager-after-swipes-*.png` | Finger swipes between tabs. |
| `back-child-button/edge-*.mp4`, `back-sheet-button-*.mp4`, `settings-child-*.png`, `add-sheet-*.png` | Back from Settings (button; edge swipe cancel then commit) and from the Add sheet (button). |
| `plans-*.png`, `plans-new-plan-sheet-*.png`, `plans-end-floating-nav-*.png` | Plans hero with + New plan, the New plan chooser (Savings goal form), end of list behind the nav. |

## Frame timing (emulator, honest)

`dumpsys gfxinfo` on the emulator's 60 Hz display, one pass each: Home scroll collapse x8: 156 frames, 11.5% janky, p50 17 ms, p95 23 ms,
p99 81 ms. Pager swipes x6: 201 frames, 19.4% janky, p50 17 ms, p95 36 ms, p99 85 ms (first composition of the neighbouring page).
12 tab taps: 261 frames, 0.8% janky, p99 48 ms. This is an emulated GPU, **not the Pixel 6a and not 120 Hz**; no 60/120 Hz claim is made.

## Known differences from Figma

- Plans: Figma shows the Weekly limit / Bills / Freelance rows in one card; the app does too. The goal icon is the piggy bank (Figma draws a laptop).
- Plans with a goal also shows a "Goal progress" card below the list (Figma Z04 has none; the app keeps its existing progress details).
- Back: parent returns from 0.2W behind; the 1dp hairline on the leaving child's edge is not drawn. The pager-tab Back (Activity -> Home) uses a pager scroll rather than the 0.2W recede.
