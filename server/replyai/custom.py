"""Commands the user makes himself: a word, where to look, how to answer. The connected model writes the answer."""
import asyncio
import re
import urllib.parse

from .errors import UserError
from . import online

MAX_COMMANDS = 20
MAX_SOURCES = 5
SOURCE_CHARS = 6000     # of each page given to the model
QUERY = "{запрос}"      # in a link: replaced by the words after the command
CHANNEL = re.compile(r"^(?:@|https?://t\.me/(?:s/)?)([A-Za-z0-9_]{4,32})/?$")
WORD = re.compile(r"^[^\s].{0,39}$")
TASK = ("Ты отвечаешь на команду «{word}» в переписке. Отвечай кратко, обычным текстом без Markdown.{data} "
        "Задание владельца команды:\n{task}")
DATA = " Пиши только по данным из сообщения; если нужного там нет — так и скажи."


def clean(commands):
    """The commands as saved; raises UserError on a wrong one."""
    if len(commands) > MAX_COMMANDS:
        raise UserError(f"Не больше {MAX_COMMANDS} команд.")
    result, words = [], set()
    for c in commands:
        word = " ".join(str(c.get("word", "")).split())
        task = str(c.get("task", "")).strip()
        sources = [s.strip() for s in c.get("sources", []) if str(s).strip()]
        if not WORD.match(word):
            raise UserError("Слово команды: от 1 до 40 символов.")
        if word.lower() in words:
            raise UserError(f"Команда «{word}» уже есть.")
        if not task or len(task) > 1000:
            raise UserError(f"«{word}»: опишите, как отвечать (до 1000 символов).")
        if len(sources) > MAX_SOURCES:
            raise UserError(f"«{word}»: не больше {MAX_SOURCES} источников.")
        for s in sources:
            if not (CHANNEL.match(s) or re.match(r"^https?://[^\s/]+\.[^\s]+$", s)):
                raise UserError(f"«{word}»: источник «{s[:60]}» — нужна ссылка https://… или канал @имя.")
        words.add(word.lower())
        result.append({"word": word, "sources": sources, "task": task})
    return result


def match(commands, text):
    """(command, words after it) when the message is one of the commands; None otherwise."""
    text = " ".join((text or "").split())
    for c in commands or []:
        word = c["word"]
        if text.lower() == word.lower() or text.lower().rstrip(".!?") == word.lower():
            return c, ""
        if text.lower().startswith(word.lower() + " "):
            return c, text[len(word):].strip()[:200]
    return None


def url(source, query):
    """The address to read: a channel goes through its public web preview, «{запрос}» gets the words."""
    m = CHANNEL.match(source)
    if m:
        return "https://t.me/s/" + m.group(1), True
    return source.replace(QUERY, urllib.parse.quote(query)), False


def gather(command, query, fetcher=None):
    """Text of every source, cut to fit; a note instead of a source that cannot be read."""
    parts = []
    for source in command["sources"]:
        address, channel = url(source, query)
        text = online.page(address, fetcher or online.fetch)
        if not text:
            parts.append(f"[{source}: не удалось прочитать]")
            continue
        # a channel preview shows the newest posts last
        parts.append(f"[{source}]\n" + (text[-SOURCE_CHARS:] if channel else text[:SOURCE_CHARS]))
    return "\n\n".join(parts)


async def answer(ai, config, command, query, fetcher=None):
    """The answer of the connected model to the command."""
    found = await asyncio.to_thread(gather, command, query, fetcher)
    request = (f"Запрос: {query}\n\n" if query else "") + (f"Данные:\n{found}" if found else "") or command["word"]
    system = TASK.format(word=command["word"], task=command["task"], data=DATA if command["sources"] else "")
    return (await ai.complete(config, system, request, 800)).strip()
