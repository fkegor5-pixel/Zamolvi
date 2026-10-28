import asyncio

import pytest

from replyai import custom, online
from replyai.errors import UserError


def test_clean_checks_word_task_and_sources():
    ok = custom.clean([{"word": " Курс  BTC ", "task": "Цена биткоина", "sources": ["@binance", "https://x.com/q?{запрос}", ""]}])
    assert ok == [{"word": "Курс BTC", "task": "Цена биткоина", "sources": ["@binance", "https://x.com/q?{запрос}"]}]
    for bad in ({"word": "", "task": "a"}, {"word": "a", "task": ""}, {"word": "a", "task": "b", "sources": ["ftp://x"]}):
        with pytest.raises(UserError):
            custom.clean([bad])
    with pytest.raises(UserError):
        custom.clean([{"word": "А", "task": "b"}, {"word": "а", "task": "c"}])


def test_match_takes_the_words_after_the_command():
    commands = [{"word": "Погода", "task": "t", "sources": []}]
    assert custom.match(commands, "погода!") == (commands[0], "")
    assert custom.match(commands, "погода  Алматы") == (commands[0], "Алматы")
    assert custom.match(commands, "какая погода") is None
    assert custom.match(None, "погода") is None


def test_answer_reads_sources_and_gives_them_to_the_model():
    online._cache.clear()
    seen, asked = [], {}

    def fetcher(url):
        seen.append(url)
        return "<p>Новость дня</p>"

    class Ai:
        async def complete(self, config, system, text, max_tokens):
            asked.update(system=system, text=text)
            return " Коротко: новость дня "

    command = {"word": "новости", "task": "Сводка", "sources": ["@channel1", "https://site.ru/s?q={запрос}"]}
    answer = asyncio.run(custom.answer(Ai(), {}, command, "мир войны", fetcher))
    assert answer == "Коротко: новость дня"
    assert seen == ["https://t.me/s/channel1", "https://site.ru/s?q=%D0%BC%D0%B8%D1%80%20%D0%B2%D0%BE%D0%B9%D0%BD%D1%8B"]
    assert "Новость дня" in asked["text"] and "Запрос: мир войны" in asked["text"]
    assert "Сводка" in asked["system"] and "только по данным" in asked["system"]
