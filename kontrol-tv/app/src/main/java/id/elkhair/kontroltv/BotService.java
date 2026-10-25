package id.elkhair.kontroltv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bot Telegram (long polling keluar, tanpa port masuk) + pengawas izin penjaga. */
public class BotService extends Service {
    private static final ExecutorService SEND = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Boolean guardWasOn;
    private Thread poller;
    private String commandsSetFor;

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            notifyOwner(c, Intent.ACTION_SCREEN_ON.equals(i.getAction()) ? "☀️ TV menyala." : "🌙 TV masuk standby.");
        }
    };

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            java.util.List<String> fixed = Doctor.heal(BotService.this);
            if (!fixed.isEmpty()) {
                notifyOwner(BotService.this, "♻️ Dipulihkan otomatis: " + String.join(", ", fixed) + ".");
                Store.log(BotService.this, "Dipulihkan: " + String.join(", ", fixed));
            }
            java.util.List<String> miss = Doctor.missing(BotService.this);
            String key = String.join("|", miss);
            if (!miss.isEmpty() && !key.equals(Store.p(BotService.this).getString("missing_reported", ""))) {
                Store.p(BotService.this).edit().putString("missing_reported", key).apply();
                notifyOwner(BotService.this, Doctor.report(BotService.this));
            } else if (miss.isEmpty()) Store.p(BotService.this).edit().remove("missing_reported").apply();
            boolean on = guardEnabled(BotService.this);
            if (guardWasOn != null && guardWasOn && !on)
                notifyOwner(BotService.this, "⚠️ *Penjaga kunci NONAKTIF* — izin aksesibilitas Kontrol TV dicabut. Aplikasi terkunci bisa dibuka.");
            if (guardWasOn != null && !guardWasOn && on) notifyOwner(BotService.this, "✅ Penjaga kunci aktif kembali.");
            guardWasOn = on;
            new Thread(BotService.this::periodic).start();
            main.postDelayed(this, 30_000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        self = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("bot", "Kontrol TV", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "bot").setSmallIcon(R.drawable.icon).setContentTitle("Kontrol TV aktif").build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(3, n);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f);
        main.postDelayed(watchdog, 5_000L);
        if (Store.p(this).getBoolean("just_booted", false)) {
            Store.p(this).edit().remove("just_booted").apply();
            main.postDelayed(() -> notifyOwner(this, "📺 TV baru saja dinyalakan/restart."), 20_000L);
        }
        startPolling();
        if (TvLock.active(this)) TvLock.ensure(this);
    }

    @Override public int onStartCommand(Intent i, int f, int id) { startPolling(); return START_STICKY; }

    /** Tiap 30 detik: batas waktu harian, jadwal kunci, laporan otomatis. */
    private void periodic() {
        try {
            if (!Store.limited(this).isEmpty()) {
                java.util.Map<String, Long> use = Extras.usageToday(this);
                for (String p : Store.limited(this)) {
                    int lim = Store.limit(this, p);
                    if (lim > 0 && !Store.limitHit(this, p) && use.getOrDefault(p, 0L) >= lim) {
                        Store.markLimitHit(this, p);
                        notifyOwner(this, "⏱ Batas harian *" + Bot.md(Apps.label(this, p)) + "* (" + lim + " menit) tercapai — dikunci sampai besok.");
                    }
                }
            }
            java.util.List<Sholat.Time> times = Sholat.refresh(this);
            long now = System.currentTimeMillis();
            int remind = Store.p(this).getInt("remind", 0), pmode = Store.prayerMode(this);
            for (Sholat.Time t : times) {
                if (!t.prayer) continue;
                if (remind > 0 && now >= t.at - remind * 60_000L && now < t.at && !Store.p(this).getBoolean("rem_" + t.at, false)) {
                    Store.p(this).edit().putBoolean("rem_" + t.at, true).apply();
                    notifyOwner(this, "🔔 *" + t.name + "* pukul " + Sholat.hhmm(t.minute) + " " + Sholat.zone + " — " + Math.max(1, (t.at - now + 59_999) / 60000) + " menit lagi.");
                }
                if (pmode > 0 && now >= t.at && now < t.at + pmode * 60_000L && !Store.p(this).getBoolean("pm_" + t.at, false)) {
                    Store.p(this).edit().putBoolean("pm_" + t.at, true).apply();
                    notifyOwner(this, "🕌 Waktu *" + t.name + "* — mode sholat: aplikasi hiburan dikunci " + pmode + " menit.");
                    Store.log(this, "Mode sholat " + t.name);
                }
            }
            TvLock.tick(this);
            GuardService.enforceNow();   // juga menutup aplikasi yang masuk jam jadwal kunci / kunci semua / mode sholat
            int at = Store.p(this).getInt("report_at", -1);
            String today = Store.today();
            if (at >= 0 && Store.nowMinute() >= at && !today.equals(Store.p(this).getString("report_sent", ""))) {
                Store.p(this).edit().putString("report_sent", today).apply();
                notifyOwner(this, Bot.report(this, true));
            }
        } catch (Exception e) { Log.w("KontrolTV", "periodic: " + e); }
    }

    private synchronized void startPolling() {
        if (poller != null && poller.isAlive()) return;
        running = true;
        poller = new Thread(this::pollLoop, "tg-poll");
        poller.start();
    }

    private void pollLoop() {
        int backoff = 5;
        while (running) {
            String token = Store.token(this);
            if (token == null) { sleep(15); continue; }
            if (!token.equals(commandsSetFor)) {
                try { Bot.api(token, "setMyCommands", new JSONObject().put("commands", Menu.commands()), 20000); commandsSetFor = token; }
                catch (Exception ignored) {}
            }
            try {
                long offset = Store.p(this).getLong("offset", 0);
                JSONObject req = new JSONObject().put("timeout", 50).put("offset", offset)
                        .put("allowed_updates", new JSONArray().put("message").put("callback_query"));
                JSONObject res = Bot.api(token, "getUpdates", req, 65_000);
                JSONArray arr = res.optJSONArray("result");
                if (arr != null) for (int i = 0; i < arr.length(); i++) {
                    JSONObject u = arr.getJSONObject(i);
                    Store.p(this).edit().putLong("offset", u.getLong("update_id") + 1).apply();
                    try { Bot.handle(this, token, u); } catch (Exception e) { Log.w("KontrolTV", "handle: " + e); }
                }
                backoff = 5;
            } catch (Exception e) {
                Log.w("KontrolTV", "poll: " + e);
                sleep(backoff);
                backoff = Math.min(backoff * 2, 120);
            }
        }
    }

    private static void sleep(int s) { try { Thread.sleep(s * 1000L); } catch (InterruptedException ignored) {} }

    /** Kirim ke pemilik dan semua admin. */
    static void notifyOwner(Context c, String text) {
        Context app = c.getApplicationContext();
        SEND.execute(() -> {
            long owner = Store.owner(app);
            String token = Store.token(app);
            if (owner == 0 || token == null) return;
            java.util.List<Long> to = new java.util.ArrayList<>();
            to.add(owner);
            for (String a : Store.admins(app)) try { to.add(Long.parseLong(a)); } catch (Exception ignored) {}
            for (long id : to) {
                for (int i = 0; i < 3; i++) {
                    try { Bot.send(token, id, text, null); break; } catch (Exception e) { sleep(5); }
                }
            }
        });
    }

    private static volatile BotService self;
    private android.view.View msgView;

    /** Pesan dari bot di layar TV: kartu di tengah, BACK/OK menutup, hilang sendiri setelah 30 detik. */
    static void showMessage(Context c, String text, String from) {
        BotService s = self;
        if (s == null) return;
        s.main.post(() -> s.showMessageNow(text, from));
    }

    private void showMessageNow(String text, String from) {
        if (!android.provider.Settings.canDrawOverlays(this)) return;
        hideMessage();
        android.view.WindowManager wm = getSystemService(android.view.WindowManager.class);
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout card = new android.widget.LinearLayout(this) {
            @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
                int k = e.getKeyCode();
                if (k == android.view.KeyEvent.KEYCODE_VOLUME_UP || k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                        || k == android.view.KeyEvent.KEYCODE_VOLUME_MUTE || k == android.view.KeyEvent.KEYCODE_POWER) return false;
                if (e.getAction() == android.view.KeyEvent.ACTION_UP) hideMessage();
                return true;
            }
        };
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setPadding((int) (34 * d), (int) (22 * d), (int) (34 * d), (int) (20 * d));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(24 * d);
        bg.setColor(0xF00B1A33);
        bg.setStroke((int) (2 * d), 0xFF3D8BFF);
        card.setBackground(bg);
        android.widget.TextView t1 = new android.widget.TextView(this);
        t1.setText("💬  Pesan" + (from.isEmpty() ? "" : " dari " + from));
        t1.setTextColor(0xB3FFFFFF);
        t1.setTextSize(16);
        card.addView(t1);
        android.widget.TextView t2 = new android.widget.TextView(this);
        t2.setText(text);
        t2.setTextColor(0xFFFFFFFF);
        t2.setTextSize(30);
        t2.setPadding(0, (int) (8 * d), 0, (int) (10 * d));
        t2.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.6f));
        card.addView(t2);
        android.widget.TextView t3 = new android.widget.TextView(this);
        t3.setText("Tekan OK atau BACK untuk menutup");
        t3.setTextColor(0x80FFFFFF);
        t3.setTextSize(13);
        card.addView(t3);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(-2, -2,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = android.view.Gravity.CENTER;
        try {
            wm.addView(card, lp);
            msgView = card;
            card.requestFocus();
            android.media.RingtoneManager.getRingtone(this, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play();
        } catch (Exception ignored) {}
        main.postDelayed(this::hideMessage, 30_000L);
    }

    private void hideMessage() {
        if (msgView != null) {
            try { getSystemService(android.view.WindowManager.class).removeView(msgView); } catch (Exception ignored) {}
            msgView = null;
        }
    }

    interface Sender { void run(String token, long owner) throws Exception; }

    static void sendAsync(Context c, Sender task) {
        Context app = c.getApplicationContext();
        SEND.execute(() -> {
            long owner = Store.owner(app);
            String token = Store.token(app);
            if (owner == 0 || token == null) return;
            try { task.run(token, owner); } catch (Exception ignored) {}
        });
    }

    static boolean guardEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/") && GuardService.instance != null;
    }

    @Override
    public void onDestroy() {
        running = false;
        main.removeCallbacks(watchdog);
        try { unregisterReceiver(screen); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
