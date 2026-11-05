"""«Серийчик»: the day count, the two «да», the picture with the number, the daily extension."""
import base64
import datetime as dt
import io
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from telethon import types

from replyai import ai as ai_module, streak
from replyai.ai import AiClient, AiError
from replyai.errors import UserError

ZONE = "Europe/Moscow"


def png(color=(200, 40, 40), size=(300, 200)):
    from PIL import Image
    out = io.BytesIO()
    Image.new("RGB", size, color).save(out, "PNG")
    return out.getvalue()


def count(today, rows):
    """rows: (day, out) newest first."""
    counter = streak.Counter(today)
    fed = 0
    for row in rows:
        if not counter.feed(*row):
            break
        fed += 1
    return counter.finish(), counter.last, fed


def test_counter_counts_days_when_both_wrote_and_stops_at_the_first_gap():
    both = lambda d: [(d, True), (d, False)]
    assert count("2026-11-05", both("2026-11-05") + both("2026-11-04") + both("2026-11-03"))[:2] == (3, "2026-11-05")
    # today only the owner wrote so far: the streak still ends yesterday
    assert count("2026-11-05", [("2026-11-05", True)] + both("2026-11-04") + both("2026-11-03"))[:2] == (2, "2026-11-04")
    # nobody wrote today yet
    assert count("2026-11-05", both("2026-11-04"))[:2] == (1, "2026-11-04")
    # a day without messages ends the streak, and the older history is not read
    days, last, fed = count("2026-11-05", both("2026-11-05") + both("2026-11-03") + both("2026-11-02"))
    assert (days, last, fed) == (1, "2026-11-05", 2)
    # a day with only one side ends it too
    assert count("2026-11-05", both("2026-11-05") + [("2026-11-04", False)] + both("2026-11-03"))[0] == 1
    assert count("2026-11-05", [])[0] == 0
    assert count("2026-11-05", [("2026-11-04", True)])[0] == 0


def test_day_uses_the_owner_zone_and_words_agree_with_numbers():
    moment = dt.datetime(2026, 11, 4, 22, 30, tzinfo=dt.timezone.utc).timestamp()
    assert streak.day(moment, "Europe/Moscow") == "2026-11-05" and streak.day(moment, "UTC") == "2026-11-04"
    assert [streak.days_word(n) for n in (1, 2, 5, 11, 21, 22, 112)] == ["день", "дня", "дней", "дней", "день", "дня", "дней"]


def test_both_must_agree_once_and_in_time():
    config = streak.initial()
    streak.ask(config, "1", 1000, "Анна")
    assert not streak.agree(config, "1", "out", 1001)
    assert not streak.agree(config, "1", "out", 1002)        # the same side twice is still one «да»
    assert streak.agree(config, "1", "in", 1003)
    assert not streak.agree(config, "1", "in", 1004)          # drawing already: no second start
    streak.ask(config, "2", 1000, "Борис")
    assert not streak.agree(config, "2", "out", 1000 + streak.ASK_SECONDS + 1)
    assert "2" not in config["chats"]                         # too late: «серийчик» has to be written again


def test_track_extends_on_the_next_day_and_restarts_after_a_gap():
    config = streak.initial()
    streak.ask(config, "1", 0, "Анна")
    streak.activate(config, "1", 4, "2026-11-05", "2026-11-05")
    assert streak.track(config, "1", True, "2026-11-05") is None        # same day
    assert streak.track(config, "1", True, "2026-11-06") is None        # only one side so far
    assert streak.track(config, "1", False, "2026-11-06") == "extended"
    assert config["chats"]["1"]["days"] == 5
    assert streak.track(config, "1", False, "2026-11-06") is None       # once a day
    streak.track(config, "1", True, "2026-11-09")
    assert streak.track(config, "1", False, "2026-11-09") == "restarted" and config["chats"]["1"]["days"] == 1


def test_picture_has_the_number_box_in_the_top_right_corner_and_a_card_without_ai():
    from PIL import Image
    for drawn in (png(), None, b"not an image"):
        out = Image.open(io.BytesIO(streak.picture(drawn, 12)))
        assert out.format == "JPEG"
        width, _ = out.size
        size = max(40, width // 8)                                        # as in streak.picture
        y = size // 3 + size // 2
        inside_box = out.getpixel((width - size // 3 - 4, y))            # the right padding of the number box
        same_row_left = out.getpixel((width // 10, y))
        assert sum(inside_box) < sum(same_row_left) - 60


def test_sample_photo_is_stored_encrypted_and_can_be_removed(env):
    data = png()
    streak.save_photo(env.store, base64.b64encode(data).decode())
    assert data not in (env.store.path / streak.PHOTO_FILE).read_bytes()
    assert streak.load_photo(env.store) == data
    with pytest.raises(ValueError):
        streak.save_photo(env.store, base64.b64encode(b"plain text").decode())
    with pytest.raises(ValueError):
        streak.save_photo(env.store, "@@@")
    streak.save_photo(env.store, "")
    assert streak.load_photo(env.store) is None


@pytest.mark.asyncio
async def test_draw_builds_requests_and_reads_the_picture():
    client = AiClient()
    bodies = []
    picture = png()
    async def fake_post(config, http, url, headers, body, cap=0):
        bodies.append((url, body, cap))
        if config["provider"] == "gemini":
            return {"candidates": [{"content": {"parts": [{"text": "вот"}, {"inlineData": {
                "mimeType": "image/png", "data": base64.b64encode(picture).decode()}}]}}]}
        return {"choices": [{"message": {"content": "", "images": [{"type": "image_url", "image_url": {
            "url": "data:image/png;base64," + base64.b64encode(picture).decode()}}]}}], "usage": {"prompt_tokens": 1}}
    client._post = fake_post
    base = {"key": "k", "prompt": "p", "model": "m", "endpoint": "https://openrouter.ai/api/v1/chat/completions"}
    try:
        config = {**base, "provider": "custom"}
        assert await client.draw(config, ai_module.image_model(config, {}), "кот", b"sample") == picture
        url, body, cap = bodies[-1]
        assert body["model"] == ai_module.DEFAULT_IMAGE_MODELS["custom"] and body["modalities"] == ["image", "text"]
        assert body["messages"][0]["content"][1]["image_url"]["url"].startswith("data:image/jpeg;base64,")
        assert cap > 2_000_000
        config = {**base, "provider": "gemini"}
        assert await client.draw(config, "gemini-img", "кот") == picture
        assert "gemini-img:generateContent" in bodies[-1][0]
        assert bodies[-1][1]["generationConfig"]["responseModalities"] == ["TEXT", "IMAGE"]
        with pytest.raises(AiError, match="Gemini"):
            await client.draw({**base, "provider": "groq"}, "x", "кот")
        async def no_picture(*args, **kw):
            return {"choices": [{"message": {"content": "не умею"}}]}
        client._post = no_picture
        with pytest.raises(AiError, match="не вернула картинку"):
            await client.draw({**base, "provider": "custom"}, "text-only", "кот")
    finally:
        await client.close()


# ----------------------------------------------------------------------------- in a chat

class StreakTelegram:
    """History and the two calls the streak needs, on top of the env's fake client."""

    def __init__(self, client, history):
        self.client, self.history = client, history
        client.iter_messages = self.iter_messages
        client.send_file = AsyncMock(return_value=SimpleNamespace(id=900))
        client.pin_message = AsyncMock()

    async def iter_messages(self, peer, limit=None):
        for message in self.history[:limit]:
            yield message


def message(env, chat, text, out, message_id):
    date = dt.datetime.fromtimestamp(env.clock[0], dt.timezone.utc)
    sender = types.User(chat, first_name="Анна")
    return SimpleNamespace(is_private=True, out=out, raw_text=text, chat_id=chat, id=message_id, date=date,
                           message=SimpleNamespace(raw_text=text, action=None),
                           get_sender=AsyncMock(return_value=sender),
                           get_chat=AsyncMock(return_value=SimpleNamespace(is_self=False, first_name="Анна", last_name=None)),
                           get_input_chat=AsyncMock(return_value=types.InputPeerUser(chat, 0)))


def past(env, days_ago, out):
    date = dt.datetime.fromtimestamp(env.clock[0], dt.timezone.utc) - dt.timedelta(days=days_ago)
    return SimpleNamespace(out=out, raw_text="привет", date=date, action=None)


def enable(env, drawn=None, error=None):
    env.store.update(lambda s: s["streak"].update(enabled=True, prompt="два кота пьют какао"))
    calls = []
    async def draw(config, model, task, photo=None):
        calls.append((model, task, photo))
        if error:
            raise error
        return drawn
    env.engine.ai.draw = draw
    return calls


@pytest.mark.asyncio
async def test_streak_is_asked_agreed_drawn_pinned_and_extended(env):
    calls = enable(env, drawn=png())
    sent = env.engine.client.sent
    history = [past(env, 0, True), past(env, 0, False), past(env, 1, False), past(env, 1, True),
               past(env, 2, True), past(env, 2, False), past(env, 4, True), past(env, 4, False)]
    telegram = StreakTelegram(env.engine.client, history)
    await env.engine.on_message(message(env, 123, "Серийчик!", False, 10))
    assert "напишите в этот чат да" in sent[-1].message and sent[-1].message.startswith(ai_module.PREFIX)
    await env.engine.on_message(message(env, 123, "да", True, 11))
    telegram.client.send_file.assert_not_called()                               # one «да» is not enough
    await env.engine.on_message(message(env, 123, "Да", False, 12))
    args, kwargs = telegram.client.send_file.call_args
    assert "Серийчик: 3 дня подряд" in kwargs["caption"] and kwargs["caption"].startswith(ai_module.PREFIX)
    assert args[1].getvalue()[:2] == b"\xff\xd8"                                # JPEG with the number on it
    telegram.client.pin_message.assert_awaited_once()
    assert "два кота" in calls[0][1] and calls[0][2] is None                  # no sample photo: drawn from the text
    entry = env.store.read()["streak"]["chats"]["123"]
    assert entry["state"] == "active" and entry["days"] == 3 and entry["name"] == "Анна"
    await env.engine.on_message(message(env, 123, "серийчик", True, 13))
    assert "Серийчик: 3 дня подряд" in sent[-1].message                        # already running: just the count
    env.clock[0] += 24 * 3600
    count_before = len(sent)
    await env.engine.on_message(message(env, 123, "доброе утро", True, 14))
    assert len(sent) == count_before                                            # the contact has not written yet
    await env.engine.on_message(message(env, 123, "привет", False, 15))
    assert "Серийчик продлён: 4 дня подряд" in sent[-1].message
    await env.engine.on_message(message(env, 123, "как дела", False, 16))
    assert len(sent) == count_before + 1                                        # once a day


@pytest.mark.asyncio
async def test_streak_without_ai_picture_disabled_and_ai_off(env):
    sent = env.engine.client.sent
    StreakTelegram(env.engine.client, [])
    await env.engine.on_message(message(env, 123, "серийчик", False, 1))
    assert not sent                                                             # the feature is off
    enable(env, error=AiError("Картинки рисуют только Gemini"))
    await env.engine.on_message(message(env, 123, "серийчик", True, 2))
    await env.engine.on_message(message(env, 123, "да", True, 3))
    await env.engine.on_message(message(env, 123, "да", False, 4))
    _, kwargs = env.engine.client.send_file.call_args
    assert "Серийчик: 1 день подряд" in kwargs["caption"]                       # still a picture: the plain card
    assert "картинка без нейросети" in env.store.read()["feature_problems"]["streak"]["message"]
    off = message(env, 123, "/ai_off", False, 5)
    await env.engine.on_message(off)
    assert "123" not in env.store.read()["streak"]["chats"]


def test_streak_settings_validate_model_and_keep_or_replace_the_photo(env):
    report = env.engine.save_streak(True, " кот ", "google/img-1", base64.b64encode(png()).decode())
    assert report["enabled"] and report["prompt"] == "кот" and report["photo"] and report["effective_model"] == "google/img-1"
    assert env.engine.save_streak(True, "кот", "", None)["photo"]               # None keeps the photo
    assert not env.engine.save_streak(True, "кот", "", "")["photo"]
    with pytest.raises(UserError):
        env.engine.save_streak(True, "", "sk-or-v1-abcdefghijklmnopqrstuvwxyz", None)
    with pytest.raises(UserError):
        env.engine.save_streak(True, "", "", base64.b64encode(b"x" * 100).decode())


def test_google_aq_keys_are_not_models_and_are_scrubbed_from_saved_logs(tmp_path):
    from cryptography.fernet import Fernet
    from replyai import media
    from replyai.store import Store
    fake = "AQ." + "Xy1-" * 12                       # the shape of the newer AI Studio keys, not a real one
    assert ai_module.looks_like_key(fake) and not ai_module.looks_like_key("gemini-2.5-flash-image")
    with pytest.raises(UserError):
        media.validate({"max_mb": 10, "pdf_pages": 20, "scan_pages": 3, "vision_model": fake})
    key = Fernet.generate_key()
    store = Store(tmp_path / "data", key)
    store.update(lambda s: (s["media"].update(vision_model=fake), s["logs"].insert(0, f"HTTP 400 (модель {fake})"),
                            s.setdefault("feature_problems", {}).update(photos={"at": 1, "message": f"{fake} is not valid"})))
    store.close()
    store = Store(tmp_path / "data", key)            # the next start cleans what an older server saved
    try:
        state = store.read()
        assert state["media"]["vision_model"] == "" and fake not in str(state["logs"])
        assert fake not in state["feature_problems"]["photos"]["message"]
    finally:
        store.close()
