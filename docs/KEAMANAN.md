# Keamanan & privasi

## Yang sengaja TIDAK ada di repo ini

| Data | Di mana disimpan sebenarnya |
|---|---|
| Token bot Telegram | Dimasukkan di layar pengaturan Kontrol TV di TV; disimpan terenkripsi AES-GCM dengan kunci di **Android Keystore** (`Store.setToken`). |
| Chat ID pemilik & admin | Didapat saat `/pair KODE` dari Telegram; disimpan di SharedPreferences privat aplikasi. |
| PIN | Diatur lewat `/pin 1234` (pesan langsung dihapus dari chat); disimpan sebagai hash SHA-256 bergaram, 20.000 iterasi. |
| Kata sandi, IP rumah, nama Wi-Fi | Tidak dipakai di kode. |
| Berkas suara azan | Tidak disertakan (hak cipta tidak jelas). Lihat [jadwal-sholat/README.md](../jadwal-sholat/README.md#suara-azan). |
| Keystore penandatangan | Build release memakai *debug keystore* lokal Anda (`~/.android/debug.keystore`), tidak pernah di-commit. |

`.gitignore` di tiap proyek menolak `local.properties`, `build/`, `*.apk`, `*.jks`, `*.keystore` dan berkas token.

## Bot Telegram

- Hanya **pemilik** (akun yang pertama mengirim `/pair KODE` dengan kode 6 digit yang tampil di TV) dan admin yang
  ditambahkan pemilik yang dilayani. Pesan dari akun lain diabaikan tanpa balasan.
- Hanya chat pribadi; grup diabaikan.
- Tidak ada perintah shell bebas. Semua aksi adalah fungsi yang sudah ditentukan.
- Bot memakai *long polling* keluar ke `api.telegram.org`; TV tidak membuka port apa pun.
- `/lepas` memutus bot, dengan konfirmasi 60 detik.

## Izin berisiko dan alasannya

| Izin | Aplikasi | Untuk apa |
|---|---|---|
| Aksesibilitas (`GuardService`) | Kontrol TV | Mendeteksi aplikasi yang dibuka & memblok tombol remote saat Kunci TV. Tidak membaca isi layar (`canRetrieveWindowContent=false`). |
| `WRITE_SECURE_SETTINGS` (ADB) | Kontrol TV | Memulihkan sendiri aksesibilitas, akses media launcher, dan screensaver setelah update. |
| `SYSTEM_ALERT_WINDOW` (appops) | Kontrol TV, Jadwal Sholat | Layar Kunci TV, pesan, layar azan. |
| `PACKAGE_USAGE_STATS` (appops) | Kontrol TV, Jadwal Sholat | Laporan lama pemakaian; menjaga layar tetap menyala saat aplikasi di depan. |
| `REQUEST_INSTALL_PACKAGES` | Kontrol TV | Memasang APK yang dikirim pemilik (selalu dikonfirmasi di TV). |

## Batasan

Ini kendali orang tua tingkat rumahan. Reset pabrik TV, atau orang yang bisa mengakses Opsi pengembang/ADB, dapat
melewatinya. Kunci Opsi pengembang (atau matikan setelah selesai memasang) bila perlu.
