# Сторонние компоненты

Код Zamolvi распространяется по лицензии MIT (файл `LICENSE`). Ниже — сторонние
библиотеки, которые входят в APK или устанавливаются на сервер. Они остаются
под своими лицензиями; их тексты лежат в репозиториях проектов по ссылкам.

## Android-приложение (входят в `base/base-app-2.7.apk`)

| Компонент | Лицензия |
|---|---|
| [AndroidX](https://developer.android.com/jetpack/androidx) (core, lifecycle, startup, annotation, tracing, profileinstaller и др.) | Apache License 2.0 |
| [Kotlin standard library, kotlinx.coroutines](https://github.com/JetBrains/kotlin) | Apache License 2.0 |
| [JSch](http://www.jcraft.com/jsch/) — SSH-подключение к VPS | BSD-style (текст в APK: `assets/third-party-jsch.txt`) |
| [JZlib](http://www.jcraft.com/jzlib/) | BSD-style (текст в APK: `META-INF/LICENSE.JZlib.txt`) |
| [jBCrypt](https://www.mindrot.org/projects/jBCrypt/) | ISC (текст в APK: `META-INF/LICENSE.jBCrypt.txt`) |

## Сервер (`server/requirements*.txt`, устанавливаются из PyPI на VPS)

| Компонент | Лицензия |
|---|---|
| [Telethon](https://github.com/LonamiWebs/Telethon) | MIT |
| [FastAPI](https://github.com/fastapi/fastapi) | MIT |
| [Pydantic](https://github.com/pydantic/pydantic) (зависимость FastAPI) | MIT |
| [Uvicorn](https://github.com/encode/uvicorn) | BSD-3-Clause |
| [HTTPX](https://github.com/encode/httpx) | BSD-3-Clause |
| [cryptography](https://github.com/pyca/cryptography) | Apache 2.0 / BSD-3-Clause |
| [qrcode](https://github.com/lincolnloop/python-qrcode) | BSD-3-Clause |
| [pypdf](https://github.com/py-pdf/pypdf) | BSD-3-Clause |
| [Pillow](https://github.com/python-pillow/Pillow) | MIT-CMU |
| [Caddy](https://github.com/caddyserver/caddy) — HTTPS на VPS, ставится установщиком из репозитория Debian/Ubuntu | Apache License 2.0 |

## Прочее

- `server/certs/gigachat.pem` — публичный корневой сертификат удостоверяющего
  центра, которым подписан API GigaChat. Это открытый сертификат, а не ключ;
  он нужен, чтобы сервер мог проверить HTTPS-соединение с GigaChat.
- Названия Telegram, Groq, Gemini, GigaChat, OpenRouter и других сервисов —
  товарные знаки их владельцев. Zamolvi не связан с ними и не одобрен ими.
