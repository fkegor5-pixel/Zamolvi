#!/usr/bin/env python3
"""Emulator smoke test: opens the app, every menu group and every entry; keeps screenshots and crashes.

Usage: smoke.py <apk> <out dir>. Needs adb with one running emulator.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "ru.replyai.android"


def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


LAST_DUMP = []


def nodes():
    root = None
    for _ in range(4):  # the dump fails while the screen is still moving
        result = subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True, text=True)
        xml = adb("shell", "cat", "/sdcard/ui.xml")
        LAST_DUMP[:] = [result.stdout + result.stderr, xml]
        try:
            root = ET.fromstring(xml[xml.index("<"):])
            break
        except (ValueError, ET.ParseError):
            time.sleep(1.5)
    if root is None:
        return []
    found = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            found.append((n.get("text") or n.get("content-desc") or "", (x1 + x2) // 2, (y1 + y2) // 2,
                          n.get("clickable") == "true", n.get("package")))
    return found


def tap_text(*texts):
    for text, x, y, _, _ in nodes():
        if any(t.lower() == text.lower() for t in texts):
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(1.5)
            return True
    return False


def dismiss():
    time.sleep(1.5)  # system dialogs come a moment after the screen
    for _ in range(3):
        if not tap_text("Do not add tile", "Don't allow", "Don\u2019t allow", "Не добавлять", "Запретить"):
            return


def shot(out, name):
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
    (out / f"{name}.png").write_bytes(data)


def home():
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    dismiss()


def main(apk, out):
    out.mkdir(parents=True, exist_ok=True)
    print(adb("install", "-r", apk))
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    adb("logcat", "-c")
    home()
    shot(out, "00-home")
    (out / "ui-home.txt").write_text("\n".join(LAST_DUMP) if nodes() is not None else "")
    heads = [t for t, *_ in nodes() if " · " in t]
    count = 0
    for g, head in enumerate(heads):
        home()
        tap_text(head)
        entries = [t.split("\n")[0] for t, x, y, c, p in nodes()
                   if c and p == PKG and t and " · " not in t and "\n" in t]
        for e, entry in enumerate(entries):
            home()
            if not any(t == head for t, *_ in nodes() if False):
                pass
            # the group stays open between visits (the choice is remembered)
            if not tap_text(entry) and not (tap_text(head) and False):
                for text, x, y, c, p in nodes():
                    if text.startswith(entry):
                        adb("shell", "input", "tap", str(x), str(y)); time.sleep(1.5); break
            time.sleep(2)
            dismiss()
            shot(out, f"g{g}-{e}-{re.sub(r'[^A-Za-zА-Яа-я0-9]+', '_', entry)[:30]}")
            count += 1
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)
    crashes = adb("logcat", "-d", "-b", "crash")
    (out / "crashes.txt").write_text(crashes)
    log = adb("logcat", "-d")
    (out / "log.txt").write_text("\n".join(l for l in log.splitlines()
                                           if re.search(r"AndroidRuntime|FATAL|Zamolvi", l))[-30000:])
    (out / "summary.txt").write_text(f"groups: {heads}\nscreens: {count}\ncrash lines: {len(crashes.splitlines())}\n")


if __name__ == "__main__":
    main(sys.argv[1], Path(sys.argv[2]))
