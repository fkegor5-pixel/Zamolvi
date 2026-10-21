"""Encrypted persistent state; an OS lock prevents duplicate Telegram workers."""
import copy
import fcntl
import json
import os
import sqlite3
import threading
import time
from . import stats, voice, quiet, archive, memory, media, approval
from pathlib import Path

from cryptography.fernet import Fernet

PROMPT = (
    "Ты AI-автоответчик личного аккаунта. Отвечай на языке собеседника, "
    "понятно и по существу. Не выдавай себя за владельца аккаунта. "
    "Не утверждай, что выполнил действия, которых не выполнял. "
    "Если не знаешь ответа, честно скажи об этом. Ответ — не более 1200 символов."
)


def initial():
    return {
        "version": 2, "enabled": False, "enabled_at": 0, "revision": 0,
        "ai": {"provider": "groq", "key": "", "model": "openai/gpt-oss-20b",
               "endpoint": "", "prompt": PROMPT, "cooldown": 15, "formatting": "auto"},
        "telegram": {}, "session": {}, "selected": {}, "queue": [],
        "seen": [], "history": {}, "last_sent": {}, "pause_until": 0,
        "sent": 0, "logs": [], "behavior": quiet.initial(), "voice": voice.initial(),
        "voice_pause_until": 0, "transcripts": [], "stats": stats.initial(time.time()),
        "message_archive": archive.initial(),
        # 2.9
        "memory": memory.initial(), "media": media.initial(), "approval": approval.initial(),
        "pricing": {"currency": "", "models": {}},
    }


class Store:
    def __init__(self, directory, key):
        self.cipher = Fernet(key.encode() if isinstance(key, str) else key)
        self.path = Path(directory)
        self.path.mkdir(parents=True, exist_ok=True, mode=0o700)
        os.chmod(self.path, 0o700)
        self._lock = (self.path / "worker.lock").open("a")
        try:
            fcntl.flock(self._lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            self._lock.close()
            raise RuntimeError("Этот каталог уже используется другим процессом Zamolvi.") from None
        self.mutex = threading.RLock()
        self.db = sqlite3.connect(self.path / "state.sqlite3", check_same_thread=False)
        try:
            self.db.execute("PRAGMA journal_mode=WAL")
            self.db.execute("PRAGMA synchronous=FULL")
            self.db.execute("CREATE TABLE IF NOT EXISTS state (id INTEGER PRIMARY KEY, data BLOB NOT NULL)")
            row = self.db.execute("SELECT data FROM state WHERE id=1").fetchone()
            # Wrong master key or damaged data must fail startup, never erase a session.
            self.state = json.loads(self.cipher.decrypt(row[0])) if row else initial()
            if self.state.get("version") != 2:
                raise RuntimeError("Неподдерживаемая версия данных сервера.")
            def migrate(s):
                s['ai'].setdefault('formatting', 'auto')
                fresh = initial()
                for key in ('behavior', 'voice', 'voice_pause_until', 'transcripts', 'stats', 'message_archive',
                            'memory', 'media', 'approval', 'pricing'):
                    s.setdefault(key, fresh[key])
                for key in ('memory', 'media', 'approval'):
                    for field, value in fresh[key].items():
                        s[key].setdefault(field, value)
                for field in ('text_model', 'document_model'):  # per-type models of 2.9.6, removed
                    s['media'].pop(field, None)
                stats.ensure(s, time.time())
            self.update(migrate)
        except BaseException:
            self.close()
            raise

    def read(self):
        with self.mutex:
            return copy.deepcopy(self.state)

    def update(self, edit):
        with self.mutex:
            candidate = copy.deepcopy(self.state)
            result = edit(candidate)
            data = self.cipher.encrypt(json.dumps(candidate, ensure_ascii=False).encode())
            with self.db:
                self.db.execute("INSERT INTO state VALUES (1, ?) ON CONFLICT(id) DO UPDATE SET data=excluded.data", (data,))
            self.state = candidate
            return result

    def close(self):
        if getattr(self, "db", None):
            self.db.close()
            self.db = None
        if not self._lock.closed:
            self._lock.close()
