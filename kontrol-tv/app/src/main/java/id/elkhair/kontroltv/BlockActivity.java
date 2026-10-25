package id.elkhair.kontroltv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Layar "aplikasi dikunci" dengan keypad PIN sendiri (tanpa keyboard Android). PIN benar = buka 30 menit. */
public class BlockActivity extends Activity {
    private static final int MAX_FAIL = 5;
    private static final long LOCKOUT_MS = 5 * 60_000L;
    private String pkg;
    private TextView msg, dots, hint;
    private View keypad;
    private final StringBuilder entered = new StringBuilder();

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF0B1A33, 0xFF04070D}));
        msg = t("", 32, 0xFFFFFFFF);
        col.addView(msg);
        col.addView(t("Aplikasi ini sedang dikunci oleh orang tua.", 18, 0xB3FFFFFF));
        dots = t("", 34, 0xFFFFFFFF);
        dots.setLetterSpacing(0.3f);
        dots.setPadding(0, dp(14), 0, dp(4));
        col.addView(dots);
        hint = t("", 15, 0x99FFFFFF);
        col.addView(hint);
        GridLayout g = new GridLayout(this);
        g.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        for (String k : keys) {
            TextView b = t(k, 24, 0xFFFFFFFF);
            b.setFocusable(true);
            b.setBackground(keyBg());
            b.setOnClickListener(v -> press(k));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(84);
            lp.height = dp(52);
            lp.setMargins(dp(5), dp(5), dp(5), dp(5));
            b.setGravity(Gravity.CENTER);
            g.addView(b, lp);
        }
        keypad = g;
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.topMargin = dp(10);
        col.addView(g, gl);
        col.addView(t("Tombol angka di remote juga bisa dipakai · BACK untuk kembali", 13, 0x80FFFFFF));
        setContentView(col);
        bind();
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); bind(); }

    private void bind() {
        pkg = getIntent().getStringExtra("pkg");
        msg.setText(Store.SETUP.equals(pkg) ? "🔒  Pengaturan Kontrol TV" : "🔒  " + Apps.label(this, pkg) + " dikunci");
        entered.setLength(0);
        boolean pin = Store.hasPin(this);
        keypad.setVisibility(pin ? View.VISIBLE : View.GONE);
        dots.setVisibility(pin ? View.VISIBLE : View.GONE);
        render(pin ? (Store.SETUP.equals(pkg) ? "Masukkan PIN untuk membuka pengaturan" : "Masukkan PIN untuk membuka 30 menit") : "");
        if (pin) keypad.post(() -> ((android.view.ViewGroup) keypad).getChildAt(10).requestFocus());   // tombol 0
    }

    private void press(String k) {
        long until = Store.p(this).getLong("pin_lock_until", 0);
        if (until > System.currentTimeMillis()) {
            render("Terlalu banyak percobaan. Coba lagi " + ((until - System.currentTimeMillis()) / 60000 + 1) + " menit lagi.");
            return;
        }
        if (k.equals("⌫")) { if (entered.length() > 0) entered.setLength(entered.length() - 1); render(null); return; }
        if (k.equals("OK")) { submit(); return; }
        if (entered.length() < 8) entered.append(k);
        if (entered.length() >= 4 && Store.checkPin(this, entered.toString())) { success(); return; }
        render(null);
    }

    private void submit() {
        if (entered.length() == 0) return;
        if (Store.checkPin(this, entered.toString())) { success(); return; }
        int fails = Store.p(this).getInt("pin_fails", 0) + 1;
        entered.setLength(0);
        if (fails >= MAX_FAIL) {
            Store.p(this).edit().putInt("pin_fails", 0).putLong("pin_lock_until", System.currentTimeMillis() + LOCKOUT_MS).apply();
            render("PIN salah " + MAX_FAIL + "×. PIN dikunci 5 menit.");
            BotService.notifyOwner(this, "🚨 PIN salah " + MAX_FAIL + "× untuk *" + Bot.md(name()) + "*. Isian PIN dikunci 5 menit.");
        } else {
            Store.p(this).edit().putInt("pin_fails", fails).apply();
            render("PIN salah (" + fails + "/" + MAX_FAIL + ")");
            BotService.notifyOwner(this, "⚠️ PIN salah dimasukkan untuk *" + Bot.md(name()) + "* (" + fails + "/" + MAX_FAIL + ").");
        }
    }

    private void success() {
        Store.p(this).edit().putInt("pin_fails", 0).apply();
        if (Store.SETUP.equals(pkg)) {
            Store.allowSetup(this, 5);
            BotService.notifyOwner(this, "🔓 Pengaturan Kontrol TV dibuka dengan PIN di TV (5 menit).");
            finish();
            return;
        }
        Store.unlockFor(this, pkg, 30);
        BotService.notifyOwner(this, "🔓 *" + Bot.md(name()) + "* dibuka 30 menit dengan PIN di TV.");
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
        finish();
        if (launch != null) startActivity(launch);
    }

    private String name() { return Store.SETUP.equals(pkg) ? "Pengaturan Kontrol TV" : Apps.label(this, pkg); }

    private void render(String h) {
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < entered.length(); i++) d.append('●');
        for (int i = entered.length(); i < Math.max(4, entered.length()); i++) d.append('○');
        dots.setText(d);
        if (h != null) hint.setText(h);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 && Store.hasPin(this)) {
            press(String.valueOf(code - KeyEvent.KEYCODE_0));   // fokus keypad tidak ikut berpindah
            return true;
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9 && Store.hasPin(this)) {
            press(String.valueOf(code - KeyEvent.KEYCODE_NUMPAD_0));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL) { press("⌫"); return true; }
        if (code == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(code, e);
    }

    private StateListDrawable keyBg() {
        GradientDrawable n = new GradientDrawable();
        n.setCornerRadius(dp(12));
        n.setColor(0x22FFFFFF);
        GradientDrawable f = new GradientDrawable();
        f.setCornerRadius(dp(12));
        f.setColor(0xFF3D8BFF);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, f);
        s.addState(new int[] {}, n);
        return s;
    }

    private TextView t(String s, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
