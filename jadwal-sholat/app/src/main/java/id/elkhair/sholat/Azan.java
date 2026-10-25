package id.elkhair.sholat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** Satu alarm untuk waktu sholat wajib berikutnya; tiap alarm menjadwalkan yang sesudahnya. */
final class Azan {
    static final String PREFS = "sholat";
    private Azan() {}

    static boolean enabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean("azan_on", true); }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("azan_on", on).apply();
        scheduleNext(c);
    }

    /** Menit lebih awal azan dimulai: 0, 5 atau 10. */
    static int lead(Context c) { return c.getSharedPreferences(PREFS, 0).getInt("azan_lead", 0); }
    static boolean notices(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean("notice_on", true); }

    /** Jam TV belum tersinkron (mis. setelah mati lampu, tahun kembali ke 1970/2020). */
    static boolean clockValid() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) >= 2026;
    }

    static void scheduleNext(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(pending(c, AzanReceiver.ACTION_AZAN, 0, 0, 0));
        am.cancel(pending(c, AzanReceiver.ACTION_NOTICE, 0, 1, 0));
        if (!clockValid()) { Log.i("Sholat", "jam belum valid, tunda penjadwalan"); return; }
        if (enabled(c)) {
            long[] n = PrayerCalc.next(c, false, lead(c));
            set(am, n[3], pending(c, AzanReceiver.ACTION_AZAN, (int) n[0], 0, n[3]));
            Log.i("Sholat", "azan berikutnya " + PrayerCalc.NAMES[(int) n[0]] + " " + PrayerCalc.hhmm((int) n[1]) + " (-" + lead(c) + " mnt)");
        }
        if (notices(c)) {
            long[] n = PrayerCalc.next(c, true, 0);
            set(am, n[3], pending(c, AzanReceiver.ACTION_NOTICE, (int) n[0], 1, n[3]));
        }
    }

    private static void set(AlarmManager am, long at, PendingIntent pi) {
        if (at - System.currentTimeMillis() < 3000) return;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static PendingIntent pending(Context c, String action, int idx, int code, long fireAt) {
        Intent i = new Intent(c, AzanReceiver.class).setAction(action).putExtra("idx", idx).putExtra("fire_at", fireAt);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
