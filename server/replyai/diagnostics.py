"""Bounded diagnostics made from explicit fields, never messages, bodies or secrets."""
from collections import deque
from contextvars import ContextVar
import datetime as dt
from importlib import metadata
import os
from pathlib import Path
import platform
import re
import resource
import shutil
import time
import traceback
import uuid
from . import quiet

REQUEST_ID = ContextVar('replyai_request_id', default='')
ROUTES = {'/api/status', '/api/settings', '/api/control', '/api/telegram/start',
          '/api/telegram/code', '/api/telegram/password', '/api/telegram/resend',
          '/api/telegram/logout', '/api/telegram/qr', '/api/telegram/qr/start', '/api/telegram/qr/cancel',
          '/api/chats', '/api/test', '/api/clear', '/api/diagnostics', '/api/formatting', '/api/behavior', '/api/voice', '/api/voice/clear', '/api/stats',
          '/api/archive', '/api/archive/list', '/api/archive/clear',
          '/api/memory', '/api/memory/forget', '/api/media', '/api/approval', '/api/approval/decide',
          '/api/stats/extended', '/api/pricing', '/api/features', '/api/notification/answer', '/api/style',
          '/api/memory/import', '/api/memory/import/cancel', '/api/commands', '/api/commands/preview'}
TEXT_FIELDS = {'kind', 'operation', 'error_type', 'provider', 'delivery', 'stage', 'request_id'}
NUMBER_FIELDS = {'http_status', 'duration_ms', 'retry_after_s', 'resend_after_s'}
FILES = {'engine.py', 'web.py', 'ai.py', 'store.py', '__main__.py', 'diagnostics.py', 'qr.py', 'formatting.py', 'voice.py', 'stats.py', 'queueing.py', 'quiet.py',
         'archive.py', 'memory.py', 'media.py', 'approval.py', 'privacy.py', 'custom.py'}


def identifier(value, limit=80):
    value = str(value)
    return value if len(value) <= limit and re.fullmatch(r'[A-Za-z0-9_./:-]+', value) else 'other'


def safe_frames(exc):
    frames = []
    for frame, line in traceback.walk_tb(exc.__traceback__):
        path = Path(frame.f_code.co_filename)
        if path.name in FILES and path.parent.name == 'replyai':
            frames.append({'file': path.name, 'function': identifier(frame.f_code.co_name), 'line': int(line)})
    return frames[-8:]


class Diagnostics:
    def __init__(self, store, engine):
        self.store, self.engine = store, engine
        self.started = time.monotonic()
        self.unsaved = deque(maxlen=100)

    def emit(self, kind, operation, exc=None, **fields):
        row = {'at': dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds'),
               'kind': identifier(kind), 'operation': identifier(operation)}
        correlation = REQUEST_ID.get()
        if correlation:
            row['request_id'] = identifier(correlation)
        for key, value in fields.items():
            if key in TEXT_FIELDS:
                row[key] = identifier(value)
            elif key in NUMBER_FIELDS and isinstance(value, (int, float)):
                row[key] = max(0, min(2**31 - 1, int(value)))
        if exc is not None:
            row['error_type'] = identifier(type(exc).__name__)
            row['frames'] = safe_frames(exc)
            causes = []
            cause = exc.__cause__ or exc.__context__
            while cause is not None and len(causes) < 4:
                causes.append(identifier(type(cause).__name__))
                cause = cause.__cause__ or cause.__context__
            row['causes'] = causes
        try:
            self.store.update(lambda s: s.update(diagnostics=(s.get('diagnostics', []) + [row])[-250:]))
        except Exception:
            # Logging must not interrupt replies, login or error handling if disk/DB fails.
            self.unsaved.append(row)

    def _features(self):
        try:
            return self.engine.feature_report()
        except Exception as exc:
            return {'error': type(exc).__name__}

    def report(self):
        state = self.store.read()
        engine = self.engine
        login = engine.login_info()
        ai = state['ai']
        def version(package):
            try:
                return identifier(metadata.version(package))
            except metadata.PackageNotFoundError:
                return 'unknown'
        disk = shutil.disk_usage(self.store.path)
        return {
            'schema': 'replyai.diagnostics.v1',
            'generated_at': dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds'),
            'report_id': uuid.uuid4().hex,
            'runtime': {'server_version': '3.4.2', 'python_version': platform.python_version(),
                        'platform': platform.system(), 'machine': platform.machine(),
                        'telethon_version': version('telethon'), 'httpx_version': version('httpx'),
                        'uptime_s': max(0, int(time.monotonic() - self.started)),
                        'peak_rss_kib': resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
                        'disk_free_bytes': disk.free, 'cpu_count': os.cpu_count() or 0},
            'state': {'authorized': bool(engine.authorized),
                      'connected': bool(engine.client and engine.client.is_connected()),
                      'worker_failed': bool(engine.failed),
                      'worker_alive': bool(engine.worker and not engine.worker.done()),
                      'enabled': bool(state['enabled']),
                      'voice_enabled': bool(state['voice']['enabled']), 'voice_uses_chat_key': bool(state['voice']['use_chat_key']),
                      'batch_seconds': state['behavior']['batch_seconds'], 'quiet_active': quiet.active(state['behavior'], engine.clock()),
                      'stage': identifier(login.get('stage', 'unknown')),
                      'delivery': identifier(login.get('delivery', 'none')),
                      'resend_available': bool(login.get('resend_available')),
                      'resend_after_s': int(login.get('resend_after', 0)),
                      'queued': len(state['queue']), 'selected': len(state['selected']), 'sent': state['sent'],
                      'uncertain': sum(j['state'] == 'uncertain' for j in state['queue']),
                      'ai_provider': ai['provider'] if ai['provider'] in {'groq', 'gemini', 'gigachat', 'custom'} else 'unknown',
                      'ai_key_configured': bool(ai.get('key')), 'ai_endpoint_configured': bool(ai.get('endpoint')),
                      'pause_remaining_s': max(0, int(state['pause_until'] - engine.clock())),
                      'memory_enabled': bool(state['memory']['enabled']),
                      'media_photos': bool(state['media']['photos']), 'media_documents': bool(state['media']['documents']),
                      'approval_mode': identifier(state['approval']['mode']),
                      'awaiting': sum(j['state'] == 'awaiting' for j in state['queue'])},
            # No raw state, chat IDs/names, account/phone, prompts, models, endpoints or log messages.
            'events': (state.get('diagnostics', []) + list(self.unsaved))[-250:],
            # Why features do or do not work, in plain words (curated texts, no message content).
            'features': self._features(),
        }
