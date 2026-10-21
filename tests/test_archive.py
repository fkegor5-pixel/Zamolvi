import datetime as dt
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from fastapi.testclient import TestClient
from telethon import events, types

from replyai import archive
from replyai.errors import UserError
from replyai.store import Store
from replyai.web import create_app

TOKEN = 'archive-test-' + 'x' * 40
AUTH = {'Authorization': 'Bearer ' + TOKEN}


def enable(env):
    env.store.update(lambda s: s.update(selected={'123': {'since': env.clock[0] - 1, 'name': 'Анна'}}))
    env.store.update(lambda s: archive.configure(s, True, env.clock[0]))


def event(env, message_id=1, chat=123, text='Секретный текст', outgoing=False, private=True, **media):
    return SimpleNamespace(is_private=private, out=outgoing, raw_text=text, chat_id=chat,
                           id=message_id, date=dt.datetime.fromtimestamp(env.clock[0], dt.timezone.utc),
                           get_sender=AsyncMock(return_value=types.User(123, first_name='Анна')),
                           message=SimpleNamespace(**media))


@pytest.mark.asyncio
async def test_incoming_delete_without_chat_is_saved_while_autoanswer_off(env):
    enable(env)
    await env.engine.on_message(event(env))
    assert env.store.read()['queue'] == []
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    result = env.engine.archive_report()
    assert result['cached_count'] == 0
    assert result['total'] == 1
    assert result['messages'][0]['text'] == 'Секретный текст'
    assert result['messages'][0]['chat_name'] == 'Анна'
    assert not result['messages'][0]['outgoing']


@pytest.mark.asyncio
async def test_outgoing_and_caption_and_last_edit_are_kept(env):
    enable(env)
    source = event(env, outgoing=True, photo=True)
    await env.engine.on_message(source)
    source.raw_text = 'Исправленная подпись'
    await env.engine.on_edited(source)
    await env.engine.on_deleted(SimpleNamespace(chat_id=123, deleted_ids=[1]))
    row = env.engine.archive_report()['messages'][0]
    assert row['text'] == 'Исправленная подпись'
    assert row['media'] == 'photo'
    assert row['outgoing'] and row['edited']


@pytest.mark.parametrize('reason', ['disabled', 'unselected', 'old', 'group', 'bot'])
@pytest.mark.asyncio
async def test_messages_outside_enabled_selected_scope_are_not_copied(env, reason):
    enable(env)
    source = event(env)
    if reason == 'disabled':
        env.store.update(lambda s: archive.configure(s, False, env.clock[0]))
    elif reason == 'unselected':
        source.chat_id = 999
    elif reason == 'old':
        source.date -= dt.timedelta(seconds=5)
    elif reason == 'group':
        source.is_private = False
    elif reason == 'bot':
        source.get_sender = AsyncMock(return_value=types.User(123, bot=True))
    await env.engine.on_message(source)
    assert env.store.read()['message_archive']['cached'] == []


@pytest.mark.asyncio
async def test_edit_does_not_import_an_unseen_message(env):
    enable(env)
    await env.engine.on_edited(event(env))
    assert env.store.read()['message_archive']['cached'] == []


@pytest.mark.asyncio
async def test_deletion_is_idempotent_and_channel_namespace_is_ignored(env):
    enable(env)
    await env.engine.on_message(event(env))
    await env.engine.on_deleted(SimpleNamespace(chat_id=-100999, deleted_ids=[1]))
    assert len(env.store.read()['message_archive']['cached']) == 1
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    first = env.engine.archive_report()['messages'][0]
    env.clock[0] += 10
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    assert env.engine.archive_report()['messages'] == [first]


@pytest.mark.asyncio
async def test_other_private_chat_deletion_does_not_match(env):
    enable(env)
    await env.engine.on_message(event(env))
    await env.engine.on_deleted(SimpleNamespace(chat_id=999, deleted_ids=[1]))
    assert env.engine.archive_report()['total'] == 0
    assert env.engine.archive_report()['cached_count'] == 1


@pytest.mark.asyncio
async def test_delete_cancels_a_queued_ai_reply(queued):
    await queued.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    await queued.engine.process_one()
    assert queued.store.read()['queue'] == []
    assert queued.engine.client.sent == []
    assert queued.ai.calls == []


@pytest.mark.asyncio
async def test_delete_during_ai_generation_prevents_send(queued):
    # Exercise the race after AI starts and before its answer is sent.
    def remove():
        queued.store.update(lambda s: archive.deleted(s, [1], None, queued.clock[0]))
    queued.ai.callback = remove
    await queued.engine.process_one()
    assert queued.engine.client.sent == []
    assert queued.store.read()['queue'] == []


def test_archive_limits_retention_search_and_pagination(env):
    enable(env)
    def populate(s):
        for i in range(1, 505):
            archive.remember(s, '123', i, 'Текст ' + str(i), '', False, env.clock[0], env.clock[0])
        assert len(s['message_archive']['cached']) == archive.MAX_CACHED
        archive.deleted(s, list(range(1, 505)), None, env.clock[0])
        for i in range(505, 520):
            archive.remember(s, '123', i, 'Текст ' + str(i), '', False, env.clock[0], env.clock[0])
            archive.deleted(s, [i], None, env.clock[0])
    env.store.update(populate)
    result = env.engine.archive_report(limit=5)
    assert result['total'] == 500 and len(result['messages']) == 5 and result['has_more']
    assert result['messages'][0]['message_id'] == 519
    assert env.engine.archive_report(offset=5, limit=5)['messages'][0]['message_id'] == 514
    assert env.engine.archive_report(query='тЕкСт 519')['filtered_total'] == 1
    assert env.engine.archive_report(query='АННА')['filtered_total'] == 500
    env.clock[0] += 30 * 86400
    assert env.engine.archive_report()['total'] == 0
    assert env.store.read()['message_archive']['deleted'] == []


def test_full_telegram_text_is_preserved_and_oversized_input_is_bounded(env):
    enable(env)
    def populate(s):
        for i, size in enumerate((4096, 10000), 1):
            archive.remember(s, '123', i, 'я' * size, '', False, env.clock[0], env.clock[0])
        archive.deleted(s, [1, 2], None, env.clock[0])
    env.store.update(populate)
    rows = env.engine.archive_report()['messages']
    assert all(len(row['text']) == 4096 for row in rows)
    assert [row['truncated'] for row in rows] == [True, False]


@pytest.mark.asyncio
async def test_disable_keeps_deleted_but_discards_staging(env):
    enable(env)
    await env.engine.on_message(event(env, 1))
    await env.engine.on_message(event(env, 2))
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    env.store.update(lambda s: archive.configure(s, False, env.clock[0]))
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[2]))
    result = env.engine.archive_report()
    assert result['total'] == 1 and result['cached_count'] == 0 and not result['enabled']


@pytest.mark.asyncio
async def test_logout_removes_archive(env):
    enable(env)
    await env.engine.on_message(event(env))
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    await env.engine.logout()
    assert env.store.read()['message_archive'] == archive.initial()


@pytest.mark.asyncio
async def test_deselection_and_ai_off_purge_chat_copies(env):
    enable(env)
    await env.engine.on_message(event(env))
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    await env.engine.on_message(event(env, 2, text='/ai_off'))
    assert env.engine.archive_report()['total'] == 0
    assert not env.engine.archive_report()['enabled']
    enable(env)
    await env.engine.on_message(event(env, 3))
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[3]))
    env.engine.chats = AsyncMock(return_value=[{'id': '123', 'name': 'Анна', 'selected': True}])
    await env.engine.select([], False)
    assert env.engine.archive_report()['total'] == 0


@pytest.mark.parametrize('qr', [False, True])
@pytest.mark.asyncio
async def test_account_switch_removes_archive(env, qr):
    enable(env)
    await env.engine.on_message(event(env))
    credentials = {'api_id': 1, 'api_hash': 'a' * 32, 'phone': '+79990000000'}
    env.store.update(lambda s: s.update(telegram=credentials))
    env.engine.authorized = False
    if qr:
        env.engine.qr.export_locked = AsyncMock(side_effect=UserError('test'))
        with pytest.raises(UserError):
            await env.engine.qr.start(dict(credentials, api_id=2))
    else:
        await env.engine.login_start(2, 'b' * 32, '+79990000001')
    assert env.store.read()['message_archive'] == archive.initial()


@pytest.mark.asyncio
async def test_archive_persists_encrypted_across_restart(env):
    enable(env)
    text = 'unique-private-deleted-message-91581'
    await env.engine.on_message(event(env, text=text))
    await env.engine.on_deleted(SimpleNamespace(chat_id=None, deleted_ids=[1]))
    directory = env.store.path
    env.store.close()
    assert all(text.encode() not in p.read_bytes() for p in directory.glob('state.sqlite3*'))
    reopened = Store(directory, env.key)
    try:
        assert reopened.read()['message_archive']['deleted'][0]['text'] == text
    finally:
        reopened.close()


def test_old_vault_migration_preserves_existing_data(env):
    env.store.update(lambda s: (s.pop('message_archive'), s['ai'].update(key='preserve-key')))
    directory = env.store.path
    env.store.close()
    reopened = Store(directory, env.key)
    try:
        assert reopened.read()['ai']['key'] == 'preserve-key'
        assert reopened.read()['message_archive'] == archive.initial()
    finally:
        reopened.close()


@pytest.fixture
def api(env):
    with TestClient(create_app(env.store, env.engine, token=TOKEN)) as client:
        yield client


@pytest.mark.parametrize('method,path', [('GET', '/api/archive'), ('POST', '/api/archive/list'),
                                         ('PUT', '/api/archive'), ('POST', '/api/archive/clear')])
def test_every_archive_endpoint_requires_owner_token(api, method, path):
    assert api.request(method, path, json={'enabled': True} if method == 'PUT' else {}).status_code == 401


def test_archive_http_configuration_clear_and_validation(api, env):
    assert api.put('/api/archive', headers=AUTH, json={'enabled': True}).status_code == 400
    enable(env)
    env.store.update(lambda s: archive.remember(s, '123', 1, 'private-needle', '', False, env.clock[0], env.clock[0]))
    env.store.update(lambda s: archive.deleted(s, [1], None, env.clock[0]))
    response = api.post('/api/archive/list', headers=AUTH, json={'query': 'needle', 'limit': 1})
    assert response.status_code == 200
    assert response.json()['messages'][0]['text'] == 'private-needle'
    assert api.post('/api/archive/list', headers=AUTH, json={'offset': -1}).status_code == 422
    assert api.post('/api/archive/list', headers=AUTH, json={'limit': 999}).status_code == 422
    assert api.post('/api/archive/list', headers=AUTH, json={'query': 'x' * 81}).status_code == 422
    diagnostics = api.get('/api/diagnostics', headers=AUTH).json()
    assert 'private-needle' not in json.dumps(diagnostics)
    assert api.post('/api/archive/clear', headers=AUTH, json={}).json()['total'] == 0
    assert api.get('/api/archive', headers=AUTH).headers['cache-control'] == 'no-store'


@pytest.mark.asyncio
async def test_telegram_registers_delete_and_edit_handlers(env):
    env.engine.client_factory = lambda *a, **kw: SimpleNamespace(
        add_event_handler=lambda handler, builder: handlers.append(type(builder)),
        connect=AsyncMock(), disconnect=AsyncMock())
    handlers = []
    await env.engine.connect({'api_id': 1, 'api_hash': 'a' * 32})
    assert handlers == [events.NewMessage, events.MessageDeleted, events.MessageEdited]
