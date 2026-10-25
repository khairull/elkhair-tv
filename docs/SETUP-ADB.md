# Menyambungkan ADB ke TV

Semua aplikasi dipasang dan diberi izin lewat ADB dari komputer di jaringan yang sama dengan TV.

## 1. Aktifkan opsi pengembang

1. **Setelan → Preferensi Perangkat → Tentang** → tekan **Build** 7 kali.
2. **Setelan → Preferensi Perangkat → Opsi pengembang** → nyalakan **Debugging USB** dan, bila ada,
   **Debugging jaringan / ADB jaringan**.

## 2. Sambungkan

```bash
adb connect <IP-TV>:5555
adb devices          # harus tampil "<IP-TV>:5555  device"
```

Saat pertama kali, TV menampilkan dialog **Izinkan debugging?** → centang **Selalu izinkan** → **OK**.

Bila TV hanya punya *Wireless debugging* (Android 11+) dengan port acak, pasangkan dulu:
`adb pair <IP-TV>:<port-pairing>` lalu `adb connect <IP-TV>:<port>`.

## 3. Setelah mati lampu / restart

Pada sebagian TV, ADB jaringan mati sendiri setelah restart. Gejalanya: `Connection refused`.
Matikan lalu nyalakan lagi **Debugging USB/ADB jaringan** di Opsi pengembang, lalu `adb connect` ulang.

## 4. Pakai IP tetap

Alamat IP TV bisa berubah setelah router restart. Supaya perintah ADB tidak perlu diubah-ubah:

- **di router**: reservasi DHCP untuk alamat MAC TV, atau
- **di TV**: Setelan → Jaringan → (Wi-Fi/Ethernet) → Setelan IP → **Statis**.

## 5. Perintah yang sering dipakai

```bash
adb install -r app-release.apk                       # pasang/perbarui
adb shell pm list packages | grep elkhair            # cek terpasang
adb shell am start -n id.elkhair.sholat/.MainActivity
adb exec-out screencap -p > layar.png                # tangkapan layar
adb shell dumpsys window | grep mCurrentFocus        # aplikasi di depan
adb logcat -b crash                                  # log crash
```

Izin khusus per aplikasi ada di README masing-masing.
