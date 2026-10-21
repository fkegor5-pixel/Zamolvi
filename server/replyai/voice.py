"""Groq speech recognition using bounded in-memory audio and curated errors."""
import asyncio
import io
import math
import httpx
from .errors import UserError

MAX_BYTES = 20 * 1024 * 1024
MAX_TRANSCRIPT = 12000
MODELS = ('whisper-large-v3-turbo', 'whisper-large-v3')
MIMES = {'audio/ogg': '.ogg', 'audio/opus': '.ogg', 'audio/mpeg': '.mp3', 'audio/mp4': '.m4a',
         'audio/x-m4a': '.m4a', 'audio/wav': '.wav', 'audio/x-wav': '.wav', 'audio/webm': '.webm', 'video/webm': '.webm'}


class VoiceError(UserError):
    def __init__(self, message, retry_after=0):
        super().__init__(message)
        self.retry_after = retry_after


class VoiceCancelled(Exception):
    pass


class LimitedAudio(io.BytesIO):
    def write(self, data):
        if self.tell() + len(data) > MAX_BYTES:
            raise VoiceError('Голосовое больше 20 МБ.')
        return super().write(data)


def initial():
    return {'enabled': True, 'use_chat_key': True, 'key': '', 'model': MODELS[0], 'language': '', 'max_seconds': 300}


def key_for(config, ai):
    return ai.get('key', '') if config.get('use_chat_key') and ai.get('provider') == 'groq' else ('' if config.get('use_chat_key') else config.get('key', ''))


def describe(message):
    document = getattr(message, 'voice', None)
    if not document:
        return None
    seconds = next((getattr(a, 'duration', 0) for a in document.attributes if getattr(a, 'voice', False)), 0)
    size, mime = getattr(document, 'size', 0), getattr(document, 'mime_type', '')
    if not isinstance(seconds, (int, float)) or not math.isfinite(seconds) or seconds <= 0:
        raise VoiceError('Не удалось определить длительность голосового.')
    if not isinstance(size, int) or size <= 0 or size > MAX_BYTES:
        raise VoiceError('Недопустимый размер голосового; максимум 20 МБ.')
    if mime not in MIMES:
        raise VoiceError('Этот аудиоформат не поддерживается распознаванием.')
    return {'document_id': str(document.id), 'seconds': seconds, 'size': size, 'mime': mime}


class VoiceClient:
    def __init__(self, client=None):
        self.client = client or httpx.AsyncClient(timeout=httpx.Timeout(90, connect=15), trust_env=False, follow_redirects=False)
        self.diagnostics = None

    async def close(self):
        await self.client.aclose()

    async def transcribe(self, config, key, audio, mime):
        if not key or config['model'] not in MODELS:
            raise VoiceError('Проверьте ключ Groq и модель распознавания.')
        if not audio or len(audio) > MAX_BYTES or mime not in MIMES:
            raise VoiceError('Пустое или слишком большое голосовое сообщение.')
        data = {'model': config['model'], 'response_format': 'json', 'temperature': '0'}
        if config.get('language'):
            data['language'] = config['language']
        try:
            async with asyncio.timeout(105):
                async with self.client.stream('POST', 'https://api.groq.com/openai/v1/audio/transcriptions',
                    headers={'Authorization': 'Bearer ' + key}, data=data,
                    files={'file': ('voice' + MIMES[mime], audio, mime)}) as response:
                    raw = bytearray()
                    async for chunk in response.aiter_bytes():
                        raw.extend(chunk)
                        if len(raw) > 200000:
                            raise VoiceError('Распознавание вернуло слишком большой ответ.')
                    if response.status_code == 429:
                        try: retry = max(1, min(3600, int(response.headers.get('Retry-After', '30'))))
                        except ValueError: retry = 30
                        raise VoiceError('Groq временно ограничил распознавание; запрос будет повторён.', retry)
                    if response.status_code in (408, 425) or response.status_code >= 500:
                        raise VoiceError('Сервис распознавания временно недоступен.', 30)
                    if response.status_code != 200:
                        raise VoiceError('Groq отклонил голосовое. Проверьте ключ, квоту и формат аудио.')
                    import json
                    try: text = json.loads(raw).get('text')
                    except (ValueError, AttributeError): text = None
                    if not isinstance(text, str) or not text.strip():
                        raise VoiceError('Не удалось распознать речь в голосовом.')
                    return text.strip()[:MAX_TRANSCRIPT]
        except (httpx.HTTPError, asyncio.TimeoutError):
            raise VoiceError('Нет связи с сервисом распознавания.', 30) from None
