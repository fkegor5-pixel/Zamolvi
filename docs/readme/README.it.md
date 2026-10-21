# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · [Português](README.pt.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · **Italiano** · [Türkçe](README.tr.md) · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [日本語](README.ja.md)

**Risponditore automatico con IA per le chat private di Telegram, ospitato sul tuo server.**

Zamolvi risponde ai tuoi contatti quando sei occupato: riceve un nuovo messaggio, lo passa all'IA che hai scelto e invia la risposta a tuo nome, solo nelle chat private che hai selezionato. Lo controlli da un'app Android; il lavoro avviene sul tuo VPS, quindi puoi chiudere l'app e bloccare il telefono.

## Funzioni

| Sezione | Cosa fa |
|---|---|
| 1. Connessione al VPS | Installa e aggiorna il server sul VPS via SSH e configura HTTPS |
| 2. Telegram | Accesso all'account con codice o QR, con password cloud. Inoltre **risposte dalle notifiche**, senza accesso a Telegram sul server |
| 3. IA e testo | Provider, modello, chiave, istruzione e formattazione, test. Inoltre lo **stile di comunicazione**: gentile, sfacciato, tamarro, saputello, pirata, computer e altri |
| 4. Chat private | A quali contatti risponde il risponditore |
| 5. Risponditore | Avvio e arresto, stato, ore di silenzio, attesa di più messaggi |
| 6. Registro del server | Eventi e diagnostica |
| 7. Come funziona | Informazioni sull'app e sull'autore |
| 8. Statistiche | Risposte, messaggi vocali ed errori per giorno |
| 9. Tema | Chiaro, scuro o di sistema. Inoltre la **lingua dell'interfaccia** |
| 10. Messaggi eliminati | Archivio dei messaggi eliminati nelle chat scelte (fino a 30 giorni), con ricerca |
| 11. Memoria dei contatti | Riassunto di ogni contatto: nome, interessi, accordi, fatti chiave; pulsante «Dimentica»; caricamento della cronologia su tuo comando |
| 12. Foto e documenti | Testo e descrizione delle foto; legge PDF, DOCX, TXT, CSV, JSON |
| 13. Approvazione delle risposte | Bozze: invia, modifica o rifiuta prima dell'invio |
| 14. Statistiche dettagliate | Costo dell'IA, spese per modello e chat, tempo di risposta, errori |
| 15. Verifica delle funzioni | Cosa funziona, cosa no e perché, con pulsanti per risolvere |

Inoltre: trascrizione dei vocali (Whisper tramite Groq), widget, notifica e riquadro nelle impostazioni rapide, comando `/ai_off` per far disattivare le risposte al contatto, ore di silenzio e risposta unica a più messaggi.

## Requisiti

- Android 9 o successivo.
- Un VPS con Debian 12 o Ubuntu 24.04, IPv4 pubblico e accesso SSH come root.
- I tuoi `api_id` e `api_hash` di Telegram da [my.telegram.org](https://my.telegram.org) (non servono per le risposte dalle notifiche).
- Una chiave API di IA: **Groq** (piano gratuito), **Google Gemini**, **GigaChat** o qualsiasi **API compatibile con OpenAI** (ad esempio OpenRouter).

## Avvio rapido

1. Scarica l'APK da **Releases** e installalo.
2. **1. Connessione al VPS**: inserisci indirizzo e password di root, tocca «Installa il server dall'app».
3. **2. Telegram**: inserisci `api_id`, `api_hash` e numero, accedi con codice o QR. Oppure apri «Risposte dalle notifiche» per lavorare senza accesso.
4. **3. IA e testo**: scegli provider, chiave e modello, scrivi l'istruzione e tocca «Prova». Se vuoi, scegli uno stile.
5. **4. Chat private**: seleziona i contatti.
6. **5. Risponditore**: attivalo.

Se qualcosa non funziona, apri **15. Verifica delle funzioni**: trovi la causa e cosa fare.

## Privacy e uso responsabile

- I messaggi vanno solo al provider di IA scelto e solo per le chat scelte. Dettagli: [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Avvisa i contatti che può rispondere un'IA e non caricare la loro cronologia senza consenso.
- Rispetta i [termini dell'API di Telegram](https://core.telegram.org/api/terms): niente spam né invii di massa.

Compilazione, struttura del repository e traduzioni: vedi il [README in inglese](../../README.md).

## Autore e licenza

**@stytam** — [t.me/stytam](https://t.me/stytam). Licenza [MIT](../../LICENSE); componenti di terze parti: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
