# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · **Español** · [Português](README.pt.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Türkçe](README.tr.md) · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [日本語](README.ja.md)

**Contestador automático con IA para chats privados de Telegram que funciona en tu propio servidor.**

Zamolvi responde a tus contactos mientras estás ocupado: recibe un mensaje nuevo, lo pasa a la IA que elijas y envía la respuesta en tu nombre, solo en los chats privados que hayas elegido. Se controla desde una app de Android y el trabajo se hace en tu VPS, así que puedes cerrar la app y bloquear el teléfono.

## Funciones

| Sección | Qué hace |
|---|---|
| 1. Conexión al VPS | Instala y actualiza el servidor en tu VPS por SSH y configura HTTPS |
| 2. Telegram | Acceso a tu cuenta por código o QR, con contraseña en la nube. Además, **respuestas desde notificaciones** sin iniciar sesión en Telegram en el servidor |
| 3. IA y texto | Proveedor, modelo, clave, instrucción y formato de las respuestas, prueba. Además, el **estilo de comunicación**: amable, atrevido, macarra, sabelotodo, pirata, ordenador y más |
| 4. Chats privados | A qué contactos responde el contestador |
| 5. Contestador | Activar y detener, estado, horas de silencio, espera de mensajes seguidos |
| 6. Registro del servidor | Eventos y diagnóstico |
| 7. Cómo funciona | Sobre la app y el autor |
| 8. Estadísticas | Respuestas, notas de voz y errores por día |
| 9. Tema de la app | Claro, oscuro o del sistema. Además, el **idioma de la interfaz** |
| 10. Mensajes borrados | Archivo de mensajes borrados en los chats elegidos (hasta 30 días), con búsqueda |
| 11. Memoria de contactos | Resumen de cada contacto: nombre, intereses, acuerdos, datos clave; botón «Olvidar»; carga del historial bajo tu orden |
| 12. Fotos y documentos | Texto y descripción de fotos; lee PDF, DOCX, TXT, CSV, JSON |
| 13. Aprobación de respuestas | Borradores: enviar, editar o rechazar antes de enviar |
| 14. Estadísticas detalladas | Coste de la IA, gasto por modelo y chat, tiempo de respuesta, errores |
| 15. Comprobación de funciones | Qué funciona, qué no y por qué, con botones para arreglarlo |

También: transcripción de notas de voz (Whisper vía Groq), widget, notificación y mosaico de ajustes rápidos, el comando `/ai_off` para que el contacto desactive las respuestas, horas de silencio y respuesta conjunta a varios mensajes.

## Requisitos

- Android 9 o superior.
- VPS con Debian 12 o Ubuntu 24.04, IPv4 pública y acceso SSH como root.
- Tus propios `api_id` y `api_hash` de Telegram desde [my.telegram.org](https://my.telegram.org) (no hacen falta para las respuestas desde notificaciones).
- Una clave de API de IA: **Groq** (tiene plan gratuito), **Google Gemini**, **GigaChat** o cualquier **API compatible con OpenAI** (por ejemplo, OpenRouter).

## Inicio rápido

1. Descarga el APK en **Releases** e instálalo.
2. **1. Conexión al VPS**: introduce la dirección y la contraseña de root y toca «Instalar el servidor desde la app».
3. **2. Telegram**: introduce `api_id`, `api_hash` y tu número y accede por código o QR. O abre «Respuestas desde notificaciones» para trabajar sin iniciar sesión.
4. **3. IA y texto**: elige proveedor, clave y modelo, escribe la instrucción y toca «Probar». Si quieres, elige un estilo.
5. **4. Chats privados**: marca tus contactos.
6. **5. Contestador**: actívalo.

Si algo no funciona, abre **15. Comprobación de funciones**: ahí verás la causa y qué hacer.

## Privacidad y uso responsable

- Los mensajes solo van al proveedor de IA que elijas y solo de los chats elegidos. Más detalles: [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Avisa a tus contactos de que puede responder una IA y no cargues su historial sin su consentimiento.
- Respeta las [condiciones de la API de Telegram](https://core.telegram.org/api/terms): nada de spam ni envíos masivos.

Compilación, estructura del repositorio y traducciones: ver el [README en inglés](../../README.md).

## Autor y licencia

**@stytam** — [t.me/stytam](https://t.me/stytam). Licencia [MIT](../../LICENSE); componentes de terceros: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
