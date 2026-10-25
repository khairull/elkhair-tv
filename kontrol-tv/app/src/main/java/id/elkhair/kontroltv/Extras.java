package id.elkhair.kontroltv;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Pemakaian hari ini (termasuk sesi yang sedang berjalan), tombol media & volume, simpan foto screensaver. */
final class Extras {
    static final String PHOTO_DIR = "Pictures/Wallpaper/";
    private Extras() {}

    /** Menit pemakaian per paket sejak tengah malam WITA, dihitung dari event resume/pause. */
    static Map<String, Long> usageToday(Context c) {
        Calendar cal = Calendar.getInstance(Store.tz());
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis(), now = System.currentTimeMillis();
        Map<String, Long> total = new HashMap<>(), open = new HashMap<>();
        try {
            UsageEvents ev = c.getSystemService(UsageStatsManager.class).queryEvents(start, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                String p = e.getPackageName();
                if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (!open.containsKey(p)) open.put(p, e.getTimeStamp());
                } else if (e.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED || e.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED) {
                    Long s = open.remove(p);
                    if (s != null) total.put(p, total.getOrDefault(p, 0L) + (e.getTimeStamp() - s));
                }
            }
        } catch (Exception ignored) {}
        for (Map.Entry<String, Long> o : open.entrySet()) total.put(o.getKey(), total.getOrDefault(o.getKey(), 0L) + (now - o.getValue()));
        Map<String, Long> min = new HashMap<>();
        for (Map.Entry<String, Long> t : total.entrySet()) min.put(t.getKey(), t.getValue() / 60000);
        return min;
    }

    static void mediaKey(Context c, int code) {
        AudioManager am = c.getSystemService(AudioManager.class);
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    /** Volume 0–100 %; -1 hanya membaca. Mengembalikan persen saat ini. */
    static int volume(Context c, int percent) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (percent >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * Math.min(100, percent) / 100f), AudioManager.FLAG_SHOW_UI);
        return Math.round(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
    }

    static boolean toggleMute(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
        return am.isStreamMute(AudioManager.STREAM_MUSIC);
    }

    /** Isi folder Wallpaper: {id, nama, ukuran byte, tanggal}. */
    static java.util.List<String[]> folder(Context c) {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        try (Cursor q = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_ADDED},
                MediaStore.Images.Media.RELATIVE_PATH + "=?", new String[] {PHOTO_DIR}, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ROOT);
            f.setTimeZone(Store.tz());
            while (q != null && q.moveToNext())
                l.add(new String[] {String.valueOf(q.getLong(0)), q.getString(1), String.valueOf(q.getLong(2)), f.format(new java.util.Date(q.getLong(3) * 1000))});
        } catch (Exception ignored) {}
        return l;
    }

    /** Pratinjau JPEG kecil (lebar maks 1280) untuk dikirim ke Telegram. */
    static byte[] preview(Context c, long id) throws Exception {
        Uri u = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(u)) { android.graphics.BitmapFactory.decodeStream(in, null, o); }
        int s = 1;
        while (o.outWidth / (s * 2) >= 1280) s *= 2;
        android.graphics.BitmapFactory.Options d = new android.graphics.BitmapFactory.Options();
        d.inSampleSize = s;
        android.graphics.Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(u)) { b = android.graphics.BitmapFactory.decodeStream(in, null, d); }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out);
        return out.toByteArray();
    }

    static boolean delete(Context c, long id) {
        try {
            return c.getContentResolver().delete(android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), null, null) > 0;
        } catch (Exception e) { return false; }
    }

    /** Foto di folder Wallpaper, terbaru dulu: {id, nama}. */
    static java.util.List<String[]> wallpapers(Context c, int max) {
        java.util.List<String[]> l = new java.util.ArrayList<>();
        try (Cursor q = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED},
                MediaStore.Images.Media.RELATIVE_PATH + "=?", new String[] {PHOTO_DIR}, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            while (q != null && q.moveToNext() && l.size() < max) {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ROOT);
                f.setTimeZone(Store.tz());
                l.add(new String[] {String.valueOf(q.getLong(0)), f.format(new java.util.Date(q.getLong(2) * 1000))});
            }
        } catch (Exception ignored) {}
        return l;
    }

    /** Jadikan foto (MediaStore id) background Home: potong 16:9, 1920x1080, gradasi gelap atas/bawah, tulis lewat ThemeProvider launcher. */
    static void setHomeBackground(Context c, long id) throws Exception {
        Uri src = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(src)) { android.graphics.BitmapFactory.decodeStream(in, null, o); }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1920 && o.outHeight / (sample * 2) >= 1080) sample *= 2;
        android.graphics.BitmapFactory.Options d = new android.graphics.BitmapFactory.Options();
        d.inSampleSize = sample;
        android.graphics.Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(src)) { b = android.graphics.BitmapFactory.decodeStream(in, null, d); }
        if (b == null) throw new Exception("gambar tidak terbaca");
        // ikuti orientasi EXIF
        try (InputStream in = c.getContentResolver().openInputStream(src)) {
            int ori = new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int rot = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
            if (rot != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(rot);
                b = android.graphics.Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
        } catch (Exception ignored) {}
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(out);
        float scale = Math.max(1920f / b.getWidth(), 1080f / b.getHeight());
        float w = b.getWidth() * scale, h = b.getHeight() * scale;
        cv.drawBitmap(b, null, new android.graphics.RectF((1920 - w) / 2, (1080 - h) / 2, (1920 + w) / 2, (1080 + h) / 2),
                new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        android.graphics.Paint g = new android.graphics.Paint();
        g.setShader(new android.graphics.LinearGradient(0, 0, 0, 1080, new int[] {0x73000000, 0x00000000, 0x00000000, 0x8C000000},
                new float[] {0f, 0.28f, 0.64f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, 1920, 1080, g);
        java.io.ByteArrayOutputStream jpg = new java.io.ByteArrayOutputStream();
        out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, jpg);
        ContentResolver cr = c.getContentResolver();
        try (OutputStream os = cr.openOutputStream(Uri.parse("content://com.gathin.tvhome.theme/wallpaper.jpg"), "w")) { os.write(jpg.toByteArray()); }
        try (OutputStream os = cr.openOutputStream(Uri.parse("content://com.gathin.tvhome.theme/theme_revision"), "w")) {
            os.write(Long.toHexString(System.nanoTime()).getBytes());
        }
        Store.p(c).edit().putLong("home_bg", id).apply();
    }

    /** Simpan foto ke Pictures/Wallpaper (dibaca slideshow screensaver). Mengembalikan jumlah foto di folder itu. */
    static long lastSavedId;

    static int savePhoto(Context c, File f, String ext) throws Exception {
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "tg_" + System.currentTimeMillis() + "." + ext);
        v.put(MediaStore.Images.Media.MIME_TYPE, ext.equals("png") ? "image/png" : "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, PHOTO_DIR);
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri u = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new Exception("MediaStore menolak");
        try (InputStream in = new FileInputStream(f); OutputStream o = cr.openOutputStream(u)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        }
        v.clear();
        v.put(MediaStore.Images.Media.IS_PENDING, 0);
        cr.update(u, v, null, null);
        lastSavedId = android.content.ContentUris.parseId(u);
        f.delete();
        try (Cursor q = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Images.Media._ID},
                MediaStore.Images.Media.RELATIVE_PATH + "=?", new String[] {PHOTO_DIR}, null)) {
            return q == null ? 1 : q.getCount();
        }
    }
}
