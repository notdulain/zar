# Add / Edit income: Figma visual parity (27 Oct 2026)

Figma `bBBJuQz1Cg8N34FcisKBs9`, page Screens `1:2`: Z162 Add income (Light `465:8316`, Dark `465:10428`) and Z171 Edit income
(Light `522:16719`, Dark `522:17140`). Official connector context and screenshot for Z162 Light were read in the earlier run;
the other three frames were compared from the local PNG exports.

Device: Pixel_6a AVD (`emulator-5554`, 1080×2400 at 420 dpi). Local Firebase emulators only (Auth 9099, Firestore 8080),
synthetic account `benchmark@zar.test` (`tools/benchmark-seed.mjs`), debug build with `-Pzar.useEmulators=true`
(`BuildConfig.USE_FIREBASE_EMULATORS = true` checked before sign-in). Nothing here is a physical-phone, 60/120 Hz or haptic claim.
Every image is a running-app `adb screencap`; there are no fixtures in this folder.

Tested code commit: `abb07bf` (this folder's docs and images are committed after it and change no code).

## What changed
- Sheet is a compact, intrinsic-height lower sheet: its top sits at about 30% of the screen, as in Figma (Z162 about 31%).
- AMOUNT and the currency control share one row. Currency stays derived from the selected account: the dropdown lists only the
  currencies of non-archived accounts and picking one selects the first usable account in it (keeps the current one when it
  already matches). An edit keeps its recorded currency and shows it without a menu.
- ACCOUNT with `All accounts →` (stacks every account; picking one returns to the strip) over a horizontal chip strip with a
  fade at the scrolling edge. CATEGORY strip, Freelance first as in Figma.
- DATE and TIME on one row, `Today, 27 Oct` / `Yesterday, …` / `Tue, 13 Oct`; the time is optional and reads `Not set`.
  Date and time open the shared Expense pickers (Today/Yesterday shortcuts, dial). The time picker offers `Remove time` only
  when a time is set. FROM and NOTE are inline rows. Save is full width; edit has `Undo this income` + Save.
- The rate row for a non-LKR account stays, directly under the amount.
- Edit opens as a dialog over the Received list, so the list shows behind the scrim as in Z171.
- Large text: from 1.3× the row labels sit above their values; from 1.5× the edit actions stack with Save first (Figma stacks at
  2.0, but `Undo this income` already clips at 1.5×). The form scrolls and Save stays pinned. All targets are at least 48 dp.

## Captures
| Area | Light | Dark |
| --- | --- | --- |
| Add default, filled | `light/01`, `02` | `dark/01`, `02` |
| Keyboard open (amount) | `light/04` | `dark/04` |
| Date picker, time picker | `light/05`, `06` | `dark/05`, `06` |
| All accounts | `light/07` | `dark/07` |
| Currency menu, USD with rate row | `light/08`, `09` | `dark/08`, `09` |
| Edit | `light/10` | `dark/10` |
| Font 1.5 / 2.0 (Add) | | `dark/11`, `12` |
| Small display 340 dpi (Add, Edit) | | `dark/13`, `14` |
| Edit at font 2.0 / 1.5 (actions stacked) | | `dark/15`, `16` |
| Figma beside app | `compare/add-light`, `edit-light` | `compare/add-dark`, `edit-dark` |

Account names, amounts and the "Today, 27 Oct" date differ from Figma because the frames use sample data (Sampath Bank, 125,000, 15 Oct).

## Known differences from Figma
- The amount is not digit-grouped while editing (`125000`, Figma `125,000`); the shared Zar amount field does the same on Expense.
- Time starts as `Not set` (the domain keeps time optional); Figma shows `9:55 AM`.
- The currency control has no caret when it has no menu (edit, or a single account currency); Figma's edit frame shows one.
- The scrolling edge of the chip strip fades; Figma Dark draws a hard clip.
- The sheet is about 3 dp taller than Z162.
- Figma stacks edit actions at 2.0× text; the app does from 1.5×.

## Functional run on the emulator (same synthetic account)
Add 5,000 LKR Salary from `ParityTest`: Received this month 883,000 → 888,000 (one record). Edit to 5,500: 888,500.
`Undo this income`: back to 883,000. Android state afterwards: Light, Reduce motion off, density 420, font 1.0, night off, animators 1.

## Tests (this change only)
- Unit: 1,232 passed, 0 failed (1,228 before, plus 4 `IncomeFormatDateLabelTest`).
- Instrumented, impacted classes only (`IncomeSheetParityUiTest`, `IncomeCorrectionUiTest`, `FinalFigmaAlignmentUiTest`,
  `AddTransactionSessionUiTest`, `ManualEntryScreenUiTest`, `TimePresetUiTest`, `ActivityScreensUiTest`, `RouteMotionNavTest`): 96 passed,
  0 failed, 0 skipped; after the last category-order change `IncomeSheetParityUiTest`, `FinalFigmaAlignmentUiTest`,
  `IncomeCorrectionUiTest` were re-run: 28 passed. The full 351-test instrumented suite was not re-run.
- `assembleDebug` and `compileDebugAndroidTestKotlin` pass on JDK 21 with `--max-workers=2`.
