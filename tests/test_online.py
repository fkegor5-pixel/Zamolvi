"""Live «space» player count: only on request, cached, and the page is data, not instructions."""
from replyai import online


PAGE = """<html><head><script>var space = 1;</script></head><body>
<table><tr><td>Arizona RP</td><td>32</td><td>Space</td><td>840 / 1000</td></tr></table>
<p>Обновлено 11:36:02</p></body></html>"""


def test_asks_about_space_only_with_a_count_question():
    assert online.wanted("Сколько онлайн на space?")
    assert online.wanted("сколько человек на сервере Space")
    assert not online.wanted("Привет, как дела?")          # no server named
    assert not online.wanted("space — хорошее слово")      # no question about the count


def test_excerpt_drops_scripts_and_tags_and_keeps_the_count():
    text = online.excerpt(PAGE)
    assert "840 / 1000" in text
    assert "var space" not in text
    assert "<" not in text


def test_excerpt_none_without_space():
    assert online.excerpt("<p>Нет данных</p>") is None


def test_context_empty_for_other_messages():
    calls = []
    assert online.context("Привет", fetcher=lambda: calls.append(1) or PAGE) == ""
    assert calls == []  # nothing fetched for an ordinary message


def test_context_gives_data_and_caches_it():
    calls = []

    def fetcher():
        calls.append(1)
        return PAGE

    now = [1000.0]
    clock = lambda: now[0]
    first = online.context("сколько онлайн на space", fetcher=fetcher, clock=clock)
    assert "840 / 1000" in first
    now[0] += 30
    online.context("онлайн space", fetcher=fetcher, clock=clock)
    assert len(calls) == 1  # second request within a minute reuses the cache
    now[0] += 120
    online.context("онлайн space", fetcher=fetcher, clock=clock)
    assert len(calls) == 2


def test_context_says_unavailable_when_site_fails():
    def broken():
        raise OSError("timeout")

    block = online.context("онлайн space", fetcher=broken, clock=lambda: 5000.0)
    assert "НЕДОСТУПНЫ" in block
    assert "не называй никаких цифр" in block
