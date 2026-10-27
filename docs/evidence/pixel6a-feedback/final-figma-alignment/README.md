# Final Figma alignment: emulator evidence (27 Oct 2026)

Device: Pixel_6a AVD, Android 17 (API 37), `emulator-5554`, 1080×2400 at 420 dpi. Local Firebase emulators only (Auth 9099,
Firestore 8080), synthetic account `benchmark@zar.test` from `tools/benchmark-seed.mjs`. Debug build with
`-Pzar.useEmulators=true`. Nothing here is a physical-phone, 60/120 Hz or haptic claim.

Tested code: branch `t3/resume-figma-alignment`, commit under test = the head recorded in
`openspec/implementation-handoffs/final-figma-alignment-sonnet-ready.md`.
Figma: file `bBBJuQz1Cg8N34FcisKBs9`, page Screens `1:2`. Local PNG exports used for comparison (Figma connector not needed).

## What is in each folder
| Folder | What it is |
| --- | --- |
| `light/`, `dark/` | Screenshots of the **running app** (`adb screencap`), real flows on the seeded account |
| `fixtures/` | **Compose fixture renders** of production composables on the emulator (`FinalFigmaFixtureEvidenceTest`). Not a running flow; used only for states the seeded account cannot reach on demand (Received empty, Received couldn't load, auth button and error text) |

## Running-app captures
| Area | Figma | Files |
| --- | --- | --- |
| Sign in, keyboard (Light) | Z188 | `light/01-sign-in`, `01b-sign-in-keyboard` |
| Add income order (Amount, rate, Account, Category, Date/Time, From, Note, Save) | Z162 `465:8316` / `465:10428` | `light/02*`, `dark/02*` |
| Real save: 250 USD at rate 300 raised Received from LKR 808,000 to 883,000 | Z162 | `light/02d-after-save` |
| Received list, Edited marker, No LKR rate | Z169 `520:16540` / `522:17043` | `light/03` (Client payment row carries Edited), `dark/03` |
| Income edit (Undo this income + Save) | Z171 `522:16719` / `522:17140` | `light/05`, `dark/05` |
| Settings switch off/on, off-track `#7A7B82` | `control/trackOff` | `light/04`, `dark/04`, `dark/04b` |
| Crypto hero gold gradient | Crypto hero fix | `light/07`, `dark/07` |
| More tab at the nav end | M2 `533:17261` | `light/06`, `dark/06` |
| Activity search with keyboard, nav hidden | Z190 `560:21113` / `560:21333` | `dark/09b` |
| Typed time: dial box collapsed, Set time above the number pad | Z191 `561:354` / `561:618` | `dark/13b`, `dark/13c` |
| New project form | Z195 `564:632` / `564:1083` | `dark/15`, `dark/15b` |
| Freelance with no projects | Z197 `564:21980` / `564:22228` | `dark/14c` |
| Enlarged text 1.5 and 2.0, small display 0.85 / 340 dpi | section 25 `569:18733` | `dark/10*`, `11`, `12*`, `14*` |

## Fixtures (clearly not app runtime)
`fixtures/{light,dark}-received-empty` (Z170), `...-received-error` (Z170b), `...-auth-button-error` (Z188/Z189 button and error colours).

## Comparisons and known differences
- Received list matches Z169: back, title, summary card, month group card, trailing amount, Add income pill. Row text wraps differently at 2.0 font (amount drops under the title from 1.3x, per section 25).
- Add income was restyled to Z162/Z171 in a later pass (compact sheet, inline amount and currency, account and category strips, date/time row): see `income-parity/README.md`. The captures `light/02*` and `dark/02*` in this folder predate it and show the old layout.
- Z197 shows the hero message with no title line, so the Freelance screen has no heading text.
- Z186 to Z189, Z192 to Z194, Z196 were not part of the single follow-up (handoff section 10 item 5 lists only Z190, Z191, Z195). Only the Z188/Z189 button and error colours are covered.

## First-reveal regression: fixed in the parent integration
`light/06b-more-from-home-tile-capsule-missing.png` preserves the before-fix capture. Opening More from the Home tile could leave its capsule blank because the first draw observed a non-snapshot `placed = false`; placing the capsule did not invalidate that draw. The parent integration makes first placement observable. The draw-invalidation regression failed before the fix and passed afterward. Fresh running-app captures `light/06c-more-from-home-tile-capsule-fixed.png` and `dark/06c-more-from-home-tile-capsule-fixed.png` show the first reveal after a fresh launch.

Verification of this small parent-authored fix: 15 `NavCapsuleControllerTest` unit tests and 12 `ZarBottomNavMotionTest` emulator tests pass, with no failures or skips. This includes the regression, LTR/RTL end stops, interruption and reduced motion. The earlier Sonnet run counted 1,228 unit tests; its full instrumentation run counted 351 tests, no failures, nine skips. The seven Firebase-backed skips passed in a separate emulator-enabled run; the other two restart-phase skips remain intentional (last separately verified in the prior suite handoff). These are separate runs, not a new combined full-suite result.

The Add Income layout differences were closed in the later `income-parity/` pass; field order alone had not established visual parity.

## Restored device state
Light appearance, Reduce motion off, Hide amounts off, density 420, font scale 1.0, night mode off, animator scale 1.
