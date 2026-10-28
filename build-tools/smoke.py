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
    adb("shell", "am", "force-stop", PKG)  # a fresh menu, scrolled to the top
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    dismiss()


def tap_prefix(prefix):
    for text, x, y, _, _ in nodes():
        if text.startswith(prefix):
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(1.5)
            return True
    return False


def open_group(name):
    """Only one group is open at a time and the choice is remembered: close the open one, then open ours."""
    home()
    for text, x, y, _, _ in nodes():
        if " · " in text and text.rstrip().endswith("▾"):
            if text.startswith(name + " · "):
                return nodes()
            adb("shell", "input", "tap", str(x), str(y)); time.sleep(1.5)
            break
    tap_prefix(name + " · ")
    return nodes()


def main(apk, out):
    out.mkdir(parents=True, exist_ok=True)
    print(adb("install", "-r", apk))
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    adb("logcat", "-c")
    home()
    shot(out, "00-home")
    (out / "ui-home.txt").write_text("\n".join(LAST_DUMP) if nodes() is not None else "")
    heads = [t.split(" · ")[0] for t, *_ in nodes() if " · " in t and t.rstrip().endswith(("▸", "▾"))]
    count = 0
    for g, head in enumerate(heads):
        seen = open_group(head)
        adb("shell", "input", "swipe", "500", "1500", "500", "600"); time.sleep(1)
        entries = list(dict.fromkeys(t.split("\n")[0] for t, x, y, c, p in seen + nodes()
                                     if c and p == PKG and t and " · " not in t and "\n" in t))
        for e, entry in enumerate(entries):
            open_group(head)
            if not tap_prefix(entry):
                adb("shell", "input", "swipe", "500", "1500", "500", "600"); time.sleep(1)
                tap_prefix(entry)
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
