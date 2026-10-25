package id.elkhair.sholat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Azan 5 waktu: layar azan menutupi semua aplikasi dan tidak bisa ditutup sampai azan selesai. Hanya tombol volume
 * dan power yang tetap berfungsi. Pemutar lain dijeda lewat audio focus sementara, lalu aplikasi sebelumnya tampil lagi.
 */
public class AzanService extends Service {
    static volatile boolean playing;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private WindowManager wm;
    private View overlay;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int idx = intent != null ? intent.getIntExtra("idx", 4) : 4;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("azan", "Azan", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "azan").setSmallIcon(R.drawable.icon)
                .setContentTitle("Azan " + PrayerCalc.NAMES[idx]).setContentText(PrayerCalc.place(this)).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
        if (playing) return START_NOT_STICKY;

        wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sholat:azan");
        wake.acquire(12 * 60_000L);
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        audio = getSystemService(AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(f -> {}).build();
        audio.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            setSource(player, this, idx == PrayerCalc.SUBUH);
            player.setOnCompletionListener(mp -> finishAzan());
            player.setOnErrorListener((mp, w, e) -> { finishAzan(); return true; });
            player.prepare();
            player.start();
            playing = true;
        } catch (Exception e) {
            Log.w("Sholat", "azan: " + e);
            finishAzan();
            return START_NOT_STICKY;
        }
        showOverlay(idx);
        main.postDelayed(this::finishAzan, 11 * 60_000L);   // pengaman: berkas yang sangat panjang tidak mengunci TV
        return START_NOT_STICKY;
    }

    /** Berkas azan pilihan pengguna (content://) atau bawaan aplikasi. */
    static void setSource(MediaPlayer p, android.content.Context c, boolean subuh) throws Exception {
        String uri = PrayerCalc.prefs(c).getString(subuh ? "azan_subuh_uri" : "azan_uri", "");
        if (!uri.isEmpty()) {
            try { p.setDataSource(c, Uri.parse(uri)); return; } catch (Exception e) { p.reset(); }
        }
        int res = subuh ? c.getResources().getIdentifier("azan_subuh", "raw", c.getPackageName()) : 0;
        if (res == 0) res = c.getResources().getIdentifier("azan", "raw", c.getPackageName());
        if (res == 0) {   // tanpa berkas azan bawaan (mis. build dari repo publik): pakai nada alarm sistem
            p.setDataSource(c, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM));
            return;
        }
        try (AssetFileDescriptor fd = c.getResources().openRawResourceFd(res)) {
            p.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        }
    }

    private void showOverlay(int idx) {
        if (!Settings.canDrawOverlays(this)) return;
        wm = getSystemService(WindowManager.class);
        Typeface base = getResources().getFont(R.font.outfit);
        int accent = getColor(R.color.accent);
        LinearLayout col = new LinearLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
                    case KeyEvent.KEYCODE_POWER: case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_WAKEUP:
                    case KeyEvent.KEYCODE_TV_POWER:
                        return false;   // biarkan sistem menangani volume dan power
                    default:
                        return true;    // tombol lain (Back, OK, angka, ...) diabaikan selama azan
                }
            }
        };
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setFocusable(true);
        col.setFocusableInTouchMode(true);
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {(accent & 0x00FFFFFF) | 0x77000000, 0xFF050607}));
        col.addView(t(base, "اللّٰهُ أَكْبَرُ", 64, 0xFFFFFFFF, 400));
        col.addView(t(base, "Waktu Sholat " + PrayerCalc.NAMES[idx], 46, 0xFFFFFFFF, 600));
        int[] m = PrayerCalc.minutes(this, PrayerCalc.today(this));
        col.addView(t(base, PrayerCalc.hhmm(m[idx]) + " " + PrayerCalc.zoneLabel(this) + " · " + PrayerCalc.place(this), 24, 0xB3FFFFFF, 400));
        col.addView(t(base, "Layar kembali setelah azan selesai", 16, 0x80FFFFFF, 400));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE);
        try {
            wm.addView(col, lp);
            overlay = col;
            col.requestFocus();
        } catch (Exception e) {
            Log.w("Sholat", "overlay: " + e);
        }
    }

    private TextView t(Typeface base, String s, float sp, int color, int weight) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Build.VERSION.SDK_INT >= 28 ? Typeface.create(base, weight, false) : base);
        v.setPadding(0, 10, 0, 10);
        return v;
    }

    private void finishAzan() {
        main.removeCallbacksAndMessages(null);
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
        if (overlay != null) { try { wm.removeView(overlay); } catch (Exception ignored) {} overlay = null; }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        if (wake != null && wake.isHeld()) wake.release();
        playing = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (playing) finishAzan();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
