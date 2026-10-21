"""Aggregate counters without message texts or audio.

Per-chat counters are keyed by chat ID only while that chat stays selected; they are
removed together with the chat (deselection, /ai_off, logout, account change).
"""
import datetime as dt
from zoneinfo import ZoneInfo

FIELDS = ('received', 'replies', 'errors', 'voice', 'merged', 'reply_ms', 'ai_calls', 'ai_ms', 'ai_success',
          # 2.9
          'auto', 'approved', 'edited', 'rejected', 'expired', 'photos', 'documents', 'memory_updates',
          'err_ai', 'err_telegram', 'err_voice', 'err_media', 'err_memory', 'auto_reply_ms')
MODEL_FIELDS = ('calls', 'errors', 'input_tokens', 'output_tokens', 'ms', 'audio_seconds')
CHAT_FIELDS = ('received', 'replies', 'errors', 'reply_ms', 'ai_calls', 'input_tokens', 'output_tokens',
               'auto', 'approved', 'edited', 'rejected', 'expired', 'photos', 'documents', 'auto_reply_ms')
ERROR_KINDS = ('ai', 'telegram', 'voice', 'media', 'memory')
PURPOSES = ('reply', 'memory', 'vision', 'voice', 'notification', 'test')
ZONE_NAME = 'Europe/Moscow'
ZONE = ZoneInfo(ZONE_NAME)
KEEP_DAYS = 90
MAX_MODELS = 40


def empty(fields=FIELDS):
    return {k: 0 for k in fields}


def initial(now):
    return {'since': now, 'timezone': ZONE_NAME, 'totals': empty(), 'days': {},
            'models': {}, 'model_days': {}, 'chats': {}}


def ensure(state, now):
    stats = state.setdefault('stats', initial(now))
    for key, value in initial(now).items():
        stats.setdefault(key, value)
    return stats


def day(now):
    return dt.datetime.fromtimestamp(now, ZONE).date().isoformat()


def _add(target, fields, values):
    for key, value in values.items():
        if key in fields:
            target[key] = target.get(key, 0) + max(0, int(value))


def _prune(stats):
    stats['days'] = {k: stats['days'][k] for k in sorted(stats['days'])[-KEEP_DAYS:]}
    stats['model_days'] = {k: stats['model_days'][k] for k in sorted(stats['model_days'])[-KEEP_DAYS:]}


def record(state, now, **values):
    stats = ensure(state, now)
    daily = stats['days'].setdefault(day(now), empty())
    _add(daily, FIELDS, values)
    _add(stats['totals'], FIELDS, values)
    _prune(stats)


def record_chat(state, chat, now, **values):
    """Per-chat counters, only for currently selected chats."""
    if not chat or chat not in state.get('selected', {}):
        return
    stats = ensure(state, now)
    row = stats['chats'].setdefault(chat, empty(CHAT_FIELDS))
    _add(row, CHAT_FIELDS, values)


def record_error(state, now, kind, chat=None):
    values = {'errors': 1}
    if kind in ERROR_KINDS:
        values['err_' + kind] = 1
    record(state, now, **values)
    record_chat(state, chat, now, errors=1)


def model_key(provider, model):
    return (str(provider) + '/' + str(model))[:200]


def record_usage(state, now, provider, model, purpose, input_tokens=0, output_tokens=0, ms=0,
                 audio_seconds=0, ok=True, chat=None):
    """Token and time usage of one AI request, by model and purpose."""
    stats = ensure(state, now)
    key = model_key(provider, model)
    purpose = purpose if purpose in PURPOSES else 'reply'
    if key not in stats['models'] and len(stats['models']) >= MAX_MODELS:
        key = 'other/other'
    values = {'calls': 1, 'errors': 0 if ok else 1, 'input_tokens': input_tokens,
              'output_tokens': output_tokens, 'ms': ms, 'audio_seconds': audio_seconds}
    _add(stats['models'].setdefault(key, empty(MODEL_FIELDS)), MODEL_FIELDS, values)
    by_day = stats['model_days'].setdefault(day(now), {})
    _add(by_day.setdefault(key, {}).setdefault(purpose, empty(MODEL_FIELDS)), MODEL_FIELDS, values)
    _prune(stats)
    if ok and purpose != 'test':
        record_chat(state, chat, now, ai_calls=1, input_tokens=input_tokens, output_tokens=output_tokens)


def restrict(state):
    """Drops per-chat counters of chats that are no longer selected."""
    stats = state.get('stats')
    if stats and 'chats' in stats:
        stats['chats'] = {k: v for k, v in stats['chats'].items() if k in state.get('selected', {})}


def report(state, now):
    stats = state.get('stats') or initial(now)
    today = dt.datetime.fromtimestamp(now, ZONE).date()
    series = []
    for offset in range(6, -1, -1):
        key = (today - dt.timedelta(days=offset)).isoformat()
        series.append({'date': key, **{**empty(), **stats['days'].get(key, {})}})
    totals = {**empty(), **stats['totals']}
    return {'since': stats['since'], 'timezone': stats.get('timezone', ZONE_NAME), 'today': series[-1],
            'series': series, 'totals': totals, 'lifetime_sent': state['sent'],
            'average_reply_ms': totals['reply_ms'] // max(1, totals['replies']),
            'average_ai_ms': totals['ai_ms'] // max(1, totals['ai_success'])}


# ---------------------------------------------------------------- pricing / extended report

def validate_pricing(pricing):
    from .errors import UserError
    currency = str(pricing.get('currency', '')).strip()
    if len(currency) > 4:
        raise UserError('Валюта — не длиннее 4 символов, например ₽ или $.')
    models = pricing.get('models', {})
    if not isinstance(models, dict) or len(models) > MAX_MODELS:
        raise UserError('Слишком много моделей в ценах.')
    clean = {}
    for key, price in models.items():
        if not isinstance(key, str) or not 1 <= len(key) <= 200:
            raise UserError('Некорректное имя модели в ценах.')
        row = {}
        for field in ('input', 'output', 'audio_hour'):
            value = price.get(field, 0) if isinstance(price, dict) else 0
            try:
                value = float(value or 0)
            except (TypeError, ValueError):
                raise UserError('Цена должна быть числом.') from None
            if not 0 <= value <= 1_000_000:
                raise UserError('Цена должна быть от 0 до 1 000 000.')
            row[field] = value
        clean[key] = row
    return {'currency': currency, 'models': clean}


def cost(usage, price):
    """Cost of usage counters with prices per 1M tokens / per audio hour; None if no price."""
    if not price or not any(price.get(k) for k in ('input', 'output', 'audio_hour')):
        return None
    return (usage.get('input_tokens', 0) * price.get('input', 0) / 1_000_000
            + usage.get('output_tokens', 0) * price.get('output', 0) / 1_000_000
            + usage.get('audio_seconds', 0) * price.get('audio_hour', 0) / 3600)


def _sum_rows(rows):
    total = empty(MODEL_FIELDS)
    for row in rows:
        _add(total, MODEL_FIELDS, row)
    return total


def extended(state, now, period_days=7):
    """period_days: 1 = today, 7, 30, 0 = all time (per-model totals since the start)."""
    stats = ensure(state, now)
    pricing = state.get('pricing') or {'currency': '', 'models': {}}
    prices = pricing.get('models', {})
    today = dt.datetime.fromtimestamp(now, ZONE).date()
    if period_days:
        keys = {(today - dt.timedelta(days=o)).isoformat() for o in range(period_days)}
        days = [v for k, v in stats['days'].items() if k in keys]
        model_days = [v for k, v in stats['model_days'].items() if k in keys]
    else:
        days = [stats['totals']]
        model_days = None
    counters = empty()
    for row in days:
        _add(counters, FIELDS, row)

    by_model, by_purpose = {}, {}
    if model_days is None:
        for key, row in stats['models'].items():
            by_model[key] = dict(row)
        for daily in stats['model_days'].values():   # purposes are only known for the kept days
            for key, purposes in daily.items():
                for purpose, row in purposes.items():
                    _add(by_purpose.setdefault(purpose, {}).setdefault(key, empty(MODEL_FIELDS)), MODEL_FIELDS, row)
    else:
        for daily in model_days:
            for key, purposes in daily.items():
                _add(by_model.setdefault(key, empty(MODEL_FIELDS)), MODEL_FIELDS, _sum_rows(purposes.values()))
                for purpose, row in purposes.items():
                    _add(by_purpose.setdefault(purpose, {}).setdefault(key, empty(MODEL_FIELDS)), MODEL_FIELDS, row)

    total_cost, priced_all = 0.0, True
    models = []
    for key, row in sorted(by_model.items(), key=lambda item: -item[1]['calls']):
        value = cost(row, prices.get(key))
        priced_all &= value is not None or not (row['input_tokens'] or row['output_tokens'] or row['audio_seconds'])
        total_cost += value or 0
        models.append({'model': key, **row, 'cost': value, 'priced': value is not None,
                       'average_ms': row['ms'] // max(1, row['calls'])})
    purposes = []
    for purpose in PURPOSES:
        rows = by_purpose.get(purpose)
        if not rows:
            continue
        values = [cost(row, prices.get(key)) for key, row in rows.items()]
        purposes.append({'purpose': purpose, **_sum_rows(rows.values()),
                         'cost': sum(v for v in values if v is not None) if any(v is not None for v in values) else None})

    selected = state.get('selected', {})
    chats = []
    for chat, row in stats['chats'].items():
        if chat not in selected:
            continue
        sent = row.get('auto', 0) + row.get('approved', 0)
        chats.append({'chat': chat, 'name': selected[chat].get('name') or chat, **{**empty(CHAT_FIELDS), **row},
                      'average_reply_ms': row.get('reply_ms', 0) // max(1, row.get('replies', 0)),
                      'average_auto_reply_ms': row.get('auto_reply_ms', 0) // max(1, row.get('auto', 0)),
                      'approved_percent': round(100 * row.get('approved', 0) / sent, 1) if sent else None})
    chats.sort(key=lambda r: -r['replies'])

    sent = counters['auto'] + counters['approved']
    decided = counters['approved'] + counters['rejected']
    return {
        'period_days': period_days, 'currency': pricing.get('currency', ''),
        'since': stats['since'], 'timezone': stats.get('timezone', ZONE_NAME),
        'counters': counters,
        'average_reply_ms': counters['reply_ms'] // max(1, counters['replies']),
        # Without the time a draft waited for approval.
        'average_auto_reply_ms': counters['auto_reply_ms'] // max(1, counters['auto']),
        'average_ai_ms': counters['ai_ms'] // max(1, counters['ai_success']),
        'errors': counters['errors'], 'error_kinds': {k: counters['err_' + k] for k in ERROR_KINDS},
        'approval': {'sent_auto': counters['auto'], 'sent_approved': counters['approved'],
                     'edited': counters['edited'], 'rejected': counters['rejected'], 'expired': counters['expired'],
                     # Replies sent before 2.9 have no auto/approved mark and are not counted here.
                     'approved_percent': round(100 * counters['approved'] / sent, 1) if sent else None,
                     'rejected_percent': round(100 * counters['rejected'] / decided, 1) if decided else None},
        'cost': {'total': round(total_cost, 6), 'complete': priced_all},
        'models': models, 'purposes': purposes, 'chats': chats,
        'prices': prices,
    }
