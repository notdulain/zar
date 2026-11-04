# Navbar transition correction

The owner's clarification was **switching between navbar tabs**. The earlier More
child-route audit did not cover this trigger and must not be treated as its resolution.

The pager used an adjacent-page scroll for neighbouring tabs and a short translated
fade for longer jumps. Returning to an earlier tab therefore differed with distance.
Back to Home also had a separate fallback when Home was not composed.

All navbar taps now use one two-page push/pop: forward pushes in from the right;
returning to an earlier tab moves the source right over the destination. No tab
arrival fade remains. Intermediate tabs may be composed for readiness but are hidden
during travel. The navbar slots stay fixed. Finger swipes retain pager behaviour.
Reduced motion resolves immediately. Home is prepared on demand for Back, avoiding
the old tab-arrival fallback.

Verification: all 13 TabPagerTest instrumented tests passed on the physical Pixel 6a,
Android 16, using the isolated `.perf` package. Coverage includes adjacent/long jumps,
returning to earlier tabs, rapid retargeting, Back to Home, swipe/capsule tracking,
page warm-up, horizontal gesture ownership, locks and reduced motion. The previous
composition-count test was replaced with animated geometry assertions because
precomposing an intermediate page does not mean it was displayed.

The regular debug app was rebuilt and upgraded in place with data retained. A local
recording of More → Plans → Home → Activity → More → Home was inspected: both
adjacent and distant returns use rightward departure, with fixed navbar slots.
The recording stays local because it contains personal account content.
