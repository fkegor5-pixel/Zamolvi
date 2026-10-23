"""«бот …» gate, the «онлайн» command and the arzdata counts: only the count next to the server name counts."""
import os

from replyai import online

os.environ[online.COMMANDS_ENV] = "1"  # the owner's server: commands on


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


# real order from the profile: hours, last launch, then the game name; the previous game comes first
STEAM_RU = ("<div>Недавняя активность 660,4 ч. за последние 2 недели</div><div>357 ч. всего последний запуск 23 окт</div>"
            "<a>Pummel Party</a> Достижения: 0 из 31 <div>5 864 ч. всего<br>последний запуск 23 окт</div>"
            "<a>Counter-Strike 2</a> Global Sentinel")
STEAM_EN = "<div>1,234.5 hrs on record<br>last played on Oct 23</div><a>Counter-Strike 2</a>"


def test_hours_command_passes_the_gate_and_reads_each_profile():
    assert online.addressed("Часы в кс") == "Часы в кс"
    assert online.is_command("часы в кс!")
    online._cache.clear()
    pages = {online.STEAM[0][1]: STEAM_RU, online.STEAM[1][1]: STEAM_EN}
    assert online.cs_hours(fetcher=fake(pages), clock=lambda: 4000.0, key="") == \
        "sethome43 - 5 864 ч.\nneurax716 - 1,234.5 ч.\nnecroa716 - нет данных"


def test_hours_missing_when_cs2_is_not_on_the_profile():
    online._cache.clear()
    pages = {online.STEAM[0][1]: "<div>Dota 2 10 hrs on record</div>", online.STEAM[1][1]: STEAM_RU}
    assert online.cs_hours(fetcher=fake(pages), clock=lambda: 4100.0, key="").splitlines()[0] == "sethome43 - нет данных"


def test_online_command_still_lists_servers():
    online._cache.clear()
    pages = {online.EXCELLENT: EXC_JSON, online.SITES[0][1]: PAGE_SPACE, online.SITES[1][1]: PAGE_WED}
    assert online.answer("онлайн", fetcher=fake(pages), clock=lambda: 4200.0).startswith("Excellent - 99")


def api_pages(key, name, steamid, games):
    import json
    resolve = f"{online.STEAM_API}/ISteamUser/ResolveVanityURL/v1/?key={key}&vanityurl={name}"
    owned = (f"{online.STEAM_API}/IPlayerService/GetOwnedGames/v1/?key={key}&steamid={steamid}"
             f"&include_played_free_games=1&appids_filter%5B0%5D=730")
    return {resolve: json.dumps({"response": {"steamid": steamid, "success": 1}}),
            owned: json.dumps({"response": {"game_count": len(games), "games": games}})}


def test_api_gives_total_hours_for_every_account():
    online._cache.clear()
    pages = {}
    pages.update(api_pages("K", "sethome43", "1", [{"appid": 730, "playtime_forever": 351840}]))
    pages.update(api_pages("K", "neurax716", "2", [{"appid": 730, "playtime_forever": 59}]))
    pages.update(api_pages("K", "necroa716", "3", [{"appid": 730, "playtime_forever": 125 * 60}]))
    assert online.cs_hours(fetcher=fake(pages), clock=lambda: 5000.0, key="K") == \
        "sethome43 - 5 864 ч.\nneurax716 - 0 ч.\nnecroa716 - 125 ч."


def test_api_hidden_games_fall_back_to_the_profile_page():
    online._cache.clear()
    pages = {}
    for i, (name, _) in enumerate(online.STEAM):
        pages.update(api_pages("K", name, str(i), []))  # games hidden: the API returns no games
    pages[online.STEAM[0][1]] = STEAM_RU
    assert online.cs_hours(fetcher=fake(pages), clock=lambda: 6000.0, key="K").splitlines() == \
        ["sethome43 - 5 864 ч.", "neurax716 - нет данных", "necroa716 - нет данных"]


def test_demob_counts_down_and_passes_the_gate():
    assert online.addressed("Дембель") == "Дембель"
    end = online.DEMOB_AT.timestamp()
    left = 2 * 7 * 86400 + 3 * 86400 + 4 * 3600 + 5 * 60 + 6
    assert online.answer("дембель", clock=lambda: end - left) == "Недель: 2. Дней: 3. Часов: 4. Минут: 5. Секунд: 6"
    assert online.answer("дембель!", clock=lambda: end + 1) == "Дембель уже наступил!"


def test_commands_are_off_without_the_owner_setting():
    os.environ.pop(online.COMMANDS_ENV)
    try:
        for command in ("онлайн", "часы в кс", "дембель"):
            assert not online.is_command(command)
            assert online.addressed(command) is None  # no «бот»: no answer at all
        assert online.context("бот сколько онлайн на space", fetcher=lambda u: 1 / 0) == ""  # nothing fetched
    finally:
        os.environ[online.COMMANDS_ENV] = "1"
