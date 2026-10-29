#!/system/bin/sh
# The script that drove the demo take in docs/evidence/S09/demo-add-activity-catch-up.mp4.
# Pixel_4 emulator, 1080x2280, Zar debug build, run right after signing in as the seeded test user (Home just opened).
# Tap positions are pixels of that screen. Run: adb shell sh /sdcard/demo-take.sh, while the emulator records:
#   adb emu screenrecord start --size 576x1216 --fps 30 --time-limit 170 take.webm   (then: adb emu screenrecord stop)
sleep 2.5
input tap 541 2088
sleep 2.2
input tap 281 2079
sleep 2.2
input tap 356 597
sleep 1.4
input keyevent 8
sleep 0.28
input keyevent 11
sleep 0.28
input keyevent 12
sleep 0.28
input keyevent 7
sleep 0.28
sleep 0.9
input keyevent 4
sleep 1.5
input tap 478 901
sleep 1.3
input tap 287 1128
sleep 1.3
input tap 559 1359
sleep 2.4
input tap 784 2096
sleep 2.8
input tap 165 1676
sleep 2.6
input tap 469 1091
sleep 2.6
input tap 805 2074
sleep 2.4
input tap 567 621
sleep 1.4
input keyevent 123
sleep 0.28
input keyevent 67
sleep 0.18
input keyevent 67
sleep 0.18
input keyevent 67
sleep 0.18
input keyevent 67
sleep 0.18
input keyevent 67
sleep 0.18
input keyevent 8
sleep 0.28
input keyevent 12
sleep 0.28
input keyevent 7
sleep 0.28
input keyevent 7
sleep 0.28
sleep 0.9
input keyevent 4
sleep 1.5
input tap 785 2074
sleep 3.2
input keyevent 4
sleep 1.5
input tap 144 2088
sleep 2.2
input tap 255 1824
sleep 2.8
input tap 541 1597
sleep 3.2
input tap 541 1903
sleep 2.8
input tap 114 971
sleep 1.4
input keyevent 8
sleep 0.28
input keyevent 16
sleep 0.28
input keyevent 9
sleep 0.28
input keyevent 7
sleep 0.28
input keyevent 7
sleep 0.28
sleep 0.9
input keyevent 4
sleep 1.5
input tap 541 1903
sleep 3.4
input tap 540 1903
sleep 3.4
input tap 540 2091
sleep 4.0
sleep 3
