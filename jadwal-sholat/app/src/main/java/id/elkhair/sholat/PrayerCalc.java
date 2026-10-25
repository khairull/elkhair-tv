package id.elkhair.sholat;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Jadwal sholat kriteria Kemenag RI (Subuh 20°, Isya 18°, Ashar Syafi'i, Dhuha 4,5°, ihtiyat 2 menit). Lokasi dari pengaturan. */
final class PrayerCalc {
    static final String[] NAMES = {"Imsak", "Subuh", "Terbit", "Dhuha", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    static final int SUBUH = 1;
    static final double FAJR = 20, ISHA = 18, RISE = 0.833, DHUHA = 4.5;

    private PrayerCalc() {}

    /** Hanya 5 waktu sholat wajib yang diazankan. */
    static boolean isPrayer(int i) { return i == 1 || i >= 4; }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(Azan.PREFS, 0); }
    static double lat(Context c) { return Double.parseDouble(prefs(c).getString("lat", "0.5435")); }
    static double lng(Context c) { return Double.parseDouble(prefs(c).getString("lng", "123.0568")); }
    static String place(Context c) { return prefs(c).getString("place", "Gorontalo"); }
    static TimeZone zone(Context c) { return TimeZone.getTimeZone(prefs(c).getString("tz", "Asia/Makassar")); }

    static String zoneLabel(Context c) {
        int h = zone(c).getOffset(System.currentTimeMillis()) / 3_600_000;
        return h == 7 ? "WIB" : h == 8 ? "WITA" : h == 9 ? "WIT" : String.format(Locale.ROOT, "UTC%+d", h);
    }

    static Calendar today(Context c) { return Calendar.getInstance(zone(c)); }

    /** Menit setelah tengah malam (zona lokasi), urutan NAMES. */
    static int[] minutes(Context c, Calendar day) {
        double tz = zone(c).getOffset(day.getTimeInMillis()) / 3_600_000.0;
        return minutes(lat(c), lng(c), tz, day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
    }

    static int[] minutes(double lat, double lng, double tz, int y, int m, int d) {
        double jd = julian(y, m, d) - lng / (15 * 24.0);
        double[] t = {5, 6, 7, 12, 13, 18, 18};   // subuh, terbit, dhuha, dzuhur, ashar, maghrib, isya
        for (int pass = 0; pass < 2; pass++) {
            double[] p = new double[t.length];
            for (int i = 0; i < t.length; i++) p[i] = t[i] / 24.0;
            t = new double[] {
                    angleTime(jd, lat, FAJR, p[0], true),
                    angleTime(jd, lat, RISE, p[1], true),
                    angleTime(jd, lat, -DHUHA, p[2], true),
                    midDay(jd, p[3]),
                    asrTime(jd, lat, 1, p[4]),
                    angleTime(jd, lat, RISE, p[5], false),
                    angleTime(jd, lat, ISHA, p[6], false)};
        }
        int[] r = new int[t.length];
        for (int i = 0; i < t.length; i++) {
            double min = (t[i] + tz - lng / 15.0) * 60;
            if (i == 1) r[i] = (int) Math.floor(min) - 2;                 // terbit dimajukan 2 menit
            else r[i] = (int) Math.ceil(min) + 2 + (i == 3 ? 1 : 0);     // ihtiyat 2; dzuhur +1 lepas meridian
        }
        return new int[] {r[0] - 10, r[0], r[1], r[2], r[3], r[4], r[5], r[6]};   // imsak = subuh - 10 menit
    }

    static long millis(Calendar day, int minute) {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() + minute * 60_000L;
    }

    /** Waktu sholat wajib berikutnya: {indeks, menit, epoch ms}. */
    static long[] next(Context c) { return next(c, false, 0); }

    /** notice=true: Imsak/Dhuha; lead = menit lebih awal (alarm). {indeks, menit, epoch waktu sholat, epoch alarm}. */
    static long[] next(Context c, boolean notice, int lead) {
        long now = System.currentTimeMillis();
        Calendar today = today(c);
        for (int day = 0; day < 2; day++) {
            Calendar d = (Calendar) today.clone();
            d.add(Calendar.DAY_OF_MONTH, day);
            int[] m = minutes(c, d);
            for (int i = 0; i < m.length; i++) {
                if (notice ? (i != 0 && i != 3) : !isPrayer(i)) continue;
                long at = millis(d, m[i]), fire = at - lead * 60_000L;
                if (fire > now) return new long[] {i, m[i], at, fire};
            }
        }
        return new long[] {SUBUH, 0, now + 86_400_000L, now + 86_400_000L};
    }

    static String hhmm(int minute) {
        minute = ((minute % 1440) + 1440) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    static String countdown(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0), b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    private static double[] sun(double jd) {
        double D = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * D);
        double q = fixAngle(280.459 + 0.98564736 * D);
        double L = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * D;
        double ra = Math.toDegrees(Math.atan2(dcos(e) * dsin(L), dcos(L))) / 15;
        return new double[] {Math.toDegrees(Math.asin(dsin(e) * dsin(L))), q / 15 - fixHour(ra)};
    }

    private static double midDay(double jd, double t) { return fixHour(12 - sun(jd + t)[1]); }

    private static double angleTime(double jd, double lat, double angle, double t, boolean ccw) {
        double decl = sun(jd + t)[0];
        double v = Math.toDegrees(Math.acos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat)))) / 15;
        return midDay(jd, t) + (ccw ? -v : v);
    }

    private static double asrTime(double jd, double lat, double factor, double t) {
        double decl = sun(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (factor + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return angleTime(jd, lat, angle, t, false);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double fixAngle(double a) { a -= 360 * Math.floor(a / 360); return a < 0 ? a + 360 : a; }
    private static double fixHour(double a) { a -= 24 * Math.floor(a / 24); return a < 0 ? a + 24 : a; }
}
