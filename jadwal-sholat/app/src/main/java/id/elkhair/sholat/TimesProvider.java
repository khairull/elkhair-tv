package id.elkhair.sholat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Calendar;

/** Jadwal hari ini & besok (hanya baca) untuk Kontrol TV: indeks, nama, menit, epoch, wajib, hari (0/1). */
public class TimesProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[] {"idx", "name", "minute", "at", "prayer", "day", "place", "zone", "lead", "azan_on"});
        Calendar today = PrayerCalc.today(getContext());
        for (int day = 0; day < 2; day++) {
            Calendar d = (Calendar) today.clone();
            d.add(Calendar.DAY_OF_MONTH, day);
            int[] m = PrayerCalc.minutes(getContext(), d);
            for (int i = 0; i < m.length; i++)
                c.addRow(new Object[] {i, PrayerCalc.NAMES[i], m[i], PrayerCalc.millis(d, m[i]), PrayerCalc.isPrayer(i) ? 1 : 0, day,
                        PrayerCalc.place(getContext()), PrayerCalc.zoneLabel(getContext()), Azan.lead(getContext()), Azan.enabled(getContext()) ? 1 : 0});
        }
        return c;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
