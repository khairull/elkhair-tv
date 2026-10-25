package id.elkhair.kontroltv;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Perintah bot. Hanya pemilik (chat ID hasil /pair) yang dilayani; tidak ada perintah shell bebas. */
final class Bot {
    private Bot() {}

    static final String HELP = "*Kontrol TV*\n"
            + "/status — kondisi TV\n"
            + "/apps — daftar aplikasi, tombol kunci/buka\n"
            + "/kunci `nama` — kunci (langsung ditutup bila sedang dibuka)\n"
            + "/buka `nama` [menit] — buka kunci (sementara bila diberi menit)\n"
            + "/terkunci — daftar yang terkunci\n"
            + "/kuncitv [1j] [pesan] — kunci seluruh TV (layar kunci + pesan) · /bukatv [menit] · /jadwaltv `21:00-05:00`\n"
            + "/jadwal `nama` `21:00-05:00` — kunci otomatis tiap hari (/jadwal untuk daftar, /jadwal hapus `nama`)\n"
            + "/batas `nama` `2j` — batas pemakaian harian, mis. 2j, 90m, 1j30m (/batas untuk daftar, /batas hapus `nama`)\n"
            + "/laporanotomatis `21:00` — laporan pemakaian harian otomatis (/laporanotomatis mati)\n"
            + "/pause /play /next /prev — kendali media yang sedang diputar\n"
            + "/volume [0-100] — atur/lihat volume · /mute — bisu/bunyi\n"
            + "📷 Kirim foto ke bot — masuk ke slideshow screensaver (Pictures/Screensaver)\n"
            + "/tutup — tutup aplikasi yang sedang dibuka\n"
            + "/laporan — lama pemakaian aplikasi hari ini\n"
            + "/standby — matikan layar (standby)\n"
            + "/pin `angka` — atur PIN buka darurat di TV (4–8 digit)\n"
            + "/pengaturan — buka layar pengaturan Kontrol TV di TV (5 menit)\n"
            + "/pasang `link-https` — pasang APK dari link (atau kirim file .apk langsung, maks 20 MB)\n"
            + "/hapus `nama` — uninstall aplikasi (konfirmasi di TV)\n"
            + "/lepas — putuskan bot dari TV ini";

    static void handle(Context c, String token, JSONObject u) throws Exception {
        long owner = Store.owner(c);
        if (u.has("callback_query")) {
            JSONObject q = u.getJSONObject("callback_query");
            long from = q.getJSONObject("from").getLong("id");
            if (owner == 0 || !Store.isAdmin(c, from)) {
                api(token, "answerCallbackQuery", new JSONObject().put("callback_query_id", q.getString("id")), 15000);
                return;
            }
            if (!q.optString("data").startsWith("a:"))
                api(token, "answerCallbackQuery", new JSONObject().put("callback_query_id", q.getString("id")), 15000);
            callback(c, token, q);
            return;
        }
        JSONObject m = u.optJSONObject("message");
        if (m == null) return;
        if ((m.has("photo") || (m.has("document") && m.getJSONObject("document").optString("mime_type").startsWith("image/")))
                && owner != 0 && Store.isAdmin(c, m.getJSONObject("from").getLong("id")) && m.getJSONObject("chat").getLong("id") == m.getJSONObject("from").getLong("id")) {
            photoFromTelegram(c, token, m.getJSONObject("chat").getLong("id"), m);
            return;
        }
        if (m.has("document") && owner != 0 && Store.isAdmin(c, m.getJSONObject("from").getLong("id"))
                && m.getJSONObject("chat").getLong("id") == m.getJSONObject("from").getLong("id")) {
            Store.log(c, who(m) + ": kirim APK");
            apkFromTelegram(c, token, m.getJSONObject("chat").getLong("id"), m.getJSONObject("document"));
            return;
        }
        if (!m.has("text")) return;
        long chat = m.getJSONObject("chat").getLong("id");
        long from = m.getJSONObject("from").getLong("id");
        String text = m.getString("text").trim();
        String cmd = text.split("\\s+")[0].toLowerCase(Locale.ROOT);
        int at = cmd.indexOf('@');
        if (at > 0) cmd = cmd.substring(0, at);
        String arg = text.contains(" ") ? text.substring(text.indexOf(' ') + 1).trim() : "";

        if (owner == 0) {
            if (cmd.equals("/pair") && arg.equals(Store.pairCode(c)) && chat == from) {
                Store.setOwner(c, from);
                send(token, chat, "✅ Terhubung. TV ini sekarang hanya menerima perintah dari akun Anda.\n\n" + HELP, null);
            } else if (chat == from) {
                send(token, chat, "Kirim `/pair KODE` dengan kode 6 digit yang tampil di aplikasi Kontrol TV di layar TV.", null);
            }
            return;
        }
        if (!Store.isAdmin(c, from) || chat != from) return;   // selain pemilik/admin: diam
        boolean isOwner = from == owner;
        if (!cmd.startsWith("/") && text.matches("(?s).*(youtube\\.com/|youtu\\.be/).*")) { playYoutube(c, token, chat, text); Store.log(c, who(m) + ": putar YouTube"); return; }
        if (!cmd.equals("/pin") && m.optLong("message_id") != 0) Store.log(c, who(m) + ": " + (text.length() > 60 ? text.substring(0, 60) + "…" : text));

        switch (cmd) {
            case "/start": case "/help": case "/menu": send(token, chat, Menu.MAIN, Menu.mainKeyboard()); break;
            case "/status": send(token, chat, status(c), null); break;
            case "/apps": send(token, chat, "Ketuk untuk mengunci/membuka:", appsKeyboard(c)); break;
            case "/terkunci": send(token, chat, lockedList(c), null); break;
            case "/kunci": lockCmd(c, token, chat, arg, true, 0); break;
            case "/buka": {
                int min = 0;
                String name = arg;
                String[] parts = arg.split("\\s+");
                if (parts.length > 1 && parts[parts.length - 1].matches("\\d{1,4}")) {
                    min = Integer.parseInt(parts[parts.length - 1]);
                    name = arg.substring(0, arg.lastIndexOf(' ')).trim();
                }
                lockCmd(c, token, chat, name, false, min);
                break;
            }
            case "/tutup": {
                String cur = GuardService.current;
                if (!GuardService.closeCurrent()) send(token, chat, "⚠️ Penjaga tidak aktif, tidak bisa menutup aplikasi.", null);
                else send(token, chat, cur.isEmpty() ? "Tidak ada aplikasi yang sedang dibuka." : "✅ " + md(Apps.label(c, cur)) + " ditutup.", null);
                break;
            }
            case "/standby":
                send(token, chat, GuardService.standby() ? "🌙 Perintah standby dikirim." : "⚠️ Standby tidak didukung / penjaga tidak aktif.", null);
                break;
            case "/laporan": send(token, chat, report(c), null); break;
            case "/pin":
                api(token, "deleteMessage", new JSONObject().put("chat_id", chat).put("message_id", m.getLong("message_id")), 15000);
                if (arg.matches("\\d{4,8}")) { Store.setPin(c, arg); send(token, chat, "✅ PIN diatur (pesan berisi PIN sudah dihapus).", null); }
                else send(token, chat, "PIN harus 4–8 angka. Contoh: `/pin 4821`", null);
                break;
            case "/pengaturan":
                Store.allowSetup(c, 5);
                try {
                    c.startActivity(new android.content.Intent(c, SetupActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    send(token, chat, "⚙️ Pengaturan Kontrol TV dibuka di TV (akses 5 menit).", null);
                } catch (Exception e) {
                    send(token, chat, "Akses pengaturan diizinkan 5 menit, tapi layar tidak bisa dibuka otomatis.", null);
                }
                break;
            case "/pasang":
                if (!arg.startsWith("https://")) { send(token, chat, "Kirim file .apk ke chat ini, atau `/pasang https://…/aplikasi.apk`", null); break; }
                final String url = arg;
                send(token, chat, "⬇️ Mengunduh…", null);
                new Thread(() -> {
                    try {
                        java.io.File f = new java.io.File(Installer.dir(c), "u" + (System.currentTimeMillis() % 1_000_000_000L) + ".apk");
                        Installer.download(url, f, Installer.MAX_URL_BYTES);
                        offerInstall(c, token, chat, f);
                    } catch (Exception e) {
                        try { send(token, chat, "❌ Gagal mengunduh: " + md(e.getMessage()), null); } catch (Exception ignored) {}
                    }
                }).start();
                break;
            case "/hapus": {
                if (arg.isEmpty()) { send(token, chat, "Contoh: `/hapus orbia`", null); break; }
                List<Apps.App> found = Apps.find(c, arg);
                if (found.isEmpty()) { send(token, chat, "Aplikasi \"" + md(arg) + "\" tidak ditemukan.", null); break; }
                JSONArray rows = new JSONArray();
                for (Apps.App a : found) if (!a.pkg.equals(Apps.SETTINGS) && !a.pkg.equals(c.getPackageName()))
                    rows.put(new JSONArray().put(btn("🗑 Hapus " + a.label, "u:" + a.pkg)));
                send(token, chat, "Pilih yang akan dihapus (konfirmasi tetap muncul di TV):", new JSONObject().put("inline_keyboard", rows));
                break;
            }
            case "/jadwal": scheduleCmd(c, token, chat, arg); break;
            case "/batas": limitCmd(c, token, chat, arg); break;
            case "/laporanotomatis": {
                if (arg.equalsIgnoreCase("mati") || arg.equalsIgnoreCase("off")) {
                    Store.p(c).edit().remove("report_at").apply();
                    send(token, chat, "Laporan otomatis dimatikan.", null);
                } else {
                    int t = parseClock(arg.isEmpty() ? "21:00" : arg);
                    if (t < 0) { send(token, chat, "Contoh: `/laporanotomatis 21:00` atau `/laporanotomatis mati`", null); break; }
                    Store.p(c).edit().putInt("report_at", t).apply();
                    send(token, chat, "✅ Laporan pemakaian dikirim otomatis setiap hari pukul " + hhmm(t) + " WITA.", null);
                }
                break;
            }
            case "/pause": Extras.mediaKey(c, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE); send(token, chat, "⏸ Dijeda.", null); break;
            case "/play": Extras.mediaKey(c, android.view.KeyEvent.KEYCODE_MEDIA_PLAY); send(token, chat, "▶️ Diputar.", null); break;
            case "/next": Extras.mediaKey(c, android.view.KeyEvent.KEYCODE_MEDIA_NEXT); send(token, chat, "⏭ Berikutnya.", null); break;
            case "/prev": Extras.mediaKey(c, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS); send(token, chat, "⏮ Sebelumnya.", null); break;
            case "/volume": {
                int v = arg.matches("\\d{1,3}") ? Extras.volume(c, Integer.parseInt(arg)) : Extras.volume(c, -1);
                send(token, chat, "🔊 Volume " + v + "%", null);
                break;
            }
            case "/mute": send(token, chat, Extras.toggleMute(c) ? "🔇 Suara dibisukan." : "🔊 Suara aktif.", null); break;
            case "/kuncisemua": {
                int min = arg.isEmpty() ? 0 : parseDuration(arg);
                if (!arg.isEmpty() && min < 0) { send(token, chat, "Contoh: `/kuncisemua` (sampai dibuka) atau `/kuncisemua 1j`", null); break; }
                Store.lockAll(c, min);
                GuardService.enforceNow();
                send(token, chat, "🔒 *Semua aplikasi hiburan dikunci*" + (min > 0 ? " selama " + min + " menit." : " sampai `/bukasemua`.")
                        + "\n(Jadwal Sholat, Setelan & Home tetap bisa dibuka.)", null);
                break;
            }
            case "/bukasemua": Store.unlockAll(c); send(token, chat, "🔓 Kunci semua dibuka. Kunci per aplikasi tetap berlaku.", null); break;
            case "/modesholat": {
                if (arg.equalsIgnoreCase("mati") || arg.equals("0")) { Store.p(c).edit().putInt("pmode", 0).apply(); send(token, chat, "Mode sholat dimatikan.", null); break; }
                int min = arg.isEmpty() ? 15 : parseDuration(arg.matches("\\d+") ? arg + "m" : arg);
                if (min <= 0 || min > 120) { send(token, chat, "Contoh: `/modesholat 15` (menit) atau `/modesholat mati`", null); break; }
                Store.p(c).edit().putInt("pmode", min).apply();
                Sholat.refresh(c);
                send(token, chat, "🕌 *Mode sholat aktif:* aplikasi hiburan terkunci " + min + " menit sejak masuk waktu sholat (5 waktu).", null);
                break;
            }
            case "/sholat": send(token, chat, Sholat.scheduleText(c), null); break;
            case "/pengingat": {
                if (arg.equalsIgnoreCase("mati") || arg.equals("0")) { Store.p(c).edit().putInt("remind", 0).apply(); send(token, chat, "Pengingat sholat ke HP dimatikan.", null); break; }
                int min = arg.isEmpty() ? 10 : parseDuration(arg.matches("\\d+") ? arg + "m" : arg);
                if (min <= 0 || min > 120) { send(token, chat, "Contoh: `/pengingat 10` (menit sebelum) atau `/pengingat mati`", null); break; }
                Store.p(c).edit().putInt("remind", min).apply();
                send(token, chat, "🔔 Pengingat dikirim ke HP " + min + " menit sebelum tiap waktu sholat.", null);
                break;
            }
            case "/perbaiki": case "/izin": send(token, chat, Doctor.report(c), null); break;
            case "/folder": {
                List<String[]> f = Extras.folder(c);
                if (f.isEmpty()) { send(token, chat, "📁 Folder *Wallpaper* kosong. Kirim foto ke chat ini untuk mengisinya.", null); break; }
                long cur = Store.p(c).getLong("home_bg", -1);
                StringBuilder sb = new StringBuilder("📁 *Folder Wallpaper* — " + f.size() + " file\n`Pictures/Wallpaper/`\n\n");
                JSONArray rows = new JSONArray();
                long total = 0;
                for (int i = 0; i < f.size(); i++) {
                    String[] x = f.get(i);
                    long size = Long.parseLong(x[2]);
                    total += size;
                    boolean home = Long.parseLong(x[0]) == cur;
                    sb.append(i + 1).append(". ").append(md(x[1])).append(home ? "  🏠" : "").append("\n     ")
                            .append(String.format(Locale.ROOT, "%.1f", size / 1048576.0)).append(" MB · ").append(x[3]).append('\n');
                    if (i < 15) rows.put(new JSONArray().put(btn("👁 " + (i + 1), "fv:" + x[0])).put(btn("🏠 " + (i + 1), "bg:" + x[0]))
                            .put(btn("🗑 " + (i + 1), "fd:" + x[0])));
                }
                sb.append(String.format(Locale.ROOT, "\nTotal %.1f MB · 🏠 = background Home saat ini", total / 1048576.0));
                send(token, chat, sb.toString(), new JSONObject().put("inline_keyboard", rows));
                break;
            }
            case "/background": case "/wallpaper": {
                List<String[]> w = Extras.wallpapers(c, 12);
                if (w.isEmpty()) { send(token, chat, "Folder Wallpaper masih kosong. Kirim foto ke chat ini dulu.", null); break; }
                long cur = Store.p(c).getLong("home_bg", -1);
                JSONArray rows = new JSONArray(), row = new JSONArray();
                for (int i = 0; i < w.size(); i++) {
                    long id = Long.parseLong(w.get(i)[0]);
                    row.put(btn((id == cur ? "✅ " : "🖼 ") + "#" + (i + 1) + " · " + w.get(i)[1], "bg:" + id));
                    if (row.length() == 2) { rows.put(row); row = new JSONArray(); }
                }
                if (row.length() > 0) rows.put(row);
                send(token, chat, "🏠 *Pilih background Home* (foto di folder Wallpaper, terbaru dulu).\nKirim foto dengan keterangan *home* untuk langsung dijadikan background.",
                        new JSONObject().put("inline_keyboard", rows));
                break;
            }
            case "/cuaca": send(token, chat, Sholat.weatherText(c), null); break;
            case "/pesan":
                if (arg.isEmpty()) { send(token, chat, "Contoh: `/pesan Ayo makan malam!`", null); break; }
                if (!android.provider.Settings.canDrawOverlays(c)) { send(token, chat, "⚠️ Izin tampil di atas aplikasi belum aktif, pesan tidak bisa ditampilkan.", null); break; }
                BotService.showMessage(c, arg, sender(c, m.getJSONObject("from")));
                send(token, chat, "💬 Pesan ditampilkan di TV.", null);
                break;
            case "/notifapp": {
                boolean on = arg.equalsIgnoreCase("on") || arg.equalsIgnoreCase("aktif") || (arg.isEmpty() && !Store.p(c).getBoolean("notif_app", false));
                if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("mati")) on = false;
                Store.p(c).edit().putBoolean("notif_app", on).apply();
                send(token, chat, on ? "📱 Notifikasi saat aplikasi dibuka: *aktif*." : "📱 Notifikasi saat aplikasi dibuka: *mati*.", null);
                break;
            }
            case "/log": {
                StringBuilder sb = new StringBuilder("🧾 *Log aktivitas* (20 terakhir)\n");
                List<String> l = Store.logs(c, 20);
                if (l.isEmpty()) sb.append("Kosong.");
                for (String e : l) sb.append(md(e)).append('\n');
                send(token, chat, sb.toString(), null);
                break;
            }
            case "/admin": {
                if (!isOwner) { send(token, chat, "Hanya pemilik yang bisa mengatur admin.", null); break; }
                String[] a = arg.split("\\s+");
                if (a.length == 2 && a[1].matches("\\d{5,15}") && (a[0].equals("tambah") || a[0].equals("hapus"))) {
                    Store.setAdmin(c, Long.parseLong(a[1]), a[0].equals("tambah"));
                    send(token, chat, a[0].equals("tambah") ? "✅ Admin ditambahkan. Minta ia mengirim /start ke bot ini." : "Admin dihapus.", null);
                } else {
                    StringBuilder sb = new StringBuilder("👥 *Admin*\nPemilik: `" + owner + "`\n");
                    for (String id : Store.admins(c)) sb.append("Admin: `").append(id).append("`\n");
                    sb.append("\n`/admin tambah ID` · `/admin hapus ID`\nID Telegram bisa dilihat lewat @userinfobot.");
                    send(token, chat, sb.toString(), null);
                }
                break;
            }
            case "/kuncitv": {
                String rest = arg;
                int min = 0;
                String first = rest.split("\\s+")[0];
                if (!first.isEmpty() && first.matches("(?i)\\d+(j|jam|h|m|mnt|menit)(\\d+(m|mnt|menit)?)?")) {
                    min = parseDuration(first);
                    rest = rest.substring(first.length()).trim();
                }
                String byName = sender(c, m.getJSONObject("from"));
                TvLock.lock(c, rest, byName, Math.max(0, min), "Telegram");
                send(token, chat, "🔒 *TV dikunci*" + (min > 0 ? " selama " + (min >= 60 ? min / 60 + " jam " : "") + (min % 60 > 0 ? min % 60 + " menit" : "") : "") + "."
                        + (rest.isEmpty() ? "" : "\n💬 Pesan: " + md(rest))
                        + (GuardService.instance == null ? "\n⚠️ Penjaga sedang tidak aktif — tombol remote belum diblok!" : ""),
                        new JSONObject().put("inline_keyboard", new JSONArray().put(new JSONArray().put(btn("🔓 Buka", "tv:ok")).put(btn("⏱ Buka 30 menit", "tv:30")))));
                break;
            }
            case "/bukatv": {
                int min = arg.isEmpty() ? 0 : parseDuration(arg.matches("\\d+") ? arg + "m" : arg);
                if (!TvLock.active(c)) { send(token, chat, "TV memang tidak sedang dikunci. " + TvLock.statusText(c), null); break; }
                TvLock.unlock(c, Math.max(0, min), "Telegram");
                send(token, chat, min > 0 ? "🔓 TV dibuka " + min + " menit, lalu terkunci lagi otomatis." : "🔓 Kunci TV dibuka.", null);
                break;
            }
            case "/jadwaltv": {
                if (arg.equalsIgnoreCase("hapus") || arg.equalsIgnoreCase("mati")) { TvLock.setSchedule(c, -1, -1); send(token, chat, "🗓 Jadwal Kunci TV dihapus.", null); break; }
                java.util.regex.Matcher mm = java.util.regex.Pattern.compile("^(\\d{1,2})[:.](\\d{2})\\s*-\\s*(\\d{1,2})[:.](\\d{2})$").matcher(arg);
                if (mm.find()) {
                    int f = Integer.parseInt(mm.group(1)) * 60 + Integer.parseInt(mm.group(2)), t = Integer.parseInt(mm.group(3)) * 60 + Integer.parseInt(mm.group(4));
                    if (f >= 1440 || t >= 1440 || f == t) { send(token, chat, "Jam tidak valid.", null); break; }
                    TvLock.setSchedule(c, f, t);
                    send(token, chat, "🗓 TV dikunci otomatis tiap hari *" + TvLock.scheduleText(c) + "*." + (TvLock.inSchedule(c) ? "\nSekarang termasuk jam jadwal — TV dikunci dalam 30 detik." : ""), null);
                } else {
                    String cur = TvLock.scheduleText(c);
                    send(token, chat, "🗓 *Jadwal Kunci TV*: " + (cur == null ? "belum ada" : cur) + "\nContoh: `/jadwaltv 21:00-05:00` · `/jadwaltv hapus`", null);
                }
                break;
            }
            case "/alias": {
                if (!isOwner) { send(token, chat, "Hanya pemilik yang bisa mengatur alias.", null); break; }
                java.util.regex.Matcher am = java.util.regex.Pattern.compile("^(\\d{5,15})\\s+(.+)$").matcher(arg);
                if (am.find()) {
                    long id = Long.parseLong(am.group(1));
                    String nm = am.group(2).trim();
                    if (nm.equalsIgnoreCase("hapus")) nm = "";
                    if (nm.length() > 30) nm = nm.substring(0, 30);
                    Store.setAlias(c, id, nm);
                    send(token, chat, nm.isEmpty() ? "Alias untuk `" + id + "` dihapus." : "✅ Alias `" + id + "` → *" + md(nm) + "*", null);
                } else if (!arg.isEmpty()) {
                    String nm = arg.length() > 30 ? arg.substring(0, 30) : arg;
                    Store.setAlias(c, from, nm);
                    send(token, chat, "✅ Alias Anda → *" + md(nm) + "*", null);
                } else {
                    StringBuilder sb = new StringBuilder("🏷 *Alias pengirim* (tampil di layar TV)\n");
                    sb.append("Anda (`").append(owner).append("`): *").append(md(Store.alias(c, owner, ""))).append("*\n");
                    for (String id : Store.admins(c)) {
                        long l = Long.parseLong(id);
                        sb.append("Admin `").append(id).append("`: *").append(md(Store.alias(c, l, "(nama Telegram)"))).append("*\n");
                    }
                    sb.append("\n`/alias Ayah` — alias Anda\n`/alias ID Ibu` — alias admin\n`/alias ID hapus` — kembali ke nama Telegram");
                    send(token, chat, sb.toString(), null);
                }
                break;
            }
            case "/lepas": {
                if (!isOwner) { send(token, chat, "Hanya pemilik yang bisa memutus bot.", null); break; }
                Store.p(c).edit().putLong("unpair_req", System.currentTimeMillis()).apply();
                JSONArray row = new JSONArray().put(btn("⚠️ Ya, putuskan", "l:ok")).put(btn("Batal", "l:no"));
                send(token, chat, "⚠️ *Putuskan bot dari TV ini?*\nSetelah diputus, bot tidak bisa lagi mengendalikan TV sampai dihubungkan ulang dari layar Kontrol TV di TV. Konfirmasi berlaku 60 detik.",
                        new JSONObject().put("inline_keyboard", new JSONArray().put(row)));
                break;
            }
            default: send(token, chat, "Perintah tidak dikenal.\n\n" + HELP, null);
        }
    }

    private static void lockCmd(Context c, String token, long chat, String name, boolean lock, int minutes) throws Exception {
        if (name.isEmpty()) { send(token, chat, lock ? "Contoh: `/kunci youtube`" : "Contoh: `/buka youtube` atau `/buka youtube 30`", null); return; }
        List<Apps.App> found = Apps.find(c, name);
        if (found.isEmpty()) { send(token, chat, "Aplikasi \"" + md(name) + "\" tidak ditemukan. Lihat /apps", null); return; }
        if (found.size() > 1) {
            JSONArray rows = new JSONArray();
            for (Apps.App a : found) rows.put(new JSONArray().put(btn((lock ? "🔒 " : "🔓 ") + a.label, (lock ? "k:" : "b:") + a.pkg)));
            send(token, chat, "Ada beberapa yang cocok, pilih:", new JSONObject().put("inline_keyboard", rows));
            return;
        }
        send(token, chat, apply(c, found.get(0).pkg, lock, minutes), null);
    }

    private static String apply(Context c, String pkg, boolean lock, int minutes) {
        String l = md(Apps.label(c, pkg));
        if (lock) {
            Store.setLocked(c, pkg, true);
            GuardService.enforceNow();
            return "🔒 *" + l + "* dikunci." + (GuardService.instance == null ? "\n⚠️ Penjaga sedang tidak aktif!" : "");
        }
        if (minutes > 0) {
            if (!Store.locked(c).contains(pkg) && !Store.inSchedule(c, pkg) && !Store.limitHit(c, pkg)) return l + " memang tidak terkunci.";
            Store.unlockFor(c, pkg, minutes);
            return "🔓 *" + l + "* dibuka " + minutes + " menit, lalu terkunci lagi otomatis.";
        }
        Store.setLocked(c, pkg, false);
        return "🔓 *" + l + "* dibuka.";
    }

    private static void callback(Context c, String token, JSONObject q) throws Exception {
        String data = q.getString("data");
        long chat = q.getJSONObject("message").getJSONObject("chat").getLong("id");
        long msgId = q.getJSONObject("message").getLong("message_id");
        String pkg = data.substring(2);
        if (data.startsWith("m:")) {
            String cat = data.substring(2);
            api(token, "editMessageText", new JSONObject().put("chat_id", chat).put("message_id", msgId).put("parse_mode", "Markdown")
                    .put("text", Menu.text(cat)).put("reply_markup", Menu.keyboard(cat)), 15000);
            return;
        }
        if (data.startsWith("a:")) {
            String r = Menu.action(c, token, chat, data.substring(2));
            JSONObject ans = new JSONObject().put("callback_query_id", q.getString("id"));
            if (r != null) ans.put("text", r);
            api(token, "answerCallbackQuery", ans, 15000);
            return;
        }
        if ((data.equals("l:ok") || data.equals("l:no")) && q.getJSONObject("from").getLong("id") != Store.owner(c)) return;
        if (data.equals("l:ok") || data.equals("l:no")) {
            long req = Store.p(c).getLong("unpair_req", 0);
            Store.p(c).edit().remove("unpair_req").apply();
            boolean ok = data.equals("l:ok") && System.currentTimeMillis() - req < 60_000L;
            api(token, "editMessageText", new JSONObject().put("chat_id", chat).put("message_id", msgId).put("text",
                    ok ? "Bot diputus dari TV ini. Untuk menghubungkan lagi, buka Kontrol TV di TV."
                       : data.equals("l:ok") ? "⌛ Konfirmasi kedaluwarsa, bot tetap terhubung." : "✅ Dibatalkan, bot tetap terhubung."), 15000);
            if (ok) Store.setOwner(c, 0);
            return;
        }
        if (data.equals("s:ok") || data.equals("s:no")) {
            boolean ok = data.equals("s:ok");
            if (ok) Store.allowSetup(c, 5);
            api(token, "editMessageText", new JSONObject().put("chat_id", chat).put("message_id", msgId)
                    .put("text", ok ? "✅ Akses pengaturan diizinkan 5 menit." : "⛔ Akses pengaturan ditolak."), 15000);
            return;
        }
        if (data.startsWith("tv:")) {
            String who = sender(c, q.getJSONObject("from"));
            String r;
            switch (data) {
                case "tv:ok": if (TvLock.active(c)) TvLock.unlock(c, 0, who); r = "✅ TV dibuka oleh " + who + "."; break;
                case "tv:30": if (TvLock.active(c)) TvLock.unlock(c, 30, who); r = "⏱ TV dibuka 30 menit oleh " + who + ", lalu terkunci lagi."; break;
                case "tv:60": if (TvLock.active(c)) TvLock.unlock(c, 60, who); r = "⏱ TV dibuka 1 jam oleh " + who + ", lalu terkunci lagi."; break;
                default: TvLock.requestAnswered(c, false); r = "❌ Permintaan ditolak oleh " + who + ".";
            }
            api(token, "editMessageText", new JSONObject().put("chat_id", chat).put("message_id", msgId).put("text", r), 15000);
            return;
        }
        if (data.startsWith("fv:")) {
            long id = Long.parseLong(data.substring(3));
            try { sendPhoto(token, chat, Extras.preview(c, id), "Pratinjau"); }
            catch (Exception e) { send(token, chat, "❌ Gagal membuka foto.", null); }
            return;
        }
        if (data.startsWith("fd:")) {
            long id = Long.parseLong(data.substring(3));
            if (id == Store.p(c).getLong("home_bg", -1)) { send(token, chat, "Foto ini sedang dipakai sebagai background Home; ganti background dulu.", null); return; }
            send(token, chat, Extras.delete(c, id) ? "🗑 Foto dihapus dari folder Wallpaper." : "❌ Foto ini tidak bisa dihapus dari bot (bukan kiriman bot).", null);
            Store.log(c, "Hapus foto wallpaper");
            return;
        }
        if (data.startsWith("bg:")) {
            long id = Long.parseLong(data.substring(3));
            try {
                Extras.setHomeBackground(c, id);
                api(token, "editMessageReplyMarkup", new JSONObject().put("chat_id", chat).put("message_id", msgId)
                        .put("reply_markup", new JSONObject().put("inline_keyboard", new JSONArray())), 15000);
                send(token, chat, "🏠 Background Home diganti.", null);
                Store.log(c, "Background Home diganti");
            } catch (Exception e) {
                send(token, chat, "❌ Gagal mengganti background: " + md(String.valueOf(e.getMessage())), null);
            }
            return;
        }
        if (data.startsWith("i:")) {
            java.io.File f = new java.io.File(Installer.dir(c), pkg);
            if (!f.exists()) { send(token, chat, "File sudah tidak ada, kirim ulang APK-nya.", null); return; }
            api(token, "editMessageReplyMarkup", new JSONObject().put("chat_id", chat).put("message_id", msgId)
                    .put("reply_markup", new JSONObject().put("inline_keyboard", new JSONArray())), 15000);
            Installer.install(c, f);
            send(token, chat, "📲 Dialog pemasangan ditampilkan di TV — tekan *Instal* dengan remote.", null);
            return;
        }
        if (data.startsWith("x:")) {
            new java.io.File(Installer.dir(c), pkg).delete();
            api(token, "editMessageText", new JSONObject().put("chat_id", chat).put("message_id", msgId).put("text", "✖️ Dibatalkan, file dihapus."), 15000);
            return;
        }
        if (data.startsWith("u:")) {
            Installer.uninstall(c, pkg);
            send(token, chat, "🗑 Dialog penghapusan *" + md(Apps.label(c, pkg)) + "* ditampilkan di TV — tekan *OK* dengan remote.", null);
            return;
        }
        if (data.startsWith("t:")) {
            boolean lock = !Store.locked(c).contains(pkg);
            apply(c, pkg, lock, 0);
            api(token, "editMessageReplyMarkup", new JSONObject().put("chat_id", chat).put("message_id", msgId)
                    .put("reply_markup", appsKeyboard(c)), 15000);
        } else if (data.startsWith("k:")) send(token, chat, apply(c, pkg, true, 0), null);
        else if (data.startsWith("b:")) send(token, chat, apply(c, pkg, false, 0), null);
    }

    private static void apkFromTelegram(Context c, String token, long chat, JSONObject doc) throws Exception {
        String name = doc.optString("file_name", "file");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) { send(token, chat, "Hanya file `.apk` yang didukung.", null); return; }
        if (doc.optLong("file_size", 0) > 20L * 1024 * 1024) {
            send(token, chat, "File lebih dari 20 MB (batas Telegram untuk bot). Unggah ke tempat lain lalu kirim `/pasang https://…`", null);
            return;
        }
        send(token, chat, "⬇️ Mengunduh " + md(name) + "…", null);
        String fileId = doc.getString("file_id");
        new Thread(() -> {
            try {
                JSONObject r = api(token, "getFile", new JSONObject().put("file_id", fileId), 30000).getJSONObject("result");
                java.io.File f = new java.io.File(Installer.dir(c), "t" + (System.currentTimeMillis() % 1_000_000_000L) + ".apk");
                Installer.download("https://api.telegram.org/file/bot" + token + "/" + r.getString("file_path"), f, 21L * 1024 * 1024);
                offerInstall(c, token, chat, f);
            } catch (Exception e) {
                try { send(token, chat, "❌ Gagal mengunduh file dari Telegram.", null); } catch (Exception ignored) {}
            }
        }).start();
    }

    private static void offerInstall(Context c, String token, long chat, java.io.File f) throws Exception {
        String info = Installer.describe(c, f);
        if (info == null) { f.delete(); send(token, chat, "❌ File bukan APK yang valid (format .apks/.xapk belum didukung).", null); return; }
        JSONArray row = new JSONArray().put(btn("📦 Pasang", "i:" + f.getName())).put(btn("✖️ Batal", "x:" + f.getName()));
        send(token, chat, info + "\nPeriksa info di atas, lalu tekan *Pasang*.", new JSONObject().put("inline_keyboard", new JSONArray().put(row)));
    }

    static JSONObject appsKeyboardPublic(Context c) throws Exception { return appsKeyboard(c); }
    static String lockedListPublic(Context c) { return lockedList(c); }

    /** Jalankan perintah teks seolah dikirim pemilik (dipakai tombol menu). */
    static void handleText(Context c, String token, long chat, String text) throws Exception {
        JSONObject m = new JSONObject().put("text", text).put("message_id", 0)
                .put("chat", new JSONObject().put("id", chat)).put("from", new JSONObject().put("id", chat));
        handle(c, token, new JSONObject().put("message", m));
    }

    /** Nama pengirim untuk layar TV: alias bila ada (pemilik = "Ayah"), selain itu nama Telegram. */
    static String sender(Context c, JSONObject from) {
        return Store.alias(c, from.optLong("id"), from.optString("first_name", "Admin"));
    }

    static String who(JSONObject m) {
        JSONObject f = m.optJSONObject("from");
        return f == null ? "?" : f.optString("first_name", String.valueOf(f.optLong("id")));
    }

    private static void playYoutube(Context c, String token, long chat, String text) throws Exception {
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("https?://\\S+").matcher(text);
        if (!mm.find()) return;
        String url = mm.group();
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean st = false;
        try { c.getPackageManager().getPackageInfo("org.smarttube.stable", 0); i.setPackage("org.smarttube.stable"); st = true; } catch (Exception ignored) {}
        try {
            c.startActivity(i);
            send(token, chat, "▶️ Diputar di TV" + (st ? " lewat SmartTube." : "."), null);
        } catch (Exception e) {
            send(token, chat, "❌ Tidak bisa membuka link di TV.", null);
        }
    }

    static int parseClock(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{1,2})[:.](\\d{2})$").matcher(s.trim());
        if (!m.find()) return -1;
        int h = Integer.parseInt(m.group(1)), mi = Integer.parseInt(m.group(2));
        return h < 24 && mi < 60 ? h * 60 + mi : -1;
    }

    static String hhmm(int m) { return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60); }

    /** "2j", "90m", "1j30m", "2" (jam) → menit; -1 bila tidak valid. */
    static int parseDuration(String s) {
        s = s.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:(\\d+)(?:j|jam|h))?(?:(\\d+)(?:m|mnt|menit)?)?$").matcher(s);
        if (s.isEmpty() || !m.find()) return -1;
        int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1)), mi = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (m.group(1) == null && m.group(2) != null && !s.matches(".*\\D.*")) { h = mi; mi = 0; }   // "2" = 2 jam
        int t = h * 60 + mi;
        return t > 0 && t <= 24 * 60 ? t : -1;
    }

    private static Apps.App one(Context c, String token, long chat, String name) throws Exception {
        List<Apps.App> f = Apps.find(c, name);
        if (f.isEmpty()) { send(token, chat, "Aplikasi \"" + md(name) + "\" tidak ditemukan. Lihat /apps", null); return null; }
        if (f.size() > 1) {
            StringBuilder sb = new StringBuilder("Ada beberapa yang cocok, tulis lebih spesifik:\n");
            for (Apps.App a : f) sb.append("• ").append(md(a.label)).append('\n');
            send(token, chat, sb.toString(), null);
            return null;
        }
        return f.get(0);
    }

    private static void scheduleCmd(Context c, String token, long chat, String arg) throws Exception {
        if (arg.isEmpty()) {
            StringBuilder sb = new StringBuilder("🗓 *Jadwal kunci harian*\n");
            if (Store.schedules(c).isEmpty()) sb.append("Belum ada. Contoh: `/jadwal youtube 21:00-05:00`");
            for (String e : Apps.sorted(Store.schedules(c))) {
                String[] a = e.split("\\|");
                sb.append("🔒 ").append(md(Apps.label(c, a[0]))).append(": ").append(hhmm(Integer.parseInt(a[1]))).append("–")
                        .append(hhmm(Integer.parseInt(a[2]))).append(Store.inSchedule(c, a[0]) ? "  _(sedang berlaku)_" : "").append('\n');
            }
            send(token, chat, sb.toString(), null);
            return;
        }
        if (arg.toLowerCase(Locale.ROOT).startsWith("hapus ")) {
            Apps.App a = one(c, token, chat, arg.substring(6));
            if (a == null) return;
            Store.setSchedule(c, a.pkg, -1, -1);
            send(token, chat, "Jadwal kunci *" + md(a.label) + "* dihapus.", null);
            return;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+?)\\s+(\\d{1,2}[:.]\\d{2})\\s*-\\s*(\\d{1,2}[:.]\\d{2})$").matcher(arg);
        if (!m.find()) { send(token, chat, "Contoh: `/jadwal youtube 21:00-05:00`", null); return; }
        int from = parseClock(m.group(2)), to = parseClock(m.group(3));
        if (from < 0 || to < 0 || from == to) { send(token, chat, "Jam tidak valid. Contoh: `21:00-05:00`", null); return; }
        Apps.App a = one(c, token, chat, m.group(1));
        if (a == null) return;
        Store.setSchedule(c, a.pkg, from, to);
        GuardService.enforceNow();
        send(token, chat, "🗓 *" + md(a.label) + "* terkunci otomatis setiap hari " + hhmm(from) + "–" + hhmm(to) + " WITA."
                + (Store.inSchedule(c, a.pkg) ? "\nSedang berlaku sekarang." : ""), null);
    }

    private static void limitCmd(Context c, String token, long chat, String arg) throws Exception {
        if (arg.isEmpty()) {
            java.util.Map<String, Long> use = Extras.usageToday(c);
            StringBuilder sb = new StringBuilder("⏱ *Batas pemakaian harian*\n");
            if (Store.limited(c).isEmpty()) sb.append("Belum ada. Contoh: `/batas youtube 2j`");
            for (String p : Apps.sorted(Store.limited(c))) {
                sb.append(Store.limitHit(c, p) ? "🔒 " : "⏳ ").append(md(Apps.label(c, p))).append(": ")
                        .append(use.getOrDefault(p, 0L)).append(" / ").append(Store.limit(c, p)).append(" menit hari ini\n");
            }
            send(token, chat, sb.toString(), null);
            return;
        }
        if (arg.toLowerCase(Locale.ROOT).startsWith("hapus ")) {
            Apps.App a = one(c, token, chat, arg.substring(6));
            if (a == null) return;
            Store.setLimit(c, a.pkg, 0);
            send(token, chat, "Batas harian *" + md(a.label) + "* dihapus.", null);
            return;
        }
        int sp = arg.lastIndexOf(' ');
        int min = sp > 0 ? parseDuration(arg.substring(sp + 1)) : -1;
        if (min < 0) { send(token, chat, "Contoh: `/batas youtube 2j`, `/batas youtube 90m`, `/batas youtube 1j30m`", null); return; }
        Apps.App a = one(c, token, chat, arg.substring(0, sp));
        if (a == null) return;
        Store.setLimit(c, a.pkg, min);
        long used = Extras.usageToday(c).getOrDefault(a.pkg, 0L);
        send(token, chat, "⏱ *" + md(a.label) + "* dibatasi " + (min >= 60 ? min / 60 + " j " : "") + (min % 60 > 0 ? min % 60 + " m" : "")
                + " per hari. Hari ini sudah dipakai " + used + " menit.", null);
    }

    private static void photoFromTelegram(Context c, String token, long chat, JSONObject m) throws Exception {
        String fileId, ext = "jpg";
        if (m.has("photo")) {
            JSONArray ph = m.getJSONArray("photo");
            fileId = ph.getJSONObject(ph.length() - 1).getString("file_id");
        } else {
            JSONObject d = m.getJSONObject("document");
            fileId = d.getString("file_id");
            if (d.optString("mime_type").contains("png")) ext = "png";
        }
        final String e = ext;
        new Thread(() -> {
            try {
                JSONObject r = api(token, "getFile", new JSONObject().put("file_id", fileId), 30000).getJSONObject("result");
                java.io.File f = new java.io.File(c.getCacheDir(), "photo." + e);
                Installer.download("https://api.telegram.org/file/bot" + token + "/" + r.getString("file_path"), f, 21L * 1024 * 1024);
                int n = Extras.savePhoto(c, f, e);
                long id = Extras.lastSavedId;
                String cap = m.optString("caption", "").toLowerCase(Locale.ROOT);
                if (cap.contains("home") || cap.contains("background") || cap.contains("latar")) {
                    Extras.setHomeBackground(c, id);
                    send(token, chat, "🏠 Background Home diganti. Foto juga masuk folder Wallpaper (" + n + " foto) untuk screensaver.", null);
                } else {
                    JSONArray row = new JSONArray().put(btn("🏠 Jadikan background Home", "bg:" + id));
                    send(token, chat, "🖼 Foto disimpan di folder *Wallpaper* (" + n + " foto) — tampil di slideshow screensaver.",
                            new JSONObject().put("inline_keyboard", new JSONArray().put(row)));
                }
            } catch (Exception ex) {
                try { send(token, chat, "❌ Gagal menyimpan foto.", null); } catch (Exception ignored) {}
            }
        }).start();
    }

    static String report(Context c, boolean auto) { return (auto ? "🌙 *Laporan otomatis*\n" : "") + report(c); }

    /** Dipanggil dari TV: minta persetujuan pemilik untuk membuka pengaturan. */
    static void requestSetup(Context c) {
        BotService.sendAsync(c, (token, owner) -> {
            JSONArray row = new JSONArray().put(btn("✅ Izinkan 5 menit", "s:ok")).put(btn("⛔ Tolak", "s:no"));
            send(token, owner, "🔐 Ada yang meminta akses *pengaturan Kontrol TV* di TV. Izinkan?",
                    new JSONObject().put("inline_keyboard", new JSONArray().put(row)));
        });
    }

    private static JSONObject appsKeyboard(Context c) throws Exception {
        Set<String> locked = Store.locked(c);
        JSONArray rows = new JSONArray();
        JSONArray row = new JSONArray();
        for (Apps.App a : Apps.list(c)) {
            if (a.pkg.length() > 60) continue;
            boolean on = locked.contains(a.pkg);
            boolean temp = on && Store.unlockedUntil(c, a.pkg) > System.currentTimeMillis();
            row.put(btn((on ? (temp ? "⏳ " : "🔒 ") : "🟢 ") + a.label, "t:" + a.pkg));
            if (row.length() == 2) { rows.put(row); row = new JSONArray(); }
        }
        if (row.length() > 0) rows.put(row);
        return new JSONObject().put("inline_keyboard", rows);
    }

    private static JSONObject btn(String text, String data) throws Exception {
        return new JSONObject().put("text", text).put("callback_data", data);
    }

    private static String lockedList(Context c) {
        List<String> l = Apps.sorted(Store.locked(c));
        if (l.isEmpty()) return "Tidak ada aplikasi yang terkunci.";
        StringBuilder sb = new StringBuilder("*Terkunci:*\n");
        for (String p : l) {
            long until = Store.unlockedUntil(c, p);
            sb.append("🔒 ").append(md(Apps.label(c, p)));
            if (until > System.currentTimeMillis()) sb.append(" — dibuka sementara s.d. ").append(hm(until));
            sb.append('\n');
        }
        return sb.toString();
    }

    static String status(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        ActivityManager am = c.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        StatFs st = new StatFs(Environment.getDataDirectory().getPath());
        long up = SystemClock.elapsedRealtime() / 60000;
        String cur = GuardService.current;
        return "📺 *Status TV* — " + hm(System.currentTimeMillis()) + " WITA\n"
                + "Layar: " + (pm.isInteractive() ? "menyala" : "standby") + "\n"
                + "Sedang dibuka: " + (cur == null || cur.isEmpty() ? "-" : md(Apps.label(c, cur))) + "\n"
                + "Penjaga kunci: " + (BotService.guardEnabled(c) ? "✅ aktif" : "⚠️ NONAKTIF") + "\n"
                + "Kunci TV: " + TvLock.statusText(c) + "\n"
                + "Terkunci: " + Store.locked(c).size() + " aplikasi\n"
                + "RAM: " + (mi.totalMem - mi.availMem) / 1048576 + " / " + mi.totalMem / 1048576 + " MB\n"
                + "Penyimpanan kosong: " + String.format(Locale.ROOT, "%.1f", st.getAvailableBytes() / 1e9) + " GB\n"
                + "Menyala sejak: " + (up / 60) + " j " + (up % 60) + " m\n"
                + "IP: " + ip() + "\n"
                + "Laporan pemakaian: " + (usageAllowed(c) ? "tersedia" : "izin belum diberikan");
    }

    private static boolean usageAllowed(Context c) {
        android.app.AppOpsManager ao = c.getSystemService(android.app.AppOpsManager.class);
        return ao.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName())
                == android.app.AppOpsManager.MODE_ALLOWED;
    }

    static String report(Context c) {
        if (!usageAllowed(c)) return "Izin akses pemakaian belum diberikan.";
        java.util.Map<String, Long> use = Extras.usageToday(c);
        List<java.util.Map.Entry<String, Long>> l = new ArrayList<>();
        for (java.util.Map.Entry<String, Long> e : use.entrySet())
            if (e.getValue() >= 1 && !e.getKey().equals(c.getPackageName()) && !e.getKey().equals("com.gathin.tvhome")
                    && !e.getKey().startsWith("com.android.")) l.add(e);
        Collections.sort(l, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder("📊 *Pemakaian hari ini*\n");
        if (l.isEmpty()) sb.append("Belum ada.");
        long total = 0;
        for (int i = 0; i < Math.min(12, l.size()); i++) {
            long min = l.get(i).getValue();
            total += min;
            sb.append(md(Apps.label(c, l.get(i).getKey()))).append(": ").append(min >= 60 ? min / 60 + " j " : "").append(min % 60).append(" m");
            int lim = Store.limit(c, l.get(i).getKey());
            if (lim > 0) sb.append(" / batas ").append(lim).append(" m");
            sb.append('\n');
        }
        if (total > 0) sb.append("\nTotal: ").append(total / 60).append(" j ").append(total % 60).append(" m");
        return sb.toString();
    }

    private static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception ignored) {}
        return "-";
    }

    static String hm(long t) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Makassar"));
        return f.format(new Date(t));
    }

    static String md(String s) { return s == null ? "" : s.replaceAll("([_*`\\[])", "\\\\$1"); }

    static void send(String token, long chat, String text, JSONObject markup) throws Exception {
        JSONObject b = new JSONObject().put("chat_id", chat).put("text", text).put("parse_mode", "Markdown");
        if (markup != null) b.put("reply_markup", markup);
        api(token, "sendMessage", b, 20000);
    }

    static void sendPhoto(String token, long chat, byte[] jpg, String caption) throws Exception {
        String b = "----kontroltv" + System.nanoTime();
        HttpURLConnection h = (HttpURLConnection) new URL("https://api.telegram.org/bot" + token + "/sendPhoto").openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(60000);
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + b);
        try (OutputStream o = h.getOutputStream()) {
            String head = "--" + b + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + chat + "\r\n"
                    + "--" + b + "\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\n" + caption + "\r\n"
                    + "--" + b + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"foto.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
            o.write(head.getBytes(StandardCharsets.UTF_8));
            o.write(jpg);
            o.write(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        int code = h.getResponseCode();
        h.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code);
    }

    static JSONObject api(String token, String method, JSONObject body, int readTimeout) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL("https://api.telegram.org/bot" + token + "/" + method).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(readTimeout);
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = h.getResponseCode();
        InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        if (in != null) for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        h.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + method);
        return new JSONObject(out.toString("UTF-8"));
    }
}
