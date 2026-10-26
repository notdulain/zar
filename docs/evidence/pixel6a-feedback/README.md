# Pixel 6a feedback — implementation evidence

Branch: `t3/resume-pixel6a-implementation` (consolidated recovery worker).
Base: `8717c518d7f1f110157977c7673e6164cf58b55f`.
Author of these screenshots: **OpenCode DeepSeek V4.1 Flash** (authorized fallback), not Opus/Sonnet.

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
| `01-grid-light.png` | Light, all-categories grid: built-ins then customs, three equal columns, wrapping long label, partial last row, Add category action above |
| `02-grid-long-name-light.png` | Light, long custom name ("Tuition for my youngest child") wrapping inside its column |
| `03-add-sheet-light.png` | Light, Add category sheet with a name typed (Name / Cancel / Save) |
| `04-add-sheet-dark.png` | Dark, same sheet, with the IME up |
| `income-01-light.png` | Light, Add/Edit income in the shared transaction sheet shell (Income tab, amount, account, date/time, received-from, category, note, Save) |
| `income-02-dark.png` | Dark, same income sheet |

## Limits (no claim made)

- **Physical Pixel 6a**: unavailable. No physical haptics, physical 60/120 Hz or physical screenshot is claimed.
- **120 Hz**: cannot be proven on these emulators; remains pending a capable physical device.
- Instrumented UI checks were run on the emulator for the pure-Compose suites listed in the handoff; Firebase-backed
  instrumented suites were not re-run in this recovery session except where noted.
