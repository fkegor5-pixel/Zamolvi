"""«бот …» gate, the «онлайн» command and the arzdata counts: only the count next to the server name counts."""
from replyai import online


PAGE_SPACE = ("<html><script>var space = 1;</script><body>Arizona (Staging) 0 / 2000 "
              "<table><tr><td>Space</td><td>840 / 1000</td></tr></table></body></html>")
PAGE_WED = "<p>Wednesday 12 / 500</p>"
EXC_JSON = '{"mode":"live","online":true,"players":99,"max_players":1000,"hostname":"Exsellent Role Play"}'


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
    result = online.report(fetcher=fake({online.EXCELLENT: EXC_JSON, online.SITES[0][1]: PAGE_SPACE, online.SITES[1][1]: PAGE_WED}),
                           clock=lambda: 1000.0)
    assert result == "Excellent - 99\nSpace - 840\nWednesday - 12"


def test_report_says_no_data_when_site_fails():
    online._cache.clear()
    def broken(url):
        raise OSError("timeout")
    assert online.report(fetcher=broken, clock=lambda: 2000.0) == "Excellent - нет данных\nSpace - нет данных\nWednesday - нет данных"


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


def test_excellent_with_broken_json_says_no_data():
    online._cache.clear()
    pages = {online.EXCELLENT: "<html>maintenance</html>", online.SITES[0][1]: PAGE_SPACE, online.SITES[1][1]: PAGE_WED}
    assert online.report(fetcher=fake(pages), clock=lambda: 3000.0).splitlines()[0] == "Excellent - нет данных"


STEAM_EN = "<div>Recent Activity</div><div>Counter-Strike 2</div><div>5,864 hrs on record</div><div>last played on Oct 23</div>"
STEAM_RU = "<div>Недавняя активность</div><a>Counter-Strike 2</a><span>1 234,5 ч. всего</span>"


def test_hours_command_passes_the_gate_and_reads_each_profile():
    assert online.addressed("Часы в кс") == "Часы в кс"
    assert online.is_command("часы в кс!")
    online._cache.clear()
    pages = {online.STEAM[0][1]: STEAM_EN, online.STEAM[1][1]: STEAM_RU}
    assert online.answer("часы в кс", fetcher=fake(pages), clock=lambda: 4000.0) == \
        "sethome43 - 5,864 ч.\nneurax716 - 1 234,5 ч."


def test_hours_missing_when_cs2_is_not_on_the_profile():
    online._cache.clear()
    pages = {online.STEAM[0][1]: "<div>Dota 2 10 hrs on record</div>", online.STEAM[1][1]: STEAM_RU}
    assert online.answer("часы в кс", fetcher=fake(pages), clock=lambda: 4100.0).splitlines()[0] == "sethome43 - нет данных"


def test_online_command_still_lists_servers():
    online._cache.clear()
    pages = {online.EXCELLENT: EXC_JSON, online.SITES[0][1]: PAGE_SPACE, online.SITES[1][1]: PAGE_WED}
    assert online.answer("онлайн", fetcher=fake(pages), clock=lambda: 4200.0).startswith("Excellent - 99")
