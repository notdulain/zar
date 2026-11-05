## Why

Signed-in people have no way to start a password change from inside Zar. The only entry is "Forgot password?" on the sign-in screen, which needs them to sign out first. Settings already shows who is signed in, so it is the natural place to offer a reset.

## What Changes

- Settings shows a **Password** row ("Reset by email") directly under "Signed in as".
- Tapping it opens a confirmation sheet that names the account email and explains that a link is sent by email. Nothing is sent until the person taps **Send reset link**.
- While sending the buttons are disabled and Back and the close button are ignored (a swipe or scrim tap can still close the sheet; the send then finishes quietly and reopening shows it in flight). Success shows "Check your inbox". A failure keeps the sheet open with a mapped message and **Try again**.
- The send reuses the existing email reset path (`SendPasswordResetUseCase` → `AuthRepository.sendPasswordReset` → Firebase `sendPasswordResetEmail`). This is not an in-app password change. The person stays signed in.
- Not in scope: changing the password inside the app, re-authentication, other sign-in methods, new Figma nodes.

## Capabilities

### New Capabilities
- `settings-password-reset`: asking for a password reset email from Settings.

### Modified Capabilities
<!-- none: no existing OpenSpec spec covers Settings -->

## Impact

- `SettingsViewModel` (reset flow state, session guard), `SettingsScreen` (row + sheet), strings.
- Tests: `SettingsViewModelTest`, `AuthViewModelsTest` (constructor), new Compose test `SettingsPasswordResetUiTest`.
- No new dependency, no Firestore/rules change, no backend change.
