# Integrated critical-flow review (sequential Sonnet 5.5 pass, 26 Oct 2026)

Emulator Pixel_6a API 37, signed in on the seeded local demo account (`tools/benchmark-seed.mjs`). Settings changed for
checks (`wm density`, `font_scale`, `user_rotation`, animator scale) were restored afterwards.

| Flow | What was checked | Result |
| --- | --- | --- |
| One Add session: scope, drafts, rotation | Typed 777 on Expense, switched to Income, typed 4242, rotated to landscape and back, returned to Expense | Both drafts and the active tab survived (`EditText` values read back via UI dump). Draft/tab/save semantics also covered by `AddTransactionSessionTest` (6) and `AddTransactionSessionUiTest` (2) |
| Save exactly once | `AddTransactionSessionTest`: one Save writes one record and leaves the other tab's draft; `ManualEntryViewModel.save` ignores a second tap while `isSaving` | Covered, unchanged |
| Keyboard / insets | Large font 1.5 with the number IME open: Save and Quick note stay above the keyboard, sheet scrolls | OK after fix below |
| Small display + small font | `wm density 340` + font 0.85: Add sheet fully laid out and readable | OK (`small-display-0.85font-add-sheet.png`) |
| Dialog hit bounds | Tapped "15 min ago" at the node's reported bounds at the small display size: dialog responds | OK; exposed the bug below |
| Fresh launch then persistent nav | Fresh launch shows Home with only the Add button; opening a tile reveals the full capsule and it stays across tabs (recordings, `appearance/05-08`) | OK |
| Appearance / Reduce motion | Light/Dark switched and persisted, Reduce motion toggled, then restored | OK (`appearance/`) |
| Categories: validation, offline, labels | Name rules and catalog unit tests; offline create + reconnect + restart on the real SDK | OK; late rejection fixed (see firestore evidence) |

## Defects found and fixed

1. **Time presets used a stale clock** (`ManualTimePickerSheet`): "Now / 15 min ago / 1 hour ago" were computed when the
   picker composed, so at 5:27 the Add sheet opened at 5:15 gave "15 min ago" = 5:01. They are now computed at the tap
   (`TimePresetUiTest`, 2 instrumented tests). Evidence of the bug: `small-display-time-dialog-preset-bug-5-01.png`.
2. **Large-font button label cut to "Quick n…"** (`ZarButton`): a decorative icon now yields to the label above 1.2x font
   scale. Before/after: `large-font-1.5-ime-quick-note-truncated-before-fix.png` and `...after-fix.png`.
3. **Late server refusal of a custom category vanished silently** (details in `firestore-integration/evidence.md`).
4. **Offline SDK test hung** on an un-awaitable offline `set().await()`, and **cross-class `FirebaseApp was deleted`**
   (details in `firestore-integration/evidence.md`).

## Not changed / observed

- Several custom-category glyphs repeat (Bills, Subscriptions, custom names share a receipt icon; Season ticket and
  Transport share a car). Design decision, left as is.
- Parallax uses `graphicsLayer` offsets, so hit-testing follows the moved layer (Compose hit-tests transformed layers);
  no separate native-hit test was added.

## Final screenshot correction

The final screenshot review found that Home's Activity/Plans/More shortcut icons retained the dark fill embedded in
 their vector assets in Dark mode. The icons now use `textPrimary`, and their arrows use `textSecondary`. The parent
Codex applied this two-line correction after the sequential Sonnet pass. `assembleDebug installDebug
-Pzar.useEmulators=true` passed; the running Pixel_6a emulator was checked in Light and Dark. Refreshed captures:
`appearance/05-home-dark-full-nav.png` and `appearance/11-home-light-shortcut-icons.png`. Appearance was restored to Light.
