"""The owner's own Telegram bot: the commands of the auto-reply with a menu of buttons.

The owner's account creates it once through @BotFather; the token stays in the encrypted state.
Only the owner and the people he lets in may use it: a stranger's request comes to the owner with buttons.
"""
import asyncio
import contextlib
import io
import re

from telethon import Button, TelegramClient, errors, events, functions, types
from telethon.sessions import StringSession

from . import online
from .formatting import render

NAME = "Zamolvi Bot"
USERNAMES = ("zamolvi_helper_bot",) + tuple(f"zamolvi_helper{i}_bot" for i in range(2, 6))
TOKEN = re.compile(r"\b\d{6,12}:[A-Za-z0-9_-]{30,}\b")
RETRY = 300          # seconds between attempts to start the bot
WAIT = 20            # checks of @BotFather for an answer, POLL seconds apart
POLL = 1
FAILURES = (errors.RPCError, OSError, ValueError, asyncio.TimeoutError)
CHECK = 60           # seconds between checks that the running bot still answers Telegram
PAUSE = 2            # seconds between commands of one person; the chats keep online.COOLDOWN
MENU = (  # (button, command); the emoji is the section
    ("🎮 Онлайн", "онлайн"), ("🎮 Топ онлайн", "топ онлайн"),
    ("🎮 График онлайна", "онлайн график"),
    ("🔫 Часы в CS2", "часы в кс"), ("🔫 Кто в игре", "кто в игре"),
    ("🔫 FACEIT", "фейсит"), ("🔫 Инвентарь", "инвентарь"),
    ("🔫 Уровень Steam", "уровень стим"), ("🔫 Скидки Steam", "стим игры"),
    ("🏆 PGL матчи", "pgl"), ("🏆 PGL сетка", "pgl сетка"), ("🏆 PGL плей-офф", "pgl плей-офф"),
    ("🧰 Дембель", "дембель"), ("🧰 Курс", "курс"), ("🧰 Монетка", "монетка"), ("🧰 Рандом", "рандом"),
    ("🧰 Новости", "новости"), ("🧰 Получить деньги", "получить деньги"),
)
MENU_TEXT = ("🤖 <b>Zamolvi Bot</b>\n"
             "Нажмите кнопку или напишите команду текстом:\n"
             "<code>погода Москва</code>\n<code>онлайн Phoenix</code>\n<code>pgl vitality</code>\n"
             "<code>рандом 10-20</code>\n<code>напомни через 30 минут позвонить</code>")
OPEN = {"/start", "/menu", "/help", "меню", "команды", "помощь"}


UNITS = {"м": ("минут", "30 позвонить маме"), "ч": ("часов", "2 забрать посылку"),
         "д": ("дней", "363 поздравить с др")}  # «⏰ Напомнить»: unit -> (word for «напомни через», example)
REMIND_ANSWER = re.compile(r"^(\d{1,15})\s+(.+)$", re.DOTALL)


def keyboard():
    buttons = [Button.inline(label, data=f"c{i}".encode()) for i, (label, _) in enumerate(MENU)]
    return [[Button.inline("⏰ Напомнить", data=b"remind")]] + [buttons[i:i + 2] for i in range(0, len(buttons), 2)]


def units():
    return [[Button.inline("Минут", data="ru:м".encode()), Button.inline("Часов", data="ru:ч".encode()),
             Button.inline("Дней", data="ru:д".encode())], [Button.inline("📋 Меню", data=b"menu")]]


def back():
    return [[Button.inline("📋 Меню", data=b"menu")]]


def name_of(user):
    name = " ".join(x for x in (getattr(user, "first_name", None), getattr(user, "last_name", None)) if x) or "Без имени"
    return name + (f" @{user.username}" if getattr(user, "username", None) else "")


class OwnBot:
    def __init__(self, engine):
        self.engine = engine
        self.client = None
        self.owner_id = None
        self.next_try = 0
        self.asked = set()  # strangers whose request was already sent to the owner
        self.shown = {}  # chat -> ids of the last command answer, removed when the next one comes
        self.last_run = {}  # chat -> time of the last command
        self.next_check = 0
        self.waiting = {}  # chat -> unit of «⏰ Напомнить» while the bot waits for «число текст»
        self.problem = ""  # why the bot does not run now, for the status of the app

    def state(self):
        return self.engine.store.read().get("bot") or {}

    def log(self, text):
        self.engine.log("Свой бот: " + text)
        print("Свой бот: " + text, flush=True)  # also to the service journal, for tools/update.sh

    # ---------------------------------------------------------------- creation and start

    def status(self):
        """One line for the notification of the app; '' when there is no own bot on this server."""
        saved = self.state()
        name = "@" + saved["username"] if saved.get("username") else ""
        if self.client:
            return f"🤖 Бот {name} работает"
        if saved.get("token"):
            return f"⚠️ Бот {name} не работает" + (f": {self.problem}" if self.problem else ", перезапускаю")
        return "⚠️ Бот не создан: напишите «создать бота» в «Избранное»" if saved.get("tried") else ""

    async def check(self):
        """A running bot that stopped answering Telegram (a hang) is restarted at the next tick."""
        now = self.engine.clock()
        if not self.client or now < self.next_check:
            return
        self.next_check = now + CHECK
        try:
            if not self.client.is_connected():
                raise ConnectionError("нет связи с Telegram")
            await asyncio.wait_for(self.client.get_me(), 20)
        except Exception as exc:
            self.log(f"не отвечает ({type(exc).__name__}), перезапускаю")
            await self.stop()
            self.next_try = 0

    async def tick(self):
        """From the engine housekeeping: creates the bot once, then keeps it running and checks it."""
        e = self.engine
        await self.check()
        if self.client or not online.enabled() or not e.authorized or not e.client or e.clock() < self.next_try:
            return
        self.next_try = e.clock() + RETRY
        if not self.state().get("token"):
            if self.state().get("tried"):
                return  # one automatic attempt; «создать бота» in «Избранное» tries again
            await self.create()
        if self.state().get("token"):
            await self.start()

    async def create(self):
        """Creates the bot through @BotFather with the owner's account; True when the token is saved."""
        e = self.engine
        e.store.update(lambda s: s.setdefault("bot", {}).update(tried=True))
        token = username = None
        try:
            await self.ask("/newbot")
            await self.ask(NAME)
            for name in USERNAMES:
                reply = await self.ask(name)
                found = TOKEN.search(reply)
                if found:
                    token, username = found.group(), name
                    break
                if "taken" not in reply.lower() and "занят" not in reply.lower():
                    break  # another refusal (too many attempts, invalid name): do not try more names
            if not token:
                await self.ask("/cancel")
        except FAILURES as exc:
            self.log("не удалось создать через @BotFather: " + type(exc).__name__)
        if not token:
            self.log("не создан. Напишите «создать бота» в «Избранное», чтобы попробовать ещё раз.")
            return False
        e.store.update(lambda s: s["bot"].update(token=token, username=username, session="", allowed={}))
        await self.note(f"🤖 Создан ваш бот @{username}: https://t.me/{username}\n"
                        "Откройте его и нажмите «Запустить» — тогда он сможет присылать вам запросы на доступ.")
        self.log(f"создан @{username}.")
        return True

    async def ask(self, text):
        """Sends a message to @BotFather and returns its next answer ('' when there is none in time)."""
        client, lock = self.engine.client, self.engine.telegram_lock
        async with lock:
            sent = await client.send_message("BotFather", text)
        for _ in range(WAIT):
            await asyncio.sleep(POLL)
            async with lock:
                last = await client.get_messages("BotFather", limit=1)
            if last and last[0].id > sent.id and not last[0].out:
                return last[0].message or ""
        return ""

    async def note(self, text):
        """A message to the owner's «Избранное» from his own account."""
        try:
            async with self.engine.telegram_lock:
                await self.engine.client.send_message("me", text)
        except FAILURES as exc:
            self.log("сообщение в «Избранное» не отправлено: " + type(exc).__name__)

    async def start(self):
        e, saved = self.engine, self.state()
        credentials = e.store.read()["telegram"]
        client = TelegramClient(StringSession(saved.get("session") or ""), credentials["api_id"],
                                credentials["api_hash"])
        try:
            await client.start(bot_token=saved["token"])
            self.owner_id = (await e.client.get_me()).id
            await client(functions.bots.SetBotCommandsRequest(
                scope=types.BotCommandScopeDefault(), lang_code="",
                commands=[types.BotCommand("menu", "Меню команд")] + [
                    types.BotCommand(slash[1:], command) for slash, command in online.SLASH.items()
                    if slash not in ("/help", "/commands")]))
        except errors.AccessTokenInvalidError:
            await client.disconnect()
            self.problem = "токен отозван"
            e.store.update(lambda s: s["bot"].update(token="", session=""))
            self.log("токен отозван в @BotFather. Напишите «создать бота» в «Избранное».")
            return
        except FAILURES as exc:
            await client.disconnect()
            self.problem = "не запустился, повтор через 5 минут"
            self.log("не запущен: " + type(exc).__name__)
            return
        self.problem = ""
        session = client.session.save()
        e.store.update(lambda s: s["bot"].update(session=session))
        client.add_event_handler(self.on_message, events.NewMessage(incoming=True))
        client.add_event_handler(self.on_button, events.CallbackQuery())
        self.client = client
        self.log(f"@{saved.get('username')} запущен.")

    async def recreate(self):
        """«создать бота» in «Избранное»: a new bot (also after a failed attempt)."""
        await self.stop()
        self.engine.store.update(lambda s: s.update(bot={}))
        self.next_try = 0
        if await self.create():
            await self.start()

    async def stop(self):
        if self.client:
            client, self.client = self.client, None
            with contextlib.suppress(Exception):
                await client.disconnect()

    # ---------------------------------------------------------------- access

    def allowed(self, user_id):
        return user_id == self.owner_id or str(user_id) in self.state().get("allowed", {})

    async def request_access(self, event):
        user_id = event.sender_id
        if user_id in self.asked:
            await event.respond("Запрос уже у владельца. Дождитесь ответа.")
            return
        self.asked.add(user_id)
        who = name_of(await event.get_sender())
        try:
            await self.client.send_message(self.owner_id, f"🔐 {who} просит доступ к боту.", buttons=[[
                Button.inline("✅ Разрешить", data=f"allow:{user_id}".encode()),
                Button.inline("❌ Отклонить", data=f"deny:{user_id}".encode())]])
            await event.respond("🔒 Это личный бот. Запрос на доступ отправлен владельцу.")
        except FAILURES:
            self.asked.discard(user_id)
            await event.respond("🔒 Это личный бот.")

    async def decide(self, event, action, user_id):
        """Owner's buttons: allow, deny or drop a person."""
        if action == "allow":
            try:
                who = name_of(await self.client.get_entity(user_id))
            except FAILURES:
                who = str(user_id)
            self.engine.store.update(lambda s: s["bot"].setdefault("allowed", {}).update({str(user_id): who}))
            await event.edit(f"✅ Доступ открыт: {who}")
            await self.safe_send(user_id, "✅ Владелец открыл вам доступ.", buttons=keyboard())
        elif action == "deny":
            await event.edit("❌ Запрос отклонён.")
        else:
            self.engine.store.update(lambda s: s["bot"].setdefault("allowed", {}).pop(str(user_id), None))
            await event.edit("Доступ закрыт.")
        self.asked.discard(user_id)

    async def users(self, chat):
        people = self.state().get("allowed", {})
        if not people:
            await self.client.send_message(chat, "Доступ есть только у вас. Новые запросы придут сюда с кнопками.")
            return
        await self.client.send_message(chat, "👥 У кого есть доступ (нажмите, чтобы убрать):", buttons=[
            [Button.inline("➖ " + who, data=f"drop:{user_id}".encode())] for user_id, who in people.items()])

    # ---------------------------------------------------------------- commands

    async def on_message(self, event):
        try:
            await self.handle_message(event)
        except Exception as exc:  # Telethon drops handler errors silently: show them in the log of the app
            self.log(f"ошибка: {type(exc).__name__}: {exc}"[:300])

    async def on_button(self, event):
        try:
            await self.handle_button(event)
        except Exception as exc:
            self.log(f"ошибка кнопки: {type(exc).__name__}: {exc}"[:300])

    def ready(self, chat):
        """False when the person sends commands faster than PAUSE seconds apart."""
        now = self.engine.clock()
        if now - self.last_run.get(chat, float("-inf")) < PAUSE:
            return False
        self.last_run[chat] = now
        return True

    async def handle_message(self, event):
        if not event.is_private:
            return
        text = (event.raw_text or "").strip()
        if not self.allowed(event.sender_id):
            await self.request_access(event)
            return
        word = text.split("@")[0].lower()
        unit = self.waiting.get(event.chat_id)
        if unit and not text.startswith("/"):
            found = REMIND_ANSWER.match(text)
            if not found:
                await event.respond(f"Напишите число и текст, например: {UNITS[unit][1]}", buttons=back())
                return
            del self.waiting[event.chat_id]
            await self.run(event.chat_id, f"напомни через {found.group(1)} {UNITS[unit][0]} {found.group(2)}")
            return
        self.waiting.pop(event.chat_id, None)  # any other command cancels «⏰ Напомнить»
        if word in OPEN:
            await self.client.send_message(event.chat_id, MENU_TEXT, parse_mode="html", buttons=keyboard())
            return
        if word == "/users" and event.sender_id == self.owner_id:
            await self.users(event.chat_id)
            return
        body = online.addressed(text)
        body = text if body is None else body
        if not online.is_command(body):
            await event.respond("Не знаю такой команды. Нажмите /menu", buttons=back())
            return
        if self.ready(event.chat_id):
            await self.run(event.chat_id, body)

    async def handle_button(self, event):
        data = event.data or b""
        action, _, user = data.decode(errors="ignore").partition(":")
        if action in ("allow", "deny", "drop") and user.isdigit():
            if event.sender_id == self.owner_id:
                await event.answer()
                await self.decide(event, action, int(user))
            else:
                await event.answer("Только для владельца")
            return
        if not self.allowed(event.sender_id):
            await event.answer("Нет доступа")
            return
        if data == b"remind":
            await event.answer()
            await self.client.send_message(event.chat_id, "⏰ Через сколько напомнить?", buttons=units())
            return
        if action == "ru" and user in UNITS:
            await event.answer()
            self.waiting[event.chat_id] = user
            await self.client.send_message(
                event.chat_id, f"Напишите, через сколько {UNITS[user][0]} и о чём.\nНапример: {UNITS[user][1]}",
                buttons=back())
            return
        if data == b"menu":
            self.waiting.pop(event.chat_id, None)
            await event.answer()  # «📋 Меню» under an answer: the answer gives way to the menu
            try:
                await self.client.delete_messages(event.chat_id, self.shown.pop(event.chat_id, []))
                await self.client.send_message(event.chat_id, MENU_TEXT, parse_mode="html", buttons=keyboard())
            except FAILURES as exc:
                self.log("меню не отправлено: " + type(exc).__name__)
        elif action.startswith("c") and action[1:].isdigit() and int(action[1:]) < len(MENU):
            if not self.ready(event.chat_id):
                await event.answer("Подождите пару секунд")
                return
            await event.answer("⏳ " + MENU[int(action[1:])][0][2:])  # a toast at once: the answer may take seconds
            await self.run(event.chat_id, MENU[int(action[1:])][1])
        else:
            await event.answer("Кнопка устарела, откройте /menu")

    async def run(self, chat, body):
        """Answers one command in the bot chat: a picture, a channel summary, a reminder or a text."""
        e = self.engine
        old, new = self.shown.pop(chat, []), []
        try:
            if online.is_image_command(body):
                result = await asyncio.to_thread(online.image, body, samples=e.store.read().get("online_samples", []))
                if isinstance(result, bytes):
                    picture = io.BytesIO(result)
                    picture.name = "picture.png"
                    new.append(await self.client.send_file(chat, picture, caption=online.caption(body), buttons=back()))
                    return
                answer = result
            elif online.digest_kind(body):
                new.append(await self.client.send_message(chat, "⏳ Читаю каналы, это займёт до минуты…"))
                answer = await e.digest(str(chat), online.digest_kind(body))
            elif online.reminder(body, e.clock()):
                answer = e.add_reminder(str(chat), *online.reminder(body, e.clock()), owner=True, bot=True)
            else:
                answer = await asyncio.to_thread(online.answer, body)
            text, entities = render(answer, "auto", "", limit=4000)
            new.append(await self.client.send_message(chat, text, formatting_entities=entities, buttons=back(),
                                                      link_preview=bool(online.MONEY_COMMAND.match(body))))
        except FAILURES as exc:
            self.log("ответ не отправлен: " + type(exc).__name__)
        finally:
            self.shown[chat] = [m.id for m in new if m is not None]
            if old:  # one answer at a time keeps the chat clean; removed after the new one is there
                try:
                    await self.client.delete_messages(chat, old)
                except FAILURES:
                    pass  # older than 48 hours or already deleted by the person

    async def safe_send(self, chat, text, buttons=None):
        """A message from the bot; False when it cannot be sent (bot not running, the person never started it)."""
        if not self.client:
            return False
        try:
            await self.client.send_message(chat, text, buttons=buttons)
            return True
        except FAILURES as exc:
            self.log("сообщение не отправлено: " + type(exc).__name__)
            return False
