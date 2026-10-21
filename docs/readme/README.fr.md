# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · [Português](README.pt.md) · [Deutsch](README.de.md) · **Français** · [Italiano](README.it.md) · [Türkçe](README.tr.md) · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [日本語](README.ja.md)

**Répondeur automatique à IA pour les discussions privées Telegram, hébergé sur votre propre serveur.**

Zamolvi répond à vos contacts quand vous êtes occupé : il reçoit un nouveau message, le transmet à l'IA de votre choix et envoie la réponse en votre nom — uniquement dans les discussions privées que vous avez choisies. Vous le pilotez depuis une application Android ; le travail s'effectue sur votre VPS, vous pouvez donc fermer l'application et verrouiller le téléphone.

## Fonctions

| Section | Rôle |
|---|---|
| 1. Connexion au VPS | Installe et met à jour le serveur sur votre VPS via SSH, configure HTTPS |
| 2. Telegram | Connexion à votre compte par code ou QR, avec mot de passe cloud. Ainsi que les **réponses depuis les notifications**, sans connexion Telegram sur le serveur |
| 3. IA et texte | Fournisseur, modèle, clé, instruction et mise en forme, test. Ainsi que le **style de communication** : gentil, insolent, racaille, je-sais-tout, pirate, ordinateur, etc. |
| 4. Discussions privées | À quels contacts le répondeur répond |
| 5. Répondeur | Activer et arrêter, état, heures calmes, attente de plusieurs messages |
| 6. Journal du serveur | Événements et diagnostic |
| 7. Comment ça marche | À propos de l'application et de l'auteur |
| 8. Statistiques | Réponses, messages vocaux et erreurs par jour |
| 9. Thème | Clair, sombre ou système. Ainsi que la **langue de l'interface** |
| 10. Messages supprimés | Archive des messages supprimés dans les discussions choisies (jusqu'à 30 jours), avec recherche |
| 11. Mémoire des contacts | Résumé de chaque contact : nom, centres d'intérêt, accords, faits clés ; bouton « Oublier » ; chargement de l'historique sur demande |
| 12. Photos et documents | Texte et description des photos ; lecture de PDF, DOCX, TXT, CSV, JSON |
| 13. Validation des réponses | Brouillons : envoyer, modifier ou refuser avant l'envoi |
| 14. Statistiques détaillées | Coût de l'IA, dépenses par modèle et discussion, délai de réponse, erreurs |
| 15. Vérification des fonctions | Ce qui marche, ce qui ne marche pas et pourquoi, avec boutons de correction |

Aussi : transcription des messages vocaux (Whisper via Groq), widget, notification et tuile de réglages rapides, commande `/ai_off` pour qu'un contact coupe les réponses, heures calmes et réponse groupée à plusieurs messages.

## Prérequis

- Android 9 ou plus récent.
- Un VPS sous Debian 12 ou Ubuntu 24.04 avec IPv4 publique et accès SSH root.
- Vos propres `api_id` et `api_hash` Telegram sur [my.telegram.org](https://my.telegram.org) (inutiles pour les réponses depuis les notifications).
- Une clé d'API d'IA : **Groq** (offre gratuite), **Google Gemini**, **GigaChat** ou toute **API compatible OpenAI** (par exemple OpenRouter).

## Démarrage rapide

1. Téléchargez l'APK dans **Releases** et installez-le.
2. **1. Connexion au VPS** : saisissez l'adresse et le mot de passe root, touchez « Installer le serveur depuis l'application ».
3. **2. Telegram** : saisissez `api_id`, `api_hash` et votre numéro, connectez-vous par code ou QR. Ou ouvrez « Réponses depuis les notifications » pour fonctionner sans connexion.
4. **3. IA et texte** : choisissez fournisseur, clé et modèle, écrivez l'instruction et touchez « Tester ». Choisissez un style si vous voulez.
5. **4. Discussions privées** : cochez vos contacts.
6. **5. Répondeur** : activez-le.

Si quelque chose ne fonctionne pas, ouvrez **15. Vérification des fonctions** : la cause et la solution y sont indiquées.

## Confidentialité et usage responsable

- Les messages ne vont qu'au fournisseur d'IA choisi et uniquement pour les discussions choisies. Détails : [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Prévenez vos contacts qu'une IA peut leur répondre et ne chargez pas leur historique sans leur accord.
- Respectez les [conditions de l'API Telegram](https://core.telegram.org/api/terms) : pas de spam ni d'envois massifs.

Compilation, structure du dépôt et traductions : voir le [README anglais](../../README.md).

## Auteur et licence

**@stytam** — [t.me/stytam](https://t.me/stytam). Licence [MIT](../../LICENSE) ; composants tiers : [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
