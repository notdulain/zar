# S09 (SCRUM-43): evidence for Add, Activity and cash catch-up

The report is [docs/report/S09-add-activity-catch-up.md](../../report/S09-add-activity-catch-up.md) and the demo script is [docs/report/S09-demo-script.md](../../report/S09-demo-script.md).

Captured on 29 Oct 2026 from the running debug app on a Pixel 4 emulator (Android 37.1), with the local Firebase Auth and Firestore emulators and a seeded test user. No real names, accounts or messages.

## Files

| File | What |
|---|---|
| `1-add-sheet-annotated.png` to `11-catchup-done-annotated.png` | Annotated screens with numbered red marks. The marks are explained in the report, section 2 |
| `raw/` | The same screens without marks (the expense form was filled with a test value) |
| `demo-add-activity-catch-up.mp4` | The demo recording, about 65 seconds, 540 x 1210, H.264, under 1 MB |

## How the annotated screens were made

Each raw screenshot was captured with `adb exec-out screencap`, and the marks were drawn with `tools/annotate-screenshot.py` (numbered circles at pixel positions of the 1080 x 2280 original, output scaled to 720 pixels wide). Marker positions were read off the screenshots with a coordinate grid.

## How the recording was made

1. Segments 1 and 2 were recorded on the emulator with `adb shell screenrecord --size 540x1140`, while a script drove the app with `adb input`. Segment 1 is "Add an expense, then correct it" (raw 116 seconds). Segment 2 is "Weekly cash catch-up" (raw 129 seconds).
2. Most of the raw time was waiting between scripted taps. With `ffmpeg`, any stretch where the screen did not change for more than 2.6 seconds was cut down to 1.8 seconds, the clips were joined, and a black caption band with the segment name was added above the phone. Nothing on the phone screen was altered or cropped.
3. Segment 3, "Reviewed quick note", is **six stills from the SCRUM-41 run** in [docs/evidence/S07](../S07), shown for about 3 seconds each. It is not a live recording: a real quick-note read cannot be made from the emulator, because a debug build there is refused by App Check and its test sign-in is not accepted by the deployed service. This is stated in the caption.

The raw recordings are not committed. They are only waiting time.

## Not covered

Segment 3 is not a live recording of a quick note being read. A physical phone, dark mode, large fonts and TalkBack were not recorded.
