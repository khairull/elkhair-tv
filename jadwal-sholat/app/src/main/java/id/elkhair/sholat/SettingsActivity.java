package id.elkhair.sholat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pengaturan: azan aktif, lokasi (cari wilayah), berkas azan & azan Subuh, latar screensaver. */
public class SettingsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences p;
    private LinearLayout list;
    private Typeface font;
    private MediaPlayer preview;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        p = PrayerCalc.prefs(this);
        font = getResources().getFont(R.font.outfit);
        ScrollView sv = new ScrollView(this);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF0D2A1E, 0xFF050607}));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(80), dp(40), dp(80), dp(40));
        sv.addView(list);
        setContentView(sv);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[] {Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES}, 1);
        else requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        build();
    }

    private void build() {
        int keep = list.getFocusedChild() == null ? 1 : list.indexOfChild(list.getFocusedChild());
        list.removeAllViews();
        boolean ss = getComponentName().getClassName().endsWith("ScreensaverSettings");
        TextView title = text(ss ? "Pengaturan Screensaver" : "Pengaturan Jadwal Sholat", 30, 0xFFFFFFFF, 500);
        title.setPadding(0, 0, 0, dp(18));
        list.addView(title);
        if (ss) {
        String mode = p.getString("ss_mode", "weather");
            row("Tampilan screensaver", mode.equals("photos") ? "Foto & jam" : "Animasi cuaca & jam", () -> {
                p.edit().putString("ss_mode", mode.equals("photos") ? "weather" : "photos").apply();
                build();
            });
            if (!mode.equals("photos")) {
                int f = p.getInt("wx_preview", -1);
                row("Cuaca yang ditampilkan", f < 0 ? "Otomatis (cuaca nyata)" : "Uji: " + Weather.label(f), () -> {
                    int nx = f + 1 > Weather.THUNDER ? -1 : f + 1;
                    p.edit().putInt("wx_preview", nx).apply();
                    build();
                });
            }
            if (mode.equals("photos")) {
            row("Folder foto", p.getString("ss_bucket_name", "Wallpaper (kiriman bot)"), this::pickFolder);
            row("Ganti foto setiap", p.getInt("ss_interval", 20) + " detik", () -> {
                int[] opts = {10, 20, 30, 60, 120};
                int cur = p.getInt("ss_interval", 20), nx = opts[0];
                for (int i = 0; i < opts.length; i++) if (opts[i] == cur) nx = opts[(i + 1) % opts.length];
                p.edit().putInt("ss_interval", nx).apply();
                build();
            });
            }
            TextView note = text(!mode.equals("photos") ? "Animasi mengikuti cuaca saat ini di lokasi Anda dan waktu (pagi, siang, senja, malam)." : "Kirim foto ke bot Telegram Kontrol TV: otomatis masuk folder Pictures/Wallpaper dan tampil di screensaver.", 14, 0x80FFFFFF, 400);
            note.setPadding(0, dp(18), 0, 0);
            list.addView(note);
            focusRow(keep);
            return;
        }
        row("Azan otomatis (5 waktu)", Azan.enabled(this) ? "AKTIF" : "NONAKTIF", () -> { Azan.setEnabled(this, !Azan.enabled(this)); build(); });
        int lead = Azan.lead(this);
        row("Mulai azan", lead == 0 ? "Tepat waktu" : lead + " menit sebelum waktunya", () -> {
            p.edit().putInt("azan_lead", lead == 0 ? 5 : lead == 5 ? 10 : 0).apply();
            Azan.scheduleNext(this);
            build();
        });
        row("Pengingat Imsak & Dhuha", Azan.notices(this) ? "AKTIF (notifikasi, tanpa azan)" : "NONAKTIF", () -> {
            p.edit().putBoolean("notice_on", !Azan.notices(this)).apply();
            Azan.scheduleNext(this);
            build();
        });
        row("Lokasi", PrayerCalc.place(this) + " · " + PrayerCalc.zoneLabel(this) + String.format(Locale.ROOT, "  (%.3f, %.3f)", PrayerCalc.lat(this), PrayerCalc.lng(this)), this::searchLocation);
        row("Azan Dzuhur–Isya", p.getString("azan_name", "Bawaan"), () -> pickAudio("azan"));
        row("Azan Subuh", p.getString("azan_subuh_name", "Bawaan"), () -> pickAudio("azan_subuh"));
        row("Uji azan", preview != null ? "■ Hentikan" : "▶ Putar 15 detik", this::togglePreview);
        focusRow(keep);
    }

    private void focusRow(int i) {
        i = Math.max(1, Math.min(i, list.getChildCount() - 1));
        View v = list.getChildAt(i);
        if (v != null && v.isFocusable()) v.requestFocus(); else if (list.getChildCount() > 1) list.getChildAt(1).requestFocus();
    }

    private void row(String label, String value, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setFocusable(true);
        r.setPadding(dp(20), dp(14), dp(20), dp(14));
        r.setBackground(rowBg());
        TextView l = text(label, 20, 0xFFFFFFFF, 500);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = text(value, 18, 0xFF8FE3B5, 400);
        r.addView(v, new LinearLayout.LayoutParams(-2, -2));
        r.setOnClickListener(x -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        list.addView(r, lp);
    }

    // ---- lokasi ----
    private void searchLocation() {
        EditText q = new EditText(this);
        q.setHint("Nama kota/kabupaten, mis. Limboto");
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Cari lokasi").setView(q)
                .setPositiveButton("Cari", (d, w) -> geocode(q.getText().toString().trim()))
                .setNegativeButton("Batal", null).show();
    }

    private void geocode(String name) {
        if (name.isEmpty()) return;
        new Thread(() -> {
            List<JSONObject> res = new ArrayList<>();
            try {
                JSONObject j = new JSONObject(Weather.get("https://geocoding-api.open-meteo.com/v1/search?count=12&language=id&format=json&name="
                        + URLEncoder.encode(name, "UTF-8")));
                JSONArray a = j.optJSONArray("results");
                if (a != null) for (int i = 0; i < a.length(); i++) res.add(a.getJSONObject(i));
            } catch (Exception ignored) {}
            main.post(() -> showResults(res));
        }).start();
    }

    private void showResults(List<JSONObject> res) {
        if (res.isEmpty()) { new AlertDialog.Builder(this).setMessage("Lokasi tidak ditemukan atau tidak ada internet.").setPositiveButton("OK", null).show(); return; }
        String[] items = new String[res.size()];
        for (int i = 0; i < items.length; i++) {
            JSONObject o = res.get(i);
            StringBuilder sb = new StringBuilder(o.optString("name"));
            for (String k : new String[] {"admin2", "admin1", "country"}) if (!o.optString(k).isEmpty()) sb.append(", ").append(o.optString(k));
            items[i] = sb.toString();
        }
        new AlertDialog.Builder(this).setTitle("Pilih lokasi").setItems(items, (d, w) -> {
            JSONObject o = res.get(w);
            p.edit().putString("lat", String.valueOf(o.optDouble("latitude"))).putString("lng", String.valueOf(o.optDouble("longitude")))
                    .putString("place", o.optString("name")).putString("tz", o.optString("timezone", "Asia/Makassar"))
                    .remove("wx_time").apply();
            Azan.scheduleNext(this);
            build();
        }).show();
    }

    // ---- berkas azan ----
    private void pickAudio(String key) {
        Map<String, Uri> files = new LinkedHashMap<>();
        files.put("Bawaan aplikasi", null);
        String[] cols = Build.VERSION.SDK_INT >= 29
                ? new String[] {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.RELATIVE_PATH}
                : new String[] {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME};
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, null, null,
                MediaStore.Audio.Media.DISPLAY_NAME)) {
            while (c != null && c.moveToNext()) {
                String label = c.getString(1) + (cols.length > 2 && c.getString(2) != null ? "  (" + c.getString(2) + ")" : "");
                files.put(label, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)));
            }
        } catch (Exception ignored) {}
        String[] items = files.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(files.size() == 1 ? "Tidak ada berkas audio di TV (salin .mp3 ke folder Music/Download)" : "Pilih berkas azan")
                .setItems(items, (d, w) -> {
                    Uri u = files.get(items[w]);
                    p.edit().putString(key + "_uri", u == null ? "" : u.toString())
                            .putString(key + "_name", u == null ? "Bawaan" : items[w].replaceAll("  \\(.*\\)$", "")).apply();
                    build();
                }).show();
    }

    private void togglePreview() {
        if (preview != null) { stopPreview(); build(); return; }
        try {
            preview = new MediaPlayer();
            AzanService.setSource(preview, this, false);
            preview.prepare();
            preview.start();
            main.postDelayed(() -> { stopPreview(); build(); }, 15_000L);
        } catch (Exception e) { stopPreview(); }
        build();
    }

    private void stopPreview() {
        main.removeCallbacksAndMessages(null);
        if (preview != null) { try { preview.stop(); } catch (Exception ignored) {} preview.release(); preview = null; }
    }

    // ---- folder foto ----
    private void pickFolder() {
        Map<String, String[]> buckets = new LinkedHashMap<>();
        Map<String, Integer> count = new LinkedHashMap<>();
        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME}, null, null,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME)) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1);
                buckets.put(id, new String[] {id, name == null ? "(tanpa nama)" : name});
                count.put(id, count.containsKey(id) ? count.get(id) + 1 : 1);
            }
        } catch (Exception ignored) {}
        if (buckets.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Belum ada foto di TV. Salin foto ke folder Pictures/Screensaver, lalu coba lagi.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        List<String[]> b = new ArrayList<>(buckets.values());
        String[] items = new String[b.size()];
        for (int i = 0; i < items.length; i++) items[i] = b.get(i)[1] + "  (" + count.get(b.get(i)[0]) + " foto)";
        new AlertDialog.Builder(this).setTitle("Pilih folder foto").setItems(items, (d, w) -> {
            p.edit().putString("ss_bucket", b.get(w)[0]).putString("ss_bucket_name", b.get(w)[1]).putString("ss_mode", "photos").apply();
            build();
        }).show();
    }

    @Override
    protected void onPause() { super.onPause(); stopPreview(); }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) { build(); }

    private StateListDrawable rowBg() {
        GradientDrawable n = new GradientDrawable();
        n.setCornerRadius(dp(14));
        n.setColor(0x14FFFFFF);
        GradientDrawable f = new GradientDrawable();
        f.setCornerRadius(dp(14));
        f.setColor(0xFF2BB673);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, f);
        s.addState(new int[] {}, n);
        return s;
    }

    private TextView text(String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
