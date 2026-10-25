package id.elkhair.sholat;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Cuaca saat ini dari Open-Meteo (gratis, tanpa akun): suhu, kode cuaca WMO, siang/malam. Disimpan 30 menit. */
final class Weather {
    static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, THUNDER = 6;
    private Weather() {}

    static int kind(int code) {
        if (code <= 1) return CLEAR;
        if (code == 2) return PARTLY;
        if (code == 3) return CLOUDY;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if (code >= 95) return THUNDER;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        return CLOUDY;
    }

    static String label(int kind) {
        switch (kind) {
            case CLEAR: return "Cerah";
            case PARTLY: return "Cerah berawan";
            case FOG: return "Berkabut";
            case DRIZZLE: return "Gerimis";
            case RAIN: return "Hujan";
            case THUNDER: return "Badai petir";
            default: return "Berawan";
        }
    }

    /** {suhu, kind} dari cache; null bila belum ada. */
    static double[] cached(Context c) {
        SharedPreferences p = PrayerCalc.prefs(c);
        if (!p.contains("wx_time")) return null;
        return new double[] {p.getFloat("wx_temp", 0), p.getInt("wx_kind", CLOUDY)};
    }

    /** Ambil cuaca bila cache lebih dari 30 menit; jalankan di thread latar. */
    static void refresh(Context c) {
        SharedPreferences p = PrayerCalc.prefs(c);
        if (System.currentTimeMillis() - p.getLong("wx_time", 0) < 30 * 60_000L) return;
        try {
            JSONObject cur = new JSONObject(get(String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                    PrayerCalc.lat(c), PrayerCalc.lng(c)))).getJSONObject("current");
            p.edit().putLong("wx_time", System.currentTimeMillis())
                    .putFloat("wx_temp", (float) cur.getDouble("temperature_2m"))
                    .putInt("wx_kind", kind(cur.getInt("weather_code"))).apply();
        } catch (Exception ignored) {}
    }

    static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(15000);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally { h.disconnect(); }
    }
}
