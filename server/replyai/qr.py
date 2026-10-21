"""Short-lived QR authorization. Tokens and viewer grants stay in RAM only."""
import asyncio
import base64
from contextlib import suppress
import math
import secrets

import qrcode
from telethon import errors, events, functions, types
from .errors import UserError
from . import archive, privacy


class QrLogin:
    def __init__(self, engine):
        self.engine = engine
        self.task = None
        self.client = None
        self.handler = None
        self.changed = asyncio.Event()
        self.key = ''
        self.until = 0
        self.expires = 0
        self.rows = []
        self.phase = 'idle'
        self.message = ''
        self.completed = 0

    def viewer_allowed(self, key):
        return bool(self.key and self.engine.clock() < self.until and
                    (not self.completed or self.engine.clock() < self.completed + 30) and
                    secrets.compare_digest(key.encode(), self.key.encode()))

    def state(self, private=False):
        phase = self.phase
        if self.engine.authorized:
            phase = 'authorized'
        if phase in ('qr', 'password') and self.engine.clock() >= self.until:
            phase = 'expired'
        result = {'stage': phase, 'message': 'Telegram подключён. Вернитесь в Zamolvi.' if phase == 'authorized' else self.message,
                  'expires_in': max(0, math.ceil(self.expires - self.engine.clock())),
                  'remaining_s': max(0, math.ceil(self.until - self.engine.clock()))}
        if phase == 'qr' and self.engine.clock() < self.expires:
            result['qr_rows'] = self.rows
        if private:
            phone = self.engine.store.read()['telegram'].get('phone', '')
            result['has_credentials'] = bool(phone)
            result['phone_last4'] = phone[-4:]
            if self.key and self.engine.clock() < self.until and phase in ('qr', 'password'):
                result['viewer_path'] = '/qr#' + self.key
        return result

    async def start(self, credentials=None):
        e = self.engine
        async with e.telegram_lock:
            if e.authorized:
                raise UserError('Telegram уже подключён. Для смены аккаунта сначала отключите его.')
            e.check_login_wait()
            credentials = credentials or e.store.read()['telegram']
            if not all(credentials.get(k) for k in ('api_id', 'api_hash', 'phone')):
                raise UserError('Для QR сначала введите собственные api_id, api_hash и номер Telegram.')
            same = credentials == e.store.read()['telegram']
            if same and self.task and not self.task.done() and self.phase == 'qr' and e.clock() < self.until:
                return self.state(private=True)
            if same and self.phase == 'password' and e.login_stage == 'password' and e.clock() < e.login_until:
                return self.state(private=True)
            clear_previous = self.phase in ('qr', 'password', 'failed', 'expired') and (not same or e.clock() >= self.until)
            await self.cancel_locked(clear_session=clear_previous)
            e.disable()
            if not same:
                if e.client:
                    await e.client.disconnect()
                    e.client = None
                def reset(s):
                    s.update(telegram=credentials, session={}, selected={}, seen=[],
                             queue=[], history={}, last_sent={}, transcripts=[],
                             pause_until=0, voice_pause_until=0)
                    privacy.forget_account(s)
                e.store.update(reset)
            e.store.update(lambda s: s.update(qr_pending=True))
            e.login_stage = 'qr'
            e.login_until = e.clock() + 300
            e.phone_hash = None
            e.login_next = None
            e.login_delivery = {'delivery': 'qr', 'message': 'Подтвердите QR в Telegram на своём телефоне.'}
            self.until, self.expires = e.login_until, 0
            self.phase, self.message = 'qr', 'Отсканируйте QR в Telegram → Настройки → Устройства → Подключить устройство.'
            self.key, self.rows, self.completed = secrets.token_urlsafe(32), [], 0
            self.changed = asyncio.Event()
            try:
                if not e.client or not e.client.is_connected():
                    await e.connect(credentials)
                self.client = e.client
                if await self.client.is_user_authorized():
                    await e.signed_in()
                    return self.state(private=True)
                changed = self.changed
                async def handler(_):
                    changed.set()
                self.handler = handler
                self.client.add_event_handler(handler, events.Raw(types.UpdateLoginToken))
                await self.export_locked()
                if self.phase == 'qr':
                    self.task = asyncio.create_task(self.run(), name='telegram-qr-login')
                else:
                    self.detach()
                return self.state(private=True)
            except errors.SessionPasswordNeededError:
                self.password()
                self.detach()
                return self.state(private=True)
            except Exception as exc:
                self.failed(exc)
                self.detach()
                await self.discard_locked()
                raise

    async def export_locked(self):
        e = self.engine
        credentials = e.store.read()['telegram']
        result = await self.client(functions.auth.ExportLoginTokenRequest(
            credentials['api_id'], credentials['api_hash'], []))
        for _ in range(2):
            if not isinstance(result, types.auth.LoginTokenMigrateTo):
                break
            await self.client._switch_dc(result.dc_id)
            result = await self.client(functions.auth.ImportLoginTokenRequest(result.token))
        if isinstance(result, types.auth.LoginTokenSuccess):
            await self.client._on_login(result.authorization.user)
            await e.signed_in()
        elif isinstance(result, types.auth.LoginToken):
            if not result.token or len(result.token) > 512:
                raise UserError('Telegram вернул некорректный QR. Начните вход заново.')
            self.expires = min(result.expires.timestamp(), self.until)
            if self.expires <= e.clock():
                raise UserError('Telegram вернул истёкший QR. Начните вход заново.')
            url = 'tg://login?token=' + base64.urlsafe_b64encode(result.token).decode('ascii').rstrip('=')
            qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=1, border=4)
            qr.add_data(url)
            qr.make(fit=True)
            self.rows = [''.join('1' if cell else '0' for cell in row) for row in qr.get_matrix()]
            e.diagnostics.emit('telegram', 'qr_created', stage='qr')
        else:
            raise UserError('Telegram вернул неподдерживаемый результат QR-входа.')

    def password(self):
        self.phase, self.rows = 'password', []
        self.message = 'QR подтверждён. Вернитесь в Zamolvi и введите облачный пароль Telegram.'
        self.engine.login_stage = 'password'
        self.engine.login_until = self.until
        self.engine.diagnostics.emit('telegram', 'qr_password', stage='password')
        self.task = asyncio.create_task(self.expire_password(), name='telegram-qr-password-expiry')

    async def expire_password(self):
        await asyncio.sleep(max(0, self.until - self.engine.clock()))
        async with self.engine.telegram_lock:
            if self.phase == 'password' and not self.engine.authorized:
                self.phase, self.rows = 'expired', []
                self.message = 'Время QR-входа истекло. Начните его заново в Zamolvi.'
                self.engine.login_stage = 'expired'
                self.engine.diagnostics.emit('telegram', 'qr_expired', stage='expired')
                await self.discard_locked()

    def authorized(self):
        self.phase, self.rows = 'authorized', []
        self.message = 'Telegram подключён. Вернитесь в Zamolvi.'
        self.completed = self.engine.clock()
        self.engine.diagnostics.emit('telegram', 'qr_authorized', stage='authorized')

    def failed(self, exc):
        e = self.engine
        self.phase, self.rows = 'failed', []
        e.login_stage = 'disconnected'
        if isinstance(exc, errors.FloodWaitError):
            e.login_block_until = e.clock() + exc.seconds
            self.message = f'Telegram просит подождать {exc.seconds} секунд перед новым входом.'
        elif isinstance(exc, UserError):
            self.message = str(exc)  # Only curated errors from this implementation.
        else:
            self.message = 'QR-вход не завершён. Сохраните диагностику и начните вход заново.'
        e.diagnostics.emit('telegram', 'qr_failed', exc, retry_after_s=getattr(exc, 'seconds', 0))

    async def run(self):
        e = self.engine
        try:
            while self.phase == 'qr' and e.clock() < self.until:
                try:
                    await asyncio.wait_for(self.changed.wait(), max(0.05, self.expires - e.clock()))
                except asyncio.TimeoutError:
                    pass
                self.changed.clear()
                async with e.telegram_lock:
                    if self.phase != 'qr' or e.client is not self.client or e.clock() >= self.until:
                        break
                    await self.export_locked()
            if self.phase == 'qr':
                async with e.telegram_lock:
                    self.phase, self.rows = 'expired', []
                    self.message = 'Время QR-входа истекло. Начните его заново в Zamolvi.'
                    e.login_stage = 'expired'
                    e.diagnostics.emit('telegram', 'qr_expired', stage='expired')
                    await self.discard_locked()
        except asyncio.CancelledError:
            raise
        except errors.SessionPasswordNeededError:
            self.password()
        except Exception as exc:
            async with e.telegram_lock:
                self.failed(exc)
                await self.discard_locked()
        finally:
            self.detach()

    def detach(self):
        if self.handler and self.client:
            self.client.remove_event_handler(self.handler)
        self.handler = None

    async def discard_locked(self):
        e = self.engine
        if not e.authorized:
            if e.client:
                await e.client.disconnect()
                e.client = None
            e.store.update(lambda s: s.update(session={}, qr_pending=False))

    async def cancel_locked(self, clear_session=True):
        task, self.task = self.task, None
        if task and not task.done() and task is not asyncio.current_task():
            task.cancel()
            with suppress(asyncio.CancelledError):
                await task
        self.detach()
        active = self.phase in ('qr', 'password', 'failed', 'expired')
        self.key, self.rows = '', []
        self.phase, self.message = 'idle', 'QR-вход не запущен.'
        self.until, self.completed = 0, 0
        if active and clear_session and not self.engine.authorized:
            await self.discard_locked()
            self.engine.login_stage = 'disconnected'

    async def cancel(self):
        async with self.engine.telegram_lock:
            await self.cancel_locked()
            self.engine.diagnostics.emit('telegram', 'qr_cancelled')
            return {'stage': 'authorized' if self.engine.authorized else 'idle', 'message': 'QR-вход закрыт.'}
