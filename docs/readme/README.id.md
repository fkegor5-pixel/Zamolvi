# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · [Português](README.pt.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Türkçe](README.tr.md) · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · **Bahasa Indonesia** · [中文](README.zh.md) · [日本語](README.ja.md)

**Penjawab otomatis berbasis AI untuk chat pribadi Telegram yang berjalan di server Anda sendiri.**

Zamolvi membalas kontak Anda saat Anda sibuk: menerima pesan baru, meneruskannya ke AI pilihan Anda, dan mengirim balasan atas nama Anda — hanya di chat pribadi yang Anda pilih. Dikendalikan dari aplikasi Android, sedangkan pekerjaannya berjalan di VPS Anda, jadi Anda bisa menutup aplikasi dan mengunci ponsel.

## Fitur

| Bagian | Fungsinya |
|---|---|
| 1. Koneksi VPS | Memasang dan memperbarui server di VPS lewat SSH, menyiapkan HTTPS |
| 2. Telegram | Masuk dengan kode atau QR dan kata sandi cloud. Juga **balasan dari notifikasi** — tanpa masuk Telegram di server |
| 3. AI dan teks | Penyedia, model, kunci, instruksi dan format balasan, uji. Juga **gaya komunikasi**: ramah, nyolot, preman, sok pintar, bajak laut, komputer, dan lainnya |
| 4. Chat pribadi | Kontak mana yang dibalas penjawab otomatis |
| 5. Penjawab otomatis | Nyalakan dan hentikan, status, jam tenang, menunggu beberapa pesan |
| 6. Log server | Peristiwa dan diagnostik |
| 7. Cara kerja | Tentang aplikasi dan pembuatnya |
| 8. Statistik | Balasan, pesan suara, dan kesalahan per hari |
| 9. Tema aplikasi | Terang, gelap, atau sistem. Juga **bahasa antarmuka** |
| 10. Pesan terhapus | Arsip pesan yang dihapus di chat terpilih (hingga 30 hari), dengan pencarian |
| 11. Memori kontak | Ringkasan tiap kontak: nama, minat, kesepakatan, fakta penting; tombol «Lupakan»; memuat riwayat atas perintah Anda |
| 12. Foto dan dokumen | Teks dan deskripsi foto; membaca PDF, DOCX, TXT, CSV, JSON |
| 13. Persetujuan balasan | Draf: kirim, ubah, atau tolak sebelum dikirim |
| 14. Statistik terperinci | Biaya AI, pengeluaran per model dan chat, waktu balasan, kesalahan |
| 15. Pemeriksaan fitur | Apa yang berfungsi, apa yang tidak, dan mengapa — dengan tombol perbaikan |

Juga: transkripsi pesan suara (Whisper melalui Groq), widget, notifikasi status dan ubin pengaturan cepat, perintah `/ai_off` untuk kontak, jam tenang, dan satu balasan untuk beberapa pesan.

## Persyaratan

- Android 9 atau lebih baru.
- VPS dengan Debian 12 atau Ubuntu 24.04, IPv4 publik, dan akses SSH sebagai root.
- `api_id` dan `api_hash` Telegram milik Anda dari [my.telegram.org](https://my.telegram.org) (tidak perlu untuk balasan dari notifikasi).
- Kunci API AI: **Groq** (ada paket gratis), **Google Gemini**, **GigaChat**, atau **API kompatibel OpenAI** apa pun (misalnya OpenRouter).

## Mulai cepat

1. Unduh APK dari **Releases** lalu pasang.
2. **1. Koneksi VPS**: masukkan alamat dan kata sandi root, ketuk «Pasang server dari aplikasi».
3. **2. Telegram**: masukkan `api_id`, `api_hash`, dan nomor, lalu masuk dengan kode atau QR. Atau buka «Balasan dari notifikasi» untuk bekerja tanpa masuk.
4. **3. AI dan teks**: pilih penyedia, kunci, dan model, tulis instruksi, lalu ketuk «Uji». Pilih gaya jika mau.
5. **4. Chat pribadi**: centang kontak Anda.
6. **5. Penjawab otomatis**: nyalakan.

Jika ada yang tidak berfungsi, buka **15. Pemeriksaan fitur**: penyebab dan solusinya ada di sana.

## Privasi dan penggunaan yang bertanggung jawab

- Pesan hanya dikirim ke penyedia AI pilihan Anda dan hanya dari chat terpilih. Detail: [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Beri tahu kontak bahwa AI mungkin membalas, dan jangan memuat riwayat mereka tanpa persetujuan.
- Patuhi [ketentuan API Telegram](https://core.telegram.org/api/terms): tanpa spam dan pengiriman massal.

Build, struktur repositori, dan terjemahan: lihat [README bahasa Inggris](../../README.md).

## Pembuat dan lisensi

**@stytam** — [t.me/stytam](https://t.me/stytam). Lisensi [MIT](../../LICENSE); komponen pihak ketiga: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
