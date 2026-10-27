"""The owner's own bot: creation through @BotFather, access requests and commands with buttons."""
import os
from types import SimpleNamespace

import pytest

from replyai import botmode, online

TOKEN = "123456789:AAH" + "x" * 32


class BotFather:
    """The owner's account talking to @BotFather: answers by the text sent."""
    def __init__(self, taken=("zamolvi_helper_bot",)):
        self.sent, self.messages, self.taken, self.notes = [], [], taken, []

    async def send_message(self, peer, text):
        if peer == "me":
            self.notes.append(text)
            return SimpleNamespace(id=0)
        self.sent.append(text)
        sent = SimpleNamespace(id=len(self.messages) * 2 + 1, out=True, message=text)
        if text in self.taken:
            reply = "Sorry, this username is already taken. Please try something different."
        elif text.endswith("_bot"):
            reply = f"Done! Use this token to access the HTTP API:\n{TOKEN}\nKeep your token secure"
        else:
            reply = "Alright. Next step."
        self.messages.append(SimpleNamespace(id=sent.id + 1, out=False, message=reply))
        return sent

    async def get_messages(self, peer, limit):
        return self.messages[-1:]


class BotClient:
    """The bot side: remembers what it sent."""
    def __init__(self):
        self.sent, self.files, self.deleted = [], [], []

    async def send_message(self, chat, text, **kw):
        self.sent.append((chat, text, kw))
        return SimpleNamespace(id=len(self.sent) + len(self.files))

    async def send_file(self, chat, file, **kw):
        self.files.append((chat, file.getvalue(), kw))
        return SimpleNamespace(id=len(self.sent) + len(self.files))

    async def delete_messages(self, chat, ids):
        self.deleted.append((chat, list(ids)))

    async def get_entity(self, user_id):
        return SimpleNamespace(first_name="Аня", last_name=None, username="anya")


def message(sender, text):
    answers = []

    async def respond(value, **kw):
        answers.append(value)

    async def get_sender():
        return SimpleNamespace(first_name="Аня", last_name=None, username="anya")
    return SimpleNamespace(is_private=True, sender_id=sender, chat_id=sender, raw_text=text, respond=respond,
                           get_sender=get_sender, answers=answers)


def button(sender, data):
    edits, notes = [], []

    async def answer(text=None, **kw):
        notes.append(text)

    async def edit(text, **kw):
        edits.append(text)
    return SimpleNamespace(sender_id=sender, chat_id=sender, data=data, answer=answer, edit=edit, edits=edits,
                           notes=notes)


@pytest.fixture
def commands_on():
    previous = os.environ.get(online.COMMANDS_ENV)
    os.environ[online.COMMANDS_ENV] = "1"
    online._last_command.clear()
    yield
    if previous is None:
        os.environ.pop(online.COMMANDS_ENV, None)
    else:
        os.environ[online.COMMANDS_ENV] = previous


@pytest.mark.asyncio
async def test_bot_is_created_through_botfather_with_a_free_username(env, commands_on):
    botmode.POLL = 0
    father = BotFather()
    env.engine.client = father
    assert await env.engine.bot.create()
    assert father.sent == ["/newbot", "Zamolvi Bot", "zamolvi_helper_bot", "zamolvi_helper2_bot"]
    saved = env.store.read()["bot"]
    assert saved["token"] == TOKEN and saved["username"] == "zamolvi_helper2_bot" and saved["tried"]
    assert "https://t.me/zamolvi_helper2_bot" in father.notes[0]


@pytest.mark.asyncio
async def test_failed_creation_is_tried_once_and_cancelled(env, commands_on):
    botmode.POLL = 0
    father = BotFather(taken=())
    father.send_message_original = father.send_message

    async def refuse(peer, text):
        sent = await father.send_message_original(peer, text)
        if text.endswith("_bot"):
            father.messages[-1].message = "Sorry, too many attempts. Please try again in 8 hours."
        return sent
    father.send_message = refuse
    env.engine.client = father
    assert not await env.engine.bot.create()
    assert father.sent[-1] == "/cancel" and father.sent.count("zamolvi_helper_bot") == 1
    assert not env.store.read()["bot"].get("token")
    await env.engine.bot.tick()  # no second automatic attempt
    assert father.sent.count("/newbot") == 1


@pytest.mark.asyncio
async def test_stranger_asks_owner_lets_in_then_commands_work(env, commands_on):
    bot = env.engine.bot
    bot.client, bot.owner_id = BotClient(), 1
    env.store.update(lambda s: s.update(bot={"token": TOKEN, "allowed": {}}))
    stranger = message(2, "онлайн")
    await bot.on_message(stranger)
    assert "личный бот" in stranger.answers[0]
    owner_chat, request, kw = bot.client.sent[-1]
    assert owner_chat == 1 and "Аня @anya просит доступ" in request
    await bot.on_button(button(2, b"allow:2"))  # not the owner: nothing changes
    assert not bot.allowed(2)
    press = button(1, b"allow:2")
    await bot.on_button(press)
    assert bot.allowed(2) and press.edits == ["✅ Доступ открыт: Аня @anya"]
    original = online.answer
    online.answer = lambda text: "Excellent - 99"
    try:
        await bot.on_message(message(2, "бот онлайн"))
        assert bot.client.sent[-1][:2] == (2, "Excellent - 99")
        online._last_command.clear()
        await bot.on_button(button(2, b"c" + str([c for _, c in botmode.MENU].index("курс")).encode()))
        assert bot.client.sent[-1][1] == "Excellent - 99"
    finally:
        online.answer = original
    await bot.on_message(message(2, "/menu"))
    assert bot.client.sent[-1][1] == botmode.MENU_TEXT and len(bot.client.sent[-1][2]["buttons"]) == (len(botmode.MENU) + 1) // 2
    await bot.on_button(button(1, b"drop:2"))
    assert not bot.allowed(2)


@pytest.mark.asyncio
async def test_bot_reminder_is_sent_by_the_bot(env, commands_on):
    bot = env.engine.bot
    bot.client, bot.owner_id = BotClient(), 1
    env.store.update(lambda s: s.update(bot={"token": TOKEN, "allowed": {}}))
    await bot.on_message(message(1, "напомни через 2 минуты позвонить"))
    assert bot.client.sent[-1][1].startswith("Напомню")
    env.clock[0] += 121
    await env.engine.send_reminders()  # the auto-reply is off: the bot still reminds
    assert bot.client.sent[-1][:2] == (1, "⏰ Напоминание: позвонить")


@pytest.mark.asyncio
async def test_help_in_a_chat_lists_the_commands_and_links_the_bot(env, commands_on):
    assert await env.engine.command_answer("команды") == online.HELP  # no bot yet
    env.store.update(lambda s: s.update(bot={"token": TOKEN, "username": "zamolvi_helper_bot"}))
    assert await env.engine.command_answer("Команды") == online.HELP + \
        "\n\n🤖 С кнопками — в боте @zamolvi_helper_bot\nhttps://t.me/zamolvi_helper_bot"


@pytest.mark.asyncio
async def test_next_command_removes_the_previous_answer(env, commands_on):
    bot = env.engine.bot
    bot.client, bot.owner_id = BotClient(), 1
    env.store.update(lambda s: s.update(bot={"token": TOKEN, "allowed": {}}))
    await bot.run(1, "монетка")
    first = len(bot.client.sent)
    assert bot.client.deleted == []
    online._last_command.clear()
    await bot.run(1, "дембель")
    assert bot.client.deleted == [(1, [first])] and bot.shown[1] == [len(bot.client.sent)]
