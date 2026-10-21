#!/usr/bin/env python3
"""Builds the Zamolvi APK from the base application and the sources of this repository.

The base application (base/base-app-2.7.apk) keeps its compiled DEX unchanged; the classes
from source/ are compiled into classes2.dex, the manifest gets the new components, and the
server from server/ is packed into the APK for installation on a VPS.

Requirements: Python 3.10+, JDK 17+, Android SDK platform 35 and build-tools 35,
Apktool 3.x. Signing key: --keystore (PKCS12) with ZAMOLVI_KEYSTORE_PASSWORD and
ZAMOLVI_KEY_ALIAS; a local key is generated on first build if none exists.
"""
import argparse
import gzip
import hashlib
import io
import os
import re
from pathlib import Path
import shutil
import subprocess
import tarfile
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
NS = "http://schemas.android.com/apk/res/android"
APP_NAME = "Zamolvi"
VERSION_NAME = "3.6"
VERSION_CODE = "35"
FORMER_NAME = "Reply AI"
ORIGINAL_SHA256 = "3bd84dd57a4709ac1abb9692055af3a414659decf8ded86799fe10e133fc291b"
ET.register_namespace("android", NS)


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def visible_files(directory, pattern):
    return sorted(p for p in directory.rglob(pattern)
                  if p.is_file() and not any(part.startswith('.') for part in p.relative_to(directory).parts))


def server_bundle():
    data = io.BytesIO()
    with gzip.GzipFile(fileobj=data, mode="wb", mtime=0, filename="") as gz:
        with tarfile.open(fileobj=gz, mode="w") as archive:
            for path in visible_files(ROOT / "server", "*"):
                if not path.is_file() or "__pycache__" in path.parts or path.suffix == ".pyc":
                    continue
                content = path.read_bytes()
                info = tarfile.TarInfo(path.relative_to(ROOT / "server").as_posix())
                info.size = len(content)
                info.mode = 0o755 if path.suffix == ".sh" else 0o644
                info.mtime = 0
                archive.addfile(info, io.BytesIO(content))
    return data.getvalue()


def patch_manifest(manifest):
    """Adds the components of this repository to the decoded base manifest (in place)."""
    manifest.set(f"{{{NS}}}versionCode", VERSION_CODE)
    manifest.set(f"{{{NS}}}versionName", VERSION_NAME)
    app = manifest.find("application")
    # Product name in the launcher, recent apps and system settings.
    for element in [app, *app.iter()]:
        if element.get(f"{{{NS}}}label") == FORMER_NAME:
            element.set(f"{{{NS}}}label", APP_NAME)
    for name in ("DeletedMessagesActivity", "DeveloperActivity", "MemoryActivity", "MediaActivity",
                 "ApprovalActivity", "StatsActivity", "CheckupActivity", "NotificationReplyActivity", "StyleActivity", "LanguageActivity", "ProfilesActivity", "RulesActivity", "UpdateActivity"):
        ET.SubElement(app, "activity", {
            f"{{{NS}}}name": "ru.replyai.android." + name,
            f"{{{NS}}}exported": "false",
        })
    ET.SubElement(app, "provider", {
        f"{{{NS}}}name": "ru.replyai.android.FeatureInitProvider",
        f"{{{NS}}}authorities": "ru.replyai.android.features",
        f"{{{NS}}}exported": "false",
    })
    main_activity = next(a for a in app.findall("activity")
                         if a.get(f"{{{NS}}}name") == "ru.replyai.android.MainActivity")
    # The app opens straight to the main menu. The video is played only from
    # «5. Автоответчик» (HomeFeatures), so its activity is internal.
    if not any(c.get(f"{{{NS}}}name") == "android.intent.category.LAUNCHER"
               for f in main_activity.findall("intent-filter") for c in f.findall("category")):
        raise RuntimeError("MainActivity has no LAUNCHER intent-filter in the original manifest")
    main_activity.set(f"{{{NS}}}exported", "true")
    ET.SubElement(app, "activity", {
        f"{{{NS}}}name": "ru.replyai.android.VideoAdActivity",
        f"{{{NS}}}exported": "false",
        f"{{{NS}}}theme": "@style/ZamolviIntroTheme",
        f"{{{NS}}}enableOnBackInvokedCallback": "false",
        f"{{{NS}}}configChanges": "orientation|screenSize|screenLayout|smallestScreenSize",
    })
    # Home-screen widget. The provider is exported for the launcher; its buttons go
    # to a separate non-exported receiver so other apps cannot send commands.
    widget = ET.SubElement(app, "receiver", {
        f"{{{NS}}}name": "ru.replyai.android.ReplyWidget",
        f"{{{NS}}}exported": "true",
        f"{{{NS}}}label": APP_NAME,
    })
    ET.SubElement(ET.SubElement(widget, "intent-filter"), "action", {
        f"{{{NS}}}name": "android.appwidget.action.APPWIDGET_UPDATE"})
    ET.SubElement(widget, "meta-data", {
        f"{{{NS}}}name": "android.appwidget.provider",
        f"{{{NS}}}resource": "@xml/replyai_widget_info",
    })
    # Buttons of the widget and the notification, plus the periodic update.
    ET.SubElement(app, "receiver", {
        f"{{{NS}}}name": "ru.replyai.android.ControlReceiver",
        f"{{{NS}}}exported": "false",
    })
    # Restores the notification after a reboot or an update (protected system broadcasts).
    boot = ET.SubElement(app, "receiver", {
        f"{{{NS}}}name": "ru.replyai.android.BootReceiver",
        f"{{{NS}}}exported": "true",
    })
    boot_filter = ET.SubElement(boot, "intent-filter")
    for action in ("android.intent.action.BOOT_COMPLETED", "android.intent.action.MY_PACKAGE_REPLACED"):
        ET.SubElement(boot_filter, "action", {f"{{{NS}}}name": action})
    # Quick Settings tile «Автоответчик». Only the system can bind it (BIND permission).
    tile = ET.SubElement(app, "service", {
        f"{{{NS}}}name": "ru.replyai.android.ReplyTileService",
        f"{{{NS}}}exported": "true",
        f"{{{NS}}}label": "Автоответчик",
        f"{{{NS}}}icon": "@drawable/replyai_notification_icon",
        f"{{{NS}}}permission": "android.permission.BIND_QUICK_SETTINGS_TILE",
    })
    ET.SubElement(ET.SubElement(tile, "intent-filter"), "action", {
        f"{{{NS}}}name": "android.service.quicksettings.action.QS_TILE"})
    ET.SubElement(tile, "meta-data", {
        f"{{{NS}}}name": "android.service.quicksettings.TOGGLEABLE_TILE",
        f"{{{NS}}}value": "true",
    })
    # Daily check of GitHub Releases for a newer version. Only the system can bind a job service.
    ET.SubElement(app, "service", {
        f"{{{NS}}}name": "ru.replyai.android.UpdateJob",
        f"{{{NS}}}exported": "false",
        f"{{{NS}}}permission": "android.permission.BIND_JOB_SERVICE",
    })
    # Replies to Telegram notifications on the phone. Only the system can bind it.
    listener = ET.SubElement(app, "service", {
        f"{{{NS}}}name": "ru.replyai.android.NotificationReplyService",
        f"{{{NS}}}exported": "true",
        f"{{{NS}}}label": "Ответы из уведомлений",
        f"{{{NS}}}permission": "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
    })
    ET.SubElement(ET.SubElement(listener, "intent-filter"), "action", {
        f"{{{NS}}}name": "android.service.notification.NotificationListenerService"})
    # Status notification (runtime permission on Android 13+) and its restore after reboot.
    present = {e.get(f"{{{NS}}}name") for e in manifest.findall("uses-permission")}
    position = list(manifest).index(app)
    # The last two keep notification replies working with the screen off and the app in the background.
    for permission in ("android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_BOOT_COMPLETED",
                       "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "android.permission.WAKE_LOCK"):
        if permission not in present:
            manifest.insert(position, ET.Element("uses-permission", {f"{{{NS}}}name": permission}))
            position += 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get(
        "ANDROID_SDK_ROOT", os.environ.get("ANDROID_HOME", str(ROOT.parent / "toolchain/sdk")))))
    parser.add_argument("--apktool", type=Path, default=Path(os.environ.get(
        "APKTOOL_JAR", str(ROOT.parent / "toolchain/apktool.jar"))))
    parser.add_argument("--keystore", type=Path, default=Path(os.environ.get(
        "ZAMOLVI_KEYSTORE", str(ROOT / "signing/release.p12"))))
    parser.add_argument("--out", type=Path, default=ROOT / f"build/{APP_NAME}-{VERSION_NAME}.apk")
    args = parser.parse_args()
    os.environ.setdefault("ZAMOLVI_KEYSTORE_PASSWORD", "android")
    sdk = args.sdk.resolve()
    tools = next((d for d in sorted((sdk / "build-tools").glob("*"), reverse=True)
                  if (d / "lib/d8.jar").is_file()), None)
    android = sdk / "platforms/android-35/android.jar"
    if tools is None or not android.is_file() or not args.apktool.is_file():
        parser.error("Install SDK platform/build-tools 35 and Apktool; supply --sdk/--apktool.")
    original = ROOT / "base/base-app-2.7.apk"
    if not original.is_file():
        parser.error("Missing base/base-app-2.7.apk")
    if hashlib.sha256(original.read_bytes()).hexdigest() != ORIGINAL_SHA256:
        parser.error("base/base-app-2.7.apk differs from the expected base application")
    password = os.environ.get("ZAMOLVI_KEYSTORE_PASSWORD", "android")
    alias = os.environ.get("ZAMOLVI_KEY_ALIAS", "zamolvi")
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    decoded = build / "resource-project"
    if decoded.exists():
        shutil.rmtree(decoded)
    # No decompilation/recompilation of the unchanged original Java methods.
    run("java", "-jar", args.apktool, "d", "--no-src", "-f", original, "-o", decoded)
    tree = ET.parse(decoded / "AndroidManifest.xml")
    patch_manifest(tree.getroot())
    tree.write(decoded / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    yml = decoded / "apktool.yml"
    yml.write_text(yml.read_text().replace("versionCode: 9", f"versionCode: {VERSION_CODE}")
                   .replace("versionName: 2.7\n", f"versionName: '{VERSION_NAME}'\n"), encoding="utf-8")
    # Optional intro video for «5. Автоответчик». It is not part of the repository: put your own
    # video (one you have the rights to) at assets/intro.mp4. Without it the button works as usual.
    intro = ROOT / "assets/intro.mp4"
    if intro.is_file():
        # The video must be stored, not deflated, for Android's resource file descriptor.
        with yml.open("a") as f:
            f.write("- mp4\n")
        (decoded / "res/raw").mkdir(exist_ok=True)
        shutil.copy2(intro, decoded / "res/raw/replyai_intro.mp4")
    (decoded / "res/values/intro.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="ZamolviIntroTheme" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">#000000</item>
        <item name="android:windowFullscreen">true</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
        <item name="android:navigationBarColor">#000000</item>
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
    </style>
</resources>
''', encoding="utf-8")
    widget_res = ROOT / "build-tools/widget-res"
    for path in visible_files(widget_res, "*.xml"):
        target = decoded / "res" / path.relative_to(widget_res)
        if target.exists():
            raise RuntimeError(f"Widget resource would overwrite an original file: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
    (decoded / "assets/replyai-server.bundle").write_bytes(server_bundle())
    # Interface translations (see I18n.java): i18n/<lang>.json, Russian text -> translation.
    (decoded / "assets/i18n").mkdir(parents=True, exist_ok=True)
    for table in visible_files(ROOT / "i18n", "*.json"):
        shutil.copy2(table, decoded / "assets/i18n" / table.name)
    classes, dex, api = build / "classes", build / "intro-dex", build / "compile-api"
    for directory in (classes, dex, api):
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir()
    # java's compiler entry point also works with a runtime containing jdk.compiler.
    run("java", "com.sun.tools.javac.Main", "--release", "8", "-encoding", "UTF-8", "-classpath", android,
        "-d", api, *visible_files(ROOT / "compile-api", "*.java"))
    run("java", "com.sun.tools.javac.Main", "--release", "8", "-encoding", "UTF-8",
        "-classpath", os.pathsep.join((str(android), str(api))), "-d", classes,
        *sorted((ROOT / "source").glob("*.java")))
    run("java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8",
        "--min-api", "28", "--lib", android, "--output", dex,
        *visible_files(classes, "*.class"))
    shutil.copy2(dex / "classes.dex", decoded / "classes2.dex")
    unsigned = build / "unsigned.apk"
    # Apktool marks the aapt2 binary executable; the SDK directory may be read-only (CI runners),
    # so it gets a private copy.
    aapt2 = build / "aapt2"
    shutil.copy2(tools / "aapt2", aapt2)
    aapt2.chmod(0o755)
    run("java", "-jar", args.apktool, "b", decoded, "-o", unsigned, "--aapt", aapt2)
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(unsigned) as new:
        if old.read("classes.dex") != new.read("classes.dex"):
            raise RuntimeError("The original DEX changed unexpectedly")
        if intro.is_file() and new.getinfo("res/raw/replyai_intro.mp4").compress_type != zipfile.ZIP_STORED:
            raise RuntimeError("Video was compressed")
    def resource_ids(apk):
        output = subprocess.check_output([str(tools / "aapt2"), "dump", "resources", str(apk)], text=True)
        return {name: ident for ident, name in re.findall(r"^\s*resource (0x[a-f0-9]+) (\S+)", output, re.M)}
    previous_ids, current_ids = resource_ids(original), resource_ids(unsigned)
    if not previous_ids or any(current_ids.get(name) != ident for name, ident in previous_ids.items()):
        raise RuntimeError("An original resource ID changed")
    aligned = build / "aligned.apk"
    run(tools / "zipalign", "-f", "4", unsigned, aligned)
    args.keystore.parent.mkdir(parents=True, exist_ok=True)
    if not args.keystore.exists():
        # A local key for test builds. Keep it: updates install only over the same signature.
        run("keytool", "-genkeypair", "-keystore", args.keystore, "-storetype", "PKCS12",
            "-storepass", password, "-keypass", password, "-alias", alias,
            "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
            "-dname", f"CN={APP_NAME} local build", "-noprompt")
        args.keystore.chmod(0o600)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    run("java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", args.keystore,
        "--ks-pass", "env:ZAMOLVI_KEYSTORE_PASSWORD", "--ks-key-alias", alias, "--ks-type", "PKCS12",
        "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true", "--out", args.out, aligned)
    run("java", "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", args.out)
    run(tools / "zipalign", "-c", "4", args.out)
    print("APK:", args.out.resolve())
    print("SHA256:", hashlib.sha256(args.out.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
