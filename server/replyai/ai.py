from .errors import UserError
import asyncio
import base64
import contextlib
import contextvars
import hashlib
import json
import math
import os
import ssl
from pathlib import Path
import uuid
import datetime
import email.utils
import re
import time
from urllib.parse import quote, urlsplit

import httpx
from .formatting import MODES, instruction

DEFAULT_MODELS = {"groq": "openai/gpt-oss-20b", "gemini": "gemini-3.5-flash-lite", "custom": "", "gigachat": "GigaChat"}
# Models that accept images. The chat model is used where it is multimodal itself.
# These are defaults only: the owner can enter any model of the provider in the app.
DEFAULT_VISION_MODELS = {"groq": "meta-llama/llama-4-scout-17b-16e-instruct", "gemini": "", "custom": "",
                         "gigachat": "GigaChat-2-Max"}
PREFIX = "[AI-автоответ] "
GIGA_BASE = "https://api.giga.chat/v1"
MAX_IMAGE_BYTES = 8 * 1024 * 1024

# Purpose and chat of the current request, for statistics only (never sent anywhere).
_USAGE = contextvars.ContextVar("replyai_usage", default=None)


@contextlib.contextmanager
def purpose(name, chat=None):
    token = _USAGE.set({"purpose": name, "chat": chat})
    try:
        yield
    finally:
        _USAGE.reset(token)


_KEY_LIKE = re.compile(r"(?<![A-Za-z0-9])(sk-|gsk_|AIza)[-_A-Za-z0-9]{12,}")


def looks_like_key(value):
    """An API key pasted into a model field: never sent as a model id or shown in errors."""
    return bool(_KEY_LIKE.search(value or ""))


class AiError(Exception):
    def __init__(self, message, retry_after=0, too_large=False):
        super().__init__(message)
        self.retry_after = retry_after
        self.too_large = too_large  # HTTP 413: the request exceeds the model's / plan's size limit


def validate(config):
    if config.get("formatting", "auto") not in MODES:
        raise UserError("Выберите корректный режим оформления ответов.")
    if config["provider"] not in DEFAULT_MODELS:
        raise UserError("Неизвестный провайдер AI.")
    if not config.get("key", "").strip():
        raise UserError("Укажите API-ключ AI.")
    if not re.fullmatch(r"[A-Za-z0-9_./:-]{1,160}", config.get("model", "")):
        raise UserError("Укажите корректный идентификатор модели.")
    if looks_like_key(config["model"]):
        raise UserError("В поле модели вставлен API-ключ. Укажите идентификатор модели, а ключ — в поле ключа.")
    if config["provider"] == "custom":
        endpoint = config.get("endpoint", "")
        try:
            u = urlsplit(endpoint)
            port = u.port
            valid = (u.scheme == "https" and u.hostname and "@" not in u.netloc
                     and not u.query and not u.fragment and (port is None or 1 <= port <= 65535)
                     and bool(httpx.URL(endpoint).host))
        except (ValueError, httpx.InvalidURL):
            valid = False
        if not valid:
            raise UserError("Endpoint должен быть полным HTTPS URL без ключей и параметров.")


def checked(config):
    """validate() for background work: a settings problem is an ordinary, logged AI error there."""
    try:
        validate(config)
    except UserError as exc:
        raise AiError(str(exc)) from None


def vision_model(config, media_config):
    model = (media_config or {}).get("vision_model", "").strip()
    return model or DEFAULT_VISION_MODELS.get(config["provider"]) or config["model"]


def retry_delay(value):
    try:
        delay = float(value)
        return max(1, delay) if math.isfinite(delay) else 60
    except (ValueError, TypeError):
        try:
            date = email.utils.parsedate_to_datetime(value)
            if date.tzinfo is None:
                date = date.replace(tzinfo=datetime.timezone.utc)
            return max(1, date.timestamp() - time.time())
        except (ValueError, TypeError, OverflowError):
            return 60


def usage_of(provider, result):
    """(input_tokens, output_tokens) reported by the provider; zeros if absent."""
    def number(value):
        return int(value) if isinstance(value, (int, float)) and math.isfinite(value) and value >= 0 else 0
    try:
        if provider == "gemini":
            meta = result.get("usageMetadata") or {}
            return (number(meta.get("promptTokenCount", 0)),
                    number(meta.get("candidatesTokenCount", 0)) + number(meta.get("thoughtsTokenCount", 0)))
        meta = result.get("usage") or {}
        return number(meta.get("prompt_tokens", 0)), number(meta.get("completion_tokens", 0))
    except AttributeError:
        return 0, 0


class AiClient:
    def __init__(self, transport=None):
        self.diagnostics = None
        # Called as on_usage(provider, model, purpose, chat, input_tokens, output_tokens, ms, ok).
        self.on_usage = None
        self.answer_lock = asyncio.Lock()
        self.giga_cache = {}
        giga_tls = ssl.create_default_context()
        ca = os.environ.get("REPLYAI_GIGACHAT_CA_FILE", str(Path(__file__).resolve().parent.parent / "certs" / "gigachat.pem"))
        if Path(ca).is_file():
            giga_tls.load_verify_locations(ca)
        self.giga_http = httpx.AsyncClient(timeout=httpx.Timeout(75, connect=15), follow_redirects=False,
                                          trust_env=False, transport=transport, verify=giga_tls)
        self.http = httpx.AsyncClient(timeout=httpx.Timeout(75, connect=15),
                                     follow_redirects=False, trust_env=False, transport=transport)

    async def close(self):
        await self.http.aclose()
        await self.giga_http.aclose()

    def _report(self, provider, model, usage, started, ok):
        if not self.on_usage:
            return
        current = _USAGE.get() or {"purpose": "reply", "chat": None}
        try:
            self.on_usage(provider, model, current["purpose"], current["chat"], usage[0], usage[1],
                          (time.perf_counter() - started) * 1000, ok)
        except Exception as exc:  # statistics must never break a reply
            if self.diagnostics:
                self.diagnostics.emit('ai', 'usage_failed', exc)

    async def _guarded(self, provider, model, coroutine):
        async with self.answer_lock:
            started = time.perf_counter()
            try:
                answer, usage = await asyncio.wait_for(coroutine, timeout=90)
            except asyncio.TimeoutError as exc:
                self._report(provider, model, (0, 0), started, False)
                if self.diagnostics:
                    self.diagnostics.emit('ai', 'timeout', exc, provider=provider)
                raise AiError("AI API не ответил за 90 секунд.", 30) from None
            except AiError:
                self._report(provider, model, (0, 0), started, False)
                raise
            self._report(provider, model, usage, started, True)
            return answer

    async def answer(self, config, text, history=()):
        """A reply to the interlocutor in the configured style."""
        return await self._guarded(config["provider"], config.get("model", ""), self._answer(config, text, history))

    async def complete(self, config, system, text, max_tokens=1024):
        """A plain completion with its own system prompt (memory summaries)."""
        async def run():
            checked(config)
            return await self._chat(config, config["model"], system, [], text[:16000], max_tokens, limit=4000)
        return await self._guarded(config["provider"], config.get("model", ""), run())

    async def describe_image(self, config, media_config, image, mime, task):
        """Text on the image plus a short description, via a vision-capable model."""
        model = vision_model(config, media_config)
        async def run():
            checked(config)
            if not image or len(image) > MAX_IMAGE_BYTES:
                raise AiError("Изображение слишком большое для распознавания.")
            if not re.fullmatch(r"[A-Za-z0-9_./:-]{1,160}", model):
                raise AiError("Укажите корректную модель для изображений.")
            return await self._vision(config, model, image, mime, task)
        return await self._guarded(config["provider"], model, run())

    async def giga_token(self, key):
        fingerprint = hashlib.sha256(key.encode()).hexdigest()
        if self.giga_cache.get("key") == fingerprint and self.giga_cache["until"] > time.time() + 30:
            return self.giga_cache["token"]
        try:
            response = await self.giga_http.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
                headers={"Authorization": "Basic " + key, "RqUID": str(uuid.uuid4()), "Accept": "application/json"},
                data={"scope": "GIGACHAT_API_PERS"})
            if self.diagnostics:
                self.diagnostics.emit('ai', 'oauth', provider='gigachat', http_status=response.status_code)
            if response.status_code == 429:
                raise AiError("Лимит авторизации GigaChat.", retry_delay(response.headers.get("Retry-After")))
            if response.status_code != 200:
                raise AiError(f"Авторизация GigaChat: HTTP {response.status_code}. Проверьте Authorization Key и доступ физлица.")
            data = response.json()
            expires = float(data["expires_at"])
            if expires > 1e12:
                expires /= 1000
            token = data["access_token"]
            if not isinstance(token, str) or not token:
                raise ValueError()
            self.giga_cache = {"key": fingerprint, "until": expires, "token": token}
            return token
        except httpx.HTTPError as exc:
            if self.diagnostics:
                self.diagnostics.emit('ai', 'oauth_network', exc, provider='gigachat')
            raise AiError("Не удалось авторизоваться в GigaChat. Проверьте сеть и сертификаты НУЦ Минцифры на сервере.", 60) from None
        except (KeyError, TypeError, ValueError):
            raise AiError("GigaChat вернул некорректный ответ авторизации.") from None

    async def _answer(self, config, text, history=()):
        validate(config)
        turns = list(history)[-6:]
        while sum(len(t["content"]) for t in turns) > 6000:
            turns.pop(0)
        prompt = config["prompt"] + "\n\n" + instruction(config.get("formatting", "auto"))
        return await self._chat(config, config["model"], prompt, turns, text[:12000], 1536, limit=1600)

    async def _endpoint(self, config):
        provider = config["provider"]
        if provider == "groq":
            return "https://api.groq.com/openai/v1/chat/completions", {"Authorization": "Bearer " + config["key"]}
        if provider == "gigachat":
            return GIGA_BASE + "/chat/completions", {"Authorization": "Bearer " + await self.giga_token(config["key"])}
        return config["endpoint"], {"Authorization": "Bearer " + config["key"]}

    async def _chat(self, config, model, system, turns, text, max_tokens, limit):
        if config["provider"] == "gemini":
            contents = [{"role": "model" if t["role"] == "assistant" else "user", "parts": [{"text": t["content"]}]} for t in turns]
            contents.append({"role": "user", "parts": [{"text": text}]})
            return await self._gemini(config, model, system, contents, max_tokens, limit)
        groq = config["provider"] == "groq"
        url, headers = await self._endpoint(config)
        body = {"model": model, "messages": [{"role": "system", "content": system}] + list(turns) + [{"role": "user", "content": text}],
                "max_completion_tokens" if groq else "max_tokens": max_tokens}
        if groq and model.startswith("openai/gpt-oss"):
            body["reasoning_effort"] = "low"
        return await self._openai(config, url, headers, body, limit)

    async def _vision(self, config, model, image, mime, task):
        provider = config["provider"]
        if provider == "gemini":
            contents = [{"role": "user", "parts": [{"inlineData": {"mimeType": mime, "data": base64.b64encode(image).decode()}},
                                                    {"text": task}]}]
            return await self._gemini(config, model, "Ты распознаёшь изображения для автоответчика.", contents, 1536, 6000)
        url, headers = await self._endpoint(config)
        if provider == "gigachat":
            file_id = await self._giga_upload(headers, image, mime)
            try:
                body = {"model": model, "messages": [{"role": "user", "content": task, "attachments": [file_id]}],
                        "max_tokens": 1536}
                return await self._openai(config, url, headers, body, 6000)
            finally:
                await self._giga_delete(headers, file_id)
        data_url = "data:" + mime + ";base64," + base64.b64encode(image).decode()
        body = {"model": model, "messages": [{"role": "user", "content": [
            {"type": "text", "text": task}, {"type": "image_url", "image_url": {"url": data_url}}]}],
            "max_completion_tokens" if provider == "groq" else "max_tokens": 1536}
        return await self._openai(config, url, headers, body, 6000)

    async def _giga_upload(self, headers, image, mime):
        try:
            response = await self.giga_http.post(GIGA_BASE + "/files", headers=headers,
                                                 files={"file": ("image" + (".png" if mime == "image/png" else ".jpg"), image, mime)},
                                                 data={"purpose": "general"})
        except httpx.HTTPError:
            raise AiError("Ошибка сети при загрузке изображения в GigaChat.", 30) from None
        if response.status_code == 401:
            self.giga_cache = {}
            raise AiError("Срок токена GigaChat истёк; повторите запрос.", 1)
        if response.status_code == 429:
            raise AiError("Лимит GigaChat: запрос отложен.", retry_delay(response.headers.get("Retry-After")))
        if response.status_code != 200:
            raise AiError(f"GigaChat не принял изображение: HTTP {response.status_code}. Проверьте модель для изображений.")
        try:
            file_id = response.json()["id"]
            if not isinstance(file_id, str) or not re.fullmatch(r"[A-Za-z0-9-]{1,80}", file_id):
                raise ValueError()
            return file_id
        except (KeyError, TypeError, ValueError):
            raise AiError("GigaChat вернул некорректный ответ на загрузку файла.") from None

    async def _giga_delete(self, headers, file_id):
        # Uploaded files count against the storage quota; remove them right away.
        with contextlib.suppress(httpx.HTTPError):
            await self.giga_http.post(GIGA_BASE + "/files/" + file_id + "/delete", headers=headers)

    async def _gemini(self, config, model, system, contents, max_tokens, limit):
        url = "https://generativelanguage.googleapis.com/v1beta/models/" + quote(model, safe="") + ":generateContent"
        body = {"systemInstruction": {"parts": [{"text": system}]}, "contents": contents,
                "generationConfig": {"maxOutputTokens": max_tokens}}
        result = await self._post(config, self.http, url, {"x-goog-api-key": config["key"]}, body)
        try:
            parts = result["candidates"][0]["content"]["parts"]
            answer = "".join(p.get("text", "") for p in parts if not p.get("thought"))
        except (KeyError, IndexError, TypeError, AttributeError):
            raise AiError("AI API не вернул текст ответа. Проверьте модель и ограничения провайдера.") from None
        return self._clean(answer, limit), usage_of("gemini", result)

    async def _openai(self, config, url, headers, body, limit):
        client = self.giga_http if config["provider"] == "gigachat" else self.http
        result = await self._post(config, client, url, headers, body)
        error = result.get("error") if isinstance(result, dict) else None
        if error:
            # OpenRouter and some proxies answer HTTP 200 with {"error": {...}} when the upstream model fails.
            info = error if isinstance(error, dict) else {"message": str(error)}
            code = info.get("code")
            message = re.sub(r"\s+", " ", str(info.get("message") or "ошибка провайдера"))[:200]
            if code == 429 or "rate" in message.lower():
                raise AiError("Лимит бесплатной модели у провайдера: " + message, 60)
            if code == 413:
                raise AiError("Запрос слишком большой для модели: " + message, too_large=True)
            raise AiError(f"Провайдер вернул ошибку{' ' + str(code) if code else ''}: {message}", 60)
        try:
            choice = result["choices"][0]
            message = choice["message"]
            answer = message.get("content") or ""
        except (KeyError, IndexError, TypeError, AttributeError):
            raise AiError("AI API не вернул текст ответа. Проверьте модель и ограничения провайдера.") from None
        if isinstance(answer, str) and not answer.strip() and (message.get("reasoning") or choice.get("finish_reason") == "length"):
            raise AiError("Модель потратила весь лимит токенов на рассуждения и не дала ответа. "
                          "Выберите модель без режима рассуждений (например, openai/gpt-oss-120b на Groq).", 60)
        return self._clean(answer, limit), usage_of(config["provider"], result)

    async def _post(self, config, client, url, headers, body):
        try:
            async with client.stream("POST", url, headers=headers, json=body) as response:
                if self.diagnostics:
                    self.diagnostics.emit('ai', 'response', provider=config['provider'], http_status=response.status_code)
                if response.status_code == 401 and config["provider"] == "gigachat":
                    self.giga_cache = {}
                    raise AiError("Срок токена GigaChat истёк; повторите запрос.", 1)
                if response.status_code == 429:
                    raise AiError("Лимит AI API: запрос отложен.", retry_delay(response.headers.get("Retry-After")))
                if response.status_code == 413:
                    raise AiError("Запрос слишком большой для лимита модели или тарифа (HTTP 413).", too_large=True)
                if response.status_code in (408, 500, 502, 503, 504):
                    raise AiError("AI API временно недоступен.", 60)
                if response.status_code != 200:
                    reason = await self._reason(response, config)
                    hint = {400: "Проверьте идентификатор модели и её возможности (например, приём изображений).",
                            404: "Модель не найдена или не поддерживает этот тип запроса (например, изображения)."}.get(
                        response.status_code, "Проверьте ключ, модель и доступность сервиса в регионе.")
                    raise AiError(f"AI API вернул HTTP {response.status_code} (модель {_KEY_LIKE.sub('***', body.get('model') or '—')}). "
                                  + (f"Ответ провайдера: {reason}. " if reason else "") + hint)
                data = bytearray()
                async for chunk in response.aiter_bytes():
                    data.extend(chunk)
                    if len(data) > 2_000_000:
                        raise AiError("AI API вернул слишком большой ответ.")
            result = json.loads(data)
            if not isinstance(result, dict):
                raise ValueError()
            return result
        except httpx.HTTPError as exc:
            if self.diagnostics:
                self.diagnostics.emit('ai', 'network', exc, provider=config['provider'])
            raise AiError("Ошибка сети при обращении к AI API.", 30) from None
        except (TypeError, ValueError):
            raise AiError("AI API не вернул текст ответа. Проверьте модель и ограничения провайдера.") from None

    @staticmethod
    async def _reason(response, config):
        """Short provider error message from a failed response (never the request or the key)."""
        try:
            data = bytearray()
            async for chunk in response.aiter_bytes():
                data.extend(chunk)
                if len(data) > 8000:
                    break
            payload = json.loads(bytes(data[:8000]))
            error = payload.get("error", payload) if isinstance(payload, dict) else payload
            message = error.get("message") if isinstance(error, dict) else error
            if not isinstance(message, str):
                return ""
        except Exception:
            return ""
        message = re.sub(r"\s+", " ", message).strip()
        key = config.get("key") or ""
        if key and len(key) > 6:
            message = message.replace(key, "***")
        message = _KEY_LIKE.sub("***", message)
        return message[:200]

    @staticmethod
    def _clean(answer, limit):
        if not isinstance(answer, str):
            raise AiError("AI API вернул неподдерживаемый формат ответа.")
        answer = answer.strip()
        if not answer:
            raise AiError("AI API вернул пустой ответ.")
        return answer[:limit]
