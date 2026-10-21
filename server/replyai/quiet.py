"""Daily quiet hours in an explicit IANA time zone, independent of the VPS clock zone."""
import datetime as dt
import re
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError
from .errors import UserError


def initial():
    return {'batch_seconds': 5, 'quiet_enabled': False, 'quiet_start': '23:00',
            'quiet_end': '08:00', 'quiet_zone': 'Europe/Moscow'}


def minute(value):
    if not isinstance(value, str) or not re.fullmatch(r'(?:[01][0-9]|2[0-3]):[0-5][0-9]', value):
        raise UserError('Время указывается в формате ЧЧ:ММ, например 23:00.')
    hour, minutes = map(int, value.split(':'))
    return hour * 60 + minutes


def validate(config):
    start, end = minute(config['quiet_start']), minute(config['quiet_end'])
    if config['quiet_enabled'] and start == end:
        raise UserError('Начало и конец тихих часов должны различаться.')
    try:
        ZoneInfo(config['quiet_zone'])
    except (ZoneInfoNotFoundError, ValueError):
        raise UserError('Неизвестный часовой пояс.') from None
    if not 0 <= config['batch_seconds'] <= 30:
        raise UserError('Пауза объединения — от 0 до 30 секунд.')


def active(config, timestamp):
    if not config.get('quiet_enabled'):
        return False
    local = dt.datetime.fromtimestamp(timestamp, ZoneInfo(config['quiet_zone']))
    now = local.hour * 60 + local.minute
    start, end = minute(config['quiet_start']), minute(config['quiet_end'])
    return start <= now < end if start < end else now >= start or now < end


def cutoff(config, timestamp):
    if not config.get('quiet_enabled'):
        return 0
    local = dt.datetime.fromtimestamp(timestamp, ZoneInfo(config['quiet_zone']))
    hour, minutes = map(int, config['quiet_start'].split(':'))
    next_start = local.replace(hour=hour, minute=minutes, second=0, microsecond=0)
    if next_start <= local:
        next_start += dt.timedelta(days=1)
    return next_start.timestamp()
