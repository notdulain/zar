# S09 (SCRUM-43): evidence for Add, Activity and cash catch-up

The report is [docs/report/S09-add-activity-catch-up.md](../../report/S09-add-activity-catch-up.md) and the demo script is [docs/report/S09-demo-script.md](../../report/S09-demo-script.md).

Captured on 29 Oct 2026 from the running debug app on a Pixel 4 emulator (Android 37.1), with the local Firebase Auth and Firestore emulators and a seeded test user. No real names, accounts or messages.

## Files

| File | What |
|---|---|
| `1-add-sheet-annotated.png` to `11-catchup-done-annotated.png` | Annotated screens with numbered red marks. The marks are explained in the report, section 2 |
| `raw/` | The same screens without marks (the expense form was filled with a test value) |
| `demo-add-activity-catch-up.mp4` | The demo recording: one continuous take, about 71 seconds, 576 x 1216, 30 frames per second, H.264, 1.3 MB. Not cut, trimmed or captioned |
| `demo-take.sh` | The script that drove the phone during the take |

## How the annotated screens were made

Each raw screenshot was captured with `adb exec-out screencap`, and the marks were drawn with `tools/annotate-screenshot.py` (numbered circles at pixel positions of the 1080 x 2280 original, output scaled to 720 pixels wide). Marker positions were read off the screenshots with a coordinate grid.

## How the recording was made

One continuous take, from the first tap to the last. It was not cut, trimmed, sped up, cropped or captioned.

1. **Smooth capture.** The emulator's own host-side recorder was used (`adb emu screenrecord start --size 576x1216 --fps 30`). It records a constant 30 frames per second. The recorder that runs on the phone (`adb shell screenrecord`) only reached 14 to 19 frames per second and left gaps of over a second when the screen was still, which is why an earlier version of this video was jerky. Animations were switched on and the emulator rendered on the machine's GPU.
2. **No dead time.** Every tap and every typed digit comes from one script that runs on the phone ([demo-take.sh](demo-take.sh)), with fixed pauses of 1.3 to 4 seconds. The tap positions were logged from a rehearsal of the same steps, so nothing waits on the computer between steps.
3. **Fresh state.** The test data was reset and the app signed in just before the take, so the take starts on Home.
4. **Size.** The recorder's output is VP9 at about 3.6 megabits per second, 30 MB for this take. GitHub accepts at most 10 MB for video on a free plan, so it was re-encoded once to H.264 (`ffmpeg -c:v libx264 -crf 24 -r 30`), giving 1.3 MB. This changes the file format, not the content: the picture is the same size and every moment is there. The encoder added 15 duplicate frames to keep a steady 30 frames per second (2,129 in the file, 2,114 in the recording).

What the take shows: Add an expense (LKR 1,450, Sampath, Coffee & dining), open it in Activity, correct it to LKR 1,500, then the weekly cash catch-up: count LKR 19,200 against 19,700 recorded, review and save the LKR 500 adjustment, and open it.

**The reviewed quick note is not in the take.** The author reported App Check/test sign-in rejection in the recorded emulator setup. A successful read requires working authentication and App Check for that install; emulator hardware alone does not decide service access. The review screens are in the stills from the SCRUM-41 run, [docs/evidence/S07](../S07).

The emulator still limits how smooth the app's own animations look. Over a second of motion the picture changes on 15 or fewer of the 30 frames, because the emulator does not draw faster. A physical phone was not measured in this run, so its animation smoothness is unverified.

## Not covered

A live recording of a quick note being read. A physical phone, dark mode, large fonts and TalkBack.

## Reproducing the driving script

`demo-take.sh` is an archival coordinate script for the stated 1080 × 2280 emulator, starting on the seeded Home screen. It changes test-account ledger data; it does not check which screen is open. On a disposable seeded emulator, copy it with `adb -s SERIAL push docs/evidence/S09/demo-take.sh /sdcard/demo-take.sh`, start the recorder, then run `adb -s SERIAL shell sh /sdcard/demo-take.sh`. Replace SERIAL with the intended device. Do not run it against a differently sized or differently seeded device.

## PR review validation (30 Oct 2026)

- Integrated main after parent PR #41 merged and retargeted this dependent PR to main. The annotation helper is already identical on main and is no longer part of this PR's final diff.
- Corrected the scope of offline saves, the distinction between structured drafts and ledger entries, surplus versus shortfall reporting, and how to reverse a correction. Updated the demo to use a fresh test account and current-month seed dates.
- Checked 56 local document/image links across the report, demo and this README: all resolve. Checked all 29 individually named class/enum references in the report against app sources: all found.
- Decoded and visually inspected all 25 PNGs (11 annotated and 14 raw). Activity's bottom navigation and Home's check-line truncation obscure some content; these historical author captures show states and data, not clipping-free UI or Figma parity.
- `sh -n docs/evidence/S09/demo-take.sh` passed. The archival driving script was read but not executed against a device.
- Fully decoded the MP4 with FFmpeg without errors and inspected 12 frames sampled at six-second intervals. FFprobe reports H.264, 576 × 1216, 30 fps, 2,129 frames, 70.966 seconds, 1,305,556 bytes. The samples show entry, correction and saved cash adjustment. The no-edit/source-recorder claims above are author provenance; the original recording is not included for comparison.
- Checked the seed arithmetic: cash 20,000 − 8,400 − 1,250 − 650 + 10,000 = 19,700; Spent 16,180, then 17,630 after the demo expense and 17,680 after correction. The LKR 500 outgoing catch-up adjustment would raise Spent to 18,180 if the optional void is skipped.
- No Android, instrumentation, backend or fresh device/demo run was performed for this docs/media-only diff. S08 test counts are dated historical runs; PR #41's separate review recorded 1,294 passing unit tests. Live quick-note responses, physical-device smoothness and Figma comparison remain unverified here.
