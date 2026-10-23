# PR #25 review evidence

Captured on 23 October 2026 from the native production Bills composables running on a Pixel 6a Android emulator (API 37). `BillsUiTest` provides local fixture state and callbacks, with dates relative to the app's current day. These are UI fixtures, not Firebase end-to-end tests; pressing a fixture Save or Confirm checks the callback rather than persisting a backend record. The list fixture omits subscription suggestions and includes extra sample history to exercise scrolling.

The PNGs are unmodified full-window `UiAutomation.takeScreenshot()` captures. They include native date-picker dialogs, bottom sheets and system bars. The large-text fixture uses Compose font scale 2.0. Screenshots were captured after the final UI edits and inspected for clipping and stale controls.

| Image | State |
| --- | --- |
| `pr25-list.png` | Bills hero, attention and due sections |
| `pr25-paid-jump.png` | Paid shortcut with enough history to verify its target |
| `pr25-attention-jump.png` | Attention shortcut past the hero |
| `pr25-detail.png` | Bill detail, payment history and actions |
| `pr25-keep-bill.png` | Keep a bill after planning to cancel |
| `pr25-end-sheet.png` | Confirm cancellation from a date |
| `pr25-end-date-picker.png` | Native cancellation date picker above the sheet |
| `pr25-stopped.png` | Stopped bill with Resume available |
| `pr25-payment.png` | Selected payment candidate and its suggested amount |
| `pr25-early-payment.png` | Running bill paid in a previous month, still reachable |
| `pr25-all-matched.png` | Active bill with no upcoming amount left to match |
| `pr25-empty.png` | Empty list |
| `pr25-load-error.png` | Load failure |
| `pr25-large-text.png` | Growing hero and wrapping rows at 2.0 font scale |
| `pr25-form.png` | Bill form with a reminder selected |
| `pr25-form-date-picker.png` | Native first-due-date picker |

Build and verification commands:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 shell am instrument -w \
  -e class com.example.zar.features.bills.BillsUiTest \
  com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w \
  -e class com.example.zar.features.catchup.CatchUpUiTest \
  com.example.zar.test/androidx.test.runner.AndroidJUnitRunner
```

Review validation: 661 unit tests; 12 Bills UI tests; 31 CatchUp UI tests. Lint has zero errors and 53 existing warnings, with none in Bills. The debug app and instrumentation APKs build. Backend writes, notification delivery, TalkBack, dark mode and physical devices were not tested in this review.

Live Figma references: file `bBBJuQz1Cg8N34FcisKBs9`, Z22 `163:2516`, Z23 `163:2609`, Z28 `171:2628`, Z21 `161:2432`. Existing domain features require extra rows/actions outside those frames: running/ended bills, yearly estimates, cancellation/stop/resume, and status banners. SmartRail was unavailable; no design agent advice was obtained. No Figma frames were changed.

The eight images under `../bills-figma/` are the author's earlier Firebase-emulator/manual evidence. They are retained as historical evidence and are not the review's refreshed captures.
