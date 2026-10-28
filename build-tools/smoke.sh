#!/usr/bin/env bash
# Emulator smoke test: install, open every screen, keep screenshots and any crash from logcat.
apk=$1; out=$2; mkdir -p "$out"
adb install -r "$apk" > "$out/install.txt" 2>&1 || { echo "install failed" >> "$out/summary.txt"; cat "$out/install.txt" >> "$out/crashes.txt"; exit 0; }
adb logcat -c
pkg=ru.replyai.android
shot() { adb exec-out screencap -p > "$out/$1.png"; }
adb shell am start -W -n $pkg/.MainActivity > /dev/null; sleep 6; shot 01-main
adb shell input swipe 500 1500 500 500; sleep 2; shot 02-main-scrolled
for a in StyleActivity NotificationReplyActivity StatsActivity CheckupActivity UpdateActivity MemoryActivity MediaActivity ApprovalActivity DeletedMessagesActivity LanguageActivity DeveloperActivity ServerSetupActivity DiagnosticsActivity; do
  adb shell am start -W -n $pkg/.$a > /dev/null 2>&1; sleep 3; shot "act-$a"; adb shell input keyevent 4; sleep 1
done
adb logcat -d -b crash > "$out/crashes.txt" 2>&1
adb logcat -d | grep -E "AndroidRuntime|FATAL|Zamolvi|ReplyAI" | tail -n 200 > "$out/log.txt"
echo "screens: $(ls "$out"/*.png | wc -l), crash lines: $(wc -l < "$out/crashes.txt")" > "$out/summary.txt"
