import asyncio
import datetime as dt
import json
import ssl
from pathlib import Path
from unittest.mock import AsyncMock

import httpx
import pytest
from cryptography.fernet import Fernet, InvalidToken
from fastapi.testclient import TestClient
from telethon import errors

from replyai.ai import AiClient, AiError, PREFIX
from replyai.errors import UserError
from replyai.formatting import render, utf16_length
from replyai.store import Store, initial
from replyai import queueing, quiet, stats
from replyai.voice import VoiceClient, VoiceError
from replyai.web import create_app


def test_encrypted_state_survives_restart(env):
    env.store.update(lambda s: s["ai"].update(key="secret-do-not-store-in-cleartext"))
    path = env.store.path
    env.store.close()
    assert all(b"secret-do-not-store-in-cleartext" not in p.read_bytes() for p in path.glob("state.sqlite3*"))
    with pytest.raises(InvalidToken):
        Store(path, Fernet.generate_key())
    restored = Store(path, env.key)
    try:
        assert restored.read()["ai"]["key"] == "secret-do-not-store-in-cleartext"
    finally:
        restored.close()


def test_failed_update_is_atomic(env):
    before = env.store.read()
    def bad(state):
        state["sent"] = 999
        raise ValueError("abort")
    with pytest.raises(ValueError):
        env.store.update(bad)
    assert env.store.read() == before
    copy = env.store.read()
    copy["sent"] = 123
    assert env.store.read()["sent"] == before["sent"]


def test_second_worker_is_rejected(env):
    with pytest.raises(RuntimeError, match="другим процессом"):
        Store(env.store.path, env.key)


@pytest.mark.asyncio
async def test_logout_removes_previous_account_transcripts(env):
    env.store.update(lambda s: s.update(transcripts=[{"chat": "123", "text": "private voice"}],
                                       last_sent={"123": 44}, pause_until=99, voice_pause_until=88))
    await env.engine.logout()
    state = env.store.read()
    assert state["transcripts"] == []
    assert state["history"] == {} and state["last_sent"] == {}
    assert state["pause_until"] == state["voice_pause_until"] == 0


@pytest.mark.asyncio
async def test_changing_login_removes_previous_account_content(env):
    env.engine.authorized = False
    env.store.update(lambda s: s.update(telegram={"api_id": 1, "api_hash": "a" * 32, "phone": "+79990000000"},
                                       transcripts=[{"chat": "123", "text": "old voice"}],
                                       history={"123": [{"role": "user", "content": "old message"}]}))
    result = await env.engine.login_start(2, "b" * 32, "+79990000001")
    assert result["stage"] == "code"
    assert env.store.read()["transcripts"] == []
    assert env.store.read()["history"] == {}


@pytest.mark.asyncio
async def test_changing_qr_login_removes_previous_account_content(env):
    env.engine.authorized = False
    env.store.update(lambda s: s.update(telegram={"api_id": 1, "api_hash": "a" * 32, "phone": "+79990000000"},
                                       transcripts=[{"chat": "123", "text": "old voice"}]))
    env.engine.qr.export_locked = AsyncMock(side_effect=UserError("test stops before external QR request"))
    with pytest.raises(UserError):
        await env.engine.qr.start({"api_id": 2, "api_hash": "b" * 32, "phone": "+79990000001"})
    assert env.store.read()["transcripts"] == []


@pytest.mark.asyncio
async def test_reply_is_sent_once_and_saved(queued):
    e = queued.engine
    client = e.client
    assert not e.enqueue("123", 1, "Привет", queued.clock[0])
    await e.process_one()
    await e.process_one()
    assert len(client.sent) == 1
    assert client.sent[0].message == PREFIX + "Тестовый ответ"
    assert client.sent[0].reply_to.reply_to_msg_id == 1
    state = queued.store.read()
    assert state["queue"] == [] and state["sent"] == 1
    assert len(state["history"]["123"]) == 2


@pytest.mark.asyncio
async def test_stopping_during_generation_prevents_send(queued):
    queued.ai.callback = queued.engine.disable
    await queued.engine.process_one()
    assert queued.engine.client.sent == []
    assert not queued.store.read()["enabled"]


@pytest.mark.asyncio
async def test_edited_source_prevents_reply(queued):
    queued.engine.client.source_text = "edited"
    await queued.engine.process_one()
    assert queued.engine.client.sent == []
    assert queued.store.read()["queue"] == []


@pytest.mark.asyncio
async def test_ambiguous_send_is_never_repeated(queued):
    client = queued.engine.client
    client.send_error = OSError("connection lost after send")
    await queued.engine.process_one()
    assert queued.store.read()["queue"][0]["state"] == "uncertain"
    client.send_error = None
    queued.clock[0] += 120
    await queued.engine.process_one()
    assert client.sent == []
    assert len(queued.ai.calls) == 1


@pytest.mark.parametrize("stage", ["voice", "download", "ai", "source", "send"])
@pytest.mark.asyncio
async def test_transient_failures_schedule_a_bounded_retry(queued, stage):
    e = queued.engine
    flood = errors.FloodWaitError(None, capture=17)
    if stage == "voice":
        e.prepare_parts = AsyncMock(side_effect=VoiceError("retry", 17))
    elif stage == "download":
        e.prepare_parts = AsyncMock(side_effect=flood)
    elif stage == "ai":
        queued.ai.error = AiError("retry", 17)
    elif stage == "source":
        e.client.source_error = flood
    else:
        e.client.send_error = flood
    await e.process_one()
    state = queued.store.read()
    assert state["queue"][0]["state"] == "queued"
    assert state["queue"][0]["after"] == queued.clock[0] + 17
    assert (state["voice_pause_until"] if stage == "voice" else state["pause_until"]) == queued.clock[0] + 17
    assert e.client.sent == []


def test_queue_batches_without_duplicates(queued):
    queued.engine.save_behavior(5)
    queued.store.update(lambda s: s.update(queue=[], seen=[]))
    assert queued.engine.enqueue("123", 1, "Привет", queued.clock[0])
    assert queued.engine.enqueue("123", 2, "ещё", queued.clock[0])
    assert not queued.engine.enqueue("123", 2, "ещё", queued.clock[0])
    state = queued.store.read()
    assert len(state["queue"]) == 1
    assert state["queue"][0]["text"] == "Привет\n\nещё"
    assert len(state["queue"][0]["parts"]) == 2


@pytest.mark.parametrize("hour,minute,expected", [(22, 59, False), (23, 0, True), (0, 0, True), (7, 59, True), (8, 0, False)])
def test_quiet_hours_cross_midnight(hour, minute, expected):
    config = {**quiet.initial(), "quiet_enabled": True}
    stamp = dt.datetime(2026, 10, 18, hour, minute, tzinfo=dt.timezone(dt.timedelta(hours=3))).timestamp()
    assert quiet.active(config, stamp) is expected


@pytest.mark.parametrize("mode", ["auto", "plain", "bold", "underline", "mono"])
def test_formatting_offsets_are_valid_with_emoji(mode):
    text, entities = render("<b>😀 Привет</b>\n<code>print(1)</code>", mode, PREFIX)
    assert text.startswith(PREFIX)
    assert all(utf16_length(PREFIX) <= e.offset < e.offset + e.length <= utf16_length(text) for e in entities)
    assert not entities if mode == "plain" else entities


def test_statistics_keep_last_ninety_days():
    state = initial()
    for day in range(100):
        stats.record(state, 1700000000 + day * 86400, received=1, replies=1)
    result = stats.report(state, 1700000000 + 99 * 86400)
    assert len(state["stats"]["days"]) == 90
    assert result["totals"]["replies"] == 100
    assert len(result["series"]) == 7


@pytest.fixture
def api(env):
    env.engine.authorized = False
    app = create_app(env.store, env.engine, token="test-token-" + "x" * 40)
    with TestClient(app) as client:
        yield client


AUTH = {"Authorization": "Bearer test-token-" + "x" * 40}


@pytest.mark.parametrize("route", ["status", "settings", "chats", "behavior", "voice", "stats", "diagnostics", "telegram/qr"])
def test_private_apis_require_token(api, route):
    assert api.get("/api/" + route).status_code == 401


def test_api_settings_diagnostics_and_health(api, env):
    assert api.get("/healthz").status_code == 200
    env.store.update(lambda s: s["ai"].update(key="never-echo-api-key"))
    for route in ("status", "settings", "stats", "diagnostics", "voice", "behavior"):
        response = api.get("/api/" + route, headers=AUTH)
        assert response.status_code == 200
        assert "never-echo-api-key" not in response.text
        assert response.headers["cache-control"] == "no-store"
        assert len(response.headers["x-replyai-request-id"]) == 32


def test_api_rejects_invalid_and_oversized_json(api):
    response = api.post("/api/telegram/code", headers=AUTH, json={"code": "12345", "password": "secret-value"})
    assert response.status_code == 422 and "secret-value" not in response.text
    assert api.post("/api/test", headers=AUTH, content=b"x" * 70000).status_code == 413


def test_api_auth_failures_are_rate_limited(api):
    for _ in range(10):
        assert api.get("/api/status").status_code == 401
    assert api.get("/api/status").status_code == 429


def test_api_provider_change_requires_a_new_key(api, env):
    config = env.store.read()["ai"]
    config.pop("formatting")
    env.store.update(lambda s: s["ai"].update(key="old-provider-key"))
    config.update(provider="custom", endpoint="https://example.invalid/chat/completions", key="")
    response = api.put("/api/settings", headers=AUTH, json=config)
    assert response.status_code == 400
    assert env.store.read()["ai"]["provider"] == "groq"


@pytest.mark.parametrize("endpoint", ["https://example.org:abc/chat", "https://example.org:99999/chat",
                                     "https://[::1/chat", "https://@example.org/chat", "https://example.org:0/chat"])
def test_invalid_custom_endpoint_returns_400_without_saving(api, env, endpoint):
    config = env.store.read()["ai"]
    config.pop("formatting")
    config.update(provider="custom", endpoint=endpoint, key="test-key")
    response = api.put("/api/settings", headers=AUTH, json=config)
    assert response.status_code == 400
    assert env.store.read()["ai"]["provider"] == "groq"


def test_qr_viewer_expires_and_does_not_accept_admin_token(api, env):
    qr = env.engine.qr
    qr.key, qr.until, qr.phase = "q" * 43, env.clock[0] + 30, "qr"
    qr.expires = env.clock[0] + 20
    assert api.post("/qr/state", headers=AUTH).status_code == 401
    response = api.post("/qr/state", headers={"Authorization": "Bearer " + qr.key})
    assert response.status_code == 200
    assert "viewer_path" not in response.json() and "phone_last4" not in response.json()
    env.clock[0] += 31
    assert api.post("/qr/state", headers={"Authorization": "Bearer " + qr.key}).status_code == 401


@pytest.mark.parametrize("provider", ["groq", "gemini", "custom", "gigachat"])
@pytest.mark.asyncio
async def test_ai_provider_requests_and_response_parsing(provider):
    requests = []
    async def handle(request):
        requests.append(request)
        if request.url.path.endswith("oauth"):
            return httpx.Response(200, json={"access_token": "temporary", "expires_at": 9999999999999})
        if provider == "gemini":
            assert request.headers["x-goog-api-key"] == "test-key"
            return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": " Ответ "}]}}]})
        assert request.headers["authorization"] == "Bearer " + ("temporary" if provider == "gigachat" else "test-key")
        return httpx.Response(200, json={"choices": [{"message": {"content": " Ответ "}}]})
    ai = AiClient(transport=httpx.MockTransport(handle))
    config = {**initial()["ai"], "provider": provider, "key": "test-key", "endpoint": "https://example.invalid/chat", "model": "test-model"}
    try:
        assert await ai.answer(config, "question") == "Ответ"
        assert await ai.answer(config, "question") == "Ответ"
        assert sum(r.url.path.endswith("oauth") for r in requests) == (1 if provider == "gigachat" else 0)
    finally:
        await ai.close()


@pytest.mark.parametrize("status", [401, 429, 500])
@pytest.mark.asyncio
async def test_upstream_errors_do_not_expose_response_body(status):
    ai = AiClient(transport=httpx.MockTransport(lambda r: httpx.Response(status, text="private-upstream-secret")))
    try:
        with pytest.raises(AiError) as caught:
            await ai.answer({**initial()["ai"], "key": "test-key"}, "question")
        assert "private-upstream-secret" not in str(caught.value)
    finally:
        await ai.close()


@pytest.mark.asyncio
async def test_voice_transcription_is_bounded_and_parsed():
    http = httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(200, json={"text": " Привет "})))
    voice = VoiceClient(http)
    try:
        assert await voice.transcribe(initial()["voice"], "test-key", b"fake-audio", "audio/ogg") == "Привет"
        with pytest.raises(VoiceError):
            await voice.transcribe(initial()["voice"], "test-key", b"", "audio/ogg")
    finally:
        await voice.close()


def test_bundled_ca_is_a_valid_trust_store():
    ssl.create_default_context(cafile=str(Path(__file__).resolve().parents[1] / "server/certs/gigachat.pem"))
