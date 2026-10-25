package id.elkhair.kontroltv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Layar pengaturan di TV: status penjaga, token bot, kode pairing. Mengganti token butuh PIN bila PIN sudah diatur. */
public class SetupActivity extends Activity {
    private TextView guard, bot, pair;
    private EditText token, pin;
    private View row, acc, gate;
    private TextView gateMsg;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable poll = new Runnable() { @Override public void run() { refresh(); main.postDelayed(this, 2000); } };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(80), dp(40), dp(80), dp(30));
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF0B1A33, 0xFF04070D}));
        col.addView(t("KONTROL TV", 16, 0xB3FFFFFF));
        col.addView(t("Kunci aplikasi & kendali lewat Telegram", 30, 0xFFFFFFFF));
        guard = t("", 20, 0xFFFFFFFF);
        guard.setPadding(0, dp(18), 0, dp(4));
        col.addView(guard);
        Button accB = new Button(this);
        acc = accB;
        accB.setText("Buka setelan Aksesibilitas");
        accB.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        col.addView(accB, new LinearLayout.LayoutParams(-2, -2));
        bot = t("", 20, 0xFFFFFFFF);
        bot.setPadding(0, dp(18), 0, dp(4));
        col.addView(bot);
        pair = t("", 26, 0xFF7FB4FF);
        col.addView(pair);
        LinearLayout row = new LinearLayout(this);
        this.row = row;
        row.setOrientation(LinearLayout.HORIZONTAL);
        token = new EditText(this);
        token.setHint("Token bot dari @BotFather");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        style(token);
        token.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        token.setOnEditorActionListener((v, id, ev) -> { save(); return true; });
        row.addView(token, new LinearLayout.LayoutParams(0, -2, 3f));
        pin = new EditText(this);
        pin.setHint("PIN");
        pin.setSingleLine(true);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        style(pin);
        row.addView(pin, new LinearLayout.LayoutParams(0, -2, 1f));
        Button save = new Button(this);
        save.setText("Simpan token");
        save.setOnClickListener(v -> save());
        row.addView(save, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = dp(10);
        col.addView(row, rl);
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        gateMsg = t("🔒 Pengaturan dikunci. Buka dengan PIN atau minta izin pemilik lewat Telegram.", 20, 0xFFFFD27F);
        g.addView(gateMsg);
        LinearLayout gb = new LinearLayout(this);
        Button byPin = new Button(this);
        byPin.setText("Masukkan PIN");
        byPin.setOnClickListener(v -> startActivity(new Intent(this, BlockActivity.class).putExtra("pkg", Store.SETUP)));
        gb.addView(byPin);
        Button byTg = new Button(this);
        byTg.setText("Minta izin lewat Telegram");
        byTg.setOnClickListener(v -> { Bot.requestSetup(this); gateMsg.setText("⏳ Permintaan terkirim ke pemilik. Menunggu persetujuan…"); });
        gb.addView(byTg);
        g.addView(gb);
        gate = g;
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = dp(16);
        col.addView(g, gl);
        col.addView(t("Token disimpan terenkripsi di TV. Hanya akun Telegram yang mengirim /pair dengan kode di atas yang bisa memerintah.", 14, 0x80FFFFFF));
        setContentView(col);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { startForegroundService(new Intent(this, BotService.class)); } catch (Exception ignored) {}
        refresh();
        if (Store.token(this) == null) token.requestFocus();
        long last = Store.p(this).getLong("setup_seen", 0);
        if (Store.configured(this) && System.currentTimeMillis() - last > 60_000L) {
            Store.p(this).edit().putLong("setup_seen", System.currentTimeMillis()).apply();
            BotService.notifyOwner(this, "👀 Aplikasi Kontrol TV dibuka di TV.");
        }
        main.postDelayed(poll, 2000);
    }

    @Override
    protected void onPause() { super.onPause(); main.removeCallbacks(poll); }

    private void refresh() {
        boolean open = Store.setupAllowed(this);
        row.setVisibility(open ? View.VISIBLE : View.GONE);
        acc.setVisibility(open ? View.VISIBLE : View.GONE);
        gate.setVisibility(open ? View.GONE : View.VISIBLE);
        guard.setText(BotService.guardEnabled(this) ? "Penjaga kunci: ✅ aktif" : "Penjaga kunci: ⚠️ belum aktif — aktifkan \"Kontrol TV\" di Aksesibilitas");
        boolean hasToken = Store.token(this) != null;
        long owner = Store.owner(this);
        bot.setText(!hasToken ? "Bot Telegram: belum ada token" : owner != 0 ? "Bot Telegram: ✅ terhubung ke pemilik" : "Bot Telegram: token tersimpan, menunggu pairing");
        pair.setText(hasToken && owner == 0 ? "Kirim ke bot:  /pair " + Store.pairCode(this) : "");
        pin.setVisibility(hasToken && Store.hasPin(this) ? View.VISIBLE : View.GONE);
    }

    private void save() {
        if (!Store.setupAllowed(this)) { refresh(); return; }
        String raw = token.getText().toString().trim();
        String[] parts = raw.split("[\\s,;|]+");
        String tk = parts[0];
        long owner = 0;
        if (parts.length > 1 && parts[1].matches("-?\\d{5,15}")) owner = Long.parseLong(parts[1]);
        if (!tk.matches("\\d+:[A-Za-z0-9_-]{30,}")) { token.setText(""); token.setHint("Format token tidak valid"); return; }
        if (Store.token(this) != null && Store.hasPin(this) && !Store.checkPin(this, pin.getText().toString())) {
            pin.setText(""); pin.setHint("PIN salah"); return;
        }
        Store.setToken(this, tk);
        if (owner != 0) {
            Store.setOwner(this, owner);
            BotService.notifyOwner(this, "✅ Kontrol TV terhubung ke akun ini.\n\n" + Bot.HELP);
        }
        token.setText("");
        pin.setText("");
        refresh();
    }

    private void style(EditText e) {
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0x66FFFFFF);
    }

    private TextView t(String s, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        return v;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
