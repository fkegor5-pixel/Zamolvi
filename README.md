# Zamolvi

**English** · [Русский](docs/readme/README.ru.md) · [Español](docs/readme/README.es.md) · [Português](docs/readme/README.pt.md) · [Deutsch](docs/readme/README.de.md) · [Français](docs/readme/README.fr.md) · [Italiano](docs/readme/README.it.md) · [Türkçe](docs/readme/README.tr.md) · [Polski](docs/readme/README.pl.md) · [Українська](docs/readme/README.uk.md) · [العربية](docs/readme/README.ar.md) · [हिन्दी](docs/readme/README.hi.md) · [Bahasa Indonesia](docs/readme/README.id.md) · [中文](docs/readme/README.zh.md) · [日本語](docs/readme/README.ja.md)

**A self-hosted AI auto-responder for private Telegram chats.**

Zamolvi answers your contacts while you are busy: it receives a new message, passes it
to the AI of your choice and sends the reply on your behalf — only in the private chats
you selected. You control it from an Android app; the work itself runs on your own VPS,
so you can close the app and lock the phone.

> The name comes from the Russian «замолвить словечко» — "to put in a good word for someone".

---

## Features

The app's main menu:

| Section | What it does |
|---|---|
| 1. VPS connection | Installs and updates the server on your VPS over SSH, sets up HTTPS |
| 2. Telegram | Logs in to your account by code or QR, with the cloud password. Also **replies from notifications** — without logging in to Telegram on the server |
| 3. AI and text | AI provider, model, key, instruction and reply formatting, connection test. Also the **communication style**: kind, cheeky, street tough, know-it-all, pirate, computer and more |
| 4. Private chats | Which contacts the auto-responder answers |
| 5. Auto-responder | Start and stop, status, quiet hours, waiting for a batch of messages |
| 6. Server log | Events and diagnostics |
| 7. How it works | About the app and the author |
| 8. Statistics | Replies, voice messages and errors by day |
| 9. App theme | Light, dark or system. Also the **interface language**: Русский, English, Español, Português, Deutsch, Türkçe, Українська |
| 10. Deleted messages | Archive of messages deleted in the selected chats (up to 30 days), with search |
| 11. Contact memory | A short summary of each contact: name, interests, agreements, key facts; a «Forget» button; loading the chat history on your command |
| 12. Photos and documents | Text and a description of photos; reads PDF, DOCX, TXT, CSV, JSON |
| 13. Reply approval | Drafts: send, edit or reject before sending |
| 14. Detailed statistics | Cost of AI requests, spending by model and chat, reply time, errors |
| 15. Feature check | What works, what doesn't and why — with fix buttons and a report |

Also:

- **Replies from notifications** — a mode without logging in to Telegram on the server: the phone sees the Telegram notification, the server writes a reply with the AI, and it is sent with the «Reply» button of the notification. Answer all private chats or only a list, see who was answered, keep an ignore list. Works while the phone is online. Besides Telegram it can answer **WhatsApp, VK, Viber and SMS**; **profiles** (Working / Sleeping / Driving) switch the style and the situation note by time or by hand; a **daily limit** of AI replies, **quick replies** without the AI and **urgent words** (the bot stays silent and notifies you) keep costs and risks down. Messages with confirmation codes never leave the phone.
- **Voice messages** are transcribed (Whisper via Groq) and answered by their text.
- **Update check**: the «Update» section shows the installed version against the latest GitHub release, checks once a day in the background and notifies you when a newer one appears. It only reports the version — the APK is downloaded from the release page and installed by you.
- **Home-screen widget, status notification and Quick Settings tile**: status, number of sent replies, «Turn on» and «Stop» buttons.
- **A contact can turn auto-replies off** with the `/ai_off` command.
- **Quiet hours** in a chosen time zone and **message batches**: the auto-responder waits a few seconds and answers several messages at once.

## How it works

```
 Android app  ──HTTPS + control key──▶  Your VPS (Python: FastAPI + Telethon)
   control,                                │            │
   settings, status                        ▼            ▼
                                     Telegram API     AI API
                                     (your account)   (Groq / Gemini / GigaChat /
                                                       OpenAI-compatible)
```

- The server is installed from the app over SSH with one button: Python environment, systemd service and HTTPS via Caddy.
- The Telegram session, API keys, memory and archive are stored encrypted on the VPS.
- The app talks only to your server; the project has no servers of its own.

## Requirements

- Android 9 or newer.
- A VPS with Debian 12 or Ubuntu 24.04, a public IPv4 and SSH access as root.
- Your own Telegram `api_id` and `api_hash` from [my.telegram.org](https://my.telegram.org) (not needed for replies from notifications).
- An AI API key from one of the providers:
  - **Groq** — has a free tier with limits;
  - **Google Gemini**;
  - **GigaChat**;
  - **any OpenAI-compatible API** (for example, OpenRouter): an address like `https://…/v1/chat/completions`, a key and a model ID.

## Quick start

1. Download the APK from **Releases** and install it.
2. **1. VPS connection**: enter the server address and root password, tap «Install the server from the app». The app checks the server's SSH key and asks you to confirm its fingerprint.
3. **2. Telegram**: enter `api_id`, `api_hash` and your number, then log in by code or QR. Or open «Replies from notifications» to work without logging in.
4. **3. AI and text**: choose a provider, paste the key and model, write an instruction and tap «Test». Pick a communication style if you like.
5. **4. Private chats**: tick your contacts.
6. **5. Auto-responder**: turn it on.

If something does not work, open **15. Feature check**: it shows the reason and what to do.

## Privacy and security

- Your messages go only to the AI provider you chose and only for the chats you chose. Read its data policy.
- The app connects to the server over HTTPS with a control key; the server's SSH key is pinned on first connection.
- Details: [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md).

### Use it responsibly

- Tell your contacts that an AI may answer them. Do not load their chat history into memory without their consent.
- Automating a personal account must follow the [Telegram API Terms](https://core.telegram.org/api/terms): no spam or mass messaging, or Telegram may limit the account.
- AI makes mistakes. For important chats turn on **13. Reply approval**.

## Building from source

See [docs/BUILD.md](docs/BUILD.md). In short:

```bash
# JDK 17+, Android SDK (platform 35, build-tools 35), Apktool 3.x
python3 build-tools/build_apk.py --sdk "$ANDROID_HOME" --apktool apktool.jar
# Result: build/Zamolvi-<version>.apk
```

You can also build without a computer: GitHub Actions workflow **Build APK** (Actions → Build APK → Run workflow).

Server tests:

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r server/requirements.txt -r server/requirements-media.txt -r requirements-dev.txt
pytest
```

## Translations

The interface is translated on the phone from `i18n/<language>.json` (Russian text → translation). To add or fix a language, edit or add a file there and open a pull request. The AI always answers the contact in the contact's own language.

## Repository layout

| Path | Contents |
|---|---|
| `server/` | Server: `replyai/` (code), `tools/` (installation), `deploy/` (systemd service) |
| `source/` | Screens and features of the Android app (Java) |
| `i18n/` | Interface translations |
| `base/base-app-2.7.apk` | The base app 2.7: main menu, VPS, Telegram, AI. Its code is not changed during the build |
| `compile-api/` | Descriptions of base-app classes needed only for compilation |
| `build-tools/` | APK build script and widget resources |
| `tests/` | Server tests (pytest) |
| `assets/` | Place for an optional intro video (see `assets/README.md`) |

Internal identifiers (Android package `ru.replyai.android`, Python package `replyai`, `/opt/replyai` on the server) remain from the project's former name. They are kept for compatibility: the app updates over earlier versions and installed servers keep working.

## Author

**@stytam** — [t.me/stytam](https://t.me/stytam)

## License

[MIT](LICENSE). Third-party components keep their own licenses, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
