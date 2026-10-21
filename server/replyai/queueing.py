"""Bounded per-chat debounce groups with persistent source-message identities."""
import secrets
from . import quiet

MAX_INPUT = 12000
MAX_PARTS = 8
MAX_PENDING = 200
MAX_WAIT = 30


def parts(job):
    if 'parts' in job:
        return job['parts']
    return [{'id': job['message_id'], 'kind': 'text', 'text': job.get('text', ''), 'original': job.get('text', '')}]


def text_of(items):
    return '\n\n'.join(p.get('text', '') for p in items if p.get('text', '')).strip()


def make_job(chat, items, revision, now, wait, expires=0):
    return {'id': f"{chat}:{items[0]['id']}", 'chat': chat, 'message_id': items[-1]['id'],
            'parts': items, 'text': text_of(items), 'state': 'queued', 'revision': revision,
            'attempts': 0, 'random_id': secrets.randbits(63) or 1, 'after': 0,
            'created_at': now, 'batch_until': now + wait if wait else 0, 'expires_at': expires}


def enqueue(state, chat, part, timestamp, now):
    selected = state['selected'].get(chat)
    identity = f"{chat}:{part['id']}"
    behavior = state['behavior']
    if not state['enabled'] or not selected or timestamp < max(state['enabled_at'], selected['since']):
        return False
    if quiet.active(behavior, now) or quiet.active(behavior, timestamp):
        return False
    if identity in state['seen'] or sum(max(1, len(parts(j))) for j in state['queue']) >= MAX_PENDING:
        return False
    wait = behavior.get('batch_seconds', 5)
    candidate = next((j for j in reversed(state['queue']) if j['chat'] == chat), None)
    if (wait and candidate and candidate['state'] == 'queued' and not candidate.get('sealed')
            and not candidate.get('answer') and not candidate['attempts'] and candidate['revision'] == state['revision']
            and candidate.get('batch_until', 0) >= now and len(parts(candidate)) < MAX_PARTS
            and len(text_of(parts(candidate) + [part])) <= MAX_INPUT):
        candidate['parts'] = parts(candidate) + [part]
        candidate['text'] = text_of(candidate['parts'])
        candidate['message_id'] = part['id']
        candidate['batch_until'] = min(now + wait, candidate['created_at'] + MAX_WAIT)
    else:
        state['queue'].append(make_job(chat, [part], state['revision'], now, wait, quiet.cutoff(behavior, now)))
    state['seen'] = (state['seen'] + [identity])[-5000:]
    return True


def seal(state, job_id, prepared, now):
    groups = [[]]
    for part in prepared:
        if groups[-1] and len(text_of(groups[-1] + [part])) > MAX_INPUT:
            groups.append([])
        groups[-1].append(part)
    for index, job in enumerate(state['queue']):
        if job['id'] != job_id:
            continue
        job.update(parts=groups[0], text=text_of(groups[0]), message_id=groups[0][-1]['id'], sealed=True, batch_until=0)
        children = []
        for group in groups[1:]:
            child = make_job(job['chat'], group, job['revision'], job.get('created_at', now), 0, job.get('expires_at', 0))
            child['sealed'] = True
            children.append(child)
        state['queue'][index + 1:index + 1] = children
        return dict(job)
    return None
