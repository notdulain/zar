## Context
Auth is email/password on Firebase. `SendPasswordResetUseCase` and `FirebaseAuthRepository.sendPasswordReset` already exist and are used by "Forgot password?". Settings already reads the session email.

## Decisions
- **Reuse the email path** rather than build an in-app password form: no re-auth flow, no password handled in the app.
- **State lives in `SettingsViewModel`** as a small flow (Confirming → Sending → Sent, with an error on Confirming). Each open gets an attempt id so a late result cannot reopen a sheet that was closed or belongs to another attempt.
- **Dismissal while sending** is blocked for Back and the close/cancel buttons; swipe/scrim cannot be blocked by the Material sheet, so the ViewModel lets the send finish quietly and reattaches on reopen instead of sending twice.
- **Account guard**: the ViewModel tracks the session email; a different email or sign-out closes the sheet and cancels the job.
- **Errors** reuse `errorText` from auth. `WrongCredentials` has no meaning for a reset, so Settings shows "We couldn't send a link to this account."
- **UI** reuses `ZarModalSheet`, `SheetHeader`, `ZarButton` and the Appearance row, which becomes a shared `ActionRow`. No new Figma node; Z87 predates Appearance and Reduce motion.

## Risks
- Firebase may succeed silently for unknown emails (enumeration protection); the sheet says only that a link was sent.
- Real delivery needs a real inbox; verified end to end against the local Auth emulator only.
