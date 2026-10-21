# PR 16 review and Activity balance surface

The Activity hero now allocates its width to Received and Spent from the same account/date selection as the totals. Teal is `received / (received + spent)` and maroon is the rest, with a 300ms transition. The amount labels keep their layout as the boundary moves. Zero, loading, and missing-conversion selections use a neutral surface instead of claiming a proportion. Money is divided as exact decimals before conversion to a drawing fraction, so adding two valid large totals cannot overflow.

Review fixes:

- Reject a balance count whose difference or absolute gap cannot be represented instead of crashing the sheet.
- Keep replayed balance adjustments immutable, even after other entries or the typed count change.
- Use balances through the check date; future entries do not change today's count.
- Keep a pending balance save from being reset into a second check.
- Reject corrections that reduce a purchase below its active refunds, with an explicit error in the editor.
- Allow a full refund to finish on retry when the local write succeeded but its acknowledgement was lost.
- Correct partial-refund void wording and use neutral date-picker surfaces.
- Read the calendar locale from Compose configuration so locale changes update it and Android lint passes.

Validation: all 430 unit tests; Android lint; debug and test APK builds; 83 Compose tests across gradient/filter states, refunds, reconciliation, Activity and the Add form. Pixel checks verify the actual gradient boundary, including 80/20, 20/80, 50/50, single-sided and neutral selections. The current screenshots are captured from those screens on a Pixel 6a running the app's Compose UI with deterministic fixtures.

The original S03 screenshots at the parent directory are historical implementation evidence. The `reviewed/` images show the reviewed version and the proportional Activity surface.

The existing offline storage model validates refunds against the locally observed ledger. Simultaneous refunds from separate offline devices are not serialized by a server transaction; this review does not claim cross-device atomic refund limits. A physical device, TalkBack and the live Firebase project were not exercised.
