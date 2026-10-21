import os
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest
from cryptography.fernet import Fernet
from telethon import functions, types

sys.path.insert(0, os.environ.get("REPLYAI_TEST_SERVER", str(Path(__file__).resolve().parents[1] / "server")))
from replyai.engine import Engine
from replyai.store import Store


class FakeAI:
    def __init__(self):
        self.calls = []
        self.error = None
        self.callback = None

    async def answer(self, config, text, history=()):
        self.calls.append((text, history))
        if self.error:
            raise self.error
        if self.callback:
            self.callback()
        return "Тестовый ответ"

    async def close(self):
        pass


class FakeVoice:
    async def close(self):
        pass


class FakeTelegram:
    def __init__(self, session=None, *args, **kwargs):
        self.session = session or SimpleNamespace(save=lambda: None)
        self.connected = True
        self.sent = []
        self.send_error = None
        self.source_error = None
        self.source_text = "Привет"

    def is_connected(self):
        return self.connected

    async def connect(self):
        self.connected = True

    async def disconnect(self):
        self.connected = False

    async def is_user_authorized(self):
        return False

    async def log_out(self):
        pass

    def add_event_handler(self, *args):
        pass

    def remove_event_handler(self, *args):
        pass

    async def get_input_entity(self, user):
        return types.InputPeerUser(user, 0)

    async def get_messages(self, peer, ids):
        if self.source_error:
            raise self.source_error
        def message(i):
            return SimpleNamespace(id=i, out=False, raw_text=self.source_text)
        return [message(i) for i in ids] if isinstance(ids, list) else message(ids)

    async def __call__(self, request):
        if isinstance(request, functions.messages.SendMessageRequest):
            if self.send_error:
                raise self.send_error
            self.sent.append(request)
            return None
        if isinstance(request, functions.auth.SendCodeRequest):
            return types.auth.SentCode(types.auth.SentCodeTypeApp(5), "test-hash")
        raise AssertionError(type(request).__name__)


@pytest.fixture
def env(tmp_path):
    key = Fernet.generate_key()
    store = Store(tmp_path / "data", key)
    clock = [1792314000.0]
    ai = FakeAI()
    engine = Engine(store, ai=ai, voice=FakeVoice(), client_factory=FakeTelegram, clock=lambda: clock[0])
    engine.client = FakeTelegram()
    engine.authorized = True
    yield SimpleNamespace(store=store, engine=engine, clock=clock, ai=ai, key=key)
    store.close()


@pytest.fixture
def queued(env):
    def setup(state):
        state.update(enabled=True, enabled_at=env.clock[0] - 10,
                     selected={"123": {"since": env.clock[0] - 10}})
        state["behavior"]["batch_seconds"] = 0
        state["ai"].update(key="fake-ai-key", cooldown=0)
    env.store.update(setup)
    assert env.engine.enqueue("123", 1, "Привет", env.clock[0])
    return env
