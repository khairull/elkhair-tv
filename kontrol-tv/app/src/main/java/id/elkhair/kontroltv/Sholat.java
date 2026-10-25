package id.elkhair.kontroltv;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Membaca jadwal & cuaca dari aplikasi Jadwal Sholat (content provider hanya-baca). */
final class Sholat {
    static final class Time { int idx, minute, day; String name; long at; boolean prayer; }
    static String place = "", zone = "WITA";
    static int lead;
    private Sholat() {}

    static List<Time> times(Context c) {
        List<Time> l = new ArrayList<>();
        try (Cursor q = c.getContentResolver().query(Uri.parse("content://id.elkhair.sholat.times"), null, null, null, null)) {
            while (q != null && q.moveToNext()) {
                Time t = new Time();
                t.idx = q.getInt(0); t.name = q.getString(1); t.minute = q.getInt(2); t.at = q.getLong(3);
                t.prayer = q.getInt(4) == 1; t.day = q.getInt(5);
                place = q.getString(6); zone = q.getString(7); lead = q.getInt(8);
                l.add(t);
            }
        } catch (Exception ignored) {}
        return l;
    }

    /** Perbarui cache waktu sholat wajib untuk mode sholat. */
    static List<Time> refresh(Context c) {
        List<Time> l = times(c);
        List<Long> at = new ArrayList<>();
        for (Time t : l) if (t.prayer) at.add(t.at);
        long[] a = new long[at.size()];
        for (int i = 0; i < a.length; i++) a[i] = at.get(i);
        if (a.length > 0) Store.prayerTimes = a;
        return l;
    }

    static String hhmm(int m) { return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60); }

    static String scheduleText(Context c) {
        List<Time> l = refresh(c);
        if (l.isEmpty()) return "Aplikasi Jadwal Sholat tidak terjangkau.";
        long now = System.currentTimeMillis();
        Time next = null;
        for (Time t : l) if (t.prayer && t.at > now) { next = t; break; }
        StringBuilder sb = new StringBuilder("🕌 *Jadwal Sholat — " + Bot.md(place) + "* (" + zone + ")\n\n");
        for (Time t : l) {
            if (t.day != 0) continue;
            boolean isNext = next != null && next.day == 0 && t.idx == next.idx;
            sb.append(isNext ? "▶️ *" : t.prayer ? "• " : "◦ ").append(t.name).append("  ").append(hhmm(t.minute)).append(isNext ? "*" : "").append('\n');
        }
        if (next != null) {
            long m = (next.at - now) / 60000;
            sb.append("\n").append(next.name).append(next.day == 1 ? " besok" : "").append(" dalam ")
                    .append(m >= 60 ? m / 60 + " j " : "").append(m % 60).append(" menit");
        }
        int pm = Store.prayerMode(c), rem = Store.p(c).getInt("remind", 0);
        sb.append("\nPengingat ke HP: ").append(rem > 0 ? rem + " menit sebelum" : "mati")
                .append(" · Mode sholat: ").append(pm > 0 ? pm + " menit" : "mati");
        return sb.toString();
    }

    static String weatherText(Context c) {
        try (Cursor q = c.getContentResolver().query(Uri.parse("content://id.elkhair.sholat.weather"), null, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                String[] icon = {"☀️", "🌤", "☁️", "🌫", "🌦", "🌧", "⛈"};
                int k = q.getInt(1);
                boolean night = q.getInt(3) == 1;
                String ic = night && k <= 1 ? "🌙" : icon[Math.max(0, Math.min(6, k))];
                return ic + " *" + q.getString(2) + "  " + q.getInt(0) + "°C*\n" + Bot.md(q.getString(5));
            }
        } catch (Exception ignored) {}
        return "Data cuaca belum tersedia.";
    }
}
