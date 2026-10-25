# TV eL Khair

Kumpulan aplikasi untuk satu Android TV keluarga: beranda yang rapi, jadwal & azan, kendali orang tua lewat
Telegram, dan Al-Qur'an yang nyaman dibaca dengan remote. Semua diuji di **Toshiba Android TV (Android 11, MediaTek)**.

![Beranda TV](docs/img/launcher-home.jpg)

| Aplikasi | Paket | Folder | Isi |
|---|---|---|---|
| **Launcher eL Khair** | `com.gathin.tvhome` | [`launcher/`](launcher/) | Beranda TV: sapaan keluarga, jam, cuaca, widget media, widget sholat, 6 aplikasi sebaris. Modifikasi [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv). |
| **Jadwal Sholat** | `id.elkhair.sholat` | [`jadwal-sholat/`](jadwal-sholat/) | 8 waktu sholat (Kemenag), azan otomatis yang tidak bisa dilewati, screensaver jam & cuaca/foto. |
| **Kontrol TV** | `id.elkhair.kontroltv` | [`kontrol-tv/`](kontrol-tv/) | Kunci aplikasi & seluruh TV, jadwal, batas waktu, laporan — dikendalikan dari bot Telegram. |
| **Al-Qur'an TV** | `com.quranapp.android` | [`quranapp-tv/`](quranapp-tv/) | Mode TV untuk [AlfaazPlus/QuranApp](https://github.com/AlfaazPlus/QuranApp): navigasi remote, huruf besar, murottal per ayat/terus. |

## Cara kerja bersama

```
            ┌──────────────── Telegram (HP Ayah/Ibu) ───────────────┐
            │  /kuncitv  /pesan  foto→background  APK  laporan      │
            └───────────────────────────┬───────────────────────────┘
                                        │ long-polling keluar (tanpa port masuk)
┌──────────────┐  jadwal & cuaca   ┌────▼────────┐   kunci / overlay   ┌──────────────┐
│ Jadwal Sholat├──────────────────►│ Kontrol TV  ├────────────────────►│ semua aplikasi│
│  (provider)  │                   │ (aksesib.)  │                     └──────────────┘
└──────┬───────┘                   └────┬────────┘
       │ widget Sholat Berikutnya,      │ tulis background lewat ThemeProvider
       │ cuaca, kartu Imsak/Dhuha       │
┌──────▼────────────────────────────────▼────────┐◄── widget media (MediaSession):
│               Launcher eL Khair                │    Al-Qur'an TV, SmartTube, YouTube…
└────────────────────────────────────────────────┘
```

- **Jadwal Sholat → Launcher**: content provider `id.elkhair.sholat.times` & `id.elkhair.sholat.weather` (hanya-baca).
- **Pemutar apa pun → Launcher**: widget media membaca sesi media aktif lewat `NotificationListenerService`.
- **Kontrol TV → Launcher**: mengganti background lewat `content://com.gathin.tvhome.theme` (dilindungi `WRITE_SECURE_SETTINGS`).
- **Jadwal Sholat → Kontrol TV**: mode sholat, pengingat ke HP, jadwal di layar Kunci TV.

## Mulai cepat

1. Siapkan ADB lewat jaringan ke TV — lihat [docs/SETUP-ADB.md](docs/SETUP-ADB.md).
2. Build tiap aplikasi (JDK 17, Android SDK 34) dan pasang dengan `adb install -r`. Petunjuk rinci ada di README setiap folder.
3. Berikan izin khusus sekali lewat ADB (overlay, aksesibilitas, `WRITE_SECURE_SETTINGS`, dll.) — daftar lengkap di README tiap aplikasi.
4. Hubungkan Kontrol TV ke bot Telegram **Anda sendiri** — lihat [kontrol-tv/README.md](kontrol-tv/README.md#menghubungkan-bot-telegram).

## Keamanan & privasi

Repo ini **tidak berisi** token bot, chat ID Telegram, PIN, kata sandi, atau alamat IP rumah.
Semua itu dimasukkan saat aplikasi berjalan dan disimpan di TV (token terenkripsi Android Keystore, PIN di-hash).
Rincian: [docs/KEAMANAN.md](docs/KEAMANAN.md).

## Dokumentasi

- [docs/SETUP-ADB.md](docs/SETUP-ADB.md) — menyambungkan ADB, IP tetap, setelah mati lampu
- [docs/KEAMANAN.md](docs/KEAMANAN.md) — data sensitif, izin, model ancaman
- [launcher/README.md](launcher/README.md) · [jadwal-sholat/README.md](jadwal-sholat/README.md) · [kontrol-tv/README.md](kontrol-tv/README.md) · [quranapp-tv/README.md](quranapp-tv/README.md)

## Lisensi

- `jadwal-sholat/`, `kontrol-tv/`, `launcher/` — MIT (lihat [LICENSE](LICENSE)). Launcher dan Jadwal Sholat berasal
  dari proyek [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT, © Gathin dan kontributor; addon Prayer Times oleh @majnioui).
- `quranapp-tv/` — **GPL-3.0**, mengikuti lisensi [QuranApp](https://github.com/AlfaazPlus/QuranApp).
- Font Outfit di Jadwal Sholat: SIL Open Font License (`jadwal-sholat/FONT-LICENSE-OFL.txt`).
