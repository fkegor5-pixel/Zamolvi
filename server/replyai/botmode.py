"""The owner's own Telegram bot: the commands of the auto-reply with a menu of buttons.

The owner's account creates it once through @BotFather; the token stays in the encrypted state.
Only the owner and the people he lets in may use it: a stranger's request comes to the owner with buttons.
"""
import asyncio
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
MENU = (  # (button, command); the emoji is the section
    ("🎮 Онлайн", "онлайн"), ("🎮 Топ онлайн", "топ онлайн"),
    ("🎮 График онлайна", "онлайн график"), ("🎮 Куда вложить", "куда вложить гта"),
    ("🔫 Часы в CS2", "часы в кс"), ("🔫 Кто в игре", "кто в игре"),
    ("🔫 FACEIT", "фейсит"), ("🔫 Инвентарь", "инвентарь"),
    ("🔫 Уровень Steam", "уровень стим"), ("🔫 Скидки Steam", "стим игры"),
    ("🔫 Куда вложить", "куда вложить кс"),
    ("🏆 PGL матчи", "pgl"), ("🏆 PGL сетка", "pgl сетка"), ("🏆 PGL плей-офф", "pgl плей-офф"),
    ("🧰 Дембель", "дембель"), ("🧰 Курс", "курс"), ("🧰 Монетка", "монетка"), ("🧰 Рандом", "рандом"),
)
MENU_TEXT = ("🤖 <b>Zamolvi Bot</b>\n"
             "Нажмите кнопку или напишите команду текстом:\n"
             "<code>погода Москва</code>\n<code>онлайн Phoenix</code>\n<code>pgl vitality</code>\n"
             "<code>рандом 10-20</code>\n<code>напомни через 30 минут позвонить</code>")
OPEN = {"/start", "/menu", "/help", "меню", "команды", "помощь"}


def keyboard():
    buttons = [Button.inline(label, data=f"c{i}".encode()) for i, (label, _) in enumerate(MENU)]
    return [buttons[i:i + 2] for i in range(0, len(buttons), 2)]


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

    def state(self):
        return self.engine.store.read().get("bot") or {}

    def log(self, text):
        self.engine.log("Свой бот: " + text)

    # ---------------------------------------------------------------- creation and start

    async def tick(self):
        """From the engine housekeeping: creates the bot once, then keeps it running."""
        e = self.engine
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
            e.store.update(lambda s: s["bot"].update(token="", session=""))
            self.log("токен отозван в @BotFather. Напишите «создать бота» в «Избранное».")
            return
        except FAILURES as exc:
            await client.disconnect()
            self.log("не запущен: " + type(exc).__name__)
            return
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
            await self.client.disconnect()
            self.client = None

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
        if not event.is_private:
            return
        text = (event.raw_text or "").strip()
        if not self.allowed(event.sender_id):
            await self.request_access(event)
            return
        word = text.split("@")[0].lower()
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
        await self.run(event.chat_id, body)

    async def on_button(self, event):
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
        await event.answer()
        if data == b"menu":
            await self.client.send_message(event.chat_id, MENU_TEXT, parse_mode="html", buttons=keyboard())
        elif action.startswith("c") and action[1:].isdigit() and int(action[1:]) < len(MENU):
            await self.run(event.chat_id, MENU[int(action[1:])][1])

    async def run(self, chat, body):
        """Answers one command in the bot chat: a picture, a channel summary, a reminder or a text."""
        e = self.engine
        if not online.allowed(f"bot:{chat}", e.clock()):
            return  # commands too often
        try:
            if online.is_image_command(body):
                result = await asyncio.to_thread(online.image, body, samples=e.store.read().get("online_samples", []))
                if isinstance(result, bytes):
                    picture = io.BytesIO(result)
                    picture.name = "picture.png"
                    await self.client.send_file(chat, picture, caption=online.caption(body), buttons=back())
                    return
                answer = result
            elif online.digest_kind(body):
                await self.client.send_message(chat, "⏳ Читаю каналы, это займёт до минуты…")
                answer = await e.digest(str(chat), online.digest_kind(body))
            elif online.reminder(body, e.clock()):
                answer = e.add_reminder(str(chat), *online.reminder(body, e.clock()), owner=True, bot=True)
            else:
                answer = await asyncio.to_thread(online.answer, body)
            text, entities = render(answer, "auto", "", limit=4000)
            await self.client.send_message(chat, text, formatting_entities=entities, link_preview=False,
                                           buttons=back())
        except FAILURES as exc:
            self.log("ответ не отправлен: " + type(exc).__name__)

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
