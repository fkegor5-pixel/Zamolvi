"""Swiss-stage grid of a CS2 tournament drawn as a PNG (team names, no logos).

The column of a match is the record both teams had before it: in a Swiss stage only teams with the same
record meet, so it is computed from the results in time order. Three wins advance, three losses eliminate.
"""
import io
from collections import defaultdict

POOLS = {  # record -> how many matches it has with 16 teams
    (0, 0): 8, (1, 0): 4, (0, 1): 4, (2, 0): 2, (1, 1): 4, (0, 2): 2, (2, 1): 3, (1, 2): 3, (2, 2): 3}
COLUMNS = [  # top to bottom; a tuple of two ints is a pool of matches, a string is a final zone
    [(0, 0)],
    [(1, 0), (0, 1)],
    [(2, 0), (1, 1), (0, 2)],
    ["3:0", (2, 1), (1, 2), "0:3"],
    ["3:1", (2, 2), "1:3"],
    ["3:2", "2:3"],
]
ZONE_SIZE = {"3:0": 2, "3:1": 3, "3:2": 3, "0:3": 2, "1:3": 3, "2:3": 3}
BG, CARD, TEXT, MUTED, WIN = (30, 36, 48), (44, 52, 68), (235, 238, 245), (120, 128, 145), (255, 255, 255)
GREEN, RED, LINE = (22, 120, 40), (130, 22, 22), (70, 80, 100)
BOX_W, BOX_H, GAP, COL_W, LABEL_H, TOP = 230, 58, 10, 262, 30, 92
FONTS = ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")


def swiss(matches):
    """(pools: record -> [match], zones: «3:0» -> [team]) from matches sorted by start time.

    A match is a dict with team1, team2, score1, score2 and done (False for a scheduled one).
    """
    record = defaultdict(lambda: (0, 0))
    pools = defaultdict(list)
    for m in matches:
        a, b = m["team1"], m["team2"]
        pool = record[a] if record[a] == record[b] else min(record[a], record[b], key=sum)
        if pool not in POOLS:
            continue  # a team that already finished; not a Swiss match
        pools[pool].append(m)
        if m.get("done") and m.get("score1") != m.get("score2"):
            winner, loser = (a, b) if m["score1"] > m["score2"] else (b, a)
            record[winner] = (record[winner][0] + 1, record[winner][1])
            record[loser] = (record[loser][0], record[loser][1] + 1)
    zones = defaultdict(list)
    for team, (w, l) in record.items():
        if w == 3 or l == 3:
            zones[f"{w}:{l}"].append(team)
    return pools, zones


def _font(size, bold=False):
    from PIL import ImageFont
    try:
        return ImageFont.truetype(FONTS[1 if bold else 0], size)
    except OSError:
        return ImageFont.load_default(size)


def _fit(draw, text, font, width):
    while text and draw.textlength(text, font=font) > width:
        text = text[:-1]
    return text


def render(matches, title, subtitle=""):
    """PNG bytes of the grid; raises ImportError when Pillow is missing."""
    from PIL import Image, ImageDraw
    pools, zones = swiss(matches)
    heights = []
    for column in COLUMNS:
        h = 0
        for part in column:
            if isinstance(part, str):
                h += LABEL_H + ZONE_SIZE[part] * (BOX_H // 2 + GAP) + GAP + 24
            else:
                h += LABEL_H + POOLS[part] * (BOX_H + GAP) + 24
        heights.append(h)
    width, height = 40 + COL_W * len(COLUMNS), TOP + max(heights) + 20
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    big, label, name, small = _font(30, True), _font(20, True), _font(17), _font(15)
    draw.text((20, 18), title, font=big, fill=TEXT)
    draw.text((20, 56), subtitle, font=small, fill=MUTED)
    for c, column in enumerate(COLUMNS):
        x, y = 20 + c * COL_W, TOP
        for part in column:
            if isinstance(part, str):  # final zone: advanced (green) or eliminated (red)
                rows = ZONE_SIZE[part]
                h = LABEL_H + rows * (BOX_H // 2 + GAP) + GAP
                zone = GREEN if part.startswith("3") else RED
                draw.rounded_rectangle((x, y, x + BOX_W, y + h), 8, fill=zone)
                draw.text((x + BOX_W // 2, y + 6), part, font=label, fill=WIN, anchor="mt")
                teams = zones.get(part, [])
                for i in range(rows):
                    ty = y + LABEL_H + i * (BOX_H // 2 + GAP)
                    draw.rounded_rectangle((x + 10, ty, x + BOX_W - 10, ty + BOX_H // 2), 6,
                                           fill=tuple(int(v * 0.7) for v in zone))
                    team = teams[i] if i < len(teams) else "?"
                    draw.text((x + BOX_W // 2, ty + BOX_H // 4), _fit(draw, team, name, BOX_W - 30), font=name,
                              fill=WIN, anchor="mm")
                y += h + 24
                continue
            draw.text((x, y), f"{part[0]}:{part[1]}", font=label, fill=MUTED)
            y += LABEL_H
            found = pools.get(part, [])
            for i in range(POOLS[part]):
                _match_box(draw, x, y, found[i] if i < len(found) else None)
                y += BOX_H + GAP
            y += 24
    return _png(image)


def _png(image):
    out = io.BytesIO()
    image.save(out, "PNG", optimize=True)
    return out.getvalue()


def _match_box(draw, x, y, m):
    """One match: two rows «team score», the winner in bold; «?» for a match not known yet."""
    draw.rounded_rectangle((x, y, x + BOX_W, y + BOX_H), 8, fill=CARD)
    draw.line((x + 8, y + BOX_H // 2, x + BOX_W - 8, y + BOX_H // 2), fill=LINE)
    for row, (team, score) in enumerate(((m["team1"], m.get("score1")), (m["team2"], m.get("score2")))
                                        if m else (("?", None), ("?", None))):
        ty = y + 6 + row * (BOX_H // 2)
        won = bool(m and m.get("done") and score is not None
                   and score == max(m.get("score1") or 0, m.get("score2") or 0) and m["score1"] != m["score2"])
        color = WIN if won or not (m and m.get("done")) else MUTED
        draw.text((x + 10, ty), _fit(draw, team, _font(17, won), BOX_W - 50), font=_font(17, won), fill=color)
        if m and m.get("done"):
            draw.text((x + BOX_W - 12, ty), str(score), font=_font(17, won), fill=color, anchor="ra")


def playoff(matches):
    """(play-off matches in time order, teams that advanced) from the matches of the whole tournament.

    A play-off match is one between two teams that already have three Swiss wins.
    """
    record = defaultdict(lambda: (0, 0))
    games, advanced = [], []
    for m in matches:
        a, b = m["team1"], m["team2"]
        if record[a][0] == 3 and record[b][0] == 3:
            games.append(m)
            continue
        if m.get("done") and m.get("score1") != m.get("score2"):
            winner, loser = (a, b) if m["score1"] > m["score2"] else (b, a)
            record[winner] = (record[winner][0] + 1, record[winner][1])
            record[loser] = (record[loser][0], record[loser][1] + 1)
            if record[winner][0] == 3:
                advanced.append(winner)
    return games, advanced


ROUNDS = (("Четвертьфиналы", 4), ("Полуфиналы", 2), ("Финал", 1))


def render_playoff(games, title, subtitle=""):
    """PNG bytes of an 8-team single-elimination bracket; games fill the rounds in time order."""
    from PIL import Image, ImageDraw
    step = BOX_H + 40
    width, height = 40 + COL_W * len(ROUNDS) + 40, TOP + LABEL_H + 4 * step + 20
    image = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(image)
    draw.text((20, 18), title, font=_font(30, True), fill=TEXT)
    draw.text((20, 56), subtitle, font=_font(15), fill=MUTED)
    first, centers = 0, []
    for c, (name, count) in enumerate(ROUNDS):
        x = 20 + c * (COL_W + 20)
        draw.text((x, TOP), name, font=_font(20, True), fill=MUTED)
        span = 4 * step / count
        now = []
        for i in range(count):
            y = TOP + LABEL_H + 10 + span * i + (span - BOX_H) / 2
            k = first + i
            _match_box(draw, x, int(y), games[k] if k < len(games) else None)
            now.append((x, int(y) + BOX_H // 2))
            if centers:  # lines from the two matches of the previous round
                for px, py in centers[2 * i:2 * i + 2]:
                    mid = px + BOX_W + 10
                    draw.line((px + BOX_W, py, mid, py), fill=LINE, width=2)
                    draw.line((mid, py, mid, int(y) + BOX_H // 2), fill=LINE, width=2)
                    draw.line((mid, int(y) + BOX_H // 2, x, int(y) + BOX_H // 2), fill=LINE, width=2)
        centers, first = now, first + count
    return _png(image)
