package id.elkhair.sholat;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Screensaver: jam, tanggal, cuaca dan hitung mundur sholat dalam satu blok yang bergeser tiap menit (anti burn-in).
 * Latar: langit cuaca+jam, atau foto acak dari folder pilihan.
 */
public class SholatDream extends DreamService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private LinearLayout block;
    private TextView date, weather, next, count;
    private WeatherSkyView sky;
    private ImageView[] photos;
    private int front;
    private List<Uri> pics = new ArrayList<>();
    private int picIdx;
    private boolean photoMode;
    private long lastMove;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (AzanService.playing) { wakeUp(); return; }
            long[] n = PrayerCalc.next(SholatDream.this);
            next.setText(PrayerCalc.NAMES[(int) n[0]] + "  ·  " + PrayerCalc.hhmm((int) n[1]) + " " + PrayerCalc.zoneLabel(SholatDream.this));
            count.setText(PrayerCalc.countdown(n[2] - System.currentTimeMillis()));
            if (System.currentTimeMillis() - lastMove > 60_000L) { move(); updateInfo(); }
            main.postDelayed(this, 1000L);
        }
    };

    private final Runnable slide = new Runnable() {
        @Override public void run() { nextPhoto(); main.postDelayed(this, PrayerCalc.prefs(SholatDream.this).getInt("ss_interval", 20) * 1000L); }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        Typeface base = getResources().getFont(R.font.outfit);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        photoMode = "photos".equals(PrayerCalc.prefs(this).getString("ss_mode", "weather")) && loadPics();
        if (photoMode) {
            photos = new ImageView[] {new ImageView(this), new ImageView(this)};
            for (ImageView v : photos) {
                v.setScaleType(ImageView.ScaleType.CENTER_CROP);
                v.setAlpha(0f);
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
            }
        } else {
            sky = new WeatherSkyView(this);
            root.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        }
        View shade = new View(this);   // gelap tipis agar tulisan terbaca
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x33000000, 0x55000000, 0x88000000}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTimeZone(PrayerCalc.zone(this).getID());
        style(clock, base, 112, 0xF2FFFFFF, 200);
        clock.setIncludeFontPadding(false);
        block.addView(clock);
        date = tv(base, 19, 0xD9FFFFFF, 500);
        date.setLetterSpacing(0.12f);
        block.addView(date);
        weather = tv(base, 19, 0xD9FFFFFF, 500);
        block.addView(weather);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(10), dp(18), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0x66081210);
        bg.setStroke(dp(1), getColor(R.color.accent));
        box.setBackground(bg);
        TextView label = tv(base, 11, 0xB3FFFFFF, 700);
        label.setLetterSpacing(0.2f);
        label.setText("SHOLAT BERIKUTNYA");
        box.addView(label);
        next = tv(base, 17, getColor(R.color.accent), 700);
        box.addView(next);
        count = tv(base, 36, 0xFFFFFFFF, 700);
        count.setFontFeatureSettings("tnum");
        box.addView(count);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(300), -2);
        bl.topMargin = dp(14);
        block.addView(box, bl);
        root.addView(block, new FrameLayout.LayoutParams(dp(620), -2));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        new Thread(() -> { Weather.refresh(this); main.post(this::updateInfo); }).start();
        updateInfo();
        block.setAlpha(0f);
        block.post(() -> { place(); block.animate().alpha(1f).setDuration(1200).start(); });
        lastMove = System.currentTimeMillis();
        main.post(tick);
        if (photoMode) main.post(slide);
    }

    @Override
    public void onDreamingStopped() {
        main.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private void updateInfo() {
        SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM", MainActivity.ID);
        df.setTimeZone(PrayerCalc.zone(this));
        date.setText(df.format(new Date()).toUpperCase(MainActivity.ID) + "  ·  " + MainActivity.hijri());
        double[] w = Weather.cached(this);
        weather.setText(w == null ? PrayerCalc.place(this) : Math.round(w[0]) + "°  " + Weather.label((int) w[1]) + "  ·  " + PrayerCalc.place(this));
        if (sky != null) {
            Calendar c = PrayerCalc.today(this);
            int[] m = PrayerCalc.minutes(this, c);
            int force = PrayerCalc.prefs(this).getInt("wx_preview", -1);
            sky.set(force >= 0 ? force : w == null ? Weather.PARTLY : (int) w[1], c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), m[2], m[6]);
        }
    }

    private void move() {
        lastMove = System.currentTimeMillis();
        block.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            place();
            block.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    private void place() {
        View parent = (View) block.getParent();
        int maxX = Math.max(0, parent.getWidth() - block.getWidth()), maxY = Math.max(0, parent.getHeight() - block.getHeight());
        block.setX(maxX * (0.06f + 0.88f * rnd.nextFloat()));
        block.setY(maxY * (0.06f + 0.88f * rnd.nextFloat()));
    }

    // ---- foto ----
    private boolean loadPics() {
        String bucket = PrayerCalc.prefs(this).getString("ss_bucket", "");
        // Tanpa folder pilihan: pakai Pictures/Wallpaper (foto kiriman bot Telegram masuk ke sini).
        String sel = bucket.isEmpty() ? MediaStore.Images.Media.RELATIVE_PATH + "=?" : MediaStore.Images.Media.BUCKET_ID + "=?";
        String arg = bucket.isEmpty() ? "Pictures/Wallpaper/" : bucket;
        try (Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Images.Media._ID},
                sel, new String[] {arg}, null)) {
            while (c != null && c.moveToNext()) pics.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)));
        } catch (Exception ignored) {}
        Collections.shuffle(pics);
        return !pics.isEmpty();
    }

    private void nextPhoto() {
        if (pics.isEmpty()) return;
        if (picIdx >= pics.size()) { Collections.shuffle(pics); picIdx = 0; }
        Uri u = pics.get(picIdx++);
        new Thread(() -> {
            Bitmap b = decode(u);
            if (b == null) return;
            main.post(() -> {
                ImageView in = photos[1 - front], out = photos[front];
                in.setImageBitmap(b);
                in.setScaleX(1f); in.setScaleY(1f);
                in.animate().alpha(1f).setDuration(1500).start();
                in.animate().scaleX(1.08f).scaleY(1.08f).setDuration(PrayerCalc.prefs(this).getInt("ss_interval", 20) * 1000L + 1500).start();
                out.animate().alpha(0f).setDuration(1500).start();
                front = 1 - front;
            });
        }).start();
    }

    private Bitmap decode(Uri u) {
        try {
            int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = getContentResolver().openInputStream(u)) { return BitmapFactory.decodeStream(in, null, d); }
        } catch (Exception e) { return null; }
    }

    private TextView tv(Typeface base, float sp, int color, int weight) {
        TextView t = new TextView(this);
        style(t, base, sp, color, weight);
        return t;
    }

    private void style(TextView t, Typeface base, float sp, int color, int weight) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(base, weight, false) : base);
        t.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
        t.setSingleLine(true);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
