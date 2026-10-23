"""«бот …» gate, the «онлайн» command and the arzdata counts: only the count next to the server name counts."""
from replyai import online


PAGE_SPACE = ("<html><script>var space = 1;</script><body>Arizona (Staging) 0 / 2000 "
              "<table><tr><td>Space</td><td>840 / 1000</td></tr></table></body></html>")
PAGE_WED = "<p>Wednesday 12 / 500</p>"


def fake(pages):
    def fetcher(url):
        if url not in pages:
            raise OSError("no route")
        return pages[url]
    return fetcher


def test_only_bot_or_command_gets_an_answer():
    assert online.addressed("бот сколько онлайн на space") == "сколько онлайн на space"
    assert online.addressed("Бот, онлайн") == "онлайн"
    assert online.addressed("онлайн") == "онлайн"
    assert online.addressed("привет") is None
    assert online.addressed("ботинки лучше") is None   # «бот» must be a whole word
    assert online.addressed("робот, онлайн") is None
    assert online.addressed("") is None


def test_command_is_only_the_word_online():
    assert online.is_command("онлайн!")
    assert not online.is_command("онлайн space")


def test_count_is_the_one_next_to_the_name():
    text = online.plain(PAGE_SPACE)
    assert online.count(text, "Space") == "840"
    assert "var space" not in text


def test_report_lists_each_server():
    online._cache.clear()
    result = online.report(fetcher=fake({online.SITES[0][1]: PAGE_SPACE, online.SITES[1][1]: PAGE_WED}),
                           clock=lambda: 1000.0)
    assert result == "Space - 840\nWednesday - 12"


def test_report_says_no_data_when_site_fails():
    online._cache.clear()
    def broken(url):
        raise OSError("timeout")
    assert online.report(fetcher=broken, clock=lambda: 2000.0) == "Space - нет данных\nWednesday - нет данных"


def test_context_empty_without_space_question_and_no_fetch():
    calls = []
    assert online.context("Привет", fetcher=lambda u: calls.append(u) or PAGE_SPACE) == ""
    assert calls == []


def test_page_is_cached_for_a_minute():
    calls = []

    def fetcher(url):
        calls.append(url)
        return PAGE_SPACE

    now = [5000.0]
    clock = lambda: now[0]
    online._cache.clear()
    online.page(online.SITES[0][1], fetcher, clock)
    now[0] += 30
    online.page(online.SITES[0][1], fetcher, clock)
    assert len(calls) == 1
    now[0] += 120
    online.page(online.SITES[0][1], fetcher, clock)
    assert len(calls) == 2


def test_context_gives_the_count_to_the_model():
    online._cache.clear()
    block = online.context("сколько онлайн на space", fetcher=fake({online.SITES[0][1]: PAGE_SPACE}),
                           clock=lambda: 9000.0)
    assert "840 / 1000" in block
