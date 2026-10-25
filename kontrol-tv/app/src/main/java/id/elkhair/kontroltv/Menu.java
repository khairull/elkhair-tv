package id.elkhair.kontroltv;

import android.content.Context;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

/** Menu bertombol untuk /start: kelompok perintah + aksi cepat. Pesan diedit di tempat, tidak menumpuk chat. */
final class Menu {
    private Menu() {}

    static final String MAIN = "📺 *Kontrol TV — eL Khair*\n"
            + "Pilih menu di bawah. Perintah juga bisa diketik langsung (ketik / untuk daftar).";

    static JSONObject mainKeyboard() throws Exception {
        return kb(new String[][] {
                {"📺 Kunci TV", "m:tv"},
                {"🔒 Kunci Aplikasi", "m:lock", "⏱ Jadwal & Batas", "m:time"},
                {"📊 Status & Laporan", "m:info", "🎵 Media & Volume", "m:media"},
                {"🕌 Sholat & Cuaca", "m:pray", "💬 Pesan & YouTube", "m:msg"},
                {"📦 Aplikasi & Gambar", "m:apk", "⚙️ Pengaturan", "m:sec"}});
    }

    static String text(String cat) {
        switch (cat) {
            case "lock": return "🔒 *Kunci Aplikasi*\n\n"
                    + "`/kunci nama` — kunci; bila sedang dibuka langsung ditutup\n"
                    + "`/buka nama` — buka kunci\n"
                    + "`/buka nama 30` — buka sementara 30 menit\n"
                    + "`/apps` — semua aplikasi dengan tombol kunci/buka\n"
                    + "`/terkunci` — daftar yang terkunci\n"
                    + "`/tutup` — tutup aplikasi yang sedang dibuka\n"
                    + "`/kuncisemua [1j]` — kunci semua aplikasi hiburan · `/bukasemua`";
            case "tv": return "📺 *Kunci TV*\n\n"
                    + "Seluruh TV dikunci dengan layar penuh (tombol aplikasi di remote ikut diblok). Volume & power tetap jalan, azan tetap tampil.\n\n"
                    + "`/kuncitv` — kunci dengan pesan default\n"
                    + "`/kuncitv Waktunya mengaji` — tambah pesan Anda\n"
                    + "`/kuncitv 1j Istirahat dulu` — kunci 1 jam lalu terbuka sendiri\n"
                    + "`/bukatv` — buka · `/bukatv 30` — buka 30 menit lalu terkunci lagi\n"
                    + "`/jadwaltv 21:00-05:00` — kunci otomatis tiap hari · `/jadwaltv hapus`\n"
                    + "Di TV: PIN atau tombol *Minta izin* (dikirim ke sini).";
            case "time": return "⏱ *Jadwal & Batas Waktu*\n\n"
                    + "`/jadwal nama 21:00-05:00` — kunci otomatis tiap hari\n"
                    + "`/jadwal hapus nama` — hapus jadwal\n"
                    + "`/batas nama 2j` — batas pemakaian harian (2j, 90m, 1j30m)\n"
                    + "`/batas hapus nama` — hapus batas\n"
                    + "`/buka nama 30` juga memberi tambahan waktu";
            case "pray": return "🕌 *Sholat & Cuaca*\n\n"
                    + "`/sholat` — jadwal hari ini\n"
                    + "`/pengingat 10` — pesan ke HP 10 menit sebelum waktu sholat (`/pengingat mati`)\n"
                    + "`/modesholat 15` — kunci aplikasi hiburan 15 menit sejak waktu sholat (`/modesholat mati`)\n"
                    + "`/cuaca` — cuaca saat ini";
            case "msg": return "💬 *Pesan & YouTube*\n\n"
                    + "`/pesan teks` — tampilkan pesan di layar TV\n"
                    + "Kirim *link YouTube* ke chat ini → langsung diputar di TV (SmartTube)\n"
                    + "`/notifapp` — notifikasi saat aplikasi dibuka (on/off)\n"
                    + "`/log` — catatan aktivitas";
            case "info": return "📊 *Status & Laporan*\n\n"
                    + "`/status` — kondisi TV saat ini\n"
                    + "`/laporan` — lama pemakaian aplikasi hari ini\n"
                    + "`/laporanotomatis 21:00` — kirim laporan tiap malam (`/laporanotomatis mati`)";
            case "media": return "🎵 *Media & Volume*\n\n"
                    + "Tombol di bawah mengendalikan SmartTube/pemutar yang sedang aktif.\n"
                    + "`/volume 30` — atur volume (0–100) · `/mute` — bisu/bunyi\n"
                    + "`/standby` — matikan layar";
            case "apk": return "📦 *Pasang / Hapus Aplikasi*\n\n"
                    + "• Kirim file `.apk` (maks 20 MB) ke chat ini\n"
                    + "• `/pasang https://…/app.apk` — dari link (maks 300 MB)\n"
                    + "• `/hapus nama` — uninstall aplikasi\n"
                    + "Konfirmasi selalu muncul di layar TV.\n\n"
                    + "🖼 Kirim *foto* → disimpan di folder Wallpaper (slideshow screensaver), lalu bisa dijadikan background Home.\n"
                    + "Foto dengan keterangan *home* → langsung jadi background Home.\n"
                    + "`/background` — pilih background Home dari folder Wallpaper.\n"
                    + "`/folder` — isi folder Wallpaper: lihat, jadikan background, hapus.";
            case "sec": return "⚙️ *Pengaturan & Keamanan*\n\n"
                    + "`/pin 1234` — PIN buka darurat di TV (pesan otomatis dihapus)\n"
                    + "`/pengaturan` — buka layar pengaturan Kontrol TV di TV (5 menit)\n"
                    + "`/izin` — periksa & pulihkan izin aplikasi\n"
                    + "`/admin` — kelola admin tambahan (mis. akun ibu)\n"
                    + "`/alias ID Ibu` — nama pengirim yang tampil di TV (Anda: Ayah)\n"
                    + "`/lepas` — putuskan bot dari TV (dengan konfirmasi)";
            default: return MAIN;
        }
    }

    static JSONObject keyboard(String cat) throws Exception {
        switch (cat) {
            case "lock": return kb(new String[][] {{"📋 Daftar aplikasi", "a:apps", "🔒 Yang terkunci", "a:locked"},
                    {"🔒 Kunci semua 1 jam", "a:lockall", "🔓 Buka semua", "a:unlockall"},
                    {"✖️ Tutup aplikasi aktif", "a:close"}, {"⬅️ Menu", "m:main"}});
            case "tv": return kb(new String[][] {{"🔒 Kunci TV sekarang", "a:tvlock", "🔓 Buka", "a:tvunlock"},
                    {"⏱ Buka 30 menit", "a:tvunlock30", "🗓 Jadwal", "a:tvsched"}, {"⬅️ Menu", "m:main"}});
            case "pray": return kb(new String[][] {{"🕌 Jadwal hari ini", "a:sholat", "🌤 Cuaca", "a:cuaca"}, {"⬅️ Menu", "m:main"}});
            case "msg": return kb(new String[][] {{"🧾 Log aktivitas", "a:log", "📱 Notif app on/off", "a:notifapp"}, {"⬅️ Menu", "m:main"}});
            case "time": return kb(new String[][] {{"🗓 Lihat jadwal", "a:sched", "⏱ Lihat batas", "a:limits"}, {"⬅️ Menu", "m:main"}});
            case "info": return kb(new String[][] {{"📺 Status TV", "a:status", "📊 Laporan hari ini", "a:report"}, {"⬅️ Menu", "m:main"}});
            case "media": return kb(new String[][] {{"⏮", "a:prev", "⏸", "a:pause", "▶️", "a:play", "⏭", "a:next"},
                    {"🔉 −10", "a:vol-", "🔇", "a:mute", "🔊 +10", "a:vol+"}, {"🌙 Standby", "a:standby"}, {"⬅️ Menu", "m:main"}});
            case "sec": return kb(new String[][] {{"🩺 Periksa izin", "a:izin"}, {"⬅️ Menu", "m:main"}});
            case "apk": return kb(new String[][] {{"📁 Isi folder Wallpaper", "a:folder", "🏠 Ganti background", "a:bg"}, {"⬅️ Menu", "m:main"}});
            default: return mainKeyboard();
        }
    }

    /** Aksi cepat; hasil berupa teks balasan singkat (null bila sudah mengirim pesan sendiri). */
    static String action(Context c, String token, long chat, String a) throws Exception {
        switch (a) {
            case "status": Bot.send(token, chat, Bot.status(c), null); return null;
            case "report": Bot.send(token, chat, Bot.report(c), null); return null;
            case "apps": Bot.send(token, chat, "Ketuk untuk mengunci/membuka:", Bot.appsKeyboardPublic(c)); return null;
            case "locked": Bot.send(token, chat, Bot.lockedListPublic(c), null); return null;
            case "sched": Bot.handleText(c, token, chat, "/jadwal"); return null;
            case "limits": Bot.handleText(c, token, chat, "/batas"); return null;
            case "close": Bot.handleText(c, token, chat, "/tutup"); return null;
            case "lockall": Bot.handleText(c, token, chat, "/kuncisemua 1j"); return null;
            case "unlockall": Bot.handleText(c, token, chat, "/bukasemua"); return null;
            case "sholat": Bot.handleText(c, token, chat, "/sholat"); return null;
            case "cuaca": Bot.handleText(c, token, chat, "/cuaca"); return null;
            case "log": Bot.handleText(c, token, chat, "/log"); return null;
            case "izin": Bot.handleText(c, token, chat, "/izin"); return null;
            case "bg": Bot.handleText(c, token, chat, "/background"); return null;
            case "folder": Bot.handleText(c, token, chat, "/folder"); return null;
            case "notifapp": Bot.handleText(c, token, chat, "/notifapp"); return null;
            case "tvlock": Bot.handleText(c, token, chat, "/kuncitv"); return null;
            case "tvunlock": Bot.handleText(c, token, chat, "/bukatv"); return null;
            case "tvunlock30": Bot.handleText(c, token, chat, "/bukatv 30"); return null;
            case "tvsched": Bot.handleText(c, token, chat, "/jadwaltv"); return null;
            case "standby": return GuardService.standby() ? "🌙 Standby dikirim" : "Standby tidak didukung";
            case "prev": Extras.mediaKey(c, KeyEvent.KEYCODE_MEDIA_PREVIOUS); return "⏮ Sebelumnya";
            case "pause": Extras.mediaKey(c, KeyEvent.KEYCODE_MEDIA_PAUSE); return "⏸ Dijeda";
            case "play": Extras.mediaKey(c, KeyEvent.KEYCODE_MEDIA_PLAY); return "▶️ Diputar";
            case "next": Extras.mediaKey(c, KeyEvent.KEYCODE_MEDIA_NEXT); return "⏭ Berikutnya";
            case "vol-": return "🔉 Volume " + Extras.volume(c, Math.max(0, Extras.volume(c, -1) - 10)) + "%";
            case "vol+": return "🔊 Volume " + Extras.volume(c, Math.min(100, Extras.volume(c, -1) + 10)) + "%";
            case "mute": return Extras.toggleMute(c) ? "🔇 Dibisukan" : "🔊 Suara aktif";
            default: return null;
        }
    }

    static JSONArray commands() throws Exception {
        String[][] cmd = {{"start", "Menu utama"}, {"status", "Kondisi TV"}, {"apps", "Kunci/buka aplikasi"},
                {"kuncitv", "Kunci seluruh TV (+pesan)"}, {"bukatv", "Buka kunci TV"}, {"jadwaltv", "Jadwal kunci TV harian"},
                {"kunci", "Kunci aplikasi"}, {"buka", "Buka kunci (opsional: menit)"}, {"tutup", "Tutup aplikasi aktif"},
                {"jadwal", "Jadwal kunci harian"}, {"batas", "Batas pemakaian harian"}, {"laporan", "Pemakaian hari ini"},
                {"laporanotomatis", "Laporan tiap malam"}, {"pause", "Jeda media"}, {"play", "Putar media"},
                {"next", "Media berikutnya"}, {"prev", "Media sebelumnya"}, {"volume", "Atur volume 0-100"},
                {"mute", "Bisu/bunyi"}, {"standby", "Matikan layar"}, {"pasang", "Pasang APK dari link"},
                {"hapus", "Uninstall aplikasi"}, {"pin", "Atur PIN darurat"}, {"pengaturan", "Buka pengaturan di TV"},
                {"kuncisemua", "Kunci semua aplikasi hiburan"}, {"bukasemua", "Buka kunci semua"},
                {"sholat", "Jadwal sholat hari ini"}, {"pengingat", "Pengingat sholat ke HP"}, {"modesholat", "Kunci hiburan saat sholat"},
                {"cuaca", "Cuaca saat ini"}, {"pesan", "Pesan ke layar TV"}, {"notifapp", "Notif saat aplikasi dibuka"},
                {"log", "Log aktivitas"}, {"izin", "Periksa & pulihkan izin"}, {"background", "Ganti background Home"}, {"folder", "Isi folder Wallpaper"}, {"admin", "Kelola admin"}, {"alias", "Alias pengirim di layar TV"}, {"lepas", "Putuskan bot"}};
        JSONArray a = new JSONArray();
        for (String[] x : cmd) a.put(new JSONObject().put("command", x[0]).put("description", x[1]));
        return a;
    }

    private static JSONObject kb(String[][] rows) throws Exception {
        JSONArray r = new JSONArray();
        for (String[] row : rows) {
            JSONArray line = new JSONArray();
            for (int i = 0; i + 1 < row.length; i += 2) line.put(new JSONObject().put("text", row[i]).put("callback_data", row[i + 1]));
            r.put(line);
        }
        return new JSONObject().put("inline_keyboard", r);
    }
}
