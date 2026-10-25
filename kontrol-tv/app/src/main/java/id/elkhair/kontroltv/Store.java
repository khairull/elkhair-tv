package id.elkhair.kontroltv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Penyimpanan: daftar kunci, buka-sementara, PIN (hash), token bot (terenkripsi Keystore), pemilik bot. */
final class Store {
    private static final String ALIAS = "kontroltv-token";
    private Store() {}

    static SharedPreferences p(Context c) { return c.getSharedPreferences("kontrol", 0); }

    // ---- kunci ----
    static Set<String> locked(Context c) { return new HashSet<>(p(c).getStringSet("locked", new HashSet<>())); }

    static void setLocked(Context c, String pkg, boolean on) {
        Set<String> s = locked(c);
        if (on) s.add(pkg); else s.remove(pkg);
        p(c).edit().putStringSet("locked", s).remove("until_" + pkg).apply();
    }

    static void unlockFor(Context c, String pkg, int minutes) {
        p(c).edit().putLong("until_" + pkg, System.currentTimeMillis() + minutes * 60_000L).apply();
    }

    static long unlockedUntil(Context c, String pkg) { return p(c).getLong("until_" + pkg, 0); }

    /** Tidak pernah ikut "kunci semua"/mode sholat. */
    static boolean exempt(Context c, String pkg) {
        return pkg.equals(c.getPackageName()) || pkg.equals("id.elkhair.sholat") || pkg.equals("com.gathin.tvhome")
                || pkg.equals(Apps.SETTINGS) || pkg.contains("packageinstaller") || pkg.equals("com.android.systemui");
    }

    static boolean isBlocked(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        boolean global = !exempt(c, pkg) && (allLocked(c) || prayerModeActive(c));
        boolean reason = global || locked(c).contains(pkg) || inSchedule(c, pkg) || limitHit(c, pkg);
        return reason && unlockedUntil(c, pkg) < System.currentTimeMillis();
    }

    // ---- kunci semua ----
    static void lockAll(Context c, int minutes) {
        p(c).edit().putLong("all_until", minutes <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + minutes * 60_000L).apply();
    }
    static void unlockAll(Context c) { p(c).edit().remove("all_until").apply(); }
    static long allUntil(Context c) { return p(c).getLong("all_until", 0); }
    static boolean allLocked(Context c) { return allUntil(c) > System.currentTimeMillis(); }

    // ---- mode sholat: kunci aplikasi hiburan N menit setelah tiap waktu sholat wajib ----
    static volatile long[] prayerTimes = new long[0];
    static int prayerMode(Context c) { return p(c).getInt("pmode", 0); }
    static boolean prayerModeActive(Context c) {
        int n = prayerMode(c);
        if (n <= 0) return false;
        long now = System.currentTimeMillis();
        for (long at : prayerTimes) if (now >= at && now < at + n * 60_000L) return true;
        return false;
    }

    // ---- admin tambahan ----
    static Set<String> admins(Context c) { return new HashSet<>(p(c).getStringSet("admins", new HashSet<>())); }
    static boolean isAdmin(Context c, long id) { return id != 0 && (id == owner(c) || admins(c).contains(String.valueOf(id))); }
    static void setAdmin(Context c, long id, boolean on) {
        Set<String> s = admins(c);
        if (on) s.add(String.valueOf(id)); else s.remove(String.valueOf(id));
        p(c).edit().putStringSet("admins", s).apply();
    }

    // ---- alias pengirim (tampil di layar TV) ----
    static String alias(Context c, long id, String fallback) {
        String a = p(c).getString("alias_" + id, null);
        if (a != null && !a.isEmpty()) return a;
        if (id != 0 && id == owner(c)) return "Ayah";
        return fallback == null ? "" : fallback;
    }
    static void setAlias(Context c, long id, String name) {
        if (name == null || name.isEmpty()) p(c).edit().remove("alias_" + id).apply();
        else p(c).edit().putString("alias_" + id, name).apply();
    }

    // ---- log aktivitas (60 terakhir) ----
    static synchronized void log(Context c, String text) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(p(c).getString("log", "[]"));
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ROOT);
            f.setTimeZone(tz());
            a.put(f.format(new java.util.Date()) + "  " + text);
            while (a.length() > 60) a.remove(0);
            p(c).edit().putString("log", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    static java.util.List<String> logs(Context c, int n) {
        java.util.List<String> l = new java.util.ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(p(c).getString("log", "[]"));
            for (int i = Math.max(0, a.length() - n); i < a.length(); i++) l.add(a.getString(i));
        } catch (Exception ignored) {}
        return l;
    }

    // ---- jadwal kunci harian: "pkg|HH:MM|HH:MM" ----
    static java.util.TimeZone tz() { return java.util.TimeZone.getTimeZone("Asia/Makassar"); }

    static int nowMinute() {
        java.util.Calendar cal = java.util.Calendar.getInstance(tz());
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
    }

    static String today() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
        f.setTimeZone(tz());
        return f.format(new java.util.Date());
    }

    static Set<String> schedules(Context c) { return new HashSet<>(p(c).getStringSet("sched", new HashSet<>())); }

    static void setSchedule(Context c, String pkg, int from, int to) {
        Set<String> s = schedules(c);
        s.removeIf(e -> e.startsWith(pkg + "|"));
        if (from >= 0) s.add(pkg + "|" + from + "|" + to);
        p(c).edit().putStringSet("sched", s).apply();
    }

    static boolean inSchedule(Context c, String pkg) {
        int now = nowMinute();
        for (String e : schedules(c)) {
            String[] a = e.split("\\|");
            if (a.length != 3 || !a[0].equals(pkg)) continue;
            int f = Integer.parseInt(a[1]), t = Integer.parseInt(a[2]);
            if (f <= t ? (now >= f && now < t) : (now >= f || now < t)) return true;
        }
        return false;
    }

    // ---- batas waktu harian (menit) ----
    static Set<String> limited(Context c) { return new HashSet<>(p(c).getStringSet("limits", new HashSet<>())); }
    static int limit(Context c, String pkg) { return p(c).getInt("limit_" + pkg, 0); }

    static void setLimit(Context c, String pkg, int minutes) {
        Set<String> s = limited(c);
        if (minutes > 0) s.add(pkg); else s.remove(pkg);
        p(c).edit().putStringSet("limits", s).putInt("limit_" + pkg, minutes).remove("hit_" + pkg).apply();
    }

    static boolean limitHit(Context c, String pkg) { return today().equals(p(c).getString("hit_" + pkg, "")); }
    static void markLimitHit(Context c, String pkg) { p(c).edit().putString("hit_" + pkg, today()).apply(); }

    // ---- akses layar pengaturan ----
    static final String SETUP = "__setup__";
    static boolean configured(Context c) { return token(c) != null && owner(c) != 0; }
    static void allowSetup(Context c, int minutes) { p(c).edit().putLong("setup_until", System.currentTimeMillis() + minutes * 60_000L).apply(); }
    static boolean setupAllowed(Context c) { return !configured(c) || p(c).getLong("setup_until", 0) > System.currentTimeMillis(); }

    // ---- PIN ----
    static boolean hasPin(Context c) { return p(c).contains("pin"); }

    static void setPin(Context c, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = Base64.encodeToString(salt, Base64.NO_WRAP);
        p(c).edit().putString("pin_salt", s).putString("pin", hash(s, pin)).apply();
    }

    static boolean checkPin(Context c, String pin) {
        String s = p(c).getString("pin_salt", null), h = p(c).getString("pin", null);
        return s != null && h != null && MessageDigest.isEqual(h.getBytes(), hash(s, pin).getBytes());
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = (salt + ":" + pin).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 20000; i++) d = md.digest(d);
            return Base64.encodeToString(d, Base64.NO_WRAP);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- bot ----
    static long owner(Context c) { return p(c).getLong("owner", 0); }
    static void setOwner(Context c, long id) { p(c).edit().putLong("owner", id).remove("pair").remove("admins").apply(); }

    static String pairCode(Context c) {
        String code = p(c).getString("pair", null);
        if (code == null) {
            code = String.format(java.util.Locale.ROOT, "%06d", new SecureRandom().nextInt(1_000_000));
            p(c).edit().putString("pair", code).apply();
        }
        return code;
    }

    static void setToken(Context c, String token) {
        try {
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.ENCRYPT_MODE, key());
            byte[] enc = ci.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));
            p(c).edit().putString("tok", Base64.encodeToString(enc, Base64.NO_WRAP))
                    .putString("tok_iv", Base64.encodeToString(ci.getIV(), Base64.NO_WRAP))
                    .remove("owner").remove("pair").remove("offset").apply();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String token(Context c) {
        String t = p(c).getString("tok", null), iv = p(c).getString("tok_iv", null);
        if (t == null || iv == null) return null;
        try {
            Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
            ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, 0)));
            return new String(ci.doFinal(Base64.decode(t, 0)), StandardCharsets.UTF_8);
        } catch (Exception e) { return null; }
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
}
