package id.elkhair.kontroltv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Aplikasi yang bisa dibuka pengguna (tile di launcher), plus Setelan. */
final class Apps {
    static final String SETTINGS = "com.android.tv.settings";
    private Apps() {}

    static final class App { final String pkg, label; App(String p, String l) { pkg = p; label = l; } }

    static List<App> list(Context c) {
        PackageManager pm = c.getPackageManager();
        TreeMap<String, App> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(c.getPackageName())) continue;
                String l = ri.loadLabel(pm).toString();
                boolean dup = false;
                for (App a : m.values()) if (a.pkg.equals(p)) { dup = true; break; }
                if (!dup) m.put(l + "|" + p, new App(p, l));
            }
        }
        List<App> out = new ArrayList<>(m.values());
        boolean hasSettings = false;
        for (App a : out) if (a.pkg.equals(SETTINGS)) hasSettings = true;
        if (!hasSettings) out.add(new App(SETTINGS, "Setelan"));
        return out;
    }

    static String label(Context c, String pkg) {
        if (SETTINGS.equals(pkg)) return "Setelan";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    /** Cari aplikasi dari nama (atau paket): sama persis dulu, lalu mengandung. */
    static List<App> find(Context c, String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        List<App> exact = new ArrayList<>(), part = new ArrayList<>();
        for (App a : list(c)) {
            String l = a.label.toLowerCase(Locale.ROOT);
            if (l.equals(q) || a.pkg.equalsIgnoreCase(q)) exact.add(a);
            else if (l.contains(q) || a.pkg.toLowerCase(Locale.ROOT).contains(q)) part.add(a);
        }
        return exact.isEmpty() ? part : exact;
    }

    static List<String> sorted(java.util.Collection<String> s) { List<String> l = new ArrayList<>(s); Collections.sort(l); return l; }
}
