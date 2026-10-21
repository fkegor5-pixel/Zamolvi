# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · [Português](README.pt.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Türkçe](README.tr.md) · **Polski** · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [日本語](README.ja.md)

**Automatyczna sekretarka z AI dla prywatnych czatów Telegram, działająca na twoim własnym serwerze.**

Zamolvi odpowiada twoim kontaktom, gdy jesteś zajęty: odbiera nową wiadomość, przekazuje ją wybranej AI i wysyła odpowiedź w twoim imieniu — tylko w wybranych prywatnych czatach. Sterujesz nią z aplikacji na Androida, a praca odbywa się na twoim VPS, więc możesz zamknąć aplikację i zablokować telefon.

## Funkcje

| Sekcja | Co robi |
|---|---|
| 1. Połączenie z VPS | Instaluje i aktualizuje serwer na VPS przez SSH, konfiguruje HTTPS |
| 2. Telegram | Logowanie kodem lub QR, z hasłem chmurowym. A także **odpowiedzi z powiadomień** — bez logowania do Telegrama na serwerze |
| 3. AI i tekst | Dostawca, model, klucz, instrukcja i formatowanie, test. A także **styl komunikacji**: miły, zuchwały, dresiarz, mądrala, pirat, komputer i inne |
| 4. Prywatne czaty | Którym kontaktom odpowiada automat |
| 5. Automatyczna odpowiedź | Włączanie i zatrzymywanie, stan, godziny ciszy, czekanie na kilka wiadomości |
| 6. Dziennik serwera | Zdarzenia i diagnostyka |
| 7. Jak to działa | O aplikacji i autorze |
| 8. Statystyki | Odpowiedzi, wiadomości głosowe i błędy według dni |
| 9. Motyw | Jasny, ciemny lub systemowy. A także **język interfejsu** |
| 10. Usunięte wiadomości | Archiwum wiadomości usuniętych w wybranych czatach (do 30 dni), z wyszukiwaniem |
| 11. Pamięć kontaktów | Podsumowanie każdego kontaktu: imię, zainteresowania, ustalenia, ważne fakty; przycisk «Zapomnij»; wczytanie historii na twoje polecenie |
| 12. Zdjęcia i dokumenty | Tekst i opis zdjęć; czyta PDF, DOCX, TXT, CSV, JSON |
| 13. Zatwierdzanie odpowiedzi | Szkice: wyślij, popraw lub odrzuć przed wysłaniem |
| 14. Szczegółowe statystyki | Koszt AI, wydatki według modelu i czatu, czas odpowiedzi, błędy |
| 15. Sprawdzenie funkcji | Co działa, co nie i dlaczego — z przyciskami naprawy |

Ponadto: transkrypcja wiadomości głosowych (Whisper przez Groq), widżet, powiadomienie i kafelek szybkich ustawień, komenda `/ai_off` do wyłączenia odpowiedzi przez rozmówcę, godziny ciszy i wspólna odpowiedź na kilka wiadomości.

## Wymagania

- Android 9 lub nowszy.
- VPS z Debian 12 lub Ubuntu 24.04, publicznym IPv4 i dostępem SSH jako root.
- Własne `api_id` i `api_hash` Telegrama z [my.telegram.org](https://my.telegram.org) (niepotrzebne przy odpowiedziach z powiadomień).
- Klucz API AI: **Groq** (darmowy plan), **Google Gemini**, **GigaChat** lub dowolne **API zgodne z OpenAI** (np. OpenRouter).

## Szybki start

1. Pobierz APK z **Releases** i zainstaluj.
2. **1. Połączenie z VPS**: wpisz adres i hasło root, dotknij «Zainstaluj serwer z aplikacji».
3. **2. Telegram**: wpisz `api_id`, `api_hash` i numer, zaloguj się kodem lub QR. Albo otwórz «Odpowiedzi z powiadomień», aby działać bez logowania.
4. **3. AI i tekst**: wybierz dostawcę, klucz i model, napisz instrukcję i dotknij «Sprawdź». Opcjonalnie wybierz styl.
5. **4. Prywatne czaty**: zaznacz kontakty.
6. **5. Automatyczna odpowiedź**: włącz.

Jeśli coś nie działa, otwórz **15. Sprawdzenie funkcji**: znajdziesz tam przyczynę i rozwiązanie.

## Prywatność i odpowiedzialne użycie

- Wiadomości trafiają tylko do wybranego dostawcy AI i tylko z wybranych czatów. Szczegóły: [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Uprzedź kontakty, że może odpowiadać AI, i nie wczytuj ich historii bez zgody.
- Przestrzegaj [warunków API Telegrama](https://core.telegram.org/api/terms): bez spamu i masowych wysyłek.

Budowanie, struktura repozytorium i tłumaczenia: zobacz [README po angielsku](../../README.md).

## Autor i licencja

**@stytam** — [t.me/stytam](https://t.me/stytam). Licencja [MIT](../../LICENSE); komponenty zewnętrzne: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
