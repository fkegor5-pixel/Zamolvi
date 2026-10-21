# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · [Português](README.pt.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · **Türkçe** · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [日本語](README.ja.md)

**Özel Telegram sohbetleri için kendi sunucunuzda çalışan yapay zekâlı otomatik yanıtlayıcı.**

Zamolvi, siz meşgulken kişilerinize yanıt verir: yeni bir mesaj alır, seçtiğiniz yapay zekâya iletir ve yanıtı sizin adınıza gönderir — yalnızca seçtiğiniz özel sohbetlerde. Android uygulamasından yönetilir; iş sizin VPS'inizde yapılır, bu yüzden uygulamayı kapatıp telefonu kilitleyebilirsiniz.

## Özellikler

| Bölüm | Ne yapar |
|---|---|
| 1. VPS bağlantısı | Sunucuyu SSH ile VPS'inize kurar ve günceller, HTTPS'yi ayarlar |
| 2. Telegram | Hesabınıza kod veya QR ve bulut şifresiyle giriş. Ayrıca sunucuda Telegram'a giriş yapmadan **bildirimlerden yanıtlar** |
| 3. Yapay zekâ ve metin | Sağlayıcı, model, anahtar, talimat ve biçim, test. Ayrıca **iletişim stili**: nazik, küstah, kabadayı, ukala, korsan, bilgisayar ve diğerleri |
| 4. Özel sohbetler | Otomatik yanıtlayıcının hangi kişilere yanıt vereceği |
| 5. Otomatik yanıtlayıcı | Açma ve durdurma, durum, sessiz saatler, art arda mesajları bekleme |
| 6. Sunucu günlüğü | Olaylar ve tanılama |
| 7. Nasıl çalışır | Uygulama ve yazar hakkında |
| 8. İstatistikler | Günlere göre yanıtlar, sesli mesajlar ve hatalar |
| 9. Uygulama teması | Açık, koyu veya sistem. Ayrıca **arayüz dili** |
| 10. Silinen mesajlar | Seçilen sohbetlerde silinen mesajların arşivi (30 güne kadar), aramalı |
| 11. Kişi hafızası | Her kişinin özeti: ad, ilgi alanları, anlaşmalar, önemli bilgiler; «Unut» düğmesi; komutunuzla geçmişi yükleme |
| 12. Fotoğraflar ve belgeler | Fotoğraflardaki metin ve açıklama; PDF, DOCX, TXT, CSV, JSON okuma |
| 13. Yanıt onayı | Taslaklar: göndermeden önce gönder, düzenle veya reddet |
| 14. Ayrıntılı istatistikler | Yapay zekâ maliyeti, modele ve sohbete göre harcama, yanıt süresi, hatalar |
| 15. Özellik denetimi | Ne çalışıyor, ne çalışmıyor ve neden — düzeltme düğmeleriyle |

Ayrıca: sesli mesajların yazıya dökülmesi (Groq üzerinden Whisper), widget, bildirim ve hızlı ayarlar kutucuğu, kişinin yanıtları kapatması için `/ai_off` komutu, sessiz saatler ve birden çok mesaja ortak yanıt.

## Gereksinimler

- Android 9 veya üzeri.
- Debian 12 veya Ubuntu 24.04, genel IPv4 ve root SSH erişimi olan bir VPS.
- [my.telegram.org](https://my.telegram.org)'dan kendi Telegram `api_id` ve `api_hash` değerleriniz (bildirimlerden yanıtlar için gerekmez).
- Bir yapay zekâ API anahtarı: **Groq** (ücretsiz plan var), **Google Gemini**, **GigaChat** veya herhangi bir **OpenAI uyumlu API** (örneğin OpenRouter).

## Hızlı başlangıç

1. APK'yı **Releases** sayfasından indirip kurun.
2. **1. VPS bağlantısı**: adresi ve root şifresini girip «Sunucuyu uygulamadan kur»a dokunun.
3. **2. Telegram**: `api_id`, `api_hash` ve numaranızı girip kod veya QR ile giriş yapın. Ya da girişsiz çalışmak için «Bildirimlerden yanıtlar»ı açın.
4. **3. Yapay zekâ ve metin**: sağlayıcı, anahtar ve model seçin, talimatı yazıp «Dene»ye dokunun. İsterseniz bir stil seçin.
5. **4. Özel sohbetler**: kişilerinizi işaretleyin.
6. **5. Otomatik yanıtlayıcı**: açın.

Bir şey çalışmıyorsa **15. Özellik denetimi**ni açın: neden ve çözüm orada yazar.

## Gizlilik ve sorumlu kullanım

- Mesajlar yalnızca seçtiğiniz yapay zekâ sağlayıcısına ve yalnızca seçilen sohbetlerden gider. Ayrıntılar: [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Kişilerinize yanıtı bir yapay zekânın verebileceğini söyleyin; geçmişlerini onaysız yüklemeyin.
- [Telegram API koşullarına](https://core.telegram.org/api/terms) uyun: spam ve toplu mesaj yok.

Derleme, depo yapısı ve çeviriler: [İngilizce README](../../README.md).

## Yazar ve lisans

**@stytam** — [t.me/stytam](https://t.me/stytam). [MIT](../../LICENSE) lisansı; üçüncü taraf bileşenler: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
