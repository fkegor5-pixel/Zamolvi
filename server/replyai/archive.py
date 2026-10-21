"""Bounded, opt-in copies of messages, stored only inside the encrypted vault.

Only selected private chats are supported. Their Telegram message IDs belong
to the account-wide namespace, so deletions without a chat can be matched.
Media bytes are never downloaded; captions and a media label are retained.
"""
from .errors import UserError

DAYS = 30
MAX_CACHED = 500
MAX_DELETED = 500
MAX_TEXT = 4096


def initial():
    return {"enabled": False, "since": 0, "cached": [], "deleted": []}


def prune(state, now):
    data = state["message_archive"]
    cutoff = now - DAYS * 86400
    data["cached"] = [r for r in data["cached"] if r["received_at"] > cutoff][-MAX_CACHED:]
    data["deleted"] = [r for r in data["deleted"] if r["deleted_at"] > cutoff][-MAX_DELETED:]


def configure(state, enabled, now):
    if enabled and not state["selected"]:
        raise UserError("Сначала выберите личные чаты в главном меню.")
    data = state["message_archive"]
    if enabled and not data["enabled"]:
        data["since"] = now
    if not enabled:
        # Copies waiting for a deletion must not survive disabling the feature.
        data["cached"] = []
    data["enabled"] = enabled
    prune(state, now)


def restrict(state):
    data = state["message_archive"]
    for key in ("cached", "deleted"):
        data[key] = [r for r in data[key] if r["chat_id"] in state["selected"]]
    if not state["selected"]:
        data["enabled"] = False


def remember(state, chat, message_id, text, media, outgoing, sent_at, now, name="", edited=False):
    prune(state, now)
    data = state["message_archive"]
    selected = state["selected"].get(chat)
    if not data["enabled"] or not selected or not chat.isdecimal() or int(chat) <= 0:
        return
    if not isinstance(message_id, int) or message_id <= 0:
        return
    rows = data["cached"]
    prior = next((r for r in rows if r["chat_id"] == chat and r["message_id"] == message_id), None)
    if any(r["chat_id"] == chat and r["message_id"] == message_id for r in data["deleted"]):
        return
    if edited and prior is None:
        return  # An edit must not import messages from before opt-in.
    if sent_at < max(data["since"], selected["since"]):
        return
    row = {"chat_id": chat, "message_id": message_id,
           "chat_name": (selected.get("name") or name or chat)[:160],
           "text": str(text or "")[:MAX_TEXT], "truncated": len(str(text or "")) > MAX_TEXT, "media": media,
           "outgoing": bool(outgoing), "sent_at": sent_at,
           "received_at": prior["received_at"] if prior else now,
           "edited": bool(edited or (prior and prior.get("edited")))}
    data["cached"] = ([r for r in rows if (r["chat_id"], r["message_id"]) != (chat, message_id)] + [row])[-MAX_CACHED:]


def deleted(state, ids, chat, now):
    prune(state, now)
    data = state["message_archive"]
    ids = set(ids)
    def matches(row):
        return row["message_id"] in ids and (chat is None or row["chat_id"] == chat)
    if data["enabled"]:
        found = [dict(r, deleted_at=now) for r in data["cached"] if matches(r)]
        data["deleted"] = (data["deleted"] + found)[-MAX_DELETED:]
        data["cached"] = [r for r in data["cached"] if not matches(r)]
    # A deleted source must never result in a later AI reply.
    affected = {j["chat"] for j in state["queue"] if (chat is None or j["chat"] == chat)
                and any(p["id"] in ids for p in j.get("parts", []))}
    state["queue"] = [j for j in state["queue"] if j["chat"] not in affected or j["state"] == "uncertain"]
    for key in affected:
        state["history"].pop(key, None)


def report(state, offset=0, limit=20, query=""):
    data = state["message_archive"]
    query = query.strip().casefold()
    rows = list(reversed(data["deleted"]))
    if query:
        rows = [r for r in rows if query in r["text"].casefold() or query in r["chat_name"].casefold()]
    return {"supported": True, "enabled": data["enabled"], "retention_days": DAYS,
            "cached_count": len(data["cached"]), "total": len(data["deleted"]),
            "filtered_total": len(rows), "selected_count": len(state["selected"]),
            "max_cached": MAX_CACHED, "max_deleted": MAX_DELETED,
            "offset": offset, "messages": rows[offset:offset + limit],
            "has_more": offset + limit < len(rows)}
