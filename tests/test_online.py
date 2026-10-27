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


def test_online_alone_or_with_a_server_name_is_a_command():
    assert online.is_command("онлайн!")
    assert online.is_command("онлайн space")  # «онлайн <сервер>»
    assert not online.is_command("онлайн 123") and not online.is_command("сколько онлайн")


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
    assert online.answer("дембель", clock=lambda: end - left).splitlines()[0] == \
        "Дней: 17. Часов: 4. Минут: 5"  # 2 weeks and 3 days are 17 days
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



import json
import random
import urllib.parse

ROWS_PAGE = ("<h2>Arizona RP</h2> Arizona (Staging) 0 / 2000 0% · очередь 0 "
             "<div>1</div><div>Phoenix</div><div>331 / 1000</div><div>33%</div><div>Свободен</div><b>0</b>"
             "<div>2</div><div>Saint-Rose</div><div>180 / 1000</div><div>18%</div><div>Свободен</div><b>0</b>"
             "<div>32</div><div>Space</div><div>854 / 1000</div><div>85%</div><div>Загружен</div><b>3</b>"
             "<h2>Vice City</h2><div>1</div><div>Vice City</div><div>423 / 750</div><div>56%</div><div>Свободен</div><b>0</b>")


def test_help_lists_the_commands():
    assert "\nонлайн — " in online.answer("команды") and online.answer("Помощь") == online.HELP
    assert online.addressed("/pgl_grid") == "pgl сетка" and online.addressed("/Online@x") == "онлайн"
    for word in online.COMMAND_WORDS:
        assert online.is_command(word), word
    from replyai.formatting import render, with_links

    def marked(username):
        message, entities = with_links(*render(online.HELP, "auto", "[AI] "), online.COMMAND_WORDS, username)
        assert "<b>" not in message
        return [(message.encode("utf-16-le")[2 * e.offset:2 * (e.offset + e.length)].decode("utf-16-le"),
                 getattr(e, "url", None)) for e in entities if type(e).__name__ in ("MessageEntityTextUrl",
                                                                                     "MessageEntityCode")]
    links = [x for x in marked("owner") if x[1]]
    assert len(links) == len(online.COMMAND_WORDS) - 1  # every command but «команды» itself
    assert ("онлайн график", "https://t.me/owner?text=%D0%BE%D0%BD%D0%BB%D0%B0%D0%B9%D0%BD%20%D0%B3%D1%80%D0%B0%D1%84%D0%B8%D0%BA") in links
    assert ("дембель", None) in marked(None)  # no username: monospace, a tap copies
    assert len(online.HELP) < 1600


def test_server_by_name_and_top_from_the_full_list():
    online._cache.clear()
    get = fake({online.ARZ_ALL: ROWS_PAGE})
    assert online.answer("онлайн space", fetcher=get, clock=lambda: 100.0) == "Space - 854 / 1000"
    assert online.answer("онлайн Saint-Rose", fetcher=get, clock=lambda: 100.0) == "Saint-Rose - 180 / 1000"
    assert "не найден" in online.answer("онлайн Нигде", fetcher=get, clock=lambda: 100.0)
    assert online.answer("топ онлайн", fetcher=get, clock=lambda: 100.0).splitlines() == [
        "1. Space - 854 / 1000", "2. Vice City - 423 / 750", "3. Phoenix - 331 / 1000", "4. Saint-Rose - 180 / 1000"]


def test_who_is_in_game():
    online._cache.clear()
    pages = {}
    for i, (name, _) in enumerate(online.STEAM):
        pages[f"{online.STEAM_API}/ISteamUser/ResolveVanityURL/v1/?key=K&vanityurl={name}"] = \
            json.dumps({"response": {"steamid": str(i), "success": 1}})
    pages[f"{online.STEAM_API}/ISteamUser/GetPlayerSummaries/v2/?key=K&steamids=0,1,2"] = json.dumps({"response": {
        "players": [{"steamid": "0", "personastate": 1, "gameextrainfo": "Counter-Strike 2"},
                    {"steamid": "1", "personastate": 0}]}})
    assert online.in_game(fetcher=fake(pages), clock=lambda: 200.0, key="K").splitlines() == [
        "sethome43 - в игре: Counter-Strike 2", "neurax716 - не в сети", "necroa716 - нет данных"]
    assert "ключ Steam" in online.in_game(key="")


def test_rates_of_the_central_bank():
    online._cache.clear()
    body = json.dumps({"Date": "2026-10-24T11:30:00+03:00", "Valute": {"USD": {"Value": 81.2345}, "EUR": {"Value": 94.1}}})
    assert online.answer("курс", fetcher=fake({online.CBR: body}), clock=lambda: 300.0) == \
        "Курс ЦБ на 24.10: доллар 81,23 ₽, евро 94,10 ₽"
    online._cache.clear()
    assert online.answer("курс", fetcher=fake({}), clock=lambda: 301.0) == "Курс сейчас недоступен."


def test_weather_now_and_tomorrow():
    online._cache.clear()
    geo = online.GEO + urllib.parse.quote("Москва")
    pages = {geo: json.dumps({"results": [{"name": "Москва", "latitude": 55.75, "longitude": 37.62}]}),
             f"{online.FORECAST}&latitude=55.75&longitude=37.62": json.dumps({
                 "current": {"temperature_2m": 4.6, "apparent_temperature": 0.2, "weather_code": 3, "wind_speed_10m": 3.4},
                 "daily": {"temperature_2m_min": [1, -2.4], "temperature_2m_max": [6, 3.5], "weather_code": [3, 71]}})}
    assert online.answer("погода Москва", fetcher=fake(pages), clock=lambda: 400.0) == \
        "Москва: сейчас +5°, ощущается 0°, пасмурно, ветер 3 м/с\nЗавтра: от -2° до +4°, снег"
    online._cache.clear()
    assert "не найден" in online.answer("погода Атлантида", fetcher=fake({}), clock=lambda: 401.0)


def test_coin_and_random_stay_in_range():
    rng = random.Random(7)
    assert online.answer("монетка", rng=rng) in ("Выпало: Орёл", "Выпало: Решка")
    for text, low, high in (("рандом", 1, 100), ("рандом 6", 1, 6), ("рандом 20-10", 10, 20)):
        for _ in range(30):
            assert low <= int(online.answer(text, rng=rng).split()[-1]) <= high


def test_faceit_level_and_elo():
    online._cache.clear()
    url = online.FACEIT_API + "s1mple"
    body = json.dumps({"nickname": "s1mple", "games": {"cs2": {"skill_level": 10, "faceit_elo": 3500}}})
    assert online.faceit("s1mple", fetcher=fake({url: body}), clock=lambda: 500.0, key="F") == "s1mple: уровень 10, Elo 3500"
    assert "не найден" in online.faceit("nobody", fetcher=fake({}), clock=lambda: 501.0, key="F")
    assert "FACEIT_API_KEY" in online.faceit("s1mple", key="")


def test_reminders_are_parsed_with_limits():
    now = 1_800_000_000.0
    assert online.reminder("напомни через 2 часа купить хлеб", now) == (now + 7200, "купить хлеб")
    assert online.reminder("Напомни через 15 мин позвонить", now) == (now + 900, "позвонить")
    due, note = online.reminder("напомни в 18:30 тренировка", now)
    local = online.datetime.fromtimestamp(due, online.MSK)
    assert (local.hour, local.minute, note) == (18, 30, "тренировка") and now < due <= now + 86400
    assert online.reminder("напомни через 367 дней что-то", now)[0] is None
    assert online.reminder("напомни в 25:00 что-то", now)[0] is None
    assert online.reminder("онлайн", now) is None
    assert online.is_command("напомни через 5 минут чай")


def test_one_command_per_cooldown_in_a_chat():
    online._last_command.clear()
    assert online.allowed("1", 100.0) and not online.allowed("1", 105.0) and online.allowed("2", 105.0)
    assert online.allowed("1", 100.0 + online.COOLDOWN)


def test_owner_switch_words():
    assert online.owner_switch("бот выкл") is False and online.owner_switch("Бот вкл!") is True
    assert online.owner_switch("бот привет") is None


def test_faceit_of_our_accounts_through_wok():
    online._cache.clear()
    assert online.is_command("фейсит")
    pages = {}
    for i, (name, _) in enumerate(online.STEAM):
        pages[f"{online.STEAM_API}/ISteamUser/ResolveVanityURL/v1/?key=K&vanityurl={name}"] = \
            json.dumps({"response": {"steamid": f"7656119800000000{i}", "success": 1}})
    pages[online.WOK_FACEIT + "76561198000000000"] = json.dumps(
        {"nickname": "Stytamimamo", "games": {"cs2": {"faceit_elo": 0, "skill_level": 0}}})
    pages[online.WOK_FACEIT + "76561198000000001"] = json.dumps(
        {"nickname": "neur", "games": {"cs2": {"faceit_elo": 1840, "skill_level": 8}}})
    calls = []
    get = fake(pages)
    counting = lambda url: calls.append(url) or get(url)
    answer = online.faceit_accounts(fetcher=counting, clock=lambda: 700.0, wok="wok_x", key="K")
    assert answer.splitlines() == ["sethome43 - Stytamimamo: нет уровня", "neurax716 - neur: уровень 8, Elo 1840",
                                   "necroa716 - нет аккаунта FACEIT или данные недоступны"]
    wok_calls = len([c for c in calls if c.startswith(online.WOK_FACEIT)])
    online.faceit_accounts(fetcher=counting, clock=lambda: 700.0 + 300, wok="wok_x", key="K")
    assert len([c for c in calls if c.startswith(online.WOK_FACEIT)]) == wok_calls + 1  # found ones cached 10 min
    assert "WOK_API_KEY" in online.faceit_accounts(wok="", key="K")



def resolved(pages, key="K"):
    for i, (name, _) in enumerate(online.STEAM):
        pages[f"{online.STEAM_API}/ISteamUser/ResolveVanityURL/v1/?key={key}&vanityurl={name}"] = \
            json.dumps({"response": {"steamid": f"7656119800000000{i}", "success": 1}})
    return pages


def test_inventories_in_rubles_with_private_and_missing():
    online._cache.clear()
    inv = online.WOK_BASE + "/v1/inventory?steam_id={}&game=cs2&top=0&currency=RUB"
    pages = resolved({inv.format("76561198000000000"): json.dumps({"status": "ok", "total": 12345.6, "items_total": 234}),
                      inv.format("76561198000000001"): json.dumps({"status": "private", "total": 0})})
    assert online.inventories(fetcher=fake(pages), clock=lambda: 800.0, wok="wok_x", key="K").splitlines() == \
        ["sethome43 - 12 346 ₽ (234 предм.)", "neurax716 - инвентарь скрыт", "necroa716 - нет данных"]
    assert "WOK_API_KEY" in online.inventories(wok="", key="K")


def test_levels_from_the_official_steam_api_and_removed_commands():
    online._cache.clear()
    pages = resolved({
        f"{online.STEAM_API}/IPlayerService/GetSteamLevel/v1/?key=K&steamid=76561198000000000":
            json.dumps({"response": {"player_level": 25}}),
        f"{online.STEAM_API}/IPlayerService/GetSteamLevel/v1/?key=K&steamid=76561198000000001": json.dumps({"response": {}})})
    assert online.steam_levels(fetcher=fake(pages), clock=lambda: 1000.0, key="K").splitlines() == [
        "sethome43 - 25 ур.", "neurax716 - скрыт или нет данных", "necroa716 - скрыт или нет данных"]
    assert not online.is_command("баны") and not online.is_command("цена AK-47 | Redline (Field-Tested)")



def block(when, one, two, score, link):
    middle = (f"<span>{score[0]}</span><span>:</span><span>{score[1]}</span>" if score else "<span>vs</span>")
    return (f"<div><span>{when}</span><div><span>{one}</span></div><div><span>{two}</span></div>{middle}"
            f'<div class="x"><span>Перейти на матч</span></div><a href="{link}"></a><span>1.13</span><span>Групповой этап</span></div>')


PGL_PAGE = ("<html><script>var x='Перейти на матч';</script><a href=\"/matches\">Матчи</a>"
            + block("Сегодня в 10:00", "FUT Esports", "TYLOO", ("2", "0"), "/matches/cs2/1")
            + block("LIVE", "MOUZ", "Liquid", ("1", "1"), "/matches/cs2/2")
            + block("Сегодня в 16:00", "Vitality", "9z Team", None, "/matches/cs2/3")
            + block("Завтра в 13:00", "G2", "NAVI", None, "/matches/cs2/4") + "</html>")
MATCH_PAGE = ("<div>MOUZ</div><div>Team Liquid</div><div>BEST-OF-3</div><b>1</b><b>:</b><b>1</b><div>LIVE</div>"
              "<h2>Статистика матча</h2>"
              "<div>Ancient</div><div>MOUZ</div><div>Liquid</div><b>16</b><b>:</b><b>13</b>"
              "<div>Inferno</div><div>MOUZ</div><div>Liquid</div><b>12</b><b>:</b><b>16</b>"
              "<div>Mirage</div><div>MOUZ</div><div>Liquid</div><b>7</b><b>:</b><b>5</b>")


def test_pgl_live_with_the_current_map_and_upcoming():
    online._cache.clear()
    pages = {online.PGL_URL: PGL_PAGE, online.CYBERSPORT + "/matches/cs2/2": MATCH_PAGE}
    assert online.is_command("PGL") and online.is_command("пгл")
    online._pgl_times.clear()
    before = PGL_PAGE.replace(block("LIVE", "MOUZ", "Liquid", ("1", "1"), "/matches/cs2/2"),
                              block("Сегодня в 13:00", "MOUZ", "Liquid", None, "/matches/cs2/2"))
    online.answer("pgl", fetcher=fake({online.PGL_URL: before}), clock=lambda: 1000.0)  # still upcoming: time seen
    assert online.answer("pgl", fetcher=fake(pages), clock=lambda: 1100.0) == (
        "Источник: cybersport.ru\nОбновлено: 01.01.1970 03:18 (МСК)\n\n"
        "Сыграно сегодня:\n10:00 FUT Esports 2-0 TYLOO\n\n"
        "Онлайн:\n13:00 MOUZ 1-1 Liquid\nКарта 3/3: Mirage 7:5\n\n"
        "Ожидаются игры:\n16:00 Vitality - 9z Team\nзавтра 13:00 G2 - NAVI")


def test_pgl_match_started_early_is_live_not_upcoming():
    online._cache.clear()
    online._pgl_times.clear()
    page = "<html>" + block("Сегодня в 16:00", "Vitality", "9z Team", None, "/matches/cs2/3") + "</html>"
    bo3 = json.dumps({"results": [{"status": "current", "team1": {"name": "9z"}, "team2": {"name": "Team Vitality"},
                                   "team1_score": 0, "team2_score": 1}]})
    assert online.answer("pgl", fetcher=fake({online.PGL_URL: page, online.BO3_MATCHES: bo3}),
                         clock=lambda: 1400.0).split("\n\n")[2:] == [
        "Онлайн:\nVitality 1-0 9z Team", "Ожидаются игры:\nнет"]


def test_pgl_without_live_or_site():
    online._cache.clear()
    quiet = "<html>" + block("Сегодня в 16:00", "Vitality", "9z Team", None, "/matches/cs2/3") + "</html>"
    assert online.answer("pgl", fetcher=fake({online.PGL_URL: quiet}), clock=lambda: 1200.0) == (
        "Источник: cybersport.ru\nОбновлено: 01.01.1970 03:20 (МСК)\n\n"
        "Сыграно сегодня:\nпока ничего\n\nОнлайн:\nсейчас матчей нет\n\nОжидаются игры:\n16:00 Vitality - 9z Team")
    assert online.answer("pgl", fetcher=fake({}), clock=lambda: 1250.0).splitlines()[1] == \
        "Обновлено: 01.01.1970 03:20 (МСК)"  # from the cache: the time it was downloaded
    online._cache.clear()
    assert online.answer("pgl", fetcher=fake({}), clock=lambda: 1300.0) == \
        "PGL: данные сейчас недоступны (cybersport.ru и bo3.gg не отвечают)."



def test_swiss_grid_places_matches_by_record_and_draws_a_png():
    from replyai import bracket
    games = [("A", "B", 2, 0), ("C", "D", 2, 1), ("A", "C", 2, 1), ("B", "D", 0, 2), ("A", "D", 2, 0)]
    matches = [dict(team1=a, team2=b, score1=x, score2=y, done=True) for a, b, x, y in games]
    matches.append(dict(team1="C", team2="B", score1=None, score2=None, done=False))
    pools, zones = bracket.swiss(matches)
    assert [len(pools[p]) for p in ((0, 0), (1, 0), (0, 1), (2, 0), (1, 1))] == [2, 1, 1, 1, 1]
    assert pools[(1, 1)][0]["team1"] == "C" and zones["3:0"] == ["A"]
    online._cache.clear()
    url = (f"{online.BO3_API}/matches?scope=widget-matches&page[offset]=0&page[limit]=100&sort=start_date"
           f"&filter[matches.discipline_id][eq]=1&filter[matches.tournament_id][eq]={online.PGL_TOURNAMENT_ID}&with=teams")
    body = json.dumps({"results": [
        {"team1": {"name": "FUT"}, "team2": {"name": "TYLOO"}, "team1_score": 2, "team2_score": 0, "status": "finished"},
        {"team1": None, "team2": {"name": "TBD"}, "status": "upcoming"}]})
    assert online.is_image_command("pgl сетка") and online.is_command("ПГЛ сетка")
    assert online.image("pgl сетка", fetcher=fake({url: body}), clock=lambda: NOW_GRID)[:8] == b"\x89PNG\r\n\x1a\n"
    online._cache.clear()
    assert "недоступны" in online.image("pgl сетка", fetcher=fake({}), clock=lambda: NOW_GRID)


NOW_GRID = 1792833900.0


def bo3_row(one, two, a, b, status="finished", start="2026-10-24T10:40:00.000+00:00", slug=None):
    return {"team1": {"name": one}, "team2": {"name": two}, "team1_score": a, "team2_score": b, "status": status,
            "start_date": start, "slug": slug, "bo_type": 3}


def swiss_to_playoff():
    """Eight teams reach three wins (two 3:0, three 3:1, three 3:2 are faked by direct wins)."""
    rows = []
    for team in "ABCDEFGH":
        rows += [bo3_row(team, f"x{team}{i}", 1, 0) for i in range(3)]
    return rows


def test_new_commands_are_recognised():
    for text in ("pgl плей-офф", "ПГЛ playoff", "pgl vitality", "онлайн график", "стим игры"):
        assert online.is_command(text), text
    assert online.is_image_command("pgl плейофф") and online.is_image_command("онлайн график")
    assert not online.is_image_command("pgl vitality")
    assert [online.digest_kind(t) for t in ("стим игры", "pgl")] == ["steam", None]
    assert not online.is_command("куда вложить кс") and not online.is_command("куда вложить гта")
    assert online.caption("онлайн график") == "Онлайн за сутки" and online.caption("pgl плей-офф").endswith("плей-офф")


def test_playoff_waits_then_draws():
    from replyai import bracket
    online._cache.clear()
    rows = swiss_to_playoff()
    text = online.image("pgl плей-офф", fetcher=fake({online.BO3_MATCHES: json.dumps({"results": rows})}),
                        clock=lambda: 1000.0)
    assert text.startswith("Плей-офф PGL ещё не начался.\nВышли в плей-офф: A, B, C, D, E, F, G, H")
    rows += [bo3_row("A", "B", 2, 1), bo3_row("C", "D", 0, 2), bo3_row("A", "D", 0, 0, "upcoming")]
    games, advanced = bracket.playoff(online.bo3_rows(fake({online.BO3_MATCHES: json.dumps({"results": rows})}),
                                                      lambda: 2000.0))
    assert [(g["team1"], g["team2"]) for g in games] == [("A", "B"), ("C", "D"), ("A", "D")] and len(advanced) == 8
    online._cache.clear()
    png = online.image("pgl плей-офф", fetcher=fake({online.BO3_MATCHES: json.dumps({"results": rows})}),
                       clock=lambda: 3000.0)
    assert png[:8] == b"\x89PNG\r\n\x1a\n"


def test_pgl_team_shows_last_live_and_next_match():
    online._cache.clear()
    rows = [bo3_row("Team Vitality", "MOUZ", 2, 1, slug="v-m"), bo3_row("G2", "Vitality", 2, 0),
            bo3_row("Vitality", "Spirit", 0, 0, "upcoming", "2026-10-26T13:00:00.000+00:00")]
    games = json.dumps({"games": [
        {"number": 2, "status": "finished", "map_name": "de_mirage", "winner_clan_score": 13, "loser_clan_score": 7,
         "winner_clan_name": "Vitality"},
        {"number": 1, "status": "finished", "map_name": "de_nuke", "winner_clan_score": 13, "loser_clan_score": 9,
         "winner_clan_name": "Vitality"}]})
    pages = {online.BO3_MATCHES: json.dumps({"results": rows}),
             f"{online.BO3_API}/matches/v-m?scope=show-match&with=games,teams": games}
    answer = online.answer("pgl vitality", fetcher=fake(pages), clock=lambda: 1000.0)
    assert answer.split("\n\n")[1:] == [
        "Team Vitality: побед 1, поражений 1",
        "Последний матч:\n24.10 13:40 G2 2-0 Vitality",
        "Следующий матч:\n26.10 16:00 Vitality - Spirit"]
    assert "Карта" not in answer  # the last match has no slug: no maps
    rows.pop(1)
    online._cache.clear()
    assert "Карта 1: Nuke 13:9 (Vitality)\nКарта 2: Mirage 13:7 (Vitality)" in online.answer(
        "pgl vitality", fetcher=fake(dict(pages, **{online.BO3_MATCHES: json.dumps({"results": rows})})),
        clock=lambda: 1100.0)
    assert "не найдена" in online.answer("pgl navi", fetcher=fake(pages), clock=lambda: 1100.0)


def test_online_samples_and_graph():
    samples = []
    online.keep_sample(samples, 0.0, [("Excellent", 90), ("Space", 800), ("Wednesday", None)])
    assert online.image("онлайн график", samples=samples, clock=lambda: 10.0).startswith("График появится")
    for i in range(1, 100):
        online.keep_sample(samples, i * 300.0, [("Excellent", 90 + i), ("Space", 800 + i), ("Wednesday", 10)])
    online.keep_sample(samples, 30 * 3600.0, [("Excellent", 1), ("Space", 2), ("Wednesday", 3)])
    assert samples[0][0] == 14400.0 and samples[-1] == [30 * 3600.0, 1, 2, 3]  # older than 26 hours are dropped
    samples = [[i * 300.0, 90, 800 + i, None] for i in range(50)]
    assert online.image("онлайн график", samples=samples, clock=lambda: 50 * 300.0)[:4] == b"\x89PNG"


def test_steam_specials_and_digest_texts():
    online._cache.clear()
    store = json.dumps({"specials": {"items": [
        {"name": "Battlefield V", "discount_percent": 90, "final_price": 39900, "currency": "RUB"},
        {"name": "No sale", "discount_percent": 0, "final_price": 100}]}})
    assert online.steam_specials(fetcher=fake({online.STEAM_SPECIALS: store}), clock=lambda: 1.0) == \
        ["Battlefield V — -90% — 399 RUB"]
    posts = [{"channel": "skesti", "id": 5, "date": 0.0, "text": "старый"},
             {"channel": "skesti", "id": 9, "date": 86400.0, "text": "GTA V -75%"}]
    request = online.digest_request(posts, ["Battlefield V — -90% — 399 RUB"])
    assert request.index("https://t.me/skesti/9") < request.index("https://t.me/skesti/5")
    assert request.endswith("СКИДКИ В МАГАЗИНЕ STEAM СЕЙЧАС:\n\nBattlefield V — -90% — 399 RUB")
    assert online.digest_answer("steam", " игры ", 0.0) == \
        "Источник: @skesti, магазин Steam\nОбновлено: 01.01.1970 03:00 (МСК)\n\nигры"


def test_digest_prompt_asks_for_russian_short_lines():
    task = online.DIGESTS["steam"][1]
    assert "ТОЛЬКО на русском" in task and "Не пересказывай" in task and list(online.DIGESTS) == ["steam"]


def test_news_are_absurd_and_marked_as_a_joke_and_money_links_the_video():
    assert online.is_command("Новости") and online.is_command("получить деньги")
    news = online.answer("новости", rng=random.Random(1))
    assert news.startswith("📰 <b>СРОЧНЫЕ НОВОСТИ</b>") and news.endswith("😄 Это шутка, все новости выдуманы.")
    assert news.count("🚨") == 3
    assert online.answer("получить деньги").endswith(online.MONEY_VIDEO)
    assert online.addressed("/news") == "новости" and online.addressed("/money") == "получить деньги"


def test_flag_command():
    assert online.is_command("🇷🇺") and online.addressed("🇷🇺") == "🇷🇺"
    assert online.answer("🇷🇺") == "Россия, Путин, Победа"
    assert not online.is_command("🇷🇺 привет")


def test_reminder_a_year_ahead_and_too_far():
    now = 1_800_000_000.0
    assert online.reminder("напомни через 100000 минут x", now)[0] == now + 100000 * 60  # more than 99999 works
    due, note = online.reminder("Напомни через 522720 минут поздравить с др", now)
    assert due == now + 522720 * 60 and note == "поздравить с др"
    for minutes in ("600000", "999999999", "999999999999999"):  # more than a year: a clear answer, never a crash
        assert online.reminder(f"напомни через {minutes} минут x", now) == \
            (None, "Напоминание можно поставить не дальше чем на год."), minutes
    assert online.when(now + 400 * 86400).count(".") == 2  # another year: the year is shown


def test_pgl_falls_back_to_bo3_when_cybersport_fails():
    online._cache.clear()
    now = online.datetime(2026, 10, 27, 20, 30, tzinfo=online.MSK).timestamp()
    rows = [bo3_row("Mongolz", "MOUZ", 0, 2, start="2026-10-27T08:00:00.000+00:00"),
            bo3_row("Vitality", "FUT", 1, 1, "current", "2026-10-27T14:00:00.000+00:00", slug="v-f"),
            bo3_row("G2", "DENDELE", 0, 0, "upcoming", "2026-10-27T17:00:00.000+00:00"),
            bo3_row("A", "B", 2, 0, start="2026-10-26T08:00:00.000+00:00"),  # yesterday: not shown
            bo3_row("C", "D", 0, 0, "upcoming", "2026-10-30T08:00:00.000+00:00")]  # in 3 days: not shown
    games = json.dumps({"games": [{"number": 3, "status": "current", "map_name": "de_inferno"}]})
    pages = {online.PGL_URL: "<html>captcha</html>", online.BO3_MATCHES: json.dumps({"results": rows}),
             f"{online.BO3_API}/matches/v-f?scope=show-match&with=games,teams": games}
    assert online.answer("pgl", fetcher=fake(pages), clock=lambda: now).split("\n\n") == [
        "Источник: bo3.gg\nОбновлено: 27.10.2026 20:30 (МСК)",
        "Сыграно сегодня:\n11:00 Mongolz 0-2 MOUZ",
        "Онлайн:\n17:00 Vitality 1-1 FUT\nКарта 3: Inferno, идёт",
        "Ожидаются игры:\n20:00 G2 - DENDELE"]
