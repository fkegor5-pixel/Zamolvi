"""2.9: interlocutor memory, photos and documents, manual approval, extended statistics."""
import asyncio
import datetime as dt
import io
import json
import zipfile
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from telethon import types

from conftest import FakeAI, FakeTelegram
from replyai import ai as ai_module, approval, media, memory, stats
from replyai.ai import AiClient, AiError, usage_of
from replyai.errors import UserError


# ----------------------------------------------------------------------------- helpers

class RichAI(FakeAI):
    """FakeAI plus the 2.9 calls; remembers the prompts it was given."""
    def __init__(self):
        super().__init__()
        self.prompts, self.completions, self.images = [], [], []
        self.summary = {"name": "Анна", "interests": ["горы"], "agreements": ["созвон в пятницу"], "facts": ["дизайнер"]}
        self.vision = "Текст на изображении: Счёт №5\nОписание: фото счёта"
        self.vision_error = None

    async def answer(self, config, text, history=()):
        self.prompts.append(config["prompt"])
        return await super().answer(config, text, history)

    async def complete(self, config, system, text, max_tokens=1024):
        self.completions.append(text)
        return "Вот память: " + json.dumps(self.summary, ensure_ascii=False)

    async def describe_image(self, config, media_config, image, mime, task):
        self.images.append((mime, len(image), task))
        if self.vision_error:
            raise self.vision_error
        return self.vision


class MediaTelegram(FakeTelegram):
    """Source messages carry an attachment; download_media writes the given bytes."""
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.attachment = {}
        self.payload = b""

    async def get_messages(self, peer, ids):
        def message(i):
            return SimpleNamespace(id=i, out=False, raw_text=self.source_text, **self.attachment)
        return [message(i) for i in ids] if isinstance(ids, list) else message(ids)

    async def download_media(self, source, file, progress_callback=None):
        if progress_callback:
            progress_callback(len(self.payload), len(self.payload))
        file.write(self.payload)


def setup_chats(env, chats=("123",), **ai):
    def edit(s):
        s.update(enabled=True, enabled_at=env.clock[0] - 10,
                 selected={c: {"since": env.clock[0] - 10, "name": "Анна" if c == "123" else "Борис"} for c in chats})
        s["behavior"]["batch_seconds"] = 0
        s["ai"].update(key="fake-ai-key", cooldown=0, **ai)
    env.store.update(edit)
    env.engine.ai = RichAI()
    return env.engine.ai


def incoming(env, message_id=1, chat=123, text="Привет", **attachment):
    message = SimpleNamespace(raw_text=text, **attachment)
    return SimpleNamespace(is_private=True, out=False, raw_text=text, chat_id=chat, id=message_id,
                           date=dt.datetime.fromtimestamp(env.clock[0], dt.timezone.utc),
                           get_sender=AsyncMock(return_value=types.User(chat, first_name="Анна")),
                           message=message)


async def reply(env, message_id, chat=123, text="Привет"):
    env.engine.client.source_text = text
    await env.engine.on_message(incoming(env, message_id, chat, text))
    await env.engine.process_one()


def photo(photo_id=77, size=2048):
    return SimpleNamespace(id=photo_id, sizes=[SimpleNamespace(size=100), SimpleNamespace(sizes=[500, size])])


def document(mime, name, size=1000, doc_id=88):
    return SimpleNamespace(id=doc_id, mime_type=mime, size=size, attributes=[SimpleNamespace(file_name=name)])


def jpeg(width=40, height=30, color=(200, 30, 30)):
    from PIL import Image
    out = io.BytesIO()
    Image.new("RGB", (width, height), color).save(out, "JPEG")
    return out.getvalue()


def text_pdf(lines):
    """A minimal one-page PDF with a real text layer."""
    content = "BT /F1 12 Tf 72 720 Td " + " ".join(f"({l}) Tj 0 -16 Td" for l in lines) + " ET"
    objects = ["<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
               "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
               f"<< /Length {len(content)} >>\nstream\n{content}\nendstream",
               "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    out, offsets = io.BytesIO(), []
    out.write(b"%PDF-1.4\n")
    for number, body in enumerate(objects, start=1):
        offsets.append(out.tell())
        out.write(f"{number} 0 obj\n{body}\nendobj\n".encode("latin-1"))
    xref = out.tell()
    out.write(f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode())
    for offset in offsets:
        out.write(f"{offset:010d} 00000 n \n".encode())
    out.write(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return out.getvalue()


def scanned_pdf():
    from PIL import Image
    out = io.BytesIO()
    Image.new("RGB", (300, 400), (255, 255, 255)).save(out, "PDF")
    return out.getvalue()


def docx(paragraphs):
    out = io.BytesIO()
    body = "".join(f"<w:p><w:r><w:t>{p}</w:t></w:r></w:p>" for p in paragraphs)
    with zipfile.ZipFile(out, "w") as archive:
        archive.writestr("word/document.xml", '<?xml version="1.0"?><w:document xmlns:w="w"><w:body>'
                         + body + "</w:body></w:document>")
    return out.getvalue()


# ----------------------------------------------------------------------------- memory

def test_memory_parse_bounds_and_rejects_garbage():
    raw = json.dumps({"name": " Анна " * 40, "interests": ["горы", "Горы", "x" * 500] + [f"i{i}" for i in range(20)],
                      "agreements": "созвон", "facts": [{"bad": 1}, 5]})
    parsed = memory.parse("```json\n" + raw + "\n```")
    assert len(parsed["name"]) <= memory.MAX_NAME
    assert parsed["interests"][:2] == ["горы", "x" * memory.MAX_ITEM]   # duplicate dropped, item cut
    assert len(parsed["interests"]) == memory.MAX_ITEMS
    assert parsed["agreements"] == ["созвон"] and parsed["facts"] == ["5"]
    for bad in ("нет памяти", "[1, 2]", "{broken"):
        with pytest.raises(ValueError):
            memory.parse(bad)


def test_memory_is_opt_in_and_requires_selected_chats(env):
    with pytest.raises(UserError):
        env.store.update(lambda s: memory.configure(s, True))
    env.store.update(lambda s: s.update(selected={"123": {"since": 0, "name": "Анна"}}))
    env.store.update(lambda s: memory.observe(s, "123", [1], "Привет", "Ответ", 0))
    assert env.store.read()["memory"]["chats"] == {}          # off: nothing collected


@pytest.mark.asyncio
async def test_memory_is_built_from_sent_replies_and_used_in_next_reply(env):
    ai = setup_chats(env)
    env.engine.save_memory(True)
    for i in range(1, 1 + memory.UPDATE_EVERY):
        await reply(env, i, text=f"Сообщение {i}")
    assert len(env.engine.client.sent) == memory.UPDATE_EVERY
    await env.engine.housekeeping()
    assert len(ai.completions) == 1 and "Сообщение 1" in ai.completions[0]
    report = env.engine.memory_report()
    row = next(r for r in report["chats"] if r["chat"] == "123")
    assert row["summary"]["agreements"] == ["созвон в пятницу"] and row["pending"] == 0
    assert env.store.read()["stats"]["totals"]["memory_updates"] == 1
    await reply(env, 10, text="Когда созвонимся?")
    assert "Договорённости: созвон в пятницу" in ai.prompts[-1]
    assert "Договорённости" not in ai.prompts[0]


@pytest.mark.asyncio
async def test_forget_one_chat_and_stale_update_is_discarded(env):
    setup_chats(env, ("123", "456"))
    env.engine.save_memory(True)
    state = env.store.read()
    for chat in ("123", "456"):
        env.store.update(lambda s, c=chat: memory.observe(s, c, [1], "a", "b", env.clock[0]))
        env.store.update(lambda s, c=chat: memory.apply(s, c, s["memory"]["epoch"], 0, {**memory.empty_summary(), "name": c}, 1))
    system, text, epoch, used = memory.request(env.store.read(), "123")
    env.engine.forget_memory("123")
    assert not env.store.update(lambda s: memory.apply(s, "123", epoch, used, memory.empty_summary(), 2))
    chats = env.store.read()["memory"]["chats"]
    assert "123" not in chats and chats["456"]["summary"]["name"] == "456"
    env.engine.forget_memory(None)
    assert env.store.read()["memory"]["chats"] == {}
    assert state["memory"]["enabled"]


@pytest.mark.asyncio
async def test_memory_is_removed_by_ai_off_deselection_logout_and_switching_off(env):
    setup_chats(env, ("123", "456"))
    env.engine.save_memory(True)
    for chat in ("123", "456"):
        env.store.update(lambda s, c=chat: memory.observe(s, c, [1], "a", "b", env.clock[0]))
    await env.engine.on_message(incoming(env, 5, 123, "/ai_off"))
    assert set(env.store.read()["memory"]["chats"]) == {"456"}
    env.store.update(lambda s: s.update(selected={}))
    env.store.update(lambda s: __import__("replyai.privacy").privacy.restrict(s))
    assert env.store.read()["memory"]["chats"] == {} and not env.store.read()["memory"]["enabled"]
    setup_chats(env)
    env.engine.save_memory(True)
    env.store.update(lambda s: memory.observe(s, "123", [1], "a", "b", env.clock[0]))
    env.engine.save_memory(False)
    assert env.store.read()["memory"]["chats"] == {}
    env.engine.save_memory(True)
    env.store.update(lambda s: memory.observe(s, "123", [1], "a", "b", env.clock[0]))
    await env.engine.logout()
    assert env.store.read()["memory"] == memory.initial()


@pytest.mark.asyncio
async def test_deleted_source_is_dropped_from_pending_memory(env):
    setup_chats(env)
    env.engine.save_memory(True)
    env.store.update(lambda s: memory.observe(s, "123", [7, 8], "секрет", "ответ", env.clock[0]))
    env.store.update(lambda s: memory.observe(s, "123", [9], "обычное", "ответ", env.clock[0]))
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[8]))
    pending = env.store.read()["memory"]["chats"]["123"]["pending"]
    assert [p["in"] for p in pending] == ["обычное"]


@pytest.mark.asyncio
async def test_memory_failure_postpones_and_never_stops_the_worker(env):
    ai = setup_chats(env)
    env.engine.save_memory(True)
    async def broken(*args, **kwargs):
        return "не JSON"
    ai.complete = broken
    for i in range(1, 1 + memory.UPDATE_EVERY):
        await reply(env, i)
    await env.engine.housekeeping()
    row = env.store.read()["memory"]["chats"]["123"]
    assert row["retry_at"] > env.clock[0] and len(row["pending"]) == memory.UPDATE_EVERY
    assert env.store.read()["stats"]["totals"]["err_memory"] == 1


# ----------------------------------------------------------------------------- media

def test_media_identity_and_settings_gate():
    assert media.identity(SimpleNamespace(photo=photo()))["size"] == 2048
    assert media.identity(SimpleNamespace(document=document("application/pdf", "a.pdf")))["media"] == "pdf"
    assert media.identity(SimpleNamespace(document=document("application/octet-stream", "Отчёт.DOCX")))["media"] == "docx"
    assert media.identity(SimpleNamespace(document=document("text/plain", "notes.txt")))["media"] == "text"
    assert media.identity(SimpleNamespace(document=document("image/png", "x.png")))["media"] == "image"
    for skipped in (SimpleNamespace(sticker=True, document=document("image/webp", "s.webp")),
                    SimpleNamespace(video=True, document=document("video/mp4", "v.mp4")),
                    SimpleNamespace(document=document("application/zip", "a.zip")), SimpleNamespace()):
        assert media.identity(skipped) is None
    config = media.initial()
    assert media.describe(SimpleNamespace(photo=photo()), config) is None          # off by default
    config.update(photos=True, max_mb=1)
    assert media.describe(SimpleNamespace(photo=photo()), config)["media"] == "photo"
    assert media.describe(SimpleNamespace(document=document("application/pdf", "a.pdf")), config) is None
    config["documents"] = True
    with pytest.raises(media.MediaError):
        media.describe(SimpleNamespace(document=document("application/pdf", "a.pdf", size=2 * 1024 * 1024)), config)


def test_documents_are_read_on_the_server():
    text, scans, pages = media.pdf_content(text_pdf(["Invoice 42", "Total 1500 RUB"]), 20, 3)
    assert "Invoice 42" in text and "Total 1500" in text and scans == [] and pages == 1
    text, scans, pages = media.pdf_content(scanned_pdf(), 20, 3)
    assert text == "" and len(scans) == 1 and scans[0][0] == 1
    assert media.docx_text(docx(["Договор &amp; условия", "Пункт 2"])) == "Договор & условия\nПункт 2"
    assert media.plain_text("Привет из cp1251".encode("cp1251")) == "Привет из cp1251"
    image, mime = media.prepare_image(jpeg(4000, 100))
    assert mime == "image/jpeg"
    from PIL import Image
    assert max(Image.open(io.BytesIO(image)).size) <= media.MAX_SIDE
    for bad in (lambda: media.prepare_image(b"not an image"), lambda: media.pdf_content(b"%PDF-broken", 5, 1),
                lambda: media.docx_text(b"zip?")):
        with pytest.raises(media.MediaError):
            bad()


@pytest.mark.asyncio
async def test_photo_is_recognised_and_answered(env):
    ai = setup_chats(env)
    env.engine.save_media({**media.initial(), "photos": True})
    env.engine.client = MediaTelegram()
    env.engine.client.attachment = {"photo": photo()}
    env.engine.client.payload = jpeg()
    env.engine.client.source_text = "Что это?"
    await env.engine.on_message(incoming(env, 3, text="Что это?", photo=photo()))
    await env.engine.process_one()
    assert len(env.engine.client.sent) == 1
    asked = ai.calls[-1][0]
    assert asked.startswith("Что это?\n[Фото]") and "Счёт №5" in asked
    assert ai.images[0][0] == "image/jpeg" and ai.images[0][2] == media.VISION_TASK
    assert env.store.read()["stats"]["totals"]["photos"] == 1


@pytest.mark.asyncio
async def test_unreadable_attachment_still_answers_the_caption(env):
    ai = setup_chats(env)
    ai.vision_error = AiError("Модель не поддерживает изображения.")
    env.engine.save_media({**media.initial(), "photos": True})
    env.engine.client = MediaTelegram()
    env.engine.client.attachment = {"photo": photo()}
    env.engine.client.payload = jpeg()
    env.engine.client.source_text = "Посмотри"
    await env.engine.on_message(incoming(env, 4, text="Посмотри", photo=photo()))
    await env.engine.process_one()
    assert len(env.engine.client.sent) == 1
    assert "Вложение не удалось обработать: Модель не поддерживает изображения" in ai.calls[-1][0]
    assert env.store.read()["stats"]["totals"]["err_media"] == 1


@pytest.mark.asyncio
async def test_pdf_document_text_is_passed_to_the_reply(env):
    ai = setup_chats(env)
    env.engine.save_media({**media.initial(), "documents": True})
    pdf = text_pdf(["Delivery on Monday"])
    env.engine.client = MediaTelegram()
    doc = document("application/pdf", "plan.pdf", size=len(pdf))
    env.engine.client.attachment = {"document": doc}
    env.engine.client.payload = pdf
    env.engine.client.source_text = ""
    await env.engine.on_message(incoming(env, 6, text="", document=doc))
    await env.engine.process_one()
    assert "[PDF «plan.pdf», 1 стр.]" in ai.calls[-1][0] and "Delivery on Monday" in ai.calls[-1][0]
    assert ai.images == []


@pytest.mark.asyncio
async def test_photos_off_keeps_caption_only_behaviour(env):
    ai = setup_chats(env)
    await env.engine.on_message(incoming(env, 2, text="Подпись", photo=photo()))
    await env.engine.process_one()
    assert ai.calls[-1][0] == "Подпись" and ai.images == []


# ----------------------------------------------------------------------------- approval

@pytest.mark.asyncio
async def test_draft_waits_then_edited_answer_is_sent(env):
    setup_chats(env)
    env.engine.save_approval("all", [], True)
    await reply(env, 1)
    sent = env.engine.client.sent
    assert len(sent) == 1 and isinstance(sent[0].peer, types.InputPeerSelf)      # only the owner's notice
    assert "черновик ответа для «Анна»" in sent[0].message and "Привет" not in sent[0].message
    drafts = env.engine.approval_report()["drafts"]
    assert len(drafts) == 1 and drafts[0]["answer"] == "Тестовый ответ" and drafts[0]["incoming"] == "Привет"
    assert env.engine.status()["awaiting"] == 1
    env.engine.decide(drafts[0]["id"], "send", "Исправленный ответ")
    await env.engine.process_one()
    assert len(sent) == 2 and sent[1].message.endswith("Исправленный ответ")
    assert len(env.ai.calls) == 1 or len(env.engine.ai.calls) == 1           # no second AI call
    report = env.engine.extended_stats(1)
    assert report["approval"]["sent_approved"] == 1 and report["approval"]["edited"] == 1
    assert report["approval"]["approved_percent"] == 100.0
    with pytest.raises(UserError):
        env.engine.decide(drafts[0]["id"], "send")


@pytest.mark.asyncio
async def test_rejected_draft_is_not_sent_and_counted(env):
    setup_chats(env)
    env.engine.save_approval("all", [], False)
    await reply(env, 1)
    assert env.engine.client.sent == []                                          # notify off
    draft = env.engine.approval_report()["drafts"][0]
    env.engine.decide(draft["id"], "reject")
    await env.engine.process_one()
    assert env.engine.client.sent == [] and env.store.read()["queue"] == []
    assert env.engine.extended_stats(1)["approval"]["rejected"] == 1


@pytest.mark.asyncio
async def test_draft_keeps_order_in_its_chat_only_and_mixed_percent(env):
    setup_chats(env, ("123", "456"))
    env.engine.save_approval("chats", ["123"], False)
    await reply(env, 1, 123)
    await env.engine.on_message(incoming(env, 2, 123, "Ещё"))
    await env.engine.process_one()
    assert env.engine.client.sent == []                       # 2nd message of chat 123 waits behind the draft
    await reply(env, 3, 456)
    assert len(env.engine.client.sent) == 1                   # chat 456 is answered automatically
    draft = env.engine.approval_report()["drafts"][0]
    env.engine.decide(draft["id"], "send", "")
    await env.engine.process_one()
    assert len(env.engine.client.sent) == 2
    report = env.engine.extended_stats(1)
    assert report["approval"]["approved_percent"] == 50.0
    chats = {c["chat"]: c for c in report["chats"]}
    assert chats["123"]["approved"] == 1 and chats["456"]["auto"] == 1


@pytest.mark.asyncio
async def test_drafts_expire_and_owner_reply_cancels_them(env):
    setup_chats(env, ("123", "456"))
    env.engine.save_approval("all", [], False)
    await reply(env, 1, 123)
    await reply(env, 2, 456)
    owner = incoming(env, 9, 456, "Отвечу сам")
    owner.out = True
    await env.engine.on_message(owner)
    assert [d["chat"] for d in env.engine.approval_report()["drafts"]] == ["123"]
    env.clock[0] += 13 * 3600
    await env.engine.housekeeping()
    assert env.engine.approval_report()["drafts"] == []
    assert env.engine.extended_stats(0)["approval"]["expired"] == 1


def test_approval_settings_are_validated(env):
    env.store.update(lambda s: s.update(selected={"1": {"since": 0, "name": "A"}}))
    with pytest.raises(UserError):
        env.engine.save_approval("chats", [], True)
    with pytest.raises(UserError):
        env.engine.save_approval("chats", ["2"], True)
    assert env.engine.save_approval("chats", ["1", "1"], True)["chats"] == ["1"]
    env.store.update(lambda s: s.update(selected={}))
    env.store.update(lambda s: __import__("replyai.privacy").privacy.restrict(s))
    assert env.store.read()["approval"]["mode"] == "off"


# ----------------------------------------------------------------------------- statistics and AI usage

def test_extended_statistics_costs_by_model_and_purpose(env):
    now = env.clock[0]
    env.store.update(lambda s: s.update(selected={"123": {"since": 0, "name": "Анна"}}))
    def usage(s):
        stats.record_usage(s, now, "groq", "openai/gpt-oss-20b", "reply", 1_000_000, 500_000, 800, chat="123")
        stats.record_usage(s, now, "groq", "openai/gpt-oss-20b", "memory", 200_000, 100_000, 400, chat="123")
        stats.record_usage(s, now, "groq", "whisper-large-v3-turbo", "voice", audio_seconds=1800, chat="123")
        stats.record_usage(s, now, "gemini", "gemini-x", "vision", 10, 10, 100, ok=False)
        stats.record_error(s, now, "ai", "123")
    env.store.update(usage)
    report = env.engine.extended_stats(1)
    assert report["cost"]["total"] == 0 and not report["cost"]["complete"]
    env.engine.save_pricing({"currency": "$", "models": {
        "groq/openai/gpt-oss-20b": {"input": 0.10, "output": 0.50}, "groq/whisper-large-v3-turbo": {"audio_hour": 0.04}}})
    report = env.engine.extended_stats(7)
    models = {m["model"]: m for m in report["models"]}
    assert models["groq/openai/gpt-oss-20b"]["cost"] == pytest.approx(0.12 + 0.30)
    assert models["groq/whisper-large-v3-turbo"]["cost"] == pytest.approx(0.02)
    assert models["gemini/gemini-x"]["errors"] == 1 and models["gemini/gemini-x"]["cost"] is None
    purposes = {p["purpose"]: p for p in report["purposes"]}
    assert purposes["memory"]["cost"] == pytest.approx(0.02 + 0.05)
    assert report["cost"]["total"] == pytest.approx(0.44) and report["currency"] == "$"
    assert report["error_kinds"]["ai"] == 1 and report["errors"] == 1
    chat = report["chats"][0]
    assert chat["name"] == "Анна" and chat["input_tokens"] == 1_200_000 and chat["errors"] == 1
    assert "groq/openai/gpt-oss-20b" in env.engine.public_pricing()["known"]
    env.store.update(lambda s: s.update(selected={}))
    env.store.update(lambda s: stats.restrict(s))
    assert env.engine.extended_stats(0)["chats"] == []
    with pytest.raises(UserError):
        env.engine.save_pricing({"currency": "долларов", "models": {}})


def test_provider_usage_is_parsed():
    assert usage_of("groq", {"usage": {"prompt_tokens": 12, "completion_tokens": 5}}) == (12, 5)
    assert usage_of("gemini", {"usageMetadata": {"promptTokenCount": 7, "candidatesTokenCount": 3,
                                                 "thoughtsTokenCount": 2}}) == (7, 5)
    assert usage_of("custom", {}) == (0, 0)
    assert usage_of("custom", {"usage": {"prompt_tokens": -1, "completion_tokens": "x"}}) == (0, 0)


@pytest.mark.asyncio
async def test_ai_client_reports_usage_with_purpose_and_builds_vision_requests():
    client = AiClient()
    seen, bodies = [], []
    client.on_usage = lambda *args: seen.append(args)
    async def fake_post(config, http, url, headers, body):
        bodies.append((url, body))
        if config["provider"] == "gemini":
            return {"candidates": [{"content": {"parts": [{"text": "ok"}]}}], "usageMetadata": {"promptTokenCount": 3}}
        return {"choices": [{"message": {"content": " ok "}}], "usage": {"prompt_tokens": 4, "completion_tokens": 2}}
    client._post = fake_post
    async def giga_token(key):
        return "token"
    client.giga_token = giga_token
    uploads = []
    async def upload(headers, image, mime):
        uploads.append(mime)
        return "file-1"
    async def delete(headers, file_id):
        uploads.append("deleted " + file_id)
    client._giga_upload, client._giga_delete = upload, delete
    base = {"key": "k", "prompt": "p", "formatting": "plain", "endpoint": "https://example.org/v1/chat"}
    try:
        with ai_module.purpose("reply", "123"):
            assert await client.answer({**base, "provider": "groq", "model": "openai/gpt-oss-20b"}, "q") == "ok"
        assert seen[-1][:6] == ("groq", "openai/gpt-oss-20b", "reply", "123", 4, 2)
        image = jpeg()
        with ai_module.purpose("vision", "123"):
            await client.describe_image({**base, "provider": "groq", "model": "openai/gpt-oss-20b"}, {"vision_model": ""}, image, "image/jpeg", "t")
        url, body = bodies[-1]
        assert body["model"] == ai_module.DEFAULT_VISION_MODELS["groq"] and seen[-1][1] == body["model"]
        assert body["messages"][0]["content"][1]["image_url"]["url"].startswith("data:image/jpeg;base64,")
        await client.describe_image({**base, "provider": "gemini", "model": "gemini-x"}, {}, image, "image/jpeg", "t")
        assert "gemini-x:generateContent" in bodies[-1][0]
        assert bodies[-1][1]["contents"][0]["parts"][0]["inlineData"]["mimeType"] == "image/jpeg"
        await client.describe_image({**base, "provider": "gigachat", "model": "GigaChat"}, {"vision_model": "GigaChat-2-Pro"}, image, "image/jpeg", "t")
        assert bodies[-1][1]["messages"][0]["attachments"] == ["file-1"] and uploads == ["image/jpeg", "deleted file-1"]
        assert bodies[-1][1]["model"] == "GigaChat-2-Pro"
        with ai_module.purpose("memory", "123"):
            await client.complete({**base, "provider": "custom", "model": "m"}, "system", "text")
        assert bodies[-1][1]["messages"][0] == {"role": "system", "content": "system"} and seen[-1][2] == "memory"
        async def failing(*args):
            raise AiError("down", 30)
        client._post = failing
        with pytest.raises(AiError):
            await client.answer({**base, "provider": "groq", "model": "openai/gpt-oss-20b"}, "q")
        assert seen[-1][-1] is False
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_new_state_sections_survive_restart_and_old_state_migrates(env, tmp_path):
    from replyai.store import Store
    env.store.update(lambda s: [s.pop(k) for k in ("memory", "media", "approval", "pricing")])
    env.store.update(lambda s: s["stats"].pop("models"))
    path, key = env.store.path, env.key
    env.store.close()
    migrated = Store(path, key)
    try:
        state = migrated.read()
        assert state["memory"] == memory.initial() and state["media"] == media.initial()
        assert state["approval"] == approval.initial() and state["stats"]["models"] == {}
    finally:
        migrated.close()
    env.store = Store(path, key)   # the fixture closes it again


# ----------------------------------------------------------------------------- HTTP API

TOKEN = "features-test-" + "x" * 40
AUTH = {"Authorization": "Bearer " + TOKEN}
ROUTES_29 = [("GET", "/api/memory"), ("PUT", "/api/memory"), ("POST", "/api/memory/forget"),
             ("GET", "/api/media"), ("PUT", "/api/media"), ("GET", "/api/approval"), ("PUT", "/api/approval"),
             ("POST", "/api/approval/decide"), ("POST", "/api/stats/extended"), ("GET", "/api/pricing"),
             ("PUT", "/api/pricing"), ("GET", "/api/features"),
             ("POST", "/api/memory/import"), ("POST", "/api/memory/import/cancel")]


@pytest.fixture
def api29(env):
    from fastapi.testclient import TestClient
    from replyai.web import create_app
    with TestClient(create_app(env.store, env.engine, token=TOKEN)) as client:
        yield client


@pytest.mark.parametrize("method,path", ROUTES_29)
def test_every_29_endpoint_requires_owner_token(api29, method, path):
    assert api29.request(method, path, json={}).status_code == 401


def test_29_endpoints_validate_and_work(api29, env):
    from replyai.diagnostics import ROUTES
    assert all(path in ROUTES for _, path in ROUTES_29)
    assert api29.put("/api/memory", headers=AUTH, json={"enabled": True}).status_code == 400   # no chats yet
    env.store.update(lambda s: s.update(selected={"123": {"since": 0, "name": "Анна"}}))
    assert api29.put("/api/memory", headers=AUTH, json={"enabled": True}).json()["enabled"] is True
    assert api29.post("/api/memory/forget", headers=AUTH, json={"chat": "-1"}).status_code == 422
    assert api29.post("/api/memory/forget", headers=AUTH, json={"chat": "123"}).status_code == 200
    assert api29.put("/api/media", headers=AUTH, json={"photos": True, "max_mb": 99}).status_code == 422
    media_settings = api29.put("/api/media", headers=AUTH, json={"photos": True, "vision_model": "v-1"}).json()
    assert media_settings["photos"] and media_settings["effective_vision_model"] == "v-1"
    assert api29.put("/api/approval", headers=AUTH, json={"mode": "chats", "chats": ["999"]}).status_code == 400
    assert api29.put("/api/approval", headers=AUTH, json={"mode": "all"}).json()["mode"] == "all"
    assert api29.post("/api/approval/decide", headers=AUTH, json={"id": "123:1", "action": "send"}).status_code == 400
    assert api29.post("/api/approval/decide", headers=AUTH, json={"id": "x", "action": "send"}).status_code == 422
    assert api29.post("/api/stats/extended", headers=AUTH, json={"period": 5}).status_code == 400
    assert api29.post("/api/stats/extended", headers=AUTH, json={"period": 30}).json()["period_days"] == 30
    import re
    assert all(re.fullmatch(r"/api/[a-z/]+", path) for _, path in ROUTES_29)   # the app's client rule
    priced = api29.put("/api/pricing", headers=AUTH, json={"currency": "₽", "models": {"groq/m": {"input": 10}}}).json()
    assert priced["models"]["groq/m"]["input"] == 10 and priced["currency"] == "₽"
    assert api29.put("/api/pricing", headers=AUTH, json={"models": {"m": {"input": -1}}}).status_code == 422


@pytest.mark.asyncio
async def test_broken_ai_settings_in_background_work_are_ai_errors():
    client = AiClient()
    try:
        for call in (lambda: client.complete({"provider": "groq", "key": "", "model": "m"}, "s", "t"),
                     lambda: client.describe_image({"provider": "groq", "key": "", "model": "m"}, {}, b"x", "image/jpeg", "t")):
            with pytest.raises(AiError):
                await call()
    finally:
        await client.close()



# ----------------------------------------------------------------------------- 2.9.1: why a feature does not work

def test_feature_report_explains_each_feature(env):
    report = env.engine.feature_report()
    from replyai import __version__
    assert report["server_version"] == __version__
    assert report["autoresponder"]["status"] == "problem"                       # no AI key / chats yet
    assert report["memory"]["status"] == "off" and "11. Память" in report["memory"]["reason"]
    assert report["approval"]["status"] == "off" and report["pricing"]["status"] == "off"
    setup_chats(env)
    env.engine.save_memory(True)
    env.engine.save_media({**media.initial(), "photos": True})
    env.engine.save_approval("all", [], True)
    report = env.engine.feature_report()
    assert report["autoresponder"]["status"] == "ok"
    assert report["memory"]["status"] == "ok" and report["photos"]["status"] == "ok"
    assert "все чаты" in report["approval"]["reason"]
    env.engine.disable()
    report = env.engine.feature_report()
    assert report["approval"]["status"] == "problem" and "не отвечает" in report["approval"]["reason"]
    assert env.engine.diagnostics.report()["features"]["memory"]["status"] == "problem"


@pytest.mark.asyncio
async def test_skipped_photo_is_explained_in_the_log_once(env):
    setup_chats(env)
    for i in (1, 2):
        await env.engine.on_message(incoming(env, i, text="Смотри", photo=photo()))
    logs = [l for l in env.store.read()["logs"] if "распознавание фото выключено" in l]
    assert len(logs) == 1                                                        # rate-limited
    assert env.engine.feature_report()["photos"]["last_problem"].startswith("Получено фото")
    await env.engine.on_message(incoming(env, 3, text="", document=document("application/zip", "a.zip")))
    assert "неподдерживаемого типа" in env.store.read()["logs"][0]


# ----------------------------------------------------------------------------- 2.9.2: chat history -> memory

class HistoryTelegram(FakeTelegram):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.history = []

        self.flood_once = False

    async def iter_messages(self, peer, limit=None, offset_id=0):
        rows = [m for m in reversed(self.history) if not offset_id or m.id < offset_id]   # newest first
        for index, message in enumerate(rows[:limit]):
            if self.flood_once and index == 5:
                self.flood_once = False
                from telethon import errors
                raise errors.FloodWaitError(None, capture=0)
            yield message


def history(env, count):
    base = dt.datetime.fromtimestamp(env.clock[0], dt.timezone.utc)
    rows = []
    for i in range(count):
        out = i % 3 == 0
        text = (ai_module.PREFIX + "автоответ") if i % 7 == 6 else f"Сообщение {i}: люблю горы " + "x" * 400
        rows.append(SimpleNamespace(id=i + 1, out=out, raw_text=text, date=base - dt.timedelta(days=count - i)))
    rows.append(SimpleNamespace(id=count + 1, out=False, raw_text="", date=base))   # photo without caption
    return rows


async def finish_import(env, chat="123"):
    await env.engine.import_tasks[chat]
    return next(r for r in env.engine.memory_report()["chats"] if r["chat"] == chat)


@pytest.mark.asyncio
async def test_history_import_builds_memory_in_parts_without_storing_history(env):
    ai = setup_chats(env)
    env.engine.client = HistoryTelegram()
    env.engine.client.history = history(env, 60)
    env.engine.save_memory(True)
    with pytest.raises(UserError):
        env.engine.start_import("123", 500, False)                                   # separate consent
    env.engine.start_import("123", 500, True)
    with pytest.raises(UserError):
        env.engine.start_import("123", 500, True)                                    # already running
    row = await finish_import(env)
    assert row["import"]["status"] == "done" and row["import"]["parts"] >= 2
    assert row["import"]["parts_done"] == row["import"]["parts"]
    assert row["summary"]["agreements"] == ["созвон в пятницу"]
    sent = "\n".join(ai.completions)
    assert "Сообщение 0" in sent and "ВЛАДЕЛЕЦ:" in sent and "СОБЕСЕДНИК:" in sent and ai_module.PREFIX not in sent
    assert sent.index("Сообщение 0") < sent.index("Сообщение 5")                   # oldest first
    stored = json.dumps(env.store.read(), ensure_ascii=False)
    assert "Сообщение 1:" not in stored                                            # raw history not kept


@pytest.mark.asyncio
async def test_history_import_respects_limits_forget_and_disabled_memory(env):
    setup_chats(env)
    env.engine.client = HistoryTelegram()
    env.engine.client.history = history(env, 10)
    with pytest.raises(UserError):
        env.engine.start_import("123", 500, True)                                    # memory off
    env.engine.save_memory(True)
    for bad in (50, 25000):
        with pytest.raises(UserError):
            env.engine.start_import("123", bad, True)
    env.engine.start_import("123", 100, True)
    env.engine.forget_memory("123")                                                  # cancels and forgets
    await asyncio.sleep(0)
    assert "123" not in env.engine.imports and env.store.read()["memory"]["chats"] == {}



def test_memory_keeps_only_the_interlocutor_and_drops_placeholders():
    parsed = memory.parse(json.dumps({"name": "неизвестно", "interests": ["Нет данных", "—", "горы", "Владелец любит рыбалку",
                                                                         "я программист"],
                                      "agreements": ["не указано"], "facts": ["дизайнер", "N/A", "отсутствует"]}))
    assert parsed == {"name": "", "interests": ["горы"], "agreements": [], "facts": ["дизайнер"]}
    assert "ВЛАДЕЛЬЦА в память не попадают" in memory.SYSTEM
    assert memory.history_line("01.01.2026", True, "я программист").startswith("[01.01.2026] ВЛАДЕЛЕЦ:")


@pytest.mark.asyncio
async def test_history_import_continues_after_telegram_pause(env):
    setup_chats(env)
    env.engine.client = HistoryTelegram()
    env.engine.client.history = history(env, 30)
    env.engine.client.flood_once = True
    env.engine.save_memory(True)
    env.engine.start_import("123", 20000, True)
    row = await finish_import(env)
    assert row["import"]["status"] == "done" and row["import"]["downloaded"] == 31   # nothing lost or doubled


@pytest.mark.asyncio
async def test_history_import_splits_parts_on_413_and_waits_on_429(env):
    ai = setup_chats(env)
    env.engine.client = HistoryTelegram()
    env.engine.client.history = history(env, 40)
    env.engine.save_memory(True)
    original, calls = ai.complete, {"n": 0}
    async def limited(config, system, text, max_tokens=1024):
        calls["n"] += 1
        if calls["n"] == 2:
            raise AiError("Лимит AI API: запрос отложен.", 0.01)            # 429 once
        if len(text) > 4000:
            raise AiError("Запрос слишком большой (HTTP 413).", too_large=True)
        return await original(config, system, text, max_tokens)
    ai.complete = limited
    env.engine.start_import("123", 20000, True)
    row = await finish_import(env)
    job = row["import"]
    assert job["status"] == "done" and job.get("split", 0) >= 1
    assert job["parts_done"] == job["parts"] and job.get("skipped", 0) == 0
    assert row["summary"]["agreements"] == ["созвон в пятницу"]


@pytest.mark.asyncio
async def test_openrouter_error_payload_and_reasoning_only_answers_are_explained():
    client = AiClient()
    replies = []
    async def fake_post(config, http, url, headers, body):
        replies.append(body.get("max_tokens"))
        return payload
    client._post = fake_post
    config = {"key": "k", "prompt": "p", "formatting": "plain", "provider": "custom",
              "model": "nvidia/nemotron-3-ultra-550b-a55b:free", "endpoint": "https://openrouter.ai/api/v1/chat/completions"}
    try:
        payload = {"error": {"code": 429, "message": "Rate limit exceeded: free-models-per-day"}}
        with pytest.raises(AiError) as error:
            await client.complete(config, "s", "t", memory.tokens(config))
        assert "Лимит бесплатной модели" in str(error.value) and error.value.retry_after == 60
        payload = {"choices": [{"message": {"content": None, "reasoning": "думаю..."}, "finish_reason": "length"}]}
        with pytest.raises(AiError) as error:
            await client.complete(config, "s", "t", memory.tokens(config))
        assert "рассуждения" in str(error.value)
        assert memory.tokens(config) == 4096 and memory.tokens({"provider": "groq"}) == 1024
    finally:
        await client.close()


@pytest.mark.asyncio
async def test_provider_error_reason_is_short_and_hides_keys():
    class Response:
        def __init__(self, raw):
            self.raw = raw
        async def aiter_bytes(self):
            yield self.raw
    raw = json.dumps({"error": {"message": "bad-model:free is not a valid model ID; key sk-or-v1-abcdefabcdefabcdef"}}).encode()
    reason = await AiClient._reason(Response(raw), {"key": "secret-key-123"})
    assert reason.startswith("bad-model:free is not a valid model ID") and "abcdef" not in reason
    assert await AiClient._reason(Response(b"<html>"), {}) == ""


def test_notification_answer_works_without_telegram_account(api29, env):
    assert api29.post("/api/notification/answer", json={"text": "Привет"}).status_code == 401
    assert api29.post("/api/notification/answer", headers=AUTH, json={"text": ""}).status_code == 422
    response = api29.post("/api/notification/answer", headers=AUTH, json={"text": "Привет, ты где?"})
    assert response.status_code == 200
    assert response.json() == {"answer": "[AI-автоответ] Тестовый ответ"}
    assert env.ai.calls[-1][0] == "Привет, ты где?"   # only the message text reaches the AI


def test_api_key_pasted_as_model_is_rejected_and_scrubbed(env):
    key = "sk-or-v1-" + "ab12" * 10
    with pytest.raises(UserError):
        media.validate({**media.initial(), "vision_model": key})
    with pytest.raises(UserError):
        ai_module.validate({"provider": "groq", "key": "k", "model": key, "prompt": "p", "formatting": "plain"})
    assert not ai_module.looks_like_key("Skywork/skywork-o1-open-llama-3.1-8b")
    env.store.update(lambda s: s["media"].update(vision_model=key))
    from replyai.store import Store
    env.store.close()
    reopened = Store(env.store.path, env.key)
    try:
        assert reopened.read()["media"]["vision_model"] == ""    # removed from storage on start
    finally:
        reopened.close()


def test_style_is_added_to_every_reply_prompt(api29, env):
    from replyai import style
    assert style.apply({"prompt": "P"}, "") == {"prompt": "P"}
    assert "гопника" in style.apply({"prompt": "P"}, "gopnik")["prompt"]
    assert api29.put("/api/style", headers=AUTH, json={"style": "nope"}).status_code == 400
    chosen = api29.put("/api/style", headers=AUTH, json={"style": "gopnik"}).json()
    assert chosen["style"] == "gopnik" and any(s["key"] == "smart" for s in chosen["styles"])
    assert api29.get("/api/style", headers=AUTH).json()["style"] == "gopnik"
    seen = []
    async def answer(config, text, history=()):
        seen.append(config["prompt"])
        return "ок"
    env.engine.ai.answer = answer
    api29.post("/api/notification/answer", headers=AUTH, json={"text": "Привет"})
    assert "гопника" in seen[-1]
