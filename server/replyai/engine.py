from .errors import UserError
import asyncio
import copy
import datetime as dt
import math
import secrets
import time
from contextlib import suppress

from telethon import TelegramClient, events, errors, functions, types
from telethon.sessions import StringSession

from .ai import AiClient, AiError, PREFIX, validate, purpose as ai_purpose
from .diagnostics import Diagnostics
from .qr import QrLogin
from .formatting import render, MODES
from . import queueing, stats, quiet, archive, memory, media, approval, privacy, style
from .media import MediaError
import importlib.util

SERVER_VERSION = "3.2.0"
from .voice import VoiceClient, VoiceError, VoiceCancelled, LimitedAudio, MAX_BYTES, MODELS, key_for, describe


class PersistentSession(StringSession):
    """Keep auth key, update cursors and input entities in the encrypted vault."""
    def __init__(self, store):
        saved = store.read()["session"]
        super().__init__(saved.get("string", ""))
        self.store = store
        self._entities = {tuple(r) for r in saved.get("entities", [])}
        for entity, state in saved.get("updates", {}).items():
            self._update_states[int(entity)] = types.updates.State(
                pts=state[0], qts=state[1], date=dt.datetime.fromtimestamp(state[2], dt.timezone.utc),
                seq=state[3], unread_count=state[4])

    def save(self):
        value = {"string": super().save(), "entities": [list(r) for r in sorted(self._entities, key=lambda r: r[0])][-2000:],
                 "updates": {str(k): [v.pts, v.qts, v.date.timestamp(), v.seq, v.unread_count]
                             for k, v in self._update_states.items()}}
        self.store.update(lambda s: s.update(session=value))
        return value["string"]


class Engine:
    def __init__(self, store, ai=None, client_factory=TelegramClient, clock=time.time, voice=None):
        self.store, self.ai, self.client_factory, self.clock = store, ai or AiClient(), client_factory, clock
        self.voice = voice or VoiceClient()
        self.voice_notice_at = 0
        self.client = None
        self.authorized = False
        self.login_stage = "disconnected"
        self.login_until = 0
        self.phone_hash = None
        self.login_delivery = {}
        self.login_next = None
        self.resend_at = 0
        self.login_block_until = 0
        self.account = ""
        self.worker = None
        self.stopping = False
        self.failed = False
        self.heartbeat = self.clock()
        self.telegram_lock = asyncio.Lock()
        self.diagnostics = Diagnostics(store, self)
        if isinstance(self.ai, AiClient):
            self.ai.diagnostics = self.diagnostics
        self.qr = QrLogin(self)
        if hasattr(self.ai, "on_usage"):
            self.ai.on_usage = self.usage_metric
        self.memory_next = 0
        self.drafts_checked_at = 0
        self.draft_notice_at = {}
        self.notice_at = {}
        self.imports = {}         # chat -> progress of a history import (kept in RAM only)
        self.import_tasks = {}

    def log(self, message):
        line = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds") + "  " + message
        self.store.update(lambda s: s.update(logs=([line] + s["logs"])[:60]))

    async def start(self):
        def recover(s):
            archive.prune(s, self.clock())
            for job in s["queue"]:
                if job["state"] == "processing":
                    job["state"] = "queued"
                elif job["state"] == "delivering":
                    # Sending may have succeeded before a crash. Never send an uncertain reply twice.
                    job["state"] = "uncertain"
                    job["text"] = ""
                    job["answer"] = ""
                    job["parts"] = []
            s["queue"] = s["queue"][-200:]
        self.store.update(recover)
        credentials = self.store.read()["telegram"]
        if credentials.get("api_id") and self.store.read()["session"].get("string"):
            try:
                await self.connect(credentials)
                if await self.client.is_user_authorized():
                    await self.signed_in()
            except Exception as exc:
                self.diagnostics.emit('telegram', 'restore', exc)
                self.log("Не удалось восстановить Telegram: " + type(exc).__name__)
                if self.client:
                    await self.client.disconnect()
                    self.client = None
                if self.store.read().get('qr_pending'):
                    self.store.update(lambda s: s.update(session={}, qr_pending=False))
        self.worker = asyncio.create_task(self.run(), name="reply-worker")

    async def stop(self):
        self.stopping = True
        async with self.telegram_lock:
            await self.qr.cancel_locked()
        if self.worker:
            self.worker.cancel()
            with suppress(asyncio.CancelledError):
                await self.worker
        if self.client:
            self.client.session.save()
            await self.client.disconnect()
        await self.ai.close()
        await self.voice.close()

    async def connect(self, credentials):
        if self.client:
            await self.client.disconnect()
        self.authorized = False
        self.client = self.client_factory(
            PersistentSession(self.store), credentials["api_id"], credentials["api_hash"],
            device_model="Zamolvi Server", system_version="Linux", app_version="3.3",
            auto_reconnect=True, connection_retries=5, request_retries=0, flood_sleep_threshold=0,
            catch_up=True, sequential_updates=True)
        self.client.add_event_handler(self.on_message, events.NewMessage())
        self.client.add_event_handler(self.on_deleted, events.MessageDeleted())
        self.client.add_event_handler(self.on_edited, events.MessageEdited())
        await self.client.connect()

    async def signed_in(self):
        me = await self.client.get_me()
        state = self.store.read()
        from_qr = state.get('qr_pending', False)
        if from_qr:
            expected = state['telegram'].get('phone', '').lstrip('+')
            actual = (getattr(me, 'phone', None) or '').lstrip('+')
            if not expected or actual != expected:
                try:
                    await self.client.log_out()
                except Exception as exc:
                    self.diagnostics.emit('telegram', 'qr_revoke_failed', exc)
                raise UserError('QR подтверждён другим аккаунтом. Выберите в Telegram тот номер, который указали в Zamolvi.')
            self.store.update(lambda s: s.update(qr_pending=False))
        self.authorized = True
        self.login_stage = "authorized"
        self.phone_hash = None
        self.login_delivery = {}
        self.login_next = None
        self.account = (me.first_name or "Аккаунт") + (" @" + me.username if me.username else "")
        self.client.session.save()
        self.log("Аккаунт Telegram подключён.")
        if from_qr:
            self.qr.authorized()

    async def login_start(self, api_id, api_hash, phone):
        async with self.telegram_lock:
            if self.authorized:
                raise UserError("Сначала отключите текущий аккаунт.")
            self.check_login_wait()
            credentials = {"api_id": api_id, "api_hash": api_hash, "phone": phone}
            saved = self.store.read()
            same_account = credentials == saved["telegram"]
            # Opening the login form again must not invalidate a pending code or auth key.
            if same_account and self.client and self.login_stage in ("code", "password", "blocked") and self.clock() < self.login_until:
                if not self.client.is_connected():
                    await self.client.connect()
                return self.login_info()
            await self.qr.cancel_locked()
            self.store.update(lambda s: s.update(qr_pending=False))
            self.disable()
            if not same_account:
                if self.client:
                    await self.client.disconnect()
                    self.client = None
                def reset(s):
                    s.update(telegram=credentials, session={}, selected={}, seen=[],
                             queue=[], last_sent={}, history={}, transcripts=[],
                             pause_until=0, voice_pause_until=0)
                    privacy.forget_account(s)
                self.store.update(reset)
            self.login_stage = "sending"
            self.phone_hash = None
            self.login_delivery = {}
            self.login_next = None
            self.login_until = self.clock() + 600
            try:
                if not self.client or not self.client.is_connected():
                    await self.connect(credentials)
                if await self.client.is_user_authorized():
                    await self.signed_in()
                    return self.login_info()
                # Handle every auth.SentCode constructor ourselves. Telethon's convenience
                # helper assumes result.type and cannot expose PaymentRequired/Success.
                for attempt in range(3):
                    try:
                        result = await self.client(functions.auth.SendCodeRequest(phone, api_id, api_hash, types.CodeSettings()))
                        break
                    except errors.AuthRestartError:
                        if attempt == 2:
                            raise
                return await self.accept_sent_code(result)
            except errors.SessionPasswordNeededError:
                self.login_stage = "password"
                return self.login_info()
            except errors.FloodWaitError as exc:
                self.login_stage = "disconnected"
                self.login_block_until = self.clock() + exc.seconds
                raise
            except Exception:
                self.login_stage = "disconnected"
                raise

    def check_login_wait(self):
        remaining = math.ceil(self.login_block_until - self.clock())
        if remaining > 0:
            raise UserError(f"Telegram ограничил запросы входа. Подождите ещё {remaining} секунд.")

    async def accept_sent_code(self, result):
        if isinstance(result, types.auth.SentCodeSuccess):
            await self.signed_in()
            return self.login_info()
        self.phone_hash = getattr(result, "phone_code_hash", None)
        self.login_next = getattr(result, "next_type", None)
        # timeout is the earliest resend time, not proof of delivery or code expiry.
        timeout = getattr(result, "timeout", None)
        self.resend_at = self.clock() + (max(0, int(timeout)) if timeout is not None else 60)
        self.login_until = self.clock() + 600
        kind = type(getattr(result, "type", None)).__name__
        descriptions = {
            "SentCodeTypeApp": ("telegram", "Telegram выбрал служебный чат Telegram с синей галочкой в уже открытых сеансах этого аккаунта."),
            "SentCodeTypeSms": ("sms", "Telegram выбрал SMS на указанный номер телефона."),
            "SentCodeTypeEmailCode": ("email", "Telegram выбрал почту для входа, настроенную в аккаунте."),
            "SentCodeTypeCall": ("call", "Telegram выбрал телефонный звонок: код продиктуют голосом."),
            "SentCodeTypeFlashCall": ("flash_call", "Telegram выбрал короткий звонок: кодом служит номер входящего звонка."),
            "SentCodeTypeMissedCall": ("missed_call", "Telegram выбрал пропущенный звонок: введите последние цифры его номера."),
            "SentCodeTypeFragmentSms": ("fragment", "Telegram выбрал доставку через Fragment. Обычный чат и SMS для этого запроса не выбраны."),
            "SentCodeTypeSmsWord": ("sms_word", "Telegram выбрал SMS со словом. Введите слово целиком."),
            "SentCodeTypeSmsPhrase": ("sms_phrase", "Telegram выбрал SMS с фразой. Введите фразу целиком."),
            "SentCodeTypeSetUpEmailRequired": ("email_setup", "Telegram требует настроить почту для входа. Этот шаг здесь не поддерживается: выполните его в официальном приложении Telegram."),
            "SentCodeTypeFirebaseSms": ("official_app", "Telegram выбрал Firebase SMS, доступный только официальным мобильным клиентам. Zamolvi не может завершить этот способ входа."),
        }
        delivery, message = descriptions.get(kind, ("unsupported", "Telegram вернул неподдерживаемый способ входа. Код не считается доставленным. Используйте официальный Telegram и повторите вход позже."))
        if isinstance(result, types.auth.SentCodePaymentRequired):
            delivery, message = "payment_required", "Telegram запросил оплату авторизации, доступную только в официальном приложении. В Zamolvi этот сценарий не поддерживается."
        length = max(0, min(32, int(getattr(getattr(result, "type", None), "length", 0) or 0)))
        self.login_delivery = {"delivery": delivery, "message": message, "length": length,
                               "input": "text" if delivery in ("sms_word", "sms_phrase") else "number"}
        if length:
            self.login_delivery["message"] += f" Длина кода: {length} цифр."
        blocked = delivery in ("email_setup", "official_app", "unsupported", "payment_required") or not self.phone_hash
        self.login_stage = "blocked" if blocked else "code"
        self.client.session.save()
        self.log("Telegram принял запрос входа. Способ: " + delivery + ". Доставка кода не подтверждена.")
        self.diagnostics.emit('telegram', 'code_delivery', delivery=delivery, stage=self.login_stage,
                              resend_after_s=max(0, self.resend_at - self.clock()))
        return self.login_info()

    def login_info(self):
        stage = self.login_stage
        if stage in ("code", "password", "blocked") and self.clock() > self.login_until:
            return {"stage": "expired", "message": "Время запроса истекло. Начните вход заново.", "resend_available": False}
        if stage == "password":
            return {"stage": stage, "message": "Введите облачный пароль двухэтапной проверки Telegram.", "resend_available": False}
        if stage == "authorized":
            return {"stage": stage, "message": "Telegram подключён.", "resend_available": False}
        remaining = max(0, math.ceil(max(self.resend_at, self.login_block_until) - self.clock()))
        return {"stage": stage, **self.login_delivery,
                "resend_available": bool(self.phone_hash and self.login_next and stage in ("code", "blocked")),
                "resend_after": remaining,
                "phone_last4": self.store.read()["telegram"].get("phone", "")[-4:]}

    async def login_resend(self):
        async with self.telegram_lock:
            self.check_login()
            self.check_login_wait()
            if self.login_stage not in ("code", "blocked"):
                raise UserError("Повторный код сейчас не нужен. Продолжите текущий шаг входа.")
            if not self.login_next:
                raise UserError("Telegram не разрешил следующий способ доставки. Повторная отправка для этого запроса недоступна.")
            remaining = math.ceil(self.resend_at - self.clock())
            if remaining > 0:
                raise UserError(f"Повторный запрос разрешён через {remaining} секунд.")
            phone = self.store.read()["telegram"]["phone"]
            try:
                result = await self.client(functions.auth.ResendCodeRequest(phone, self.phone_hash))
                return await self.accept_sent_code(result)
            except errors.FloodWaitError as exc:
                self.login_block_until = self.clock() + exc.seconds
                raise
            except errors.PhoneCodeExpiredError:
                self.login_until = 0
                raise

    def check_login(self):
        if not self.client or (not self.phone_hash and self.login_stage != "password") or self.clock() > self.login_until:
            raise UserError("Время входа истекло. Запросите новый код.")

    async def login_code(self, code):
        async with self.telegram_lock:
            self.check_login()
            if self.login_stage != "code":
                raise UserError("Telegram ожидает другой шаг входа. Откройте текущее состояние входа.")
            code = code.strip()
            if not code or len(code) > 128 or any(ord(c) < 32 for c in code):
                raise UserError("Проверьте код Telegram.")
            if self.login_delivery.get("input", "number") == "number" and not (code.isascii() and code.isdigit()):
                raise UserError("Этот код должен содержать только цифры.")
            c = self.store.read()["telegram"]
            try:
                await self.client.sign_in(c["phone"], code, phone_code_hash=self.phone_hash)
            except errors.SessionPasswordNeededError:
                self.login_stage = "password"
                return {"stage": "password", "message": "Введите облачный пароль двухэтапной проверки Telegram."}
            await self.signed_in()
            return {"stage": "authorized", "message": "Telegram подключён."}

    async def login_password(self, password):
        async with self.telegram_lock:
            self.check_login()
            if self.login_stage != "password":
                raise UserError("Сначала введите код Telegram.")
            try:
                await self.client.sign_in(password=password)
                await self.signed_in()
            except UserError as exc:
                if self.store.read().get('qr_pending'):
                    self.qr.failed(exc)
                    await self.qr.discard_locked()
                raise
            return {"stage": "authorized", "message": "Telegram подключён."}

    async def logout(self):
        self.disable()
        self.cancel_imports()
        async with self.telegram_lock:
            await self.qr.cancel_locked()
            # If logout fails, keep the session so it can be revoked or retried.
            if self.client and self.authorized:
                await self.client.log_out()
            if self.client:
                await self.client.disconnect()
                self.client = None
            self.authorized = False
            self.login_stage = "disconnected"
            self.account = ""
            self.phone_hash = None
            self.login_delivery = {}
            self.login_next = None
            def reset(s):
                s.update(telegram={}, session={}, selected={}, history={}, queue=[],
                         seen=[], transcripts=[], last_sent={}, qr_pending=False,
                         pause_until=0, voice_pause_until=0)
                privacy.forget_account(s)
            self.store.update(reset)
            self.log("Сеанс Telegram завершён.")

    def public_settings(self):
        c = self.store.read()["ai"]
        c.setdefault("formatting", "auto")
        has_key = bool(c.pop("key"))
        return {**c, "has_key": has_key}

    def save_settings(self, config):
        previous = self.store.read()["ai"]
        config["formatting"] = previous.get("formatting", "auto")
        # A key is retained only for the same provider AND endpoint, never sent to a new host.
        if not config.get("key"):
            if config["provider"] != previous["provider"] or config.get("endpoint", "") != previous["endpoint"]:
                raise UserError("Для другого провайдера или endpoint введите новый ключ.")
            config["key"] = previous["key"]
        validate(config)
        def edit(s):
            s["ai"] = config.copy()
            s["revision"] += 1
            s["queue"] = [j for j in s["queue"] if j["state"] == "uncertain"]
            s["history"] = {}
        self.store.update(edit)

    def save_formatting(self, mode):
        if mode not in MODES:
            raise UserError("Выберите корректный режим оформления ответов.")
        def edit(s):
            s["ai"]["formatting"] = mode
            s["revision"] += 1
            s["queue"] = [j for j in s["queue"] if j["state"] == "uncertain"]
        self.store.update(edit)

    def metric(self, **values):
        self.store.update(lambda s: stats.record(s, self.clock(), **values))

    def error_metric(self, kind, chat=None):
        self.store.update(lambda s: stats.record_error(s, self.clock(), kind, chat))

    def usage_metric(self, provider, model, purpose, chat, input_tokens, output_tokens, ms, ok):
        self.store.update(lambda s: stats.record_usage(s, self.clock(), provider, model, purpose,
                                                       input_tokens, output_tokens, ms, ok=ok, chat=chat))

    def public_voice(self):
        state = self.store.read()
        config = state['voice'].copy()
        config['has_key'] = bool(config.pop('key'))
        config['available'] = bool(key_for(state['voice'], state['ai']))
        config['can_use_chat_key'] = state['ai']['provider'] == 'groq' and bool(state['ai'].get('key'))
        config['transcripts'] = state.get('transcripts', [])
        return config

    def save_voice(self, config):
        state = self.store.read()
        if config['model'] not in MODELS or not 30 <= config['max_seconds'] <= 600:
            raise UserError('Проверьте модель и длительность голосовых: от 30 до 600 секунд.')
        config['key'] = config.get('key', '').strip() or state['voice'].get('key', '')
        if config['enabled'] and not key_for(config, state['ai']):
            raise UserError('Для голосовых нужен ключ Groq. Подключите Groq в AI API или введите отдельный ключ в этом окне.')
        def edit(s):
            s['voice'] = config.copy()
            s['voice_pause_until'] = 0
            s['queue'] = [j for j in s['queue'] if j['state'] == 'uncertain' or
                          not any(p['kind'] == 'voice' for p in queueing.parts(j))]
        self.store.update(edit)

    def save_behavior(self, values):
        config = self.store.read()['behavior']
        values = {'batch_seconds': values} if isinstance(values, int) else values
        config.update(values)
        quiet.validate(config)
        def edit(s):
            s['behavior'] = config
            if any(k.startswith('quiet_') for k in values):
                s['queue'] = [j for j in s['queue'] if j['state'] == 'uncertain']
        self.store.update(edit)

    def disable(self):
        def edit(s):
            s["enabled"] = False
            s["revision"] += 1
            s["queue"] = [j for j in s["queue"] if j["state"] == "uncertain"]
            s["history"] = {}
        self.store.update(edit)

    def enable(self):
        if not self.authorized or not self.client or not self.client.is_connected():
            raise UserError("Сначала подключите Telegram.")
        s = self.store.read()
        validate(s["ai"])
        if not s["selected"]:
            raise UserError("Сначала выберите личные чаты с согласием собеседников.")
        self.store.update(lambda s: s.update(enabled=True, enabled_at=self.clock(), revision=s["revision"] + 1))

    async def chats(self):
        if not self.authorized:
            raise UserError("Сначала подключите Telegram.")
        selected = self.store.read()["selected"]
        result = []
        async for dialog in self.client.iter_dialogs(limit=200):
            user = dialog.entity
            if isinstance(user, types.User) and not (user.bot or user.is_self or user.deleted or user.id == 777000):
                result.append({"id": str(user.id), "name": dialog.name or str(user.id), "selected": str(user.id) in selected})
        self.client.session.save()
        return result

    async def select(self, ids, consent):
        if ids and not consent:
            raise UserError("Подтвердите согласие собеседников на обработку сообщений AI.")
        available = {c["id"]: c for c in await self.chats()}
        if any(i not in available for i in ids):
            raise UserError("Обновите список: один из чатов недоступен.")
        def edit(s):
            old = s["selected"]
            s["selected"] = {i: dict(old.get(i, {"since": self.clock()}), name=available[i]["name"]) for i in ids}
            privacy.restrict(s)
            s["revision"] += 1
            s["history"] = {k: v for k, v in s["history"].items() if k in ids}
            s["transcripts"] = [t for t in s["transcripts"] if t["chat"] in ids]
            # A revision change cancels all in-flight generations.
            s["queue"] = [j for j in s["queue"] if j["state"] == "uncertain"]
            if not ids:
                s["enabled"] = False
        self.store.update(edit)

    def status(self):
        s = self.store.read()
        return {"version": SERVER_VERSION, "enabled": s["enabled"], "authorized": self.authorized,
                "connected": bool(self.client and self.client.is_connected()), "stage": self.login_stage,
                "login": self.login_info(),
                "account": self.account, "sent": s["sent"], "selected": len(s["selected"]),
                "queued": sum(j["state"] in ("queued", "processing", "delivering") for j in s["queue"]),
                "uncertain": sum(j["state"] == "uncertain" for j in s["queue"]),
                "awaiting": sum(j["state"] == "awaiting" for j in s["queue"]),
                "memory_enabled": s["memory"]["enabled"], "approval_mode": s["approval"]["mode"],
                "media": {"photos": s["media"]["photos"], "documents": s["media"]["documents"]},
                "quiet_active": quiet.active(s["behavior"], self.clock()), "behavior": s["behavior"],
                "pause_until": s["pause_until"], "logs": s["logs"]}

    async def on_message(self, event):
        if not event.is_private:
            return
        message = getattr(event, 'message', event)
        text = event.raw_text or ''
        chat = str(event.chat_id)
        if event.out:
            self.remember_message(event)
            if not text.startswith(PREFIX):
                self.cancel_chat(chat)
            return
        sender = await event.get_sender()
        if not isinstance(sender, types.User) or sender.bot or sender.is_self or sender.id == 777000:
            return
        if text.strip().lower() == '/ai_off':
            def revoke(s):
                s['selected'].pop(chat, None)
                s['history'].pop(chat, None)
                s['queue'] = [j for j in s['queue'] if j['chat'] != chat]
                s['transcripts'] = [t for t in s['transcripts'] if t['chat'] != chat]
                memory.forget(s, chat)
                privacy.restrict(s)
                s['revision'] += 1
            self.store.update(revoke)
            self.log('Собеседник отключил автоответы командой /ai_off.')
            return
        self.remember_message(event, sender)
        if text.startswith(PREFIX):
            return
        if getattr(message, 'voice', None):
            try:
                self.enqueue_voice(chat, event.id, message, event.date.timestamp())
            except VoiceError as exc:
                self.error_metric('voice', chat)
                self.log(str(exc))
            return
        handled = False
        try:
            handled = self.enqueue_media(chat, event.id, message, event.date.timestamp())
        except MediaError as exc:
            # The file itself is skipped; a caption is still answered as ordinary text.
            self.error_metric('media', chat)
            self.feature_event('media', str(exc))
        if not handled and text:
            self.enqueue(chat, event.id, text, event.date.timestamp())

    def remember_message(self, event, sender=None, edited=False):
        state = self.store.read()
        chat = str(event.chat_id)
        if not state['message_archive']['enabled'] or chat not in state['selected']:
            return
        message = getattr(event, 'message', event)
        media = ('voice' if getattr(message, 'voice', None) else
                 'photo' if getattr(message, 'photo', None) else
                 'video' if getattr(message, 'video', None) else
                 'file' if getattr(message, 'media', None) else '')
        name = ' '.join(filter(None, (getattr(sender, 'first_name', ''), getattr(sender, 'last_name', ''))))
        self.store.update(lambda s: archive.remember(s, chat, event.id, event.raw_text, media,
                          event.out, event.date.timestamp(), self.clock(), name, edited))

    async def on_deleted(self, event):
        chat = getattr(event, 'chat_id', None)
        if chat is not None and chat <= 0:
            return  # Private IDs must never be matched against a channel namespace.
        def edit(s):
            key = str(chat) if chat is not None else None
            archive.deleted(s, event.deleted_ids, key, self.clock())
            memory.drop_sources(s, event.deleted_ids, key)
        self.store.update(edit)

    async def on_edited(self, event):
        if event.is_private:
            self.remember_message(event, edited=True)

    def archive_report(self, offset=0, limit=20, query=''):
        state = self.store.read()
        before = state['message_archive']
        # Avoid writing on every refresh unless retention actually removed rows.
        previous = copy.deepcopy(before)
        archive.prune(state, self.clock())
        if before != previous:
            self.store.update(lambda s: archive.prune(s, self.clock()))
        return archive.report(state, offset, limit, query)

    def cancel_chat(self, chat):
        def edit(s):
            s['queue'] = [j for j in s['queue'] if j['chat'] != chat]
            s['history'].pop(chat, None)
        self.store.update(edit)

    def _enqueue_part(self, chat, part, timestamp):
        def edit(s):
            result = queueing.enqueue(s, chat, part, timestamp, self.clock())
            if result:
                stats.record(s, self.clock(), received=1)
                stats.record_chat(s, chat, self.clock(), received=1)
            return result
        return self.store.update(edit)

    def enqueue(self, chat, message_id, text, timestamp):
        text = text[:4000]
        if not text.strip():
            return False
        return self._enqueue_part(chat, {'id': message_id, 'kind': 'text', 'text': text, 'original': text}, timestamp)

    def enqueue_voice(self, chat, message_id, message, timestamp):
        state = self.store.read()
        selected = state['selected'].get(chat)
        if not state['enabled'] or not selected or timestamp < max(state['enabled_at'], selected['since']):
            return False
        config = state['voice']
        if not config['enabled'] or quiet.active(state['behavior'], self.clock()) or quiet.active(state['behavior'], timestamp):
            return False
        if not key_for(config, state['ai']):
            if self.clock() >= self.voice_notice_at:
                self.voice_notice_at = self.clock() + 60
                self.log('Голосовое пропущено: настройте ключ Groq в разделе «Голосовые сообщения».')
            return False
        meta = describe(message)
        if not meta:
            return False
        if meta['seconds'] > config['max_seconds']:
            raise VoiceError('Голосовое длиннее настроенного ограничения и не отправлено в AI API.')
        part = {'id': message_id, 'kind': 'voice', 'text': '', 'original': (getattr(message, 'raw_text', '') or '')[:4000], **meta}
        return self._enqueue_part(chat, part, timestamp)

    def enqueue_media(self, chat, message_id, message, timestamp):
        state = self.store.read()
        selected = state['selected'].get(chat)
        if not state['enabled'] or not selected or timestamp < max(state['enabled_at'], selected['since']):
            return False
        if quiet.active(state['behavior'], self.clock()) or quiet.active(state['behavior'], timestamp):
            return False
        found = media.identity(message)
        if found is None and getattr(message, 'document', None) is not None and not any(
                getattr(message, n, None) for n in ('voice', 'video', 'video_note', 'gif', 'sticker', 'audio')):
            self.feature_event('documents', 'Получен файл неподдерживаемого типа: читаются PDF, DOCX, TXT, MD, CSV, JSON и изображения.',
                               key='documents_type')
        elif found is not None:
            photo = found['media'] in ('photo', 'image')
            if not (state['media']['photos'] if photo else state['media']['documents']):
                self.feature_event('photos' if photo else 'documents',
                                   ('Получено фото, но распознавание фото выключено' if photo else
                                    'Получен документ, но чтение документов выключено')
                                   + ' (раздел «12. Фото и документы»). Отвечаю только на подпись.',
                                   key='off_photos' if photo else 'off_documents')
        meta = media.describe(message, state['media'])
        if not meta:
            return False
        part = {'id': message_id, 'kind': 'media', 'text': '', 'original': (getattr(message, 'raw_text', '') or '')[:4000], **meta}
        return self._enqueue_part(chat, part, timestamp)

    def current(self, job):
        s = self.store.read()
        return (s["enabled"] and s["revision"] == job["revision"] and job["chat"] in s["selected"]
                and any(j["id"] == job["id"] for j in s["queue"]))

    def change_job(self, job, **fields):
        def edit(s):
            for j in s["queue"]:
                if j["id"] == job["id"]:
                    j.update(fields)
        self.store.update(edit)

    def remove_job(self, job):
        self.store.update(lambda s: s.update(queue=[j for j in s["queue"] if j["id"] != job["id"]]))

    def work_allowed(self, job):
        state = self.store.read()
        if quiet.active(state['behavior'], self.clock()) or (job.get('expires_at', 0) and self.clock() >= job['expires_at']):
            self.remove_job(job)
            return False
        return self.current(job)

    def source_matches(self, part, source):
        if not source or source.out or (source.raw_text or '')[:4000] != part.get('original', part.get('text', '')):
            return False
        if part['kind'] == 'voice':
            try:
                meta = describe(source)
                return bool(meta) and all(meta[k] == part[k] for k in ('document_id', 'seconds', 'size', 'mime'))
            except VoiceError:
                return False
        if part['kind'] == 'media':
            meta = media.identity(source)
            return bool(meta) and all(meta[k] == part[k] for k in ('media', 'document_id', 'size', 'mime'))
        return True

    async def prepare_parts(self, job, state):
        items = queueing.parts(job)
        for part in items:
            if part['kind'] == 'media' and not part.get('extracted'):
                await self.prepare_media(job, part, items, state)
                continue
            if part['kind'] != 'voice' or part.get('transcribed'):
                continue
            if not self.work_allowed(job):
                raise VoiceCancelled()
            config = state['voice']
            if not config['enabled'] or not key_for(config, state['ai']):
                raise VoiceError('Распознавание голосовых отключено или не указан ключ Groq.')
            self.heartbeat = self.clock()
            peer = await self.client.get_input_entity(int(job['chat']))
            source = await self.client.get_messages(peer, ids=part['id'])
            if not self.source_matches(part, source):
                self.remove_job(job)
                raise VoiceCancelled()
            def progress(received, total):
                if not self.work_allowed(job):
                    raise VoiceCancelled()
                if received > MAX_BYTES or (total and total > MAX_BYTES):
                    raise VoiceError('Голосовое больше 20 МБ.')
            with LimitedAudio() as audio:
                await asyncio.wait_for(self.client.download_media(source, file=audio, progress_callback=progress), 60)
                if not self.work_allowed(job):
                    raise VoiceCancelled()
                self.heartbeat = self.clock()
                transcript = await self.voice.transcribe(config, key_for(config, state['ai']), audio.getvalue(), part['mime'])
            if not self.work_allowed(job):
                raise VoiceCancelled()
            part['text'] = '\n'.join(x for x in (part.get('original', ''), transcript) if x)[:queueing.MAX_INPUT]
            part['transcribed'] = True
            def save(s):
                for stored in s['queue']:
                    if stored['id'] == job['id']:
                        stored['parts'] = items
                entry = {'chat': job['chat'], 'message_id': part['id'], 'at': self.clock(), 'text': transcript}
                s['transcripts'] = ([entry] + [t for t in s['transcripts'] if (t['chat'], t['message_id']) != (job['chat'], part['id'])])[:10]
                stats.record(s, self.clock(), voice=1)
                stats.record_usage(s, self.clock(), 'groq', config['model'], 'voice',
                                   audio_seconds=part.get('seconds', 0), chat=job['chat'])
            self.store.update(save)
            self.heartbeat = self.clock()
        return items

    async def prepare_media(self, job, part, items, state):
        if not self.work_allowed(job):
            raise VoiceCancelled()
        config = state['media']
        wanted = config['photos'] if part['media'] in ('photo', 'image') else config['documents']
        self.heartbeat = self.clock()
        processed = False
        if not wanted:
            text = media.failure_text(part, 'обработка отключена в настройках')
        else:
            peer = await self.client.get_input_entity(int(job['chat']))
            source = await self.client.get_messages(peer, ids=part['id'])
            if not self.source_matches(part, source):
                self.remove_job(job)
                raise VoiceCancelled()
            limit = config['max_mb'] * 1024 * 1024
            def progress(received, total):
                if not self.work_allowed(job):
                    raise VoiceCancelled()
                if received > limit or (total and total > limit):
                    raise MediaError('Файл больше установленного ограничения.')
            try:
                with media.LimitedBuffer(limit) as buffer:
                    await asyncio.wait_for(self.client.download_media(source, file=buffer, progress_callback=progress), 90)
                    data = buffer.getvalue()
                if not self.work_allowed(job):
                    raise VoiceCancelled()
                self.heartbeat = self.clock()
                text = await self.extract_media(state, job, part, data)
                processed = True
            except MediaError as exc:
                if exc.retry_after and job['attempts'] < 3:
                    raise
                # Not retryable: answer the rest of the message and say the file was not read.
                self.error_metric('media', job['chat'])
                self.feature_event('photos' if part['media'] in ('photo', 'image') else 'documents',
                                   'Вложение не прочитано: ' + str(exc), every=0)
                text = media.failure_text(part, str(exc).rstrip('.'))
        if not self.work_allowed(job):
            raise VoiceCancelled()
        part['text'] = '\n'.join(x for x in (part.get('original', ''), text) if x)[:queueing.MAX_INPUT]
        part['extracted'] = True
        counter = 'photos' if part['media'] in ('photo', 'image') else 'documents'
        def save(s):
            for stored in s['queue']:
                if stored['id'] == job['id']:
                    stored['parts'] = items
            if processed:
                stats.record(s, self.clock(), **{counter: 1})
                stats.record_chat(s, job['chat'], self.clock(), **{counter: 1})
        self.store.update(save)
        self.heartbeat = self.clock()

    async def extract_media(self, state, job, part, data):
        config, kind = state['media'], part['media']
        def thread(function, *args):
            return asyncio.wait_for(asyncio.to_thread(function, *args), 60)
        try:
            with ai_purpose('vision', job['chat']):
                if kind in ('photo', 'image'):
                    image, mime = await thread(media.prepare_image, data)
                    described = await self.ai.describe_image(state['ai'], config, image, mime, media.VISION_TASK)
                    return media.header(part) + '\n' + described
                if kind == 'pdf':
                    text, scans, pages = await thread(media.pdf_content, data, config['pdf_pages'], config['scan_pages'])
                    blocks = [text] if text else []
                    for number, raw in scans:
                        if not self.work_allowed(job):
                            raise VoiceCancelled()
                        image, mime = await thread(media.prepare_image, raw)
                        page = await self.ai.describe_image(state['ai'], config, image, mime, media.SCAN_TASK)
                        blocks.append(f'— стр. {number} (скан) —\n' + page)
                    if not blocks:
                        raise MediaError('В PDF не найден текст.')
                    more = f"\n[Прочитаны первые {config['pdf_pages']} стр. из {pages}]" if pages > config['pdf_pages'] else ''
                    return media.header(part, pages) + '\n' + '\n'.join(blocks)[:media.MAX_TEXT] + more
                text = await thread(media.docx_text if kind == 'docx' else media.plain_text, data)
                if not text:
                    raise MediaError('Документ пустой.')
                return media.header(part) + '\n' + text
        except AiError as exc:
            raise MediaError(str(exc), exc.retry_after) from None
        except asyncio.TimeoutError:
            raise MediaError('Обработка файла заняла слишком много времени.') from None

    async def sources_unchanged(self, peer, items):
        if len(items) == 1:
            return self.source_matches(items[0], await self.client.get_messages(peer, ids=items[0]['id']))
        messages = await self.client.get_messages(peer, ids=[p['id'] for p in items])
        by_id = {m.id: m for m in messages if m}
        return all(self.source_matches(p, by_id.get(p['id'])) for p in items)

    def next_job(self, state):
        blocked = set()
        for job in state['queue']:
            # A draft waiting for approval keeps later replies of its chat in order.
            if job['state'] not in ('queued', 'processing', 'delivering', 'awaiting') or job['chat'] in blocked:
                continue
            blocked.add(job['chat'])
            if job['state'] != 'queued' or max(job['after'], job.get('batch_until', 0)) > self.clock():
                continue
            if self.clock() < state['last_sent'].get(job['chat'], 0) + state['ai']['cooldown']:
                continue
            if state.get('voice_pause_until', 0) > self.clock() and any(p['kind'] == 'voice' and not p.get('transcribed') for p in queueing.parts(job)):
                continue
            return job
        return None

    async def process_one(self):
        s = self.store.read()
        if quiet.active(s['behavior'], self.clock()):
            if any(j['state'] != 'uncertain' for j in s['queue']):
                self.store.update(lambda x: x.update(queue=[j for j in x['queue'] if j['state'] == 'uncertain']))
            return
        if not s["enabled"] or not self.authorized or not self.client or not self.client.is_connected() or s["pause_until"] > self.clock():
            return
        job = self.next_job(s)
        if not job:
            return
        if not self.work_allowed(job):
            self.remove_job(job)
            return
        self.change_job(job, state="processing")
        try:
            items = await self.prepare_parts(job, s)
        except VoiceCancelled:
            return
        except MediaError as exc:
            # Only retryable errors reach this point; others are answered as text.
            self.diagnostics.emit('media', 'extract_failed', exc, retry_after_s=exc.retry_after)
            if self.work_allowed(job):
                self.log(str(exc))
                self.change_job(job, state='queued', after=self.clock() + exc.retry_after, attempts=job['attempts'] + 1)
            return
        except VoiceError as exc:
            self.error_metric('voice', job['chat'])
            self.diagnostics.emit('voice', 'transcribe_failed', exc, retry_after_s=exc.retry_after)
            if self.work_allowed(job):
                self.log(str(exc))
                if exc.retry_after and job['attempts'] < 3:
                    self.change_job(job, state='queued', after=self.clock() + exc.retry_after, attempts=job['attempts'] + 1)
                    self.store.update(lambda x, until=self.clock() + exc.retry_after: x.update(voice_pause_until=until))
                else:
                    self.remove_job(job)
            return
        except errors.FloodWaitError as exc:
            self.change_job(job, state='queued', after=self.clock() + exc.seconds)
            self.store.update(lambda x, until=self.clock() + exc.seconds: x.update(pause_until=until))
            return
        except (errors.RPCError, OSError, asyncio.TimeoutError, ValueError) as exc:
            self.error_metric('telegram', job['chat'])
            self.diagnostics.emit('voice', 'download_failed', exc)
            if self.work_allowed(job):
                if job['attempts'] < 3:
                    self.change_job(job, state='queued', after=self.clock() + 30, attempts=job['attempts'] + 1)
                else:
                    self.remove_job(job)
                self.log('Не удалось загрузить голосовое или вложение с сервера Telegram.')
            return
        if not self.work_allowed(job):
            return
        job = self.store.update(lambda state: queueing.seal(state, job['id'], items, self.clock()))
        if not job:
            return
        try:
            answer = job.get('answer')
            if not answer:
                self.metric(ai_calls=1)
                started = time.perf_counter()
                config = style.apply(s['ai'], s.get('style', ''))
                remembered = memory.context(s, job['chat'])
                if remembered:
                    config = dict(config, prompt=config['prompt'] + '\n\n' + remembered)
                with ai_purpose('reply', job['chat']):
                    answer = await self.ai.answer(config, job['text'], s['history'].get(job['chat'], []))
                self.metric(ai_success=1, ai_ms=(time.perf_counter() - started) * 1000)
        except AiError as exc:
            self.error_metric('ai', job['chat'])
            self.diagnostics.emit('ai', 'answer_failed', exc, retry_after_s=exc.retry_after)
            if not self.work_allowed(job):
                return
            self.log(str(exc))
            if exc.retry_after and job["attempts"] < 3:
                self.change_job(job, state="queued", after=self.clock() + exc.retry_after, attempts=job["attempts"] + 1)
                self.store.update(lambda s, until=self.clock() + exc.retry_after: s.update(pause_until=until))
            else:
                self.remove_job(job)
            return
        if not self.work_allowed(job):
            return
        self.change_job(job, answer=answer)
        if not job.get('approved') and approval.required(self.store.read(), job['chat']):
            self.change_job(job, state='awaiting', awaiting_since=self.clock())
            self.log('Черновик ответа ждёт согласования в приложении.')
            await self.notify_draft(job['chat'])
            return
        async with self.telegram_lock:
            if not self.work_allowed(job) or not self.authorized:
                return
            try:
                peer = await self.client.get_input_entity(int(job["chat"]))
                if not await self.sources_unchanged(peer, queueing.parts(job)):
                    self.remove_job(job)
                    return
            except errors.FloodWaitError as exc:
                self.change_job(job, state="queued", answer=answer, after=self.clock() + exc.seconds)
                self.store.update(lambda s, until=self.clock() + exc.seconds: s.update(pause_until=until))
                return
            except ValueError:
                self.remove_job(job)
                self.log("Не найден адрес чата. Обновите выбор чатов в приложении.")
                return
            except (errors.RPCError, OSError, asyncio.TimeoutError):
                if job["attempts"] < 3:
                    self.change_job(job, state="queued", after=self.clock() + 30, attempts=job["attempts"] + 1)
                else:
                    self.remove_job(job)
                    self.log("Не удалось проверить исходное сообщение; ответ отменён.")
                return
            if not self.work_allowed(job):
                return
            self.change_job(job, state="delivering")
            try:
                message, entities = render(answer, s["ai"].get("formatting", "auto"), PREFIX)
                await self.client(functions.messages.SendMessageRequest(
                    peer=peer, message=message, entities=entities, random_id=job["random_id"], no_webpage=True,
                    reply_to=types.InputReplyToMessage(reply_to_msg_id=job["message_id"])))
            except errors.FloodWaitError as exc:
                self.change_job(job, state="queued", answer=answer, after=self.clock() + exc.seconds)
                self.store.update(lambda s, until=self.clock() + exc.seconds: s.update(pause_until=until))
                self.log(f"Telegram запросил паузу {exc.seconds} сек.")
                return
            except errors.RPCError as exc:
                self.error_metric('telegram', job['chat'])
                self.diagnostics.emit('telegram', 'send', exc)
                self.remove_job(job)
                self.log("Telegram отклонил отправку: " + type(exc).__name__)
                return
            except (OSError, asyncio.TimeoutError) as exc:
                self.error_metric('telegram', job['chat'])
                self.diagnostics.emit('telegram', 'send_ambiguous', exc)
                self.change_job(job, state="uncertain", text="", answer="", parts=[])
                self.log("Результат отправки неизвестен. Проверьте чат; повторная отправка отменена.")
                return
            def success(state):
                state["queue"] = [j for j in state["queue"] if j["id"] != job["id"]]
                state["sent"] += 1
                state["last_sent"][job["chat"]] = self.clock()
                reply_ms = max(0, self.clock()-job.get('created_at', self.clock()))*1000
                stats.record(state, self.clock(), replies=1, merged=max(0, len(queueing.parts(job))-1),
                             reply_ms=reply_ms)
                kind = ({'approved': 1, 'edited': 1 if job.get('edited') else 0} if job.get('approved')
                        else {'auto': 1, 'auto_reply_ms': reply_ms})
                stats.record(state, self.clock(), **kind)
                stats.record_chat(state, job["chat"], self.clock(), replies=1, reply_ms=reply_ms, **kind)
                if self.current(job):
                    history = state["history"].get(job["chat"], []) + [
                        {"role": "user", "content": job["text"]}, {"role": "assistant", "content": answer}]
                    while len(history) > 6 or sum(len(t["content"]) for t in history) > 6000:
                        history = history[2:]
                    state["history"][job["chat"]] = history
                    memory.observe(state, job["chat"], [p['id'] for p in queueing.parts(job)],
                                   job["text"], answer, self.clock())
            self.store.update(success)
            self.log("Автоответ отправлен.")

    # ------------------------------------------------------------ 2.9: memory, drafts, settings

    async def housekeeping(self):
        """Low-priority work between replies. Never stops the worker."""
        try:
            if self.clock() >= self.drafts_checked_at:
                self.drafts_checked_at = self.clock() + 60
                if any(j['state'] == 'awaiting' for j in self.store.read()['queue']):
                    expired = self.store.update(lambda s: approval.expire(s, self.clock()))
                    if expired:
                        self.log(f'Черновиков без ответа удалено по сроку: {expired}.')
            await self.process_memory()
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            self.diagnostics.emit('worker', 'housekeeping', exc)

    async def process_memory(self):
        if self.clock() < self.memory_next:
            return
        s = self.store.read()
        complete = getattr(self.ai, 'complete', None)
        chat = memory.due(s, self.clock()) if complete else None
        if not chat:
            self.memory_next = self.clock() + 30
            return
        if self.next_job(s):
            return  # replies first
        system, text, epoch, used = memory.request(s, chat)
        try:
            validate(s['ai'])
            with ai_purpose('memory', chat):
                summary = memory.parse(await complete(s['ai'], system, text, memory.tokens(s['ai'])))
        except (AiError, UserError, ValueError) as exc:
            self.error_metric('memory', chat)
            self.diagnostics.emit('memory', 'update_failed', exc)
            reason = str(exc) if isinstance(exc, (AiError, UserError)) else 'модель вернула ответ не в формате памяти'
            self.feature_event('memory', 'Не удалось обновить память собеседника: ' + reason
                               + '. Повтор через 15 минут.', every=0)
            self.store.update(lambda x: memory.postpone(x, chat, self.clock()))
            return
        def save(x):
            if memory.apply(x, chat, epoch, used, summary, self.clock()):
                stats.record(x, self.clock(), memory_updates=1)
        self.store.update(save)

    async def notify_draft(self, chat):
        """A short note in the owner's Saved Messages; no message content, at most every 10 minutes per chat."""
        s = self.store.read()
        if not s['approval'].get('notify', True) or not self.client or self.clock() < self.draft_notice_at.get(chat, 0):
            return
        self.draft_notice_at[chat] = self.clock() + 600
        name = (s['selected'].get(chat, {}).get('name') or 'собеседника')[:60]
        text = f'Zamolvi: черновик ответа для «{name}» ждёт согласования. Откройте приложение → «Согласование ответов».'
        async with self.telegram_lock:
            try:
                await self.client(functions.messages.SendMessageRequest(
                    peer=types.InputPeerSelf(), message=text, random_id=secrets.randbits(63) or 1, no_webpage=True))
            except Exception as exc:
                self.diagnostics.emit('telegram', 'draft_notice', exc)
                self.feature_event('approval', 'Черновик создан, но заметку в «Избранное» Telegram отправить не удалось.')

    def feature_event(self, feature, message, key=None, every=1800):
        """Why a feature did not work: server log (rate-limited) and the last problem per feature."""
        key = key or feature + ':' + message
        if every and self.clock() < self.notice_at.get(key, 0):
            return
        self.notice_at[key] = self.clock() + every
        self.log(message)
        def save(s):
            problems = s.setdefault('feature_problems', {})
            problems[feature] = {'at': self.clock(), 'message': message[:300]}
        self.store.update(save)

    def feature_report(self):
        """Status of every 2.9 feature with a reason in plain words, for the app and diagnostics."""
        s = self.store.read()
        now = self.clock()
        problems = {k: v for k, v in s.get('feature_problems', {}).items() if now - v.get('at', 0) < 86400}
        connected = bool(self.client and self.client.is_connected())
        def row(status, reason, feature=None):
            problem = problems.get(feature) if feature else None
            result = {'status': status, 'reason': reason}
            if problem:
                result.update(last_problem=problem['message'], last_problem_at=problem['at'])
                if status == 'ok':
                    result['status'] = 'warning'
            return result
        if not self.authorized:
            auto = row('problem', 'Telegram не подключён: раздел «2. Telegram».')
        elif not connected:
            auto = row('problem', 'Нет соединения с Telegram; сервер переподключается.')
        elif not s['ai'].get('key'):
            auto = row('problem', 'Не указан ключ AI: раздел «3. Нейросеть и текст».')
        elif not s['selected']:
            auto = row('problem', 'Не выбраны чаты: раздел «4. Личные чаты».')
        elif not s['enabled']:
            auto = row('off', 'Автоответчик выключен: раздел «5. Автоответчик».')
        elif quiet.active(s['behavior'], now):
            auto = row('off', 'Сейчас тихие часы: ответы на паузе.')
        else:
            auto = row('ok', f"Работает. Чатов: {len(s['selected'])}.")
        running = auto['status'] == 'ok'
        m = s['memory']
        if not m['enabled']:
            mem = row('off', 'Выключено: включите в «11. Память собеседников».', 'memory')
        elif not running:
            mem = row('problem', 'Память пополняется только из отправленных автоответов, а автоответчик сейчас не отвечает.', 'memory')
        else:
            filled = sum(1 for v in m['chats'].values() if v['summary'] != memory.empty_summary())
            waiting = sum(len(v['pending']) for v in m['chats'].values())
            mem = row('ok', f"Включено. Резюме есть у {filled} чатов; ответов ждут обновления: {waiting} "
                            f"(обновление после каждых {memory.UPDATE_EVERY}).", 'memory')
        missing = [name for name, module in (('pypdf', 'pypdf'), ('pillow', 'PIL'))
                   if importlib.util.find_spec(module) is None]
        from .ai import vision_model
        vision = vision_model(s['ai'], s['media'])
        if missing:
            photos = row('problem', 'На сервере не установлены ' + ', '.join(missing)
                         + ': переустановите сервер из приложения.', 'photos')
            docs = row('problem', photos['reason'], 'documents')
        else:
            photos = (row('off', 'Выключено: включите в «12. Фото и документы».', 'photos') if not s['media']['photos'] else
                      row('problem', 'Включено, но автоответчик сейчас не отвечает.', 'photos') if not running else
                      row('ok', f"Включено. Модель для изображений: {vision}.", 'photos'))
            docs = (row('off', 'Выключено: включите в «12. Фото и документы».', 'documents') if not s['media']['documents'] else
                    row('problem', 'Включено, но автоответчик сейчас не отвечает.', 'documents') if not running else
                    row('ok', 'Включено: PDF, DOCX, TXT, MD, CSV, JSON.', 'documents'))
        a = s['approval']
        drafts = sum(j['state'] == 'awaiting' for j in s['queue'])
        if a['mode'] == 'off':
            appr = row('off', 'Выключено: выберите режим в «13. Согласование ответов».', 'approval')
        elif not running:
            appr = row('problem', 'Режим включён, но автоответчик сейчас не отвечает — черновики не создаются.', 'approval')
        else:
            where = 'все чаты' if a['mode'] == 'all' else f"выбранные чаты: {len(a['chats'])}"
            appr = row('ok', f"Включено ({where}). Ждут решения: {drafts}.", 'approval')
        priced = len((s.get('pricing') or {}).get('models', {}))
        cost = row('ok' if priced else 'off', f'Цены заданы для моделей: {priced}.' if priced
                   else 'Цены моделей не заданы: стоимость не считается («14. Подробная статистика»).')
        return {'server_version': SERVER_VERSION, 'autoresponder': auto, 'memory': mem, 'photos': photos,
                'documents': docs, 'approval': appr, 'pricing': cost}

    def memory_report(self):
        report = memory.report(self.store.read())
        for row in report['chats']:
            progress = self.imports.get(row['chat'])
            if progress:
                row['import'] = dict(progress)
        report['import_limits'] = list(memory.IMPORT_LIMITS)
        return report

    def save_memory(self, enabled):
        self.store.update(lambda s: memory.configure(s, enabled))
        if not enabled:
            self.cancel_imports()
        return self.memory_report()

    # ------------------------------------------------------------ chat history -> memory

    def start_import(self, chat, limit, consent):
        """Reads up to `limit` past messages of a selected chat and folds them into its memory.

        Separate consent: choosing a chat covers new messages only. Raw history is never stored:
        it stays in RAM while the parts are summarised, then only the summary remains.
        """
        s = self.store.read()
        if not consent:
            raise UserError('Подтвердите, что собеседник согласен на передачу прошлой переписки в AI.')
        if not s['memory']['enabled']:
            raise UserError('Сначала включите память собеседников.')
        if chat not in s['selected']:
            raise UserError('Этот чат не выбран в «4. Личные чаты».')
        if not self.authorized or not self.client or not self.client.is_connected():
            raise UserError('Сначала подключите Telegram.')
        low, high = memory.IMPORT_LIMITS
        if not low <= limit <= high:
            raise UserError(f'Количество сообщений: от {low} до {high}.')
        validate(s['ai'])
        task = self.import_tasks.get(chat)
        if task and not task.done():
            raise UserError('Загрузка этого чата уже идёт.')
        self.imports[chat] = {'status': 'loading', 'limit': limit, 'downloaded': 0, 'messages': 0, 'parts': 0, 'parts_done': 0,
                              'started_at': self.clock(), 'finished_at': 0, 'error': ''}
        self.import_tasks[chat] = asyncio.create_task(self._import(chat, limit, s['memory']['epoch']),
                                                      name='memory-import')
        self.log('Загрузка истории переписки в память начата.')
        return self.memory_report()

    def cancel_import(self, chat):
        task = self.import_tasks.get(chat)
        if task and not task.done():
            task.cancel()
        return self.memory_report()

    def cancel_imports(self):
        for task in self.import_tasks.values():
            if not task.done():
                task.cancel()

    async def _import(self, chat, limit, epoch):
        info = self.imports[chat]
        def finish(status, error=''):
            info.update(status=status, error=error, finished_at=self.clock())
        def still_allowed():
            x = self.store.read()
            return x['memory']['enabled'] and x['memory']['epoch'] == epoch and chat in x['selected']
        try:
            peer = await self.client.get_input_entity(int(chat))
            lines, read, offset, pauses = [], 0, 0, 0
            while read < limit:
                try:
                    async for message in self.client.iter_messages(peer, limit=limit - read, offset_id=offset):
                        read += 1
                        offset = message.id
                        info['downloaded'] = read
                        text = (getattr(message, 'raw_text', '') or '').strip()
                        if not text or text.startswith(PREFIX):
                            continue  # media without caption, AI auto-replies
                        date = getattr(message, 'date', None)
                        lines.append(memory.history_line(date.strftime('%d.%m.%Y') if date else '', message.out, text))
                    break  # the whole history (or the limit) was read
                except errors.FloodWaitError as exc:
                    # Long histories: Telegram asks for short pauses; continue from the last message.
                    pauses += 1
                    if exc.seconds > 300 or pauses > 20:
                        raise
                    info['status'] = 'waiting'
                    await asyncio.sleep(exc.seconds + 1)
                    info['status'] = 'loading'
                    if not still_allowed():
                        finish('cancelled', 'Память выключена, забыта или чат исключён.')
                        return
            lines.reverse()
            parts = memory.chunks(lines)
            info.update(status='processing', messages=len(lines), parts=len(parts))
            if not parts:
                finish('done', 'В этом чате нет текстовых сообщений.')
                return
            queue, waited, skipped = list(parts), 0, 0
            while queue:
                part = queue.pop(0)
                summary, bad_json = None, 0
                while summary is None:
                    while True:
                        if not still_allowed():
                            finish('cancelled', 'Память выключена, забыта или чат исключён.')
                            return
                        if not self.next_job(self.store.read()):
                            break
                        await asyncio.sleep(2)  # replies first
                    s = self.store.read()
                    system, text = memory.import_request(s, chat, part)
                    try:
                        with ai_purpose('memory', chat):
                            summary = memory.parse(await self.ai.complete(s['ai'], system, text, memory.tokens(s['ai'])))
                    except AiError as exc:
                        if getattr(exc, 'too_large', False):
                            # Too big for the model/plan limit: split the part in two and go on.
                            if len(part) > 1 and sum(len(l) for l in part) > memory.MIN_CHUNK:
                                half = len(part) // 2
                                queue[0:0] = [part[:half], part[half:]]
                                info['parts'] += 1
                                info['split'] = info.get('split', 0) + 1
                            else:
                                skipped += 1
                                info['parts_done'] += 1
                            break
                        if not exc.retry_after or waited >= memory.MAX_WAIT:
                            raise
                        info['status'] = 'waiting'
                        pause = min(exc.retry_after, 120)
                        waited += pause
                        await asyncio.sleep(pause)        # rate limit (HTTP 429): wait, then the same part again
                        info['status'] = 'processing'
                    except ValueError:
                        bad_json += 1
                        if bad_json >= 3:
                            skipped += 1
                            info['parts_done'] += 1
                            break
                if summary is None:
                    continue
                if not self.store.update(lambda x, sm=summary: memory.apply_import(x, chat, epoch, sm, self.clock())):
                    finish('cancelled', 'Память выключена, забыта или чат исключён.')
                    return
                info['parts_done'] += 1
            info['skipped'] = skipped
            if skipped and skipped >= info['parts']:
                finish('failed', 'Ни одна часть не обработана: модель отклоняет запросы. Проверьте модель и тариф AI.')
                return
            self.store.update(lambda x: stats.record(x, self.clock(), memory_updates=1))
            finish('done')
            self.log(f'История переписки загружена в память: {len(lines)} сообщений.')
        except asyncio.CancelledError:
            finish('cancelled', 'Загрузка остановлена.')
            raise
        except errors.FloodWaitError as exc:
            finish('failed', f'Telegram попросил подождать {exc.seconds} с. Повторите загрузку позже.')
        except (AiError, UserError) as exc:
            finish('failed', str(exc))
            self.feature_event('memory', 'Загрузка истории в память не завершена: ' + str(exc), every=0)
        except ValueError:
            finish('failed', 'Модель вернула ответ не в формате памяти.')
            self.feature_event('memory', 'Загрузка истории в память не завершена: модель вернула ответ не в формате памяти.', every=0)
        except Exception as exc:
            self.diagnostics.emit('memory', 'import_failed', exc)
            finish('failed', 'Не удалось прочитать историю чата (' + type(exc).__name__ + ').')

    def forget_memory(self, chat=None):
        if chat is not None and chat not in self.store.read()['selected']:
            raise UserError('Этот чат больше не выбран; его память уже удалена.')
        self.store.update(lambda s: memory.forget(s, chat))
        for key in ([chat] if chat else list(self.import_tasks)):
            self.cancel_import(key)
            self.imports.pop(key, None)
        self.log('Память собеседника удалена.' if chat else 'Память всех собеседников удалена.')
        return self.memory_report()

    def public_media(self):
        s = self.store.read()
        from .ai import vision_model
        return {**s['media'], 'effective_vision_model': vision_model(s['ai'], s['media']),
                'provider': s['ai']['provider']}

    def save_media(self, config):
        media.validate(config)
        self.store.update(lambda s: s['media'].update(config))
        return self.public_media()

    def approval_report(self):
        return approval.report(self.store.read())

    def save_approval(self, mode, chats, notify):
        self.store.update(lambda s: approval.configure(s, mode, chats, notify))
        return self.approval_report()

    def decide(self, job_id, action, text=''):
        message = self.store.update(lambda s: approval.decide(s, job_id, action, text, self.clock()))
        return {'message': message, **self.approval_report()}

    def extended_stats(self, period):
        return stats.extended(self.store.read(), self.clock(), period)

    def public_pricing(self):
        s = self.store.read()
        pricing = s.get('pricing') or {'currency': '', 'models': {}}
        from .ai import vision_model
        known = list(s.get('stats', {}).get('models', {}))
        for key in (stats.model_key(s['ai']['provider'], s['ai']['model']),
                    stats.model_key(s['ai']['provider'], vision_model(s['ai'], s['media'])),
                    stats.model_key('groq', s['voice']['model'])):
            if key not in known:
                known.append(key)
        return {**pricing, 'known': [k for k in known if k != 'other/other']}

    def save_pricing(self, data):
        clean = stats.validate_pricing(data)
        self.store.update(lambda s: s.update(pricing=clean))
        return self.public_pricing()

    async def run(self):
        try:
            reconnect_at = 0
            archive_prune_at = 0
            auth_check_at = self.clock() + 60
            while not self.stopping:
                self.heartbeat = self.clock()
                if self.clock() >= archive_prune_at:
                    self.store.update(lambda s: archive.prune(s, self.clock()))
                    archive_prune_at = self.clock() + 3600
                if self.authorized and self.client and self.client.is_connected() and self.clock() >= auth_check_at:
                    try:
                        await self.client(functions.updates.GetStateRequest())
                    except (errors.AuthKeyUnregisteredError, errors.AuthKeyDuplicatedError,
                            errors.SessionRevokedError, errors.SessionExpiredError):
                        self.disable()
                        self.authorized = False
                        self.login_stage = "disconnected"
                        await self.client.disconnect()
                        self.client = None
                        def reset(s):
                            s.update(session={}, selected={}, seen=[], queue=[], last_sent={}, transcripts=[])
                            privacy.forget_account(s)
                        self.store.update(reset)
                        self.log("Сеанс Telegram отозван или истёк. Войдите заново.")
                    except errors.FloodWaitError as exc:
                        auth_check_at = self.clock() + exc.seconds
                    except (errors.RPCError, OSError, asyncio.TimeoutError):
                        pass
                    auth_check_at = max(auth_check_at, self.clock() + 60)
                # Telethon reconnects ordinary interruptions; a failed initial connection is retried here.
                if (not self.authorized or not self.client or not self.client.is_connected()) and self.clock() >= reconnect_at and self.login_stage not in ("sending", "code", "password", "blocked", "qr"):
                    saved = self.store.read()
                    if saved["session"].get("string") and saved["telegram"].get("api_id"):
                        async with self.telegram_lock:
                            # A login request may have acquired the lock while this task waited.
                            if self.login_stage in ("sending", "code", "password", "blocked", "qr") or (self.authorized and self.client and self.client.is_connected()):
                                continue
                            saved = self.store.read()
                            try:
                                await self.connect(saved["telegram"])
                                if await self.client.is_user_authorized():
                                    await self.signed_in()
                            except Exception as exc:
                                self.diagnostics.emit('telegram', 'reconnect', exc)
                                self.log("Повтор подключения Telegram: " + type(exc).__name__)
                        reconnect_at = self.clock() + 60
                await self.process_one()
                await self.housekeeping()
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            self.diagnostics.emit('worker', 'failed', exc)
            self.failed = True
            self.log("Рабочий процесс остановлен: " + type(exc).__name__ + ". Требуется перезапуск.")
            raise
