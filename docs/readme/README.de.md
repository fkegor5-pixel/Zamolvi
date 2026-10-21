# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · [Português](README.pt.md) · **Deutsch** · [Français](README.fr.md) · [Italiano](README.it.md) · [Türkçe](README.tr.md) · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [日本語](README.ja.md)

**KI-Autoresponder für private Telegram-Chats, der auf deinem eigenen Server läuft.**

Zamolvi antwortet deinen Kontakten, während du beschäftigt bist: Er empfängt eine neue Nachricht, gibt sie an die KI deiner Wahl weiter und sendet die Antwort in deinem Namen – nur in den privaten Chats, die du ausgewählt hast. Gesteuert wird über eine Android-App, die Arbeit läuft auf deinem VPS – du kannst die App schließen und das Telefon sperren.

## Funktionen

| Bereich | Was er tut |
|---|---|
| 1. VPS-Verbindung | Installiert und aktualisiert den Server per SSH und richtet HTTPS ein |
| 2. Telegram | Anmeldung per Code oder QR, mit Cloud-Passwort. Außerdem **Antworten aus Benachrichtigungen** – ohne Telegram-Anmeldung auf dem Server |
| 3. KI und Text | Anbieter, Modell, Schlüssel, Anweisung und Formatierung, Test. Außerdem der **Kommunikationsstil**: freundlich, frech, Proll, Besserwisser, Pirat, Computer u. v. m. |
| 4. Private Chats | Welchen Kontakten der Autoresponder antwortet |
| 5. Autoresponder | Ein- und Ausschalten, Status, Ruhezeiten, Warten auf mehrere Nachrichten |
| 6. Serverprotokoll | Ereignisse und Diagnose |
| 7. So funktioniert es | Über die App und den Autor |
| 8. Statistik | Antworten, Sprachnachrichten und Fehler pro Tag |
| 9. App-Design | Hell, dunkel oder System. Außerdem die **Sprache der Oberfläche** |
| 10. Gelöschte Nachrichten | Archiv gelöschter Nachrichten in den gewählten Chats (bis 30 Tage), mit Suche |
| 11. Kontaktgedächtnis | Zusammenfassung zu jedem Kontakt: Name, Interessen, Absprachen, Fakten; «Vergessen»; Laden des Verlaufs auf deinen Befehl |
| 12. Fotos und Dokumente | Text und Beschreibung von Fotos; liest PDF, DOCX, TXT, CSV, JSON |
| 13. Antwortfreigabe | Entwürfe: vor dem Senden senden, bearbeiten oder ablehnen |
| 14. Detaillierte Statistik | KI-Kosten, Ausgaben nach Modell und Chat, Antwortzeit, Fehler |
| 15. Funktionsprüfung | Was läuft, was nicht und warum – mit Schaltflächen zur Behebung |

Außerdem: Transkription von Sprachnachrichten (Whisper über Groq), Widget, Statusbenachrichtigung und Schnelleinstellungs-Kachel, der Befehl `/ai_off` für Kontakte, Ruhezeiten und gemeinsame Antworten auf mehrere Nachrichten.

## Voraussetzungen

- Android 9 oder neuer.
- Ein VPS mit Debian 12 oder Ubuntu 24.04, öffentlicher IPv4 und SSH-Zugang als root.
- Eigene Telegram-`api_id` und `api_hash` von [my.telegram.org](https://my.telegram.org) (für Antworten aus Benachrichtigungen nicht nötig).
- Ein KI-API-Schlüssel: **Groq** (mit kostenlosem Tarif), **Google Gemini**, **GigaChat** oder jede **OpenAI-kompatible API** (z. B. OpenRouter).

## Schnellstart

1. Lade die APK unter **Releases** herunter und installiere sie.
2. **1. VPS-Verbindung**: Serveradresse und Root-Passwort eingeben, «Server aus der App installieren» tippen.
3. **2. Telegram**: `api_id`, `api_hash` und Nummer eingeben, per Code oder QR anmelden. Oder «Antworten aus Benachrichtigungen» öffnen, um ohne Anmeldung zu arbeiten.
4. **3. KI und Text**: Anbieter, Schlüssel und Modell wählen, Anweisung schreiben, «Prüfen» tippen. Optional einen Stil wählen.
5. **4. Private Chats**: Kontakte markieren.
6. **5. Autoresponder**: einschalten.

Wenn etwas nicht klappt, öffne **15. Funktionsprüfung**: dort stehen Grund und Lösung.

## Datenschutz und verantwortungsvolle Nutzung

- Nachrichten gehen nur an den gewählten KI-Anbieter und nur aus den gewählten Chats. Details: [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Sag deinen Kontakten, dass eine KI antworten kann, und lade ihren Verlauf nicht ohne Zustimmung.
- Halte die [Telegram-API-Bedingungen](https://core.telegram.org/api/terms) ein: kein Spam, keine Massennachrichten.

Build, Aufbau des Repositorys und Übersetzungen: siehe die [englische README](../../README.md).

## Autor und Lizenz

**@stytam** — [t.me/stytam](https://t.me/stytam). Lizenz [MIT](../../LICENSE); Fremdkomponenten: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
