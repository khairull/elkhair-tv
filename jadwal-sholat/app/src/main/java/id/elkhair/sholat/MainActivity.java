package id.elkhair.sholat;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Jadwal hari ini (Imsak s.d. Isya) untuk lokasi terpilih. OK = pengaturan. */
public class MainActivity extends Activity {
    private static final String[] ARABIC = {"إمساك", "الفجر", "الشروق", "الضحى", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] ICONS = {R.drawable.ic_fajr, R.drawable.ic_fajr, R.drawable.ic_sunrise, R.drawable.ic_sunrise,
            R.drawable.ic_dhuhr, R.drawable.ic_asr, R.drawable.ic_maghrib, R.drawable.ic_isha};
    static final String[] HIJRI = {"Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal",
            "Jumadil Akhir", "Rajab", "Syakban", "Ramadan", "Syawal", "Zulkaidah", "Zulhijah"};
    static final Locale ID = new Locale("id", "ID");

    private final Handler main = new Handler(Looper.getMainLooper());
    private Typeface font;
    private int accent;
    private TextView place, dates, next, status;
    private LinearLayout rows;

    private final Runnable tick = new Runnable() {
        @Override public void run() { render(); main.postDelayed(this, 1000L); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        font = getResources().getFont(R.font.outfit);
        accent = getColor(R.color.accent);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {(accent & 0x00FFFFFF) | 0x55000000, 0xFF050607, 0xFF050607}));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(40), dp(56), dp(32));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        place = text(18, 400, 0xB3FFFFFF);
        place.setLetterSpacing(0.25f);
        col.addView(place);
        next = text(48, 300, 0xFFFFFFFF);
        next.setPadding(0, dp(8), 0, dp(4));
        col.addView(next);
        dates = text(19, 400, 0xB3FFFFFF);
        col.addView(dates);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, 0, 1f);
        rlp.setMargins(0, dp(30), 0, 0);
        col.addView(rows, rlp);
        status = text(15, 400, 0x80FFFFFF);
        status.setPadding(0, dp(16), 0, 0);
        col.addView(status);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Azan.scheduleNext(this);
        startForegroundService(new Intent(this, NextWidgetService.class));
        render();
        main.postDelayed(tick, 1000L);
    }

    @Override protected void onPause() { super.onPause(); main.removeCallbacks(tick); }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_MENU) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private void render() {
        Calendar c = PrayerCalc.today(this);
        int[] minutes = PrayerCalc.minutes(this, c);
        long[] n = PrayerCalc.next(this);
        int nextIdx = (int) n[0];
        boolean today = n[2] - System.currentTimeMillis() < 86_400_000L && sameDay(c, n[2]);
        place.setText((PrayerCalc.place(this) + " · " + PrayerCalc.zoneLabel(this)).toUpperCase(Locale.ROOT));
        next.setText(PrayerCalc.NAMES[nextIdx] + (today ? "" : " besok") + " · " + PrayerCalc.countdown(n[2] - System.currentTimeMillis()));
        SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM yyyy", ID);
        df.setTimeZone(PrayerCalc.zone(this));
        dates.setText(df.format(new Date()) + "  ·  " + hijri());

        rows.removeAllViews();
        for (int i = 0; i < minutes.length; i++) {
            boolean hl = today && i == nextIdx;
            boolean prayer = PrayerCalc.isPrayer(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(4), dp(16), dp(4), dp(16));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            bg.setColor(hl ? accent : prayer ? 0x18FFFFFF : 0x0CFFFFFF);
            card.setBackground(bg);
            int dim = prayer ? 0xFFFFFFFF : 0x99FFFFFF;
            int fg = hl ? 0xFFFFFFFF : dim;
            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(hl ? 0xFFFFFFFF : prayer ? accent : 0x80FFFFFF));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(36), dp(36));
            ilp.bottomMargin = dp(16);
            card.addView(icon, ilp);
            TextView ar = centered(text(22, 400, fg));
            ar.setText(ARABIC[i]);
            card.addView(ar);
            TextView name = centered(text(18, 500, hl ? 0xE6FFFFFF : prayer ? 0xB3FFFFFF : 0x80FFFFFF));
            name.setText(PrayerCalc.NAMES[i]);
            name.setPadding(0, dp(6), 0, dp(14));
            card.addView(name);
            TextView time = centered(text(34, hl ? 500 : 300, fg));
            time.setText(PrayerCalc.hhmm(minutes[i]));
            time.setMaxLines(1);
            time.setAutoSizeTextTypeUniformWithConfiguration(16, 34, 1, TypedValue.COMPLEX_UNIT_SP);
            card.addView(time, new LinearLayout.LayoutParams(-1, dp(44)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.setMargins(i == 0 ? 0 : dp(5), 0, i == minutes.length - 1 ? 0 : dp(5), 0);
            rows.addView(card, lp);
        }
        int lead = Azan.lead(this);
        status.setText("Kemenag RI · Imsak −10 mnt · Azan " + (Azan.enabled(this) ? (lead == 0 ? "tepat waktu" : lead + " mnt sebelumnya") : "NONAKTIF") + "  ·  OK = Pengaturan");
    }

    private boolean sameDay(Calendar c, long t) {
        Calendar o = (Calendar) c.clone();
        o.setTimeInMillis(t);
        return o.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
    }

    static String hijri() {
        IslamicCalendar h = new IslamicCalendar(ULocale.forLocale(ID));
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.setTimeInMillis(System.currentTimeMillis());
        return h.get(IslamicCalendar.DAY_OF_MONTH) + " " + HIJRI[h.get(IslamicCalendar.MONTH)] + " " + h.get(IslamicCalendar.YEAR) + " H";
    }

    private static TextView centered(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        return t;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, weight, false) : font);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
