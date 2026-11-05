"""«Серийчик»: how many days in a row both people wrote in a private chat, a picture with that number and a note
when the streak goes on.

The days are counted by code from the Telegram history (exact); the AI only draws the picture.
"""
import base64
import binascii
import datetime as dt
import io
import os
import re
from zoneinfo import ZoneInfo

COMMAND = re.compile(r"^/?серийчик[.!?\s]*$", re.IGNORECASE)
YES = re.compile(r"^да[.!)\s]*$", re.IGNORECASE)
ASK_SECONDS = 24 * 3600     # the two «да» count within a day after «серийчик»
# shortcut: a streak longer than these messages is cut there; page by date if someone gets there
MAX_HISTORY = 10000
MAX_PHOTO = 2_000_000
PHOTO_FILE = "streak_photo.bin"
DEFAULT_TASK = "Два друга весело переписываются в мессенджере, яркая тёплая иллюстрация"


def initial():
    return {"enabled": False, "prompt": "", "model": "", "chats": {}}


def day(timestamp, zone):
    """The calendar day of a moment in the owner's time zone (the one of «Тихие часы»)."""
    return dt.datetime.fromtimestamp(timestamp, ZoneInfo(zone)).date().isoformat()


def previous(day_iso):
    return (dt.date.fromisoformat(day_iso) - dt.timedelta(days=1)).isoformat()


def days_word(n):
    if n % 10 == 1 and n % 100 != 11:
        return "день"
    return "дня" if 2 <= n % 10 <= 4 and not 12 <= n % 100 <= 14 else "дней"


class Counter:
    """Days in a row on which both wrote, ending today, or yesterday while today is not complete yet.

    Fed with (day, out) of the messages newest first; feed() returns False as soon as the answer is known.
    """

    def __init__(self, today):
        self.today, self.expected, self.grace = today, today, True
        self.days, self.last = 0, ""
        self.current, self.sides, self.done = None, set(), False

    def feed(self, day_iso, out):
        if self.done:
            return False
        if day_iso != self.current:
            if self.current is not None and not self._close():
                return False
            while self.expected > day_iso:  # the days in between had no messages at all
                if not self._skip_today():
                    self.done = True
                    return False
            self.current, self.sides = day_iso, set()
        self.sides.add(bool(out))
        return True

    def finish(self):
        if self.current is not None and not self.done:
            self._close()
        return self.days

    def _close(self):
        current, complete = self.current, len(self.sides) == 2
        if self.expected < current:  # a day after today (clock skew): not part of the count
            return True
        if complete:
            self.days += 1
            self.last = self.last or current
            self.expected, self.grace = previous(current), False
            return True
        if self._skip_today():
            return True
        self.done = True
        return False

    def _skip_today(self):
        if self.grace and self.expected == self.today:
            self.expected, self.grace = previous(self.today), False
            return True
        return False


def ask(config, chat, now, name):
    """«серийчик»: wait for «да» from both sides."""
    config["chats"][chat] = {"state": "pending", "asked": now, "yes": [], "name": name[:60]}


def agree(config, chat, side, now):
    """One «да» ('out' — the owner, 'in' — the contact); True once both agreed in time."""
    entry = config["chats"].get(chat)
    if not entry or entry["state"] != "pending":
        return False
    if now - entry["asked"] > ASK_SECONDS:
        config["chats"].pop(chat)
        return False
    if side in entry["yes"]:
        return False
    entry["yes"].append(side)
    if len(entry["yes"]) < 2:
        return False
    entry["state"] = "drawing"  # one start only, however many more «да» come while it is drawn
    return True


def activate(config, chat, days, last, today):
    entry = config["chats"].get(chat)
    if entry is None:  # forgotten meanwhile (/ai_off)
        return
    config["chats"][chat] = {"state": "active", "days": days, "last": last, "name": entry.get("name", ""),
                             "seen": {"day": today, "out": True, "in": True}}


def track(config, chat, out, today):
    """A message in a chat with a streak: 'extended' or 'restarted' once both wrote on a new day, else None."""
    entry = config["chats"].get(chat)
    if not entry or entry["state"] != "active":
        return None
    seen = entry["seen"]
    if seen.get("day") != today:
        entry["seen"] = seen = {"day": today}
    seen["out" if out else "in"] = True
    if not (seen.get("out") and seen.get("in")) or entry["last"] >= today:
        return None
    extended = entry["last"] == previous(today)
    entry["days"] = entry["days"] + 1 if extended else 1
    entry["last"] = today
    return "extended" if extended else "restarted"


def task(prompt, with_photo):
    """What the AI is asked to draw; the number is added by code, so the picture has no text."""
    what = (prompt or "").strip() or DEFAULT_TASK
    if with_photo:
        return (f"Отредактируй это фото: {what}. Сохрани узнаваемость людей и предметов с фото. "
                "Квадратный формат. Без надписей и цифр на изображении.")
    return f"Нарисуй картинку: {what}. Квадратный формат. Без надписей и цифр на изображении."


def _font(size):
    from .bracket import _font as font
    return font(size, True)


def picture(drawn, days):
    """The AI picture (or a plain teal card when there is none) with the day count in the top right corner; JPEG."""
    from PIL import Image, ImageDraw
    image = None
    if drawn:
        try:
            image = Image.open(io.BytesIO(drawn)).convert("RGB")
        except Exception:
            image = None
    if image is None:
        image = Image.new("RGB", (1024, 1024))
        draw = ImageDraw.Draw(image)
        for y in range(1024):  # the teal of the app icon fading to graphite
            k = y / 1023
            draw.line((0, y, 1024, y), fill=(int(16 - 5 * k), int(160 - 140 * k), int(152 - 135 * k)))
        draw.text((512, 560), "Серийчик", font=_font(110), fill=(255, 255, 255), anchor="mm")
        draw.text((512, 680), "переписываемся каждый день", font=_font(44), fill=(220, 240, 238), anchor="mm")
    image.thumbnail((1280, 1280))
    width, _ = image.size
    size = max(40, width // 8)
    number, word = str(days), days_word(days)
    big, small = _font(size), _font(max(16, size * 2 // 5))
    draw = ImageDraw.Draw(image, "RGBA")
    pad = size // 4
    box_w = max(draw.textlength(number, font=big), draw.textlength(word, font=small)) + 2 * pad
    box_h = size + size * 2 // 5 + 3 * pad
    x1, y0 = width - size // 3, size // 3
    draw.rounded_rectangle((x1 - box_w, y0, x1, y0 + box_h), radius=size // 3, fill=(0, 0, 0, 150))
    draw.text((x1 - box_w / 2, y0 + pad), number, font=big, fill=(255, 255, 255), anchor="mt")
    draw.text((x1 - box_w / 2, y0 + box_h - pad), word, font=small, fill=(255, 214, 10), anchor="mb")
    out = io.BytesIO()
    image.save(out, "JPEG", quality=90)
    return out.getvalue()


def save_photo(store, value):
    """The sample photo from the app (base64 JPEG/PNG): stored encrypted next to the state; '' removes it."""
    path = store.path / PHOTO_FILE
    if not value:
        path.unlink(missing_ok=True)
        return
    try:
        data = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError):
        raise ValueError("Фото-образец повреждено.") from None
    if not data or len(data) > MAX_PHOTO:
        raise ValueError("Фото-образец больше 2 МБ.")
    from PIL import Image
    try:
        Image.open(io.BytesIO(data)).verify()
    except Exception:
        raise ValueError("Это не фото: выберите JPEG или PNG.") from None
    temporary = path.with_suffix(".tmp")
    temporary.write_bytes(store.cipher.encrypt(data))
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def load_photo(store):
    path = store.path / PHOTO_FILE
    return store.cipher.decrypt(path.read_bytes()) if path.is_file() else None
