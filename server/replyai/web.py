import asyncio
import hmac
import os
import time
import uuid
import re
from secrets import token_urlsafe as secrets_token
from collections import defaultdict, deque
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, HTMLResponse
from pydantic import BaseModel, ConfigDict, Field
from telethon import errors

from .errors import UserError
from .ai import AiError, purpose as ai_purpose
from .engine import Engine
from .store import Store
from .diagnostics import REQUEST_ID, ROUTES
from .viewer import PAGE
from .formatting import preview
from . import stats, archive


class Input(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Login(Input):
    api_id: int = Field(gt=0)
    api_hash: str = Field(pattern=r"^[a-fA-F0-9]{32}$", repr=False)
    phone: str = Field(pattern=r"^\+[0-9]{7,15}$", repr=False)


class Code(Input):
    code: str = Field(min_length=1, max_length=128, pattern=r"^[^\x00-\x1f\x7f]+$", repr=False)


class Password(Input):
    password: str = Field(min_length=1, max_length=256, repr=False)


class Settings(Input):
    provider: str = Field(pattern=r"^(groq|gemini|custom|gigachat)$")
    key: str = Field(default="", max_length=2048, repr=False)
    model: str = Field(min_length=1, max_length=160)
    endpoint: str = Field(default="", max_length=2048)
    prompt: str = Field(min_length=1, max_length=4000)
    cooldown: int = Field(default=15, ge=0, le=300)


class Selection(Input):
    ids: list[str] = Field(max_length=200)
    consent: bool


class Formatting(Input):
    mode: str = Field(pattern=r"^(auto|plain|bold|underline|mono)$")


class Behavior(Input):
    batch_seconds: int | None = Field(default=None, ge=0, le=30)
    quiet_enabled: bool | None = None
    quiet_start: str | None = Field(default=None, max_length=5)
    quiet_end: str | None = Field(default=None, max_length=5)
    quiet_zone: str | None = Field(default=None, min_length=1, max_length=80)


class VoiceSettings(Input):
    enabled: bool = True
    use_chat_key: bool = True
    key: str = Field(default='', max_length=2048, repr=False)
    model: str = Field(default='whisper-large-v3-turbo', pattern=r'^whisper-large-v3(-turbo)?$')
    language: str = Field(default='', pattern=r'^(|[a-z]{2})$')
    max_seconds: int = Field(default=300, ge=30, le=600)


class Control(Input):
    enabled: bool


class ArchiveQuery(Input):
    offset: int = Field(default=0, ge=0, le=10000)
    limit: int = Field(default=20, ge=1, le=50)
    query: str = Field(default='', max_length=80)


class Test(Input):
    text: str = Field(min_length=1, max_length=4000)


class MemorySettings(Input):
    enabled: bool


class MemoryImport(Input):
    chat: str = Field(pattern=r'^[0-9]{1,20}$')
    limit: int = Field(default=5000, ge=100, le=20000)
    consent: bool = False


class ImportCancel(Input):
    chat: str = Field(pattern=r'^[0-9]{1,20}$')


class Forget(Input):
    # Empty: forget every chat.
    chat: str = Field(default='', pattern=r'^(|[0-9]{1,20})$')


class MediaSettings(Input):
    photos: bool = False
    documents: bool = False
    vision_model: str = Field(default='', max_length=160, pattern=r'^[A-Za-z0-9_./:-]{0,160}$')
    max_mb: int = Field(default=10, ge=1, le=20)
    pdf_pages: int = Field(default=20, ge=1, le=50)
    scan_pages: int = Field(default=3, ge=0, le=5)


class ApprovalSettings(Input):
    mode: str = Field(pattern=r'^(off|all|chats)$')
    chats: list[str] = Field(default=[], max_length=200)
    notify: bool = True


class Decision(Input):
    id: str = Field(min_length=3, max_length=60, pattern=r'^[0-9]{1,20}:[0-9]{1,20}$')
    action: str = Field(pattern=r'^(send|reject)$')
    text: str = Field(default='', max_length=1600)


class StatsPeriod(Input):
    # 1 = today, 7 or 30 days, 0 = all time
    period: int = Field(default=7)


class ModelPrice(Input):
    input: float = Field(default=0, ge=0, le=1_000_000)
    output: float = Field(default=0, ge=0, le=1_000_000)
    audio_hour: float = Field(default=0, ge=0, le=1_000_000)


class Pricing(Input):
    currency: str = Field(default='', max_length=4)
    models: dict[str, ModelPrice] = Field(default={}, max_length=40)


def create_app(store=None, engine=None, token=None):
    token = token or os.environ.get("REPLYAI_ADMIN_TOKEN", "")
    if len(token) < 32 or len(token) > 256 or not token.isascii() or any(c.isspace() for c in token):
        raise RuntimeError("Задайте случайный REPLYAI_ADMIN_TOKEN длиной от 32 до 256 ASCII-символов без пробелов.")
    owned = store is None
    if owned:
        store = Store(os.environ.get("REPLYAI_DATA_DIR", "data"), os.environ.get("REPLYAI_MASTER_KEY", ""))
    engine = engine or Engine(store)

    @asynccontextmanager
    async def lifespan(app):
        try:
            await engine.start()
            yield
        finally:
            await engine.stop()
            if owned:
                store.close()

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.engine = engine
    failures = defaultdict(deque)
    login_attempts = deque()
    test_lock = asyncio.Lock()
    viewer_failures = defaultdict(deque)

    async def authenticate(request: Request):
        ip = request.client.host if request.client else "unknown"
        # Bound memory even under many-source scans; never trust X-Forwarded-For by default.
        now = time.monotonic()
        if len(failures) > 1000:
            failures.clear()
        recent = failures[ip]
        while recent and recent[0] < now - 60:
            recent.popleft()
        if len(recent) >= 10:
            raise HTTPException(429, "Слишком много попыток. Подождите минуту.")
        supplied = request.headers.get("authorization", "")
        if not hmac.compare_digest(supplied.encode(), ("Bearer " + token).encode()):
            recent.append(now)
            raise HTTPException(401, "Неверный ключ управления сервером.")
        request.state.replyai_owner = True

    def record_error(request, exc):
        if getattr(request.state, 'replyai_owner', False) and not getattr(request.state, 'diag_error_recorded', False):
            request.state.diag_error_recorded = True
            engine.diagnostics.emit('api', request.url.path if request.url.path in ROUTES else 'unknown', exc,
                                    request_id=getattr(request.state, 'diag_request_id', 'unknown'),
                                    retry_after_s=getattr(exc, 'seconds', 0))

    async def login_limit():
        now = time.monotonic()
        while login_attempts and login_attempts[0] < now - 60:
            login_attempts.popleft()
        if len(login_attempts) >= 6:
            raise HTTPException(429, "Подождите минуту перед следующей попыткой входа.")
        login_attempts.append(now)

    @app.middleware("http")
    async def limit_body(request, call_next):
        # APIs receive small JSON only. Never echo malformed bodies, secrets or upstream errors.
        if request.method in ("POST", "PUT", "PATCH"):
            body = bytearray()
            async for part in request.stream():
                body.extend(part)
                if len(body) > 65536:
                    return JSONResponse({"detail": "Слишком большой запрос."}, status_code=413)
            request._body = bytes(body)
        request.state.diag_request_id = uuid.uuid4().hex
        context = REQUEST_ID.set(request.state.diag_request_id)
        started = time.monotonic()
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
        except Exception as exc:
            record_error(request, exc)
            status_code = 503
            response = JSONResponse({"detail": "Операция не завершена. Проверьте соединение и журнал сервера."}, status_code=503)
        finally:
            if getattr(request.state, 'replyai_owner', False) and request.url.path in ROUTES and not (request.url.path in ('/api/status', '/api/telegram/qr') and status_code == 200):
                engine.diagnostics.emit('http', request.url.path, http_status=status_code,
                                        duration_ms=(time.monotonic() - started) * 1000)
            REQUEST_ID.reset(context)
        response.headers['X-ReplyAI-Request-ID'] = request.state.diag_request_id
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.exception_handler(RequestValidationError)
    async def invalid(request, exc):
        record_error(request, exc)
        return JSONResponse({"detail": "Некорректные поля запроса. Проверьте введённые значения."}, status_code=422)

    @app.exception_handler(UserError)
    async def bad_value(request, exc):
        record_error(request, exc)
        # Only local, curated messages; never forward library exception text.
        return JSONResponse({"detail": str(exc)}, status_code=400)

    @app.exception_handler(errors.FloodWaitError)
    async def flood(request, exc):
        record_error(request, exc)
        return JSONResponse({"detail": f"Telegram просит подождать {exc.seconds} секунд."}, status_code=429,
                            headers={"Retry-After": str(exc.seconds)})

    @app.exception_handler(errors.RPCError)
    async def telegram_error(request, exc):
        record_error(request, exc)
        names = {"PhoneCodeInvalidError": "Неверный код Telegram.", "PhoneCodeExpiredError": "Код Telegram истёк. Запросите новый.",
                 "PasswordHashInvalidError": "Неверный облачный пароль.", "ApiIdInvalidError": "Неверные api_id или api_hash.",
                 "PhoneNumberInvalidError": "Неверный номер телефона.", "AuthKeyUnregisteredError": "Сеанс Telegram недействителен. Войдите заново."}
        names.update({"PhoneNumberFloodError": "Telegram ограничил запросы кода для этого номера. Повторите вход позже.",
                      "PhoneNumberBannedError": "Telegram запретил вход для этого номера.",
                      "SendCodeUnavailableError": "Telegram не предоставил доступный способ доставки. Не повторяйте запросы подряд.",
                      "ApiIdPublishedFloodError": "Telegram ограничил этот API ID. Используйте собственные api_id и api_hash из my.telegram.org.",
                      "EmailInstallMissingError": "Telegram запросил почту для входа, но она не настроена. Проверьте официальный Telegram."})
        return JSONResponse({"detail": names.get(type(exc).__name__, "Ошибка Telegram: " + type(exc).__name__)}, status_code=400)

    @app.exception_handler(AiError)
    async def ai_error(request, exc):
        record_error(request, exc)
        return JSONResponse({"detail": str(exc)}, status_code=429 if exc.retry_after else 502)

    @app.exception_handler(Exception)
    async def unexpected(request, exc):
        record_error(request, exc)
        # Uvicorn logs only the exception class in the standalone runner (no bodies/tokens).
        return JSONResponse({"detail": "Операция не завершена. Проверьте соединение и журнал сервера."}, status_code=503)

    @app.get("/healthz")
    async def health():
        alive = bool(engine.worker and not engine.worker.done() and not engine.failed)
        return JSONResponse({"ok": alive}, status_code=200 if alive else 503)

    @app.get('/qr')
    async def qr_page():
        nonce = secrets_token()
        return HTMLResponse(PAGE.replace('__NONCE__', nonce), headers={
            'Content-Security-Policy': "default-src 'none'; script-src 'nonce-" + nonce + "'; style-src 'nonce-" + nonce + "'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
            'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY',
            'Permissions-Policy': 'camera=(), microphone=(), geolocation=()'})

    @app.post('/qr/state')
    async def qr_view(request: Request):
        ip = request.client.host if request.client else 'unknown'
        if len(viewer_failures) > 1000:
            viewer_failures.clear()
        recent, now = viewer_failures[ip], time.monotonic()
        while recent and recent[0] < now - 60:
            recent.popleft()
        if len(recent) >= 20:
            raise HTTPException(429, 'Подождите минуту перед повторным открытием.')
        header = request.headers.get('authorization', '')
        key = header[7:] if header.startswith('Bearer ') else ''
        if not re.fullmatch(r'[A-Za-z0-9_-]{43}', key) or not engine.qr.viewer_allowed(key):
            recent.append(now)
            raise HTTPException(401, 'Ссылка QR истекла или вход отменён.')
        return engine.qr.state()

    protected = [Depends(authenticate)]

    @app.get('/api/telegram/qr', dependencies=protected)
    async def qr_state():
        return engine.qr.state(private=True)

    @app.post('/api/telegram/qr/start', dependencies=protected + [Depends(login_limit)])
    async def qr_start(data: Login | None = None):
        return await engine.qr.start(data.model_dump() if data else None)

    @app.post('/api/telegram/qr/cancel', dependencies=protected)
    async def qr_cancel():
        return await engine.qr.cancel()

    @app.get("/api/status", dependencies=protected)
    async def status():
        return engine.status()

    @app.get('/api/diagnostics', dependencies=protected)
    async def diagnostics():
        return engine.diagnostics.report()

    @app.get("/api/settings", dependencies=protected)
    async def settings():
        return engine.public_settings()

    @app.put("/api/settings", dependencies=protected)
    async def put_settings(data: Settings):
        engine.save_settings(data.model_dump())
        return {"message": "Настройки AI сохранены на сервере."}

    @app.post("/api/control", dependencies=protected)
    async def control(data: Control):
        engine.enable() if data.enabled else engine.disable()
        return engine.status()

    @app.post("/api/telegram/start", dependencies=protected + [Depends(login_limit)])
    async def login(data: Login):
        return await engine.login_start(data.api_id, data.api_hash, data.phone)

    @app.post("/api/telegram/code", dependencies=protected + [Depends(login_limit)])
    async def code(data: Code):
        return await engine.login_code(data.code)

    @app.post("/api/telegram/resend", dependencies=protected + [Depends(login_limit)])
    async def resend():
        return await engine.login_resend()

    @app.post("/api/telegram/password", dependencies=protected + [Depends(login_limit)])
    async def password(data: Password):
        return await engine.login_password(data.password)

    @app.post("/api/telegram/logout", dependencies=protected)
    async def logout():
        await engine.logout()
        return {"message": "Аккаунт отключён, серверный сеанс Telegram завершён."}

    @app.get("/api/chats", dependencies=protected)
    async def chats():
        return {"chats": await engine.chats()}

    @app.put("/api/chats", dependencies=protected)
    async def select(data: Selection):
        await engine.select(list(dict.fromkeys(data.ids)), data.consent)
        return {"message": "Выбор чатов сохранён. Новые сообщения будут обрабатываться после включения."}

    @app.post("/api/test", dependencies=protected)
    async def test(data: Test):
        if test_lock.locked():
            raise HTTPException(409, "Тест AI уже выполняется.")
        async with test_lock:
            config = store.read()["ai"]
            with ai_purpose('test'):
                answer = await engine.ai.answer(config, data.text)
            return preview(answer, config.get("formatting", "auto"))

    @app.put("/api/formatting", dependencies=protected)
    async def formatting(data: Formatting):
        engine.save_formatting(data.mode)
        return {"message": "Оформление сохранено. Оно применяется к новым ответам."}

    @app.get('/api/behavior', dependencies=protected)
    async def behavior():
        return store.read()['behavior']

    @app.put('/api/behavior', dependencies=protected)
    async def put_behavior(data: Behavior):
        engine.save_behavior(data.model_dump(exclude_none=True))
        return {'message': 'Настройки автоответчика сохранены.'}

    @app.get('/api/voice', dependencies=protected)
    async def voice():
        return engine.public_voice()

    @app.put('/api/voice', dependencies=protected)
    async def put_voice(data: VoiceSettings):
        engine.save_voice(data.model_dump())
        return {'message': 'Настройки голосовых сохранены.'}

    @app.post('/api/voice/clear', dependencies=protected)
    async def clear_voice():
        store.update(lambda s: s.update(transcripts=[]))
        return {'message': 'Сохранённые расшифровки очищены.'}

    @app.get('/api/stats', dependencies=protected)
    async def statistics():
        return stats.report(store.read(), engine.clock())

    @app.get('/api/archive', dependencies=protected)
    async def message_archive():
        return engine.archive_report()

    @app.post('/api/archive/list', dependencies=protected)
    async def archive_list(data: ArchiveQuery):
        return engine.archive_report(data.offset, data.limit, data.query)

    @app.put('/api/archive', dependencies=protected)
    async def archive_settings(data: Control):
        store.update(lambda s: archive.configure(s, data.enabled, engine.clock()))
        return engine.archive_report()

    @app.post('/api/archive/clear', dependencies=protected)
    async def archive_clear():
        # Clear the staging cache too, so old copies cannot reappear later.
        store.update(lambda s: s['message_archive'].update(cached=[], deleted=[]))
        return engine.archive_report()

    # ------------------------------------------------------------ 2.9

    @app.get('/api/features', dependencies=protected)
    async def features():
        return engine.feature_report()

    @app.get('/api/memory', dependencies=protected)
    async def memory_report():
        return engine.memory_report()

    @app.put('/api/memory', dependencies=protected)
    async def memory_settings(data: MemorySettings):
        return engine.save_memory(data.enabled)

    @app.post('/api/memory/forget', dependencies=protected)
    async def memory_forget(data: Forget):
        return engine.forget_memory(data.chat or None)

    @app.post('/api/memory/import', dependencies=protected)
    async def memory_import(data: MemoryImport):
        return engine.start_import(data.chat, data.limit, data.consent)

    @app.post('/api/memory/import/cancel', dependencies=protected)
    async def memory_import_cancel(data: ImportCancel):
        return engine.cancel_import(data.chat)

    @app.get('/api/media', dependencies=protected)
    async def media_settings():
        return engine.public_media()

    @app.put('/api/media', dependencies=protected)
    async def put_media(data: MediaSettings):
        return engine.save_media(data.model_dump())

    @app.get('/api/approval', dependencies=protected)
    async def approval_report():
        return engine.approval_report()

    @app.put('/api/approval', dependencies=protected)
    async def put_approval(data: ApprovalSettings):
        return engine.save_approval(data.mode, data.chats, data.notify)

    @app.post('/api/approval/decide', dependencies=protected)
    async def decide(data: Decision):
        return engine.decide(data.id, data.action, data.text)

    # POST: the app's API client accepts only plain /api/... paths without query strings.
    @app.post('/api/stats/extended', dependencies=protected)
    async def extended_statistics(data: StatsPeriod):
        if data.period not in (0, 1, 7, 30):
            raise UserError('Период: 1, 7, 30 дней или 0 — за всё время.')
        return engine.extended_stats(data.period)

    @app.get('/api/pricing', dependencies=protected)
    async def pricing():
        return engine.public_pricing()

    @app.put('/api/pricing', dependencies=protected)
    async def put_pricing(data: Pricing):
        return engine.save_pricing(data.model_dump())

    @app.post("/api/clear", dependencies=protected)
    async def clear():
        store.update(lambda s: s.update(logs=[], queue=[j for j in s["queue"] if j["state"] != "uncertain"]))
        return {"message": "Журнал и отметки неопределённых отправок очищены."}

    return app
