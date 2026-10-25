package id.elkhair.kontroltv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Kunci TV: layar penuh di atas semua aplikasi. Tombol aplikasi di remote, HOME dan MENU diblok lewat penjaga
 * aksesibilitas; Power & Volume tetap jalan; azan dari Jadwal Sholat tampil di atasnya.
 * Dibuka lewat Telegram (/bukatv, tombol izin) atau PIN di TV. Status tersimpan, tetap terkunci setelah restart.
 */
final class TvLock {
    static final String TITLE = "Waktunya istirahat sebentar...";
    static final String BODY = "Kamu sudah nonton TV cukup lama hari ini, saatnya belajar, istirahat atau bermain bola dulu ya!";
    private static final int MAX_FAIL = 5;
    private static final long LOCKOUT_MS = 5 * 60_000L;
    private static final long REQ_COOLDOWN = 2 * 60_000L;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static LockView view;
    private TvLock() {}

    // ---------------- status ----------------
    static boolean active(Context c) { return Store.p(c).getBoolean("tvlock", false); }

    /** Kunci TV. minutes > 0 = buka otomatis setelah sekian menit. */
    static void lock(Context c, String msg, String from, int minutes, String reason) {
        Store.p(c).edit().putBoolean("tvlock", true)
                .putString("tvlock_msg", msg == null ? "" : msg.trim())
                .putString("tvlock_from", from == null ? "" : from)
                .putLong("tvlock_until", minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0)
                .putString("tvlock_reason", reason)
                .remove("tvlock_relock_at").remove("tvreq_at").remove("tvreq_state")
                .apply();
        Store.log(c, "Kunci TV (" + reason + ")");
        // hentikan tontonan yang sedang berjalan
        try { Extras.mediaKey(c, KeyEvent.KEYCODE_MEDIA_PAUSE); } catch (Exception ignored) {}
        GuardService.lockKeys(true);
        String cur = GuardService.current;
        if (cur != null && !cur.isEmpty() && !Store.exempt(c, cur)) GuardService.closeCurrent();
        ensure(c);
    }

    /** Buka kunci. relockMin > 0 = kunci lagi otomatis setelah sekian menit. */
    static void unlock(Context c, int relockMin, String by) {
        long now = System.currentTimeMillis();
        android.content.SharedPreferences.Editor e = Store.p(c).edit().putBoolean("tvlock", false)
                .remove("tvlock_until").remove("tvreq_at").remove("tvreq_state");
        if (relockMin > 0) e.putLong("tvlock_relock_at", now + relockMin * 60_000L);
        else e.remove("tvlock_relock_at");
        // dibuka saat jam jadwal: jangan dikunci lagi oleh jadwal sampai jadwal itu selesai
        long end = scheduleEnd(c);
        if (end > 0) e.putLong("tvsched_skip_until", relockMin > 0 ? Math.min(end, now + relockMin * 60_000L) : end);
        e.apply();
        Store.log(c, "Kunci TV dibuka (" + by + ")" + (relockMin > 0 ? " " + relockMin + " menit" : ""));
        GuardService.lockKeys(false);
        ensure(c);
    }

    /** Tampilkan/sembunyikan layar kunci sesuai status. */
    static void ensure(Context c) {
        Context app = c.getApplicationContext();
        main.post(() -> {
            if (active(app)) {
                GuardService.lockKeys(true);
                if (view == null) show(app); else view.bind();
            } else hide(app);
        });
    }

    /** Dipanggil tiap 30 detik: buka otomatis, kunci lagi setelah buka sementara, jadwal harian. */
    static void tick(Context c) {
        long now = System.currentTimeMillis();
        android.content.SharedPreferences p = Store.p(c);
        if (active(c)) {
            long until = p.getLong("tvlock_until", 0);
            if (until > 0 && now >= until) {
                unlock(c, 0, "waktu habis");
                BotService.notifyOwner(c, "🔓 Kunci TV dibuka otomatis (waktu kunci selesai).");
            } else ensure(c);
            return;
        }
        long relock = p.getLong("tvlock_relock_at", 0);
        if (relock > 0 && now >= relock) {
            p.edit().remove("tvlock_relock_at").apply();
            lock(c, p.getString("tvlock_msg", ""), p.getString("tvlock_from", ""), 0, "buka sementara selesai");
            BotService.notifyOwner(c, "🔒 Waktu buka sementara habis — TV dikunci lagi.");
            return;
        }
        if (inSchedule(c) && p.getLong("tvsched_skip_until", 0) < now) {
            lock(c, "", "", 0, "jadwal");
            BotService.notifyOwner(c, "🔒 TV dikunci otomatis sesuai jadwal (" + scheduleText(c) + ").");
        }
    }

    // ---------------- jadwal harian ----------------
    static void setSchedule(Context c, int from, int to) {
        if (from < 0) Store.p(c).edit().remove("tvsched").remove("tvsched_skip_until").apply();
        else Store.p(c).edit().putString("tvsched", from + "|" + to).remove("tvsched_skip_until").apply();
    }

    static String scheduleText(Context c) {
        String s = Store.p(c).getString("tvsched", null);
        if (s == null) return null;
        String[] a = s.split("\\|");
        return Bot.hhmm(Integer.parseInt(a[0])) + "–" + Bot.hhmm(Integer.parseInt(a[1]));
    }

    static boolean inSchedule(Context c) {
        String s = Store.p(c).getString("tvsched", null);
        if (s == null) return false;
        String[] a = s.split("\\|");
        int f = Integer.parseInt(a[0]), t = Integer.parseInt(a[1]), now = Store.nowMinute();
        return f <= t ? (now >= f && now < t) : (now >= f || now < t);
    }

    /** Waktu (ms) jadwal yang sedang berjalan berakhir; 0 bila di luar jadwal. */
    static long scheduleEnd(Context c) {
        if (!inSchedule(c)) return 0;
        String[] a = Store.p(c).getString("tvsched", "").split("\\|");
        int t = Integer.parseInt(a[1]), now = Store.nowMinute();
        int left = t > now ? t - now : t + 1440 - now;
        java.util.Calendar cal = java.util.Calendar.getInstance(Store.tz());
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis() + left * 60_000L;
    }

    static String statusText(Context c) {
        android.content.SharedPreferences p = Store.p(c);
        String sched = scheduleText(c);
        String s;
        if (active(c)) {
            long until = p.getLong("tvlock_until", 0);
            s = "🔒 terkunci" + (until > 0 ? " sampai " + Bot.hhmm(minuteOf(until)) : "");
        } else {
            long r = p.getLong("tvlock_relock_at", 0);
            s = r > System.currentTimeMillis() ? "🔓 dibuka sementara, terkunci lagi " + Bot.hhmm(minuteOf(r)) : "🔓 tidak terkunci";
        }
        return s + (sched != null ? " · jadwal " + sched : "");
    }

    private static int minuteOf(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance(Store.tz());
        cal.setTimeInMillis(ms);
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    // ---------------- minta izin ----------------
    static void requestAnswered(Context c, boolean ok) {
        Store.p(c).edit().putString("tvreq_state", ok ? "ok" : "no").apply();
        ensure(c);
    }

    private static void sendRequest(Context c) {
        Store.p(c).edit().putLong("tvreq_at", System.currentTimeMillis()).putString("tvreq_state", "wait").apply();
        Store.log(c, "Minta izin buka TV");
        BotService.sendAsync(c, (token, owner) -> {
            JSONArray rows = new JSONArray()
                    .put(new JSONArray().put(btn("✅ Buka sekarang", "tv:ok")).put(btn("⏱ Buka 30 menit", "tv:30")))
                    .put(new JSONArray().put(btn("⏱ Buka 1 jam", "tv:60")).put(btn("❌ Tolak", "tv:no")));
            JSONObject kb = new JSONObject().put("inline_keyboard", rows);
            String text = "🙋 *Ada yang minta izin membuka TV.*\nTV sedang dikunci. Izinkan?";
            java.util.List<Long> to = new java.util.ArrayList<>();
            to.add(owner);
            for (String a : Store.admins(c)) try { to.add(Long.parseLong(a)); } catch (Exception ignored) {}
            for (long id : to) try { Bot.send(token, id, text, kb); } catch (Exception ignored) {}
        });
    }

    private static JSONObject btn(String t, String d) throws Exception { return new JSONObject().put("text", t).put("callback_data", d); }

    // ---------------- tampilan ----------------
    private static void show(Context c) {
        if (!android.provider.Settings.canDrawOverlays(c)) return;
        WindowManager wm = c.getSystemService(WindowManager.class);
        LockView v = new LockView(c);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(v, lp);
            view = v;
            v.focusDefault();
        } catch (Exception ignored) {}
    }

    private static void hide(Context c) {
        if (view == null) return;
        view.stop();
        try { c.getSystemService(WindowManager.class).removeView(view); } catch (Exception ignored) {}
        view = null;
    }

    private static Bitmap wallpaper(Context c, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            Uri u = Uri.parse("content://com.gathin.tvhome.theme/wallpaper.jpg");
            try (InputStream in = c.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            try (InputStream in = c.getContentResolver().openInputStream(u)) { return BitmapFactory.decodeStream(in, null, o2); }
        } catch (Exception e) { return null; }
    }

    /** Layar kunci. */
    private static final class LockView extends FrameLayout {
        private final Context c;
        private final float d;
        private final TextView clock, date, prayer, msgFrom, msgText, status, dots, hint;
        private final LinearLayout msgCard, buttons, keypadBox;
        private final View pinBtn, askBtn;
        private final GridLayout keypad;
        private final DanceView dance;
        private final StringBuilder entered = new StringBuilder();
        private final Runnable clockTick = new Runnable() {
            @Override public void run() { updateClock(); main.postDelayed(this, 1000); }
        };

        LockView(Context ctx) {
            super(ctx);
            c = ctx;
            d = ctx.getResources().getDisplayMetrics().density;
            int sw = ctx.getResources().getDisplayMetrics().widthPixels, sh = ctx.getResources().getDisplayMetrics().heightPixels;
            setBackgroundColor(0xFF070A14);
            ImageView bg = new ImageView(ctx);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = wallpaper(ctx, sw, sh);
            if (b != null) bg.setImageBitmap(b);
            addView(bg, new LayoutParams(-1, -1));
            View scrim = new View(ctx);
            scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x99000000, 0xB3000000, 0xE6000000}));
            addView(scrim, new LayoutParams(-1, -1));

            // atas: jam (kiri) + sholat berikutnya (kanan)
            LinearLayout top = new LinearLayout(ctx);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setPadding(dp(56), dp(36), dp(56), 0);
            LinearLayout left = new LinearLayout(ctx);
            left.setOrientation(LinearLayout.VERTICAL);
            clock = text("", 64, 0xFFFFFFFF, false);
            clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
            date = text("", 18, 0xCCFFFFFF, false);
            clock.setGravity(Gravity.START);
            date.setGravity(Gravity.START);
            date.setPadding(dp(4), 0, 0, 0);
            left.addView(clock);
            left.addView(date);
            top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            prayer = text("", 18, 0xFFFFFFFF, false);
            prayer.setGravity(Gravity.END);
            prayer.setPadding(dp(20), dp(12), dp(20), dp(12));
            GradientDrawable pc = new GradientDrawable();
            pc.setCornerRadius(dp(18));
            pc.setColor(0x55000000);
            pc.setStroke(dp(1), 0x6634D399);
            prayer.setBackground(pc);
            top.addView(prayer, new LinearLayout.LayoutParams(-2, -2));
            addView(top, new LayoutParams(-1, -2, Gravity.TOP));

            // tengah
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            dance = new DanceView(ctx);
            col.addView(dance, new LinearLayout.LayoutParams(dp(520), dp(110)));
            TextView title = text(TITLE, 38, 0xFFFFFFFF, true);
            col.addView(title);
            TextView body = text(BODY, 21, 0xE6FFFFFF, false);
            body.setMaxWidth(dp(820));
            body.setLineSpacing(0, 1.15f);
            body.setPadding(0, dp(6), 0, 0);
            col.addView(body);
            msgCard = new LinearLayout(ctx);
            msgCard.setOrientation(LinearLayout.VERTICAL);
            msgCard.setPadding(dp(24), dp(12), dp(24), dp(14));
            GradientDrawable mc = new GradientDrawable();
            mc.setCornerRadius(dp(18));
            mc.setColor(0x88102A55);
            mc.setStroke(dp(2), 0xFF3D8BFF);
            msgCard.setBackground(mc);
            msgFrom = text("", 15, 0xB3FFFFFF, false);
            msgText = text("", 24, 0xFFFFFFFF, true);
            msgText.setMaxWidth(dp(760));
            msgCard.addView(msgFrom);
            msgCard.addView(msgText);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
            ml.topMargin = dp(16);
            col.addView(msgCard, ml);
            status = text("", 17, 0xFFFFD27A, false);
            status.setPadding(0, dp(10), 0, 0);
            col.addView(status);

            // tombol
            buttons = new LinearLayout(ctx);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.CENTER);
            pinBtn = button("🔢  Masukkan PIN", v -> showKeypad(true));
            askBtn = button("🙋  Minta izin", v -> ask());
            buttons.addView(pinBtn);
            buttons.addView(askBtn);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
            bl.topMargin = dp(18);
            col.addView(buttons, bl);

            // keypad PIN sendiri (tanpa keyboard Android)
            keypadBox = new LinearLayout(ctx);
            keypadBox.setOrientation(LinearLayout.VERTICAL);
            keypadBox.setGravity(Gravity.CENTER_HORIZONTAL);
            dots = text("", 30, 0xFFFFFFFF, true);
            dots.setLetterSpacing(0.3f);
            hint = text("", 14, 0xB3FFFFFF, false);
            keypadBox.addView(dots);
            keypadBox.addView(hint);
            keypad = new GridLayout(ctx);
            keypad.setColumnCount(6);
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "⌫", "OK"};
            for (String k : keys) {
                TextView kb = text(k, 22, 0xFFFFFFFF, true);
                kb.setFocusable(true);
                kb.setGravity(Gravity.CENTER);
                kb.setBackground(keyBg(dp(12)));
                kb.setOnClickListener(v -> press(k));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = dp(70);
                lp.height = dp(48);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                keypad.addView(kb, lp);
            }
            keypadBox.addView(keypad);
            keypadBox.addView(text("Tombol angka di remote juga bisa · BACK untuk kembali", 12, 0x80FFFFFF, false));
            keypadBox.setVisibility(GONE);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
            kl.topMargin = dp(10);
            col.addView(keypadBox, kl);

            LayoutParams cl = new LayoutParams(-1, -2, Gravity.CENTER);
            cl.topMargin = dp(60);
            addView(col, cl);

            TextView foot = text("📺 TV dikunci · dibuka lewat Telegram atau PIN", 13, 0x80FFFFFF, false);
            LayoutParams fl = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            fl.bottomMargin = dp(18);
            addView(foot, fl);

            setFocusable(true);
            bind();
            main.post(clockTick);
            dance.start();
        }

        void stop() { main.removeCallbacks(clockTick); dance.stop(); }

        void focusDefault() { post(() -> (keypadBox.getVisibility() == VISIBLE ? keypad.getChildAt(9) : pinBtn).requestFocus()); }

        void bind() {
            android.content.SharedPreferences p = Store.p(c);
            String m = p.getString("tvlock_msg", ""), from = p.getString("tvlock_from", "");
            msgCard.setVisibility(m.isEmpty() ? GONE : VISIBLE);
            msgFrom.setText("💬  Pesan" + (from.isEmpty() ? "" : " dari " + from));
            msgText.setText(m);
            pinBtn.setVisibility(Store.hasPin(c) ? VISIBLE : GONE);
            String st = p.getString("tvreq_state", "");
            long until = p.getLong("tvlock_until", 0);
            String s = "";
            if (st.equals("wait")) s = "⏳ Permintaan izin sudah dikirim. Tunggu jawaban ya…";
            else if (st.equals("no")) s = "❌ Belum diizinkan. Coba lagi nanti ya.";
            else if (until > 0) s = "⏱ TV terbuka lagi pukul " + Bot.hhmm(minuteOf(until));
            status.setText(s);
            status.setVisibility(s.isEmpty() ? GONE : VISIBLE);
            if (!hasFocus()) focusDefault();
        }

        private void ask() {
            long at = Store.p(c).getLong("tvreq_at", 0);
            if (System.currentTimeMillis() - at < REQ_COOLDOWN && "wait".equals(Store.p(c).getString("tvreq_state", ""))) {
                status.setText("⏳ Permintaan sudah dikirim. Tunggu sebentar ya…");
                status.setVisibility(VISIBLE);
                return;
            }
            sendRequest(c);
            bind();
        }

        private void showKeypad(boolean on) {
            entered.setLength(0);
            keypadBox.setVisibility(on ? VISIBLE : GONE);
            buttons.setVisibility(on ? GONE : VISIBLE);
            render("Masukkan PIN untuk membuka TV");
            focusDefault();
        }

        private void press(String k) {
            long lockedUntil = Store.p(c).getLong("pin_lock_until", 0);
            if (lockedUntil > System.currentTimeMillis()) {
                render("Terlalu banyak percobaan. Coba lagi " + ((lockedUntil - System.currentTimeMillis()) / 60000 + 1) + " menit lagi.");
                return;
            }
            if (k.equals("⌫")) { if (entered.length() > 0) entered.setLength(entered.length() - 1); render(null); return; }
            if (k.equals("OK")) { submit(); return; }
            if (entered.length() < 8) entered.append(k);
            if (entered.length() >= 4 && Store.checkPin(c, entered.toString())) { success(); return; }
            render(null);
        }

        private void submit() {
            if (entered.length() == 0) return;
            if (Store.checkPin(c, entered.toString())) { success(); return; }
            int fails = Store.p(c).getInt("pin_fails", 0) + 1;
            entered.setLength(0);
            if (fails >= MAX_FAIL) {
                Store.p(c).edit().putInt("pin_fails", 0).putLong("pin_lock_until", System.currentTimeMillis() + LOCKOUT_MS).apply();
                render("PIN salah " + MAX_FAIL + "×. PIN dikunci 5 menit.");
                BotService.notifyOwner(c, "🚨 PIN salah " + MAX_FAIL + "× di layar Kunci TV. Isian PIN dikunci 5 menit.");
            } else {
                Store.p(c).edit().putInt("pin_fails", fails).apply();
                render("PIN salah (" + fails + "/" + MAX_FAIL + ")");
                BotService.notifyOwner(c, "⚠️ PIN salah dimasukkan di layar Kunci TV (" + fails + "/" + MAX_FAIL + ").");
            }
        }

        private void success() {
            Store.p(c).edit().putInt("pin_fails", 0).apply();
            BotService.notifyOwner(c, "🔓 Kunci TV dibuka dengan PIN di TV.");
            unlock(c, 0, "PIN");
        }

        private void render(String h) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < entered.length(); i++) s.append('●');
            for (int i = entered.length(); i < Math.max(4, entered.length()); i++) s.append('○');
            dots.setText(s);
            if (h != null) hint.setText(h);
        }

        private void updateClock() {
            Date now = new Date();
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.ROOT);
            hm.setTimeZone(Store.tz());
            SimpleDateFormat dt = new SimpleDateFormat("EEEE, d MMMM yyyy", new Locale("id"));
            dt.setTimeZone(Store.tz());
            clock.setText(hm.format(now));
            date.setText(dt.format(now));
            if (now.getTime() / 1000 % 30 == 0 || prayer.getText().length() == 0) nextPrayer();
            else if (nextAt > 0) prayer.setText(prayerLabel + "\n" + countdown(nextAt - now.getTime()));
        }

        private long nextAt;
        private String prayerLabel = "";

        private void nextPrayer() {
            List<Sholat.Time> l = Sholat.times(c);
            long now = System.currentTimeMillis();
            nextAt = 0;
            for (Sholat.Time t : l) if (t.prayer && t.at > now) { nextAt = t.at; prayerLabel = "🕌 " + t.name + "  " + Sholat.hhmm(t.minute) + " " + Sholat.zone; break; }
            prayer.setVisibility(nextAt > 0 ? VISIBLE : GONE);
            if (nextAt > 0) prayer.setText(prayerLabel + "\n" + countdown(nextAt - now));
        }

        private String countdown(long ms) {
            long s = Math.max(0, ms / 1000);
            return String.format(Locale.ROOT, "%02d:%02d:%02d lagi", s / 3600, s / 60 % 60, s % 60);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE
                    || k == KeyEvent.KEYCODE_POWER) return false;
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            boolean kp = keypadBox.getVisibility() == VISIBLE;
            if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) {
                if (down) { if (!kp && Store.hasPin(c)) showKeypad(true); if (Store.hasPin(c)) press(String.valueOf(k - KeyEvent.KEYCODE_0)); }
                return true;
            }
            if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) {
                if (down) { if (!kp && Store.hasPin(c)) showKeypad(true); if (Store.hasPin(c)) press(String.valueOf(k - KeyEvent.KEYCODE_NUMPAD_0)); }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DEL) { if (down && kp) press("⌫"); return true; }
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) { if (!down && kp) showKeypad(false); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT
                    || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_NUMPAD_ENTER) return super.dispatchKeyEvent(e);
            return true;   // tombol lain ditelan
        }

        private View button(String label, OnClickListener l) {
            TextView b = text(label, 20, 0xFFFFFFFF, true);
            b.setFocusable(true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(28), dp(12), dp(28), dp(12));
            b.setBackground(keyBg(dp(28)));
            b.setOnClickListener(l);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(10), 0, dp(10), 0);
            b.setLayoutParams(lp);
            return b;
        }

        private StateListDrawable keyBg(int r) {
            GradientDrawable n = new GradientDrawable();
            n.setCornerRadius(r);
            n.setColor(0x33FFFFFF);
            n.setStroke(dp(1), 0x40FFFFFF);
            GradientDrawable f = new GradientDrawable();
            f.setCornerRadius(r);
            f.setColor(0xFFFFFFFF);
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[] {android.R.attr.state_focused}, f);
            s.addState(new int[] {}, n);
            return s;
        }

        private TextView text(String s, float sp, int color, boolean bold) {
            TextView v = new TextView(c) {
                @Override protected void drawableStateChanged() {
                    super.drawableStateChanged();
                    if (isFocusable()) setTextColor(isFocused() ? 0xFF0B1220 : color);
                }
            };
            v.setText(s);
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            v.setTextColor(color);
            v.setGravity(Gravity.CENTER);
            if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            v.setShadowLayer(dp(4), 0, dp(1), 0x99000000);
            return v;
        }

        private int dp(int v) { return Math.round(v * d); }
    }

    /** Ikon yang "menari": buku, bola, bantal, anak berlari — melompat & bergoyang bergantian. */
    private static final class DanceView extends View {
        private final String[] icons = {"📚", "⚽", "🧒", "😴", "🏃"};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        private float t;

        DanceView(Context c) {
            super(c);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(54 * c.getResources().getDisplayMetrics().density);
            anim.setDuration(2400);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        }

        void start() { anim.start(); }
        void stop() { anim.cancel(); }

        @Override
        protected void onDraw(Canvas cv) {
            int n = icons.length;
            float w = getWidth(), h = getHeight(), step = w / n;
            for (int i = 0; i < n; i++) {
                double ph = 2 * Math.PI * (t * 2 + i / (double) n);
                float jump = (float) Math.max(0, Math.sin(ph)) * h * 0.28f;          // lompatan
                float sway = (float) Math.sin(ph + Math.PI / 2) * 14f;              // goyang kiri-kanan
                float squash = 1f + (float) Math.max(0, -Math.sin(ph)) * 0.10f;      // memantul saat mendarat
                float cx = step * (i + 0.5f), base = h * 0.82f;
                cv.save();
                cv.translate(cx, base - jump);
                cv.rotate(sway);
                cv.scale(squash, 2f - squash);
                cv.drawText(icons[i], 0, 0, paint);
                cv.restore();
            }
        }
    }
}
