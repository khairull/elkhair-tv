package id.elkhair.kontroltv;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/** Penjaga: aplikasi terkunci yang muncul di layar langsung dibawa ke Home, prosesnya dimatikan, layar blokir tampil. */
public class GuardService extends AccessibilityService {
    static volatile GuardService instance;
    static volatile String current = "";
    private static final java.util.Set<String> IGNORE = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.android.systemui", "android", "com.google.android.inputmethod.latin", "com.android.inputmethod.latin"));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastReport = new HashMap<>();

    @Override
    protected void onServiceConnected() {
        instance = this;
        try { startForegroundService(new Intent(this, BotService.class)); } catch (Exception ignored) {}
        if (TvLock.active(this)) TvLock.ensure(this);
    }

    /** Aktif/nonaktifkan penyaringan tombol remote (hanya saat Kunci TV). */
    static void lockKeys(boolean on) {
        GuardService g = instance;
        if (g == null) return;
        g.main.post(() -> {
            try {
                android.accessibilityservice.AccessibilityServiceInfo i = g.getServiceInfo();
                if (i == null) return;
                int f = i.flags;
                int nf = on ? f | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                            : f & ~android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                if (nf != f) { i.flags = nf; g.setServiceInfo(i); }
            } catch (Exception ignored) {}
        });
    }

    /** Saat Kunci TV: hanya tombol arah/OK/angka/BACK (untuk layar kunci) serta volume & power yang diteruskan. */
    @Override
    protected boolean onKeyEvent(android.view.KeyEvent e) {
        if (!TvLock.active(this)) return false;
        switch (e.getKeyCode()) {
            case android.view.KeyEvent.KEYCODE_DPAD_UP: case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER: case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_NUMPAD_ENTER: case android.view.KeyEvent.KEYCODE_BACK:
            case android.view.KeyEvent.KEYCODE_DEL:
            case android.view.KeyEvent.KEYCODE_VOLUME_UP: case android.view.KeyEvent.KEYCODE_VOLUME_DOWN:
            case android.view.KeyEvent.KEYCODE_VOLUME_MUTE: case android.view.KeyEvent.KEYCODE_POWER:
            case android.view.KeyEvent.KEYCODE_TV_POWER: case android.view.KeyEvent.KEYCODE_SLEEP:
            case android.view.KeyEvent.KEYCODE_WAKEUP:
                return false;
            default:
                int k = e.getKeyCode();
                if ((k >= android.view.KeyEvent.KEYCODE_0 && k <= android.view.KeyEvent.KEYCODE_9)
                        || (k >= android.view.KeyEvent.KEYCODE_NUMPAD_0 && k <= android.view.KeyEvent.KEYCODE_NUMPAD_9)) return false;
                return true;   // HOME, MENU, tombol YouTube/Netflix, input, dll. ditelan
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || IGNORE.contains(pkg)) return;
        String prev = current;
        current = pkg;
        if (TvLock.active(this)) {
            // aplikasi lolos terbuka saat TV dikunci: tutup, layar kunci tetap di atas
            if (!pkg.equals("com.gathin.tvhome") && !pkg.equals("id.elkhair.sholat")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                main.postDelayed(() -> kill(pkg), 1500);
                current = "";
            }
            TvLock.ensure(this);
            return;
        }
        if (Store.isBlocked(this, pkg)) { block(pkg, true); return; }
        if (!pkg.equals(prev) && Store.p(this).getBoolean("notif_app", false) && !Store.exempt(this, pkg) && isApp(pkg)) {
            long now = System.currentTimeMillis();
            Long last = lastReport.get("open_" + pkg);
            if (last == null || now - last > 10 * 60_000L) {
                lastReport.put("open_" + pkg, now);
                BotService.notifyOwner(this, "📱 *" + Bot.md(Apps.label(this, pkg)) + "* dibuka.");
                Store.log(this, "Dibuka: " + Apps.label(this, pkg));
            }
        }
    }

    /** Dipanggil bot setelah /kunci: tutup aplikasi bila sedang di layar. */
    static void enforceNow() {
        GuardService g = instance;
        if (g != null && Store.isBlocked(g, current)) g.main.post(() -> g.block(current, false));
    }

    /** Tutup aplikasi yang sedang dibuka (tanpa mengunci). */
    static boolean closeCurrent() {
        GuardService g = instance;
        if (g == null) return false;
        String pkg = current;
        g.main.post(() -> {
            g.performGlobalAction(GLOBAL_ACTION_HOME);
            g.main.postDelayed(() -> g.kill(pkg), 1500);
        });
        return true;
    }

    static boolean standby() {
        GuardService g = instance;
        if (g == null || Build.VERSION.SDK_INT < 28) return false;
        return g.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    private void block(String pkg, boolean attempt) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        main.postDelayed(() -> {
            try {
                startActivity(new Intent(this, BlockActivity.class).putExtra("pkg", pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (Exception ignored) {}
        }, 350);
        main.postDelayed(() -> kill(pkg), 1500);
        current = "";
        if (attempt) {
            long now = System.currentTimeMillis();
            Long last = lastReport.get(pkg);
            if (last == null || now - last > 5 * 60_000L) {
                lastReport.put(pkg, now);
                BotService.notifyOwner(this, "🔒 Percobaan membuka *" + Bot.md(Apps.label(this, pkg)) + "* diblokir.");
                Store.log(this, "Diblokir: " + Apps.label(this, pkg));
            }
        }
    }

    private boolean isApp(String pkg) {
        android.content.pm.PackageManager pm = getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    private void kill(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        try { getSystemService(ActivityManager.class).killBackgroundProcesses(pkg); } catch (Exception ignored) {}
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
