package id.elkhair.kontroltv;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Pemeriksa izin. Dengan WRITE_SECURE_SETTINGS (diberikan sekali lewat ADB, bertahan saat update) aplikasi
 * memulihkan sendiri: penjaga aksesibilitas, akses media launcher, dan screensaver Jam & Sholat.
 * Izin appops (overlay, akses pemakaian, pasang APK) tidak bisa diubah aplikasi; hanya dilaporkan.
 */
final class Doctor {
    static final String LAUNCHER_LISTENER = "com.gathin.tvhome/com.gathin.tvhome.MediaListener";
    static final String DREAM = "id.elkhair.sholat/id.elkhair.sholat.SholatDream";
    private Doctor() {}

    static boolean canWriteSecure(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Pulihkan yang bisa; kembalikan daftar yang dipulihkan. */
    static List<String> heal(Context c) {
        List<String> fixed = new ArrayList<>();
        if (!canWriteSecure(c)) return fixed;
        try {
            String me = c.getPackageName() + "/" + GuardService.class.getName();
            if (addToList(c, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, me, c.getPackageName() + "/")) fixed.add("penjaga kunci (aksesibilitas)");
            if (!"1".equals(Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED))) {
                Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            }
            if (installed(c, "com.gathin.tvhome")
                    && addToList(c, "enabled_notification_listeners", LAUNCHER_LISTENER, "com.gathin.tvhome/")) fixed.add("akses media launcher");
            if (installed(c, "id.elkhair.sholat")) {
                String cur = Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
                if (cur == null || cur.isEmpty() || cur.startsWith("com.google.android.backdrop") || cur.startsWith("com.android.dreams.basic")) {
                    Settings.Secure.putString(c.getContentResolver(), "screensaver_components", DREAM);
                    fixed.add("screensaver Jam & Sholat");
                }
            }
        } catch (Exception ignored) {}
        return fixed;
    }

    /** Tambahkan komponen ke daftar "a:b:c" bila belum ada; true bila diubah. */
    private static boolean addToList(Context c, String key, String comp, String prefix) {
        String cur = Settings.Secure.getString(c.getContentResolver(), key);
        if (cur != null) for (String e : cur.split(":")) if (e.startsWith(prefix) || e.equals(comp)) return false;
        String nv = cur == null || cur.isEmpty() || cur.equals("null") ? comp : cur + ":" + comp;
        Settings.Secure.putString(c.getContentResolver(), key, nv);
        return true;
    }

    private static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    /** Izin yang hilang dan tidak bisa dipulihkan sendiri. */
    static List<String> missing(Context c) {
        List<String> m = new ArrayList<>();
        if (!Settings.canDrawOverlays(c)) m.add("Tampil di atas aplikasi (pesan, YouTube, pengaturan)");
        AppOpsManager ao = c.getSystemService(AppOpsManager.class);
        if (ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName()) != AppOpsManager.MODE_ALLOWED)
            m.add("Akses pemakaian (laporan, batas waktu)");
        if (!c.getPackageManager().canRequestPackageInstalls()) m.add("Pasang aplikasi (APK lewat bot)");
        if (!canWriteSecure(c)) m.add("Tulis setelan sistem (pemulihan otomatis)");
        if (!BotService.guardEnabled(c) && !canWriteSecure(c)) m.add("Penjaga kunci (aksesibilitas)");
        return m;
    }

    static String report(Context c) {
        List<String> fixed = heal(c), miss = missing(c);
        StringBuilder sb = new StringBuilder("🩺 *Pemeriksaan izin*\n");
        if (!fixed.isEmpty()) sb.append("♻️ Dipulihkan: ").append(String.join(", ", fixed)).append('\n');
        if (miss.isEmpty()) sb.append("✅ Semua izin lengkap.");
        else {
            sb.append("⚠️ Perlu diaktifkan lewat ADB/Setelan:\n");
            for (String s : miss) sb.append("• ").append(s).append('\n');
        }
        return sb.toString();
    }
}
