# Pixel 6a feedback — implementation evidence

Branch: `t3/complete-pixel6a-gaps-1` (sequential Sonnet 5.5 pass, continuing `t3/complete-pixel6a-gaps` @ `ee588b6`).
Base: `8717c518d7f1f110157977c7673e6164cf58b55f`.
Authorship: the earlier screenshots (`add-session/`, `income-*`) were produced by **OpenCode DeepSeek V4.1 Flash** (authorized
fallback); the design tokens and Appearance/Reduce-motion frames they follow were authored by **Opus**. Everything added
from `ca85e6d` onward (`categories/` 01-06, `appearance/`, `review/`, `motion/`) is **Sonnet 5.5**.

## What is real here

- Every PNG under this folder is a screenshot of the **actual production Compose composables**, rendered on the
  **`emulator-5554` Pixel_6a AVD (API 37, 1080×2400)** by instrumented tests (`uiAutomation.takeScreenshot`).
- This is emulator evidence. It is **not** physical-device evidence and makes **no** 120 Hz / performance claim.
- No test fixture is presented as a physical screenshot. Test data (account names, custom category names) is
  labelled in the test and is illustrative only.

## Captures

### `categories/` — three-column all-categories grid and Add category sheet

Source test: `app/src/androidTest/java/com/example/zar/features/transactions/CategoryGridEvidenceTest.kt`
(`add-expense-categories-grid` 3.1/3.2, `align-income-with-figma` 2.1).

| File | State |
| --- | --- |
| `01-category-row-light.png` | Light, the form's inline category row with "All categories →" (grid not yet open) |
| `02-grid-built-ins-light.png` | Light, grid open at the built-ins |
| `03-grid-custom-long-name-light.png` | Light, grid scrolled to the user's own categories; "Tuition for my youngest child" wraps inside its column |
| `04-grid-custom-long-name-dark.png` | Dark, same scrolled grid |
| `05-add-sheet-dark.png` | Dark, Add category sheet with a name typed |
| `06-add-sheet-light.png` | Light, same sheet |
| `income-01-light.png` / `income-02-dark.png` | Add/Edit income in the shared transaction sheet shell |

(The earlier `01-grid-light`/`02-grid-long-name-light` were byte-identical: both captured the same scroll state. They are
replaced by the distinct captures above.)

### `appearance/` — real app, signed in on the seeded local demo account (emulator)

`01-settings-light`, `02-appearance-options-light` (Light/Dark/System), `03-settings-dark`,
`04-settings-dark-reduce-motion-on`, `05-home-dark-full-nav`, `06-activity-dark-nav`, `07-settings-light-restored`,
`08-activity-light-nav`, `09-time-dial-light` (hour mode) and `10-time-dial-dragged-light` (after a drag, minute mode).
Appearance and Reduce motion were restored to Light / off afterwards.

### `review/` — real-device-size checks that led to fixes (see `review/review.md`)

### `motion/` — benchmark tables, Perfetto-derived metrics, screen recordings (see `motion/evidence.md`)

## Limits (no claim made)

- **Physical Pixel 6a**: unavailable. No physical haptics, physical 60/120 Hz or physical screenshot is claimed.
- **120 Hz**: cannot be proven on these emulators; remains pending a capable physical device.
- Instrumented UI checks were run on the emulator for the pure-Compose suites listed in the handoff; Firebase-backed
  instrumented suites were not re-run in this recovery session except where noted.
