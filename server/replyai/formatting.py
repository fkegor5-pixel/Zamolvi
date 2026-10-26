"""Render AI text as Telegram entities; the visible AI label is always plain text."""
from html import escape
from urllib.parse import quote
import re

from telethon.extensions import html, markdown
from telethon.tl.types import (MessageEntityBold, MessageEntityItalic, MessageEntityUnderline,
                              MessageEntityStrike, MessageEntityCode, MessageEntityPre, MessageEntityTextUrl)

MODES = {"auto", "plain", "bold", "underline", "mono"}
AUTO_INSTRUCTION = (
    "Оформляй ответ для Telegram умеренно: <b>жирный</b> для главной мысли, "
    "<u>подчёркнутый</u> для отдельного акцента, <code>моноширинный</code> для кода "
    "и команд; для блока кода — <pre>код</pre>. Можно использовать <i>курсив</i>. "
    "Не выделяй весь ответ сразу. Используй только эти HTML-теги без атрибутов; "
    "не используй Markdown, заголовки с # и внешнюю обёртку ответа в блок кода. "
    "Символы <, > и & внутри кода экранируй как &lt;, &gt;, &amp;."
)
PLAIN_INSTRUCTION = "Пиши обычным текстом без HTML-тегов и Markdown-разметки."
_TAG = re.compile(r"</?(?:b|strong|i|em|u|code|pre|s|strike)>", re.I)
_ALL_TAGS = re.compile(r"<[^>]*>")
_NO_LINKS = re.compile(r"(?!)")
_STYLES = (MessageEntityBold, MessageEntityItalic, MessageEntityUnderline, MessageEntityStrike)
_CODE = (MessageEntityCode, MessageEntityPre)
_DELIMITERS = {"**": MessageEntityBold, "__": MessageEntityItalic, "++": MessageEntityUnderline,
               "~~": MessageEntityStrike, "`": MessageEntityCode, "```": MessageEntityPre}
_TYPES = {MessageEntityBold: "bold", MessageEntityItalic: "italic", MessageEntityUnderline: "underline",
          MessageEntityStrike: "strike", MessageEntityCode: "mono", MessageEntityPre: "mono"}


def utf16_length(value):
    return len(value.encode("utf-16-le")) // 2


def instruction(mode):
    return AUTO_INSTRUCTION if mode == "auto" else PLAIN_INSTRUCTION


def _parse(raw):
    if _TAG.search(raw):
        # Unsupported markup stays visible. Only presentation tags become entities.
        safe = _ALL_TAGS.sub(lambda m: m[0] if _TAG.fullmatch(m[0]) else escape(m[0]), raw)
        return html.parse(safe)
    return markdown.parse(raw, delimiters=_DELIMITERS, url_re=_NO_LINKS)


def render(answer, mode="auto", prefix="", limit=1600):
    raw = answer.encode("utf-8", errors="replace").decode()[:limit]
    if mode == "plain" or mode not in MODES:
        return prefix + raw, []
    try:
        text, entities = _parse(raw)
    except (ValueError, TypeError, IndexError):
        text, entities = raw, []
    if not text.strip():
        text, entities = raw, []
    if mode in {"bold", "underline", "mono"}:
        kind = {"bold": MessageEntityBold, "underline": MessageEntityUnderline, "mono": MessageEntityCode}[mode]
        entities = [kind(0, utf16_length(text.rstrip()))] if text.rstrip() else []
        if mode == "mono" and "\n" in text and entities:
            entities = [MessageEntityPre(0, entities[0].length, "")]
    limit = utf16_length(text)
    entities = [e for e in entities if type(e) in _TYPES and e.length > 0 and
                0 <= e.offset < e.offset + e.length <= limit]
    code = [e for e in entities if isinstance(e, _CODE)]
    entities = [e for e in entities if not isinstance(e, _STYLES) or
                not any(e.offset < c.offset + c.length and c.offset < e.offset + e.length for c in code)]
    shift = utf16_length(prefix)
    for entity in entities:
        entity.offset += shift
    return prefix + text, sorted(entities, key=lambda e: (e.offset, -e.length))


def with_links(text, entities, words, username=None):
    """Command words at line starts («онлайн — …») become links that put the command into the input field.

    Telegram shows bot-command entities of a person as plain text, so a t.me/<username>?text=… link is used: a tap
    opens the chat with that person and fills the message. Without a username the word is monospace (a tap copies).
    """
    found, pos = [], 0
    for line in text.split("\n"):
        word = next((w for w in sorted(words, key=len, reverse=True) if line.startswith(w + " — ")), None)
        if word:
            offset, length = utf16_length(text[:pos]), utf16_length(word)
            found.append(MessageEntityTextUrl(offset, length, f"https://t.me/{username}?text={quote(word)}")
                         if username else MessageEntityCode(offset, length))
        pos += len(line) + 1
    return text, sorted(entities + found, key=lambda e: (e.offset, -e.length))


def preview(answer, mode):
    text, entities = render(answer, mode)
    return {"answer": text, "entities": [{"type": _TYPES[type(e)], "offset": e.offset,
                                          "length": e.length} for e in entities]}
