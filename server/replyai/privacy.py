"""One place for clean-ups that follow the chat selection and the Telegram account."""
from . import archive, memory, approval, stats


def restrict(state):
    """After the selection changed: drop data of chats that are no longer selected."""
    archive.restrict(state)
    memory.restrict(state)
    approval.restrict(state)
    stats.restrict(state)
    state["reminders"] = [r for r in state.get("reminders", [])
                          if r["chat"] in state["selected"] or r.get("owner")]  # the owner's own stay


def forget_account(state):
    """Logout, revoked session or another account: nothing of the old account may remain."""
    state["message_archive"] = archive.initial()
    state["memory"] = memory.initial()
    state["reminders"] = []
    state["muted"] = []
    state.pop("bot", None)  # the own bot belongs to the old account
    state["approval"]["chats"] = []
    if state["approval"]["mode"] == "chats":
        state["approval"]["mode"] = "off"
    if "stats" in state:
        state["stats"]["chats"] = {}
