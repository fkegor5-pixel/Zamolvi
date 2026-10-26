"""Online of the servers over the last day as a PNG line chart."""
from datetime import datetime

from .bracket import BG, TEXT, MUTED, LINE, _font, _png

COLORS = ((255, 170, 60), (90, 160, 255), (120, 220, 120))
W, H, LEFT, RIGHT, TOP, BOTTOM = 1000, 560, 70, 30, 130, 50
GAP = 15 * 60  # no sample for longer than this: the line is broken (bot was off)


def render(samples, names, now, tz, title, subtitle=""):
    """samples: [time, count of names[0], count of names[1], ...] with None for no data."""
    from PIL import Image, ImageDraw
    start = now - 86400
    rows = sorted((s for s in samples if s[0] >= start), key=lambda s: s[0])
    top = max([v for s in rows for v in s[1:] if isinstance(v, int)] or [1])
    top = max(100, -(-top // 100) * 100)
    image = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(image)
    draw.text((20, 18), title, font=_font(30, True), fill=TEXT)
    draw.text((20, 56), subtitle, font=_font(15), fill=MUTED)

    def xy(t, v):
        return (LEFT + (t - start) / 86400 * (W - LEFT - RIGHT), H - BOTTOM - v / top * (H - TOP - BOTTOM))

    for i in range(6):
        value = top * i // 5
        _, y = xy(start, value)
        draw.line((LEFT, y, W - RIGHT, y), fill=LINE)
        draw.text((LEFT - 8, y), str(value), font=_font(14), fill=MUTED, anchor="rm")
    for hours in range(0, 25, 3):
        t = start + hours * 3600
        x, _ = xy(t, 0)
        draw.text((x, H - BOTTOM + 8), f"{datetime.fromtimestamp(t, tz):%H:%M}", font=_font(14), fill=MUTED, anchor="mt")
    legend_x = 20
    for n, name in enumerate(names):
        color = COLORS[n % len(COLORS)]
        values = [(s[0], s[n + 1]) for s in rows if len(s) > n + 1 and isinstance(s[n + 1], int)]
        for (t1, v1), (t2, v2) in zip(values, values[1:]):
            if t2 - t1 <= GAP:
                draw.line((*xy(t1, v1), *xy(t2, v2)), fill=color, width=3)
        last = values[-1][1] if values else "—"
        peak = max((v for _, v in values), default="—")
        text = f"{name}: сейчас {last}, максимум {peak}"
        draw.rectangle((legend_x, 92, legend_x + 14, 106), fill=color)
        draw.text((legend_x + 20, 99), text, font=_font(15), fill=TEXT, anchor="lm")
        legend_x += 30 + int(draw.textlength(text, font=_font(15)))
    return _png(image)
