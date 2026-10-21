"""Manual approval: the AI prepares a draft, the owner sends it (as is or edited) or rejects it.

A draft is a queue job in the "awaiting" state. It blocks later replies in the same chat,
so answers keep their order. Everything that cancels a queued reply (disabling, quiet hours,
a manual answer by the owner, a deleted or edited source message, a settings change)
cancels drafts as well. Unanswered drafts expire.
"""
from .errors import UserError
from . import stats

MODES = ("off", "all", "chats")
MAX_TEXT = 1600


def initial():
    return {"mode": "off", "chats": [], "notify": True, "expire_hours": 12}


def configure(state, mode, chats, notify):
    if mode not in MODES:
        raise UserError("Выберите режим согласования.")
    chats = list(dict.fromkeys(str(c) for c in chats))
    if any(c not in state["selected"] for c in chats):
        raise UserError("Обновите список: один из чатов больше не выбран.")
    if mode == "chats" and not chats:
        raise UserError("Отметьте хотя бы один чат или выберите «Все чаты».")
    # Switching off keeps drafts that are already waiting: they can still be sent or rejected.
    state["approval"].update(mode=mode, chats=chats, notify=bool(notify))


def required(state, chat):
    config = state["approval"]
    return chat in state["selected"] and (config["mode"] == "all" or
                                          (config["mode"] == "chats" and chat in config["chats"]))


def restrict(state):
    state["approval"]["chats"] = [c for c in state["approval"]["chats"] if c in state["selected"]]
    if state["approval"]["mode"] == "chats" and not state["approval"]["chats"]:
        state["approval"]["mode"] = "off"


def drafts(state):
    rows = []
    for job in state["queue"]:
        if job["state"] != "awaiting":
            continue
        rows.append({"id": job["id"], "chat": job["chat"],
                     "name": state["selected"].get(job["chat"], {}).get("name") or job["chat"],
                     "incoming": job.get("text", ""), "answer": job.get("answer", ""),
                     "created_at": job.get("created_at", 0), "awaiting_since": job.get("awaiting_since", 0),
                     "expires_at": job.get("awaiting_since", 0) + state["approval"]["expire_hours"] * 3600})
    return rows


def report(state):
    config = state["approval"]
    return {"mode": config["mode"], "chats": config["chats"], "notify": config["notify"],
            "expire_hours": config["expire_hours"],
            "selected": [{"id": k, "name": v.get("name") or k} for k, v in state["selected"].items()],
            "drafts": drafts(state)}


def decide(state, job_id, action, text, now):
    job = next((j for j in state["queue"] if j["id"] == job_id and j["state"] == "awaiting"), None)
    if job is None:
        raise UserError("Черновик уже обработан, устарел или отменён.")
    if action == "reject":
        state["queue"] = [j for j in state["queue"] if j["id"] != job_id]
        stats.record(state, now, rejected=1)
        stats.record_chat(state, job["chat"], now, rejected=1)
        return "Черновик отклонён, ответ не отправлен."
    if action != "send":
        raise UserError("Неизвестное действие с черновиком.")
    text = (text or "").strip() or job.get("answer", "")
    if not text:
        raise UserError("Текст ответа пуст.")
    if len(text) > MAX_TEXT:
        raise UserError(f"Ответ длиннее {MAX_TEXT} символов.")
    job.update(state="queued", after=0, approved=True, edited=text != job.get("answer", ""), answer=text)
    return "Ответ отправляется."


def expire(state, now):
    hours = state["approval"]["expire_hours"]
    old = [j for j in state["queue"] if j["state"] == "awaiting" and j.get("awaiting_since", now) + hours * 3600 <= now]
    if not old:
        return 0
    ids = {j["id"] for j in old}
    state["queue"] = [j for j in state["queue"] if j["id"] not in ids]
    for job in old:
        stats.record(state, now, expired=1)
        stats.record_chat(state, job["chat"], now, expired=1)
    return len(old)
