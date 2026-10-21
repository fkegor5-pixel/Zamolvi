"""One Uvicorn worker; exit on worker failure so systemd can restart it."""
import asyncio
import logging
import os

import uvicorn

from .web import create_app


class QuietErrors(logging.Filter):
    def filter(self, record):
        if record.exc_info:
            name = record.exc_info[0].__name__
            record.msg, record.args, record.exc_info = "Server error (%s)", (name,), None
        return True


async def main():
    logging.basicConfig(level=logging.WARNING)
    # No HTTP access logs, request bodies, AI responses, login codes or session values.
    for name in ("uvicorn.error", "telethon"):
        logger = logging.getLogger(name)
        logger.setLevel(logging.ERROR)
        handler = logging.StreamHandler()
        handler.addFilter(QuietErrors())
        logger.handlers = [handler]
        logger.propagate = False
    app = create_app()
    server = uvicorn.Server(uvicorn.Config(app, host=os.environ.get("REPLYAI_HOST", "127.0.0.1"),
                                         port=int(os.environ.get("PORT", "8000")),
                                         workers=1, access_log=False, log_config=None,
                                         proxy_headers=False, timeout_graceful_shutdown=10))
    async def watch():
        while not server.should_exit:
            await asyncio.sleep(10)
            e = app.state.engine
            if e.failed or (e.worker and e.worker.done()) or time_elapsed(e) > 300:
                server.should_exit = True
                break
    watchdog = asyncio.create_task(watch())
    try:
        await server.serve()
    finally:
        watchdog.cancel()
    if app.state.engine.failed:
        raise SystemExit(1)


def time_elapsed(engine):
    return engine.clock() - engine.heartbeat


if __name__ == "__main__":
    asyncio.run(main())
