# Physical predictive Back and installed-artifact verification

The owner clarified that the inconsistent behaviour occurs on Android edge swipes
from More's child screens, rather than toolbar Back or navbar taps.

## Captured fault

The regular app installed when this investigation began had APK SHA-256:
`e733b0c63b91b1518cd897373488507a59217868b864fd820c874402468ff1e2`.
Package Manager reported its last update as 2026-11-03 16:18:11.

A physical-phone recording reproduced the default shrinking predictive preview,
followed by a different departure on release. DEX inspection with Android SDK
`apkanalyzer dex code` established that its ZarNavHost call used default arguments
for both predictive callbacks: the default-argument mask was `0x1c3c`, including
bits 10 and 11. Its pager also still contained the obsolete `arrivalNonce` field.
It was not the implementation currently in this worktree. The process that replaced
the earlier installed app has not been identified; do not infer a source regression
from this artifact mismatch.

## Verified replacement

Built the regular package with `:app:assembleDebug --no-configuration-cache` from
runtime source `9032aab`. Confirmed output metadata applicationId `com.example.zar`.
The resulting ZarNavHost call supplies both predictive callbacks; its defaults mask
is `0x103c`, excluding bits 10 and 11. Both callbacks invoke the shared pop transitions.

Copied the build to a distinct local APK before installing it with `adb install -r`.
Pulled the installed base.apk back from the phone and compared SHA-256 hashes:

`d904eabd731302fd907c961cbf6493fb84db2826dea5cf3eebecf90abcff84bb`

Both hashes matched exactly. The app's data and signed-in session were retained.

Physical Pixel 6a recordings of Accounts, Bills and Capture sources cover left/right
edges with 80ms, 300ms and 800ms swipes. The replacement shows rightward travel
through the gesture, rather than the old shrinking preview. These recordings are
local only because they contain personal content. This is visual direction evidence,
not a frame-time benchmark or an assertion that every future installation is this APK.

An attempted persistent-tab visibility change was discarded: its device test disproved
the hypothesis that visibleEntries excluded the preview parent in this configuration.
No speculative runtime changes from that investigation were retained.

Settings was additionally recorded with a slow right-edge swipe on the verified
replacement; the leaving surface travels right without shrinking. An attempted
short-swipe cancellation sequence committed instead, so it does not count as
physical cancellation evidence.
