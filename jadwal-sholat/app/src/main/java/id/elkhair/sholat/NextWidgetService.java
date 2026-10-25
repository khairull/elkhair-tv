package id.elkhair.sholat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/** Widget "waktu sholat berikutnya" di kanan-tengah, hanya saat layar Home (launcher) di depan. */
public class NextWidgetService extends Service {
    static final String HOME_PKG = "com.gathin.tvhome";
    static final String ACTION_NOTICE = "id.elkhair.sholat.SHOW_NOTICE";
    private View notice;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View view;
    private TextView name, time, left;
    private String top = "";
    private long lastQuery;
    private volatile boolean dreaming;
    private final android.content.BroadcastReceiver dreams = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) {
            dreaming = Intent.ACTION_DREAMING_STARTED.equals(i.getAction());
            update();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() { update(); main.postDelayed(this, 1000); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("widget", "Widget sholat", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "widget").setSmallIcon(R.drawable.icon)
                .setContentTitle("Jadwal Sholat").build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(2, n);
        wm = getSystemService(WindowManager.class);
        lastQuery = System.currentTimeMillis() - 60 * 60_000L;
        android.content.IntentFilter f = new android.content.IntentFilter(Intent.ACTION_DREAMING_STARTED);
        f.addAction(Intent.ACTION_DREAMING_STOPPED);
        registerReceiver(dreams, f);
        main.post(tick);
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (i != null && ACTION_NOTICE.equals(i.getAction())) main.post(() -> showNotice(i.getIntExtra("idx", 0)));
        return START_STICKY;
    }

    /** Pengingat Imsak/Dhuha: kartu kecil di atas layar, BACK menutup, hilang sendiri setelah 30 detik. */
    private void showNotice(int idx) {
        if (!Settings.canDrawOverlays(this)) return;
        hideNotice();
        float d = getResources().getDisplayMetrics().density;
        Typeface base = getResources().getFont(R.font.outfit);
        Typeface bold = Build.VERSION.SDK_INT >= 28 ? Typeface.create(base, 700, false) : base;
        LinearLayout card = new LinearLayout(this) {
            @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
                if (e.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK || e.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                        || e.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER) {
                    if (e.getAction() == android.view.KeyEvent.ACTION_UP) hideNotice();
                    return true;
                }
                hideNotice();   // tombol lain: tutup kartu dan biarkan aplikasi di bawahnya menerima tombol berikutnya
                return false;
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setPadding((int) (26 * d), (int) (14 * d), (int) (26 * d), (int) (14 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(0xE6081210);
        bg.setStroke((int) (1.5f * d), getColor(R.color.accent));
        card.setBackground(bg);
        int[] m = PrayerCalc.minutes(this, PrayerCalc.today(this));
        TextView t1 = tv(bold, 20, 0xFFFFFFFF);
        t1.setText((idx == 0 ? "🌙  Waktu Imsak" : "☀️  Waktu Dhuha") + "  ·  " + PrayerCalc.hhmm(m[idx]) + " " + PrayerCalc.zoneLabel(this));
        card.addView(t1);
        TextView t2 = tv(bold, 13, 0xB3FFFFFF);
        t2.setText(idx == 0 ? "Subuh pukul " + PrayerCalc.hhmm(m[1]) + "  ·  tekan BACK untuk menutup" : "Tekan BACK untuk menutup");
        card.addView(t2);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = (int) (40 * d);
        try {
            wm.addView(card, lp);
            notice = card;
            card.requestFocus();
            android.media.RingtoneManager.getRingtone(this, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play();
        } catch (Exception ignored) {}
        main.postDelayed(this::hideNotice, 30_000L);
    }

    private void hideNotice() {
        if (notice != null) { try { wm.removeView(notice); } catch (Exception ignored) {} notice = null; }
    }

    private long lastWx;
    private View keepAwake;
    private String awakeFor = "";
    private long awakeSince;
    private static final long AWAKE_MAX_MS = 4 * 60 * 60_000L;

    /** Aplikasi di depan (selain Home): tahan layar agar screensaver tidak muncul. Di Home screensaver tetap berjalan normal. */
    private void keepAwakeForForegroundApp() {
        boolean app = !top.isEmpty() && !HOME_PKG.equals(top) && !dreaming && !top.equals(getPackageName())
                && !top.equals("com.android.systemui") && !top.equals("android");
        if (app && !top.equals(awakeFor)) { awakeFor = top; awakeSince = System.currentTimeMillis(); }
        boolean want = app && System.currentTimeMillis() - awakeSince < AWAKE_MAX_MS;
        if (want && keepAwake == null && Settings.canDrawOverlays(this)) {
            View v = new View(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            try { wm.addView(v, lp); keepAwake = v; } catch (Exception ignored) {}
        } else if (!want && keepAwake != null) {
            try { wm.removeView(keepAwake); } catch (Exception ignored) {}
            keepAwake = null;
        }
        if (!app) awakeFor = "";
    }

    private void update() {
        if (System.currentTimeMillis() - lastWx > 10 * 60_000L) {
            lastWx = System.currentTimeMillis();
            new Thread(() -> Weather.refresh(this)).start();
        }
        UsageStatsManager us = getSystemService(UsageStatsManager.class);
        long now = System.currentTimeMillis();
        UsageEvents ev = us.queryEvents(lastQuery, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) top = e.getPackageName();
        }
        lastQuery = now - 1000;
        keepAwakeForForegroundApp();
        boolean show = HOME_PKG.equals(top) && !dreaming && Settings.canDrawOverlays(this) && !AzanService.playing;
        if (show) { ensureView(); fill(); view.setVisibility(View.VISIBLE); }
        else if (view != null) view.setVisibility(View.GONE);
    }

    private void fill() {
        long[] n = PrayerCalc.next(this);
        name.setText(PrayerCalc.NAMES[(int) n[0]] + "  ·  " + PrayerCalc.hhmm((int) n[1]) + " " + PrayerCalc.zoneLabel(this));
        left.setText(PrayerCalc.countdown(n[2] - System.currentTimeMillis()));
    }

    private void ensureView() {
        if (view != null) return;
        Typeface base = getResources().getFont(R.font.outfit);
        Typeface font = Build.VERSION.SDK_INT >= 28 ? Typeface.create(base, 700, false) : Typeface.create(base, Typeface.BOLD);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (22 * d), (int) (12 * d), (int) (22 * d), (int) (12 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(22 * d);
        bg.setColor(0x99081210);
        bg.setStroke((int) (1.5f * d), getColor(R.color.accent));
        box.setBackground(bg);
        TextView label = tv(font, 11, 0xB3FFFFFF);
        label.setLetterSpacing(0.2f);
        label.setText("SHOLAT BERIKUTNYA");
        box.addView(label);
        name = tv(font, 17, getColor(R.color.accent));
        box.addView(name);
        left = tv(font, 36, 0xFFFFFFFF);
        left.setFontFeatureSettings("tnum");
        box.addView(left);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.END | Gravity.TOP;
        lp.x = (int) (getResources().getDisplayMetrics().widthPixels * 0.0586f);
        lp.y = (int) (getResources().getDisplayMetrics().heightPixels * 0.253f);
        wm.addView(box, lp);
        view = box;
    }

    private TextView tv(Typeface f, float sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(f);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        t.setPadding(0, 3, 0, 3);
        return t;
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        try { unregisterReceiver(dreams); } catch (Exception ignored) {}
        if (keepAwake != null) { try { wm.removeView(keepAwake); } catch (Exception ignored) {} }
        if (view != null) wm.removeView(view);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
