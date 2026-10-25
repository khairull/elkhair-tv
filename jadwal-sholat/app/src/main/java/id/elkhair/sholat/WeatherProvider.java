package id.elkhair.sholat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Calendar;

/** Cuaca saat ini (hanya baca) untuk launcher: suhu, jenis, label, malam, waktu pembaruan. */
public class WeatherProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[] {"temp", "kind", "label", "night", "updated", "place"});
        double[] w = Weather.cached(getContext());
        if (w == null) {
            new Thread(() -> Weather.refresh(getContext())).start();
            return c;
        }
        Calendar cal = PrayerCalc.today(getContext());
        int[] m = PrayerCalc.minutes(getContext(), cal);
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int night = now < m[2] || now >= m[6] ? 1 : 0;
        c.addRow(new Object[] {Math.round(w[0]), (int) w[1], Weather.label((int) w[1]), night,
                PrayerCalc.prefs(getContext()).getLong("wx_time", 0), PrayerCalc.place(getContext())});
        if (System.currentTimeMillis() - PrayerCalc.prefs(getContext()).getLong("wx_time", 0) > 30 * 60_000L)
            new Thread(() -> Weather.refresh(getContext())).start();
        return c;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
