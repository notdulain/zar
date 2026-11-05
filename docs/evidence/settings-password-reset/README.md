# Settings password reset: evidence

Captured on the Pixel_8 Android emulator (API 37.1, 1080x2400), serial `emulator-5570`, with `UiAutomation.takeScreenshot()`.

- `01`–`07` are **fixture renders** from `SettingsPasswordResetUiTest`. The state is driven by the test with the synthetic address `nimal.test@example.com`; no ViewModel or backend is involved. The error states (04, 06) show how each message looks, not a real failure.
- `08`–`09` are from `SettingsPasswordResetEmulatorTest`: the real app, registered and signed in with a throwaway account on the local Firebase Auth emulator (project `demo-zar`). The reset request was real; the test then read the emulator's out-of-band code list and found exactly one `PASSWORD_RESET` code for that address (none before opening the sheet or before tapping Send). No email left the machine.
