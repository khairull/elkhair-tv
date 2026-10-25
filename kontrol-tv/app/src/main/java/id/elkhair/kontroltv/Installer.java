package id.elkhair.kontroltv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Unduh, periksa, dan pasang/hapus APK. Pemasangan selalu lewat dialog konfirmasi Android di TV. */
final class Installer {
    static final long MAX_URL_BYTES = 300L * 1024 * 1024;
    private Installer() {}

    static File dir(Context c) { File d = new File(c.getCacheDir(), "apk"); d.mkdirs(); return d; }

    static File download(String url, File out, long max) throws Exception {
        URL u = new URL(url);
        for (int hop = 0; hop < 5; hop++) {
            if (!"https".equalsIgnoreCase(u.getProtocol())) throw new Exception("hanya link https yang diterima");
            HttpURLConnection h = (HttpURLConnection) u.openConnection();
            h.setInstanceFollowRedirects(false);
            h.setConnectTimeout(15000);
            h.setReadTimeout(60000);
            int code = h.getResponseCode();
            if (code >= 300 && code < 400 && h.getHeaderField("Location") != null) {
                u = new URL(u, h.getHeaderField("Location"));
                h.disconnect();
                continue;
            }
            if (code != 200) throw new Exception("HTTP " + code);
            long len = h.getContentLengthLong();
            if (len > max) throw new Exception("file terlalu besar");
            try (InputStream in = h.getInputStream(); OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                long total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > max) throw new Exception("file terlalu besar");
                    o.write(buf, 0, n);
                }
            } finally { h.disconnect(); }
            return out;
        }
        throw new Exception("terlalu banyak redirect");
    }

    /** Ringkasan APK untuk diperiksa pemilik; null bila bukan APK yang valid. */
    static String describe(Context c, File f) throws Exception {
        PackageManager pm = c.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(f.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (pi == null || pi.applicationInfo == null) return null;
        pi.applicationInfo.sourceDir = f.getPath();
        pi.applicationInfo.publicSourceDir = f.getPath();
        String label = pi.applicationInfo.loadLabel(pm).toString();
        String signer = signer(pi);
        StringBuilder sb = new StringBuilder("📦 *").append(Bot.md(label)).append("*\n")
                .append("Paket: `").append(pi.packageName).append("`\n")
                .append("Versi: ").append(Bot.md(String.valueOf(pi.versionName))).append(" (").append(pi.getLongVersionCode()).append(")\n")
                .append("Ukuran: ").append(String.format(Locale.ROOT, "%.1f", f.length() / 1048576.0)).append(" MB\n")
                .append("SHA-256 file: `").append(sha256(f)).append("`\n")
                .append("Penanda tangan: `").append(signer).append("`\n");
        try {
            PackageInfo cur = pm.getPackageInfo(pi.packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            sb.append("\nSudah terpasang versi ").append(Bot.md(String.valueOf(cur.versionName)));
            sb.append(signer.equals(signer(cur)) ? " — tanda tangan *sama* (update)." : " — ⚠️ tanda tangan *BERBEDA*: Android akan menolak update ini.");
            if (cur.getLongVersionCode() > pi.getLongVersionCode()) sb.append("\n⚠️ Versi di TV lebih baru; Android menolak downgrade.");
        } catch (PackageManager.NameNotFoundException e) {
            sb.append("\nBelum terpasang di TV.");
        }
        return sb.toString();
    }

    static String packageOf(Context c, File f) {
        PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
        return pi == null ? null : pi.packageName;
    }

    private static String signer(PackageInfo pi) {
        try {
            Signature[] s = pi.signingInfo.hasMultipleSigners() ? pi.signingInfo.getApkContentsSigners() : pi.signingInfo.getSigningCertificateHistory();
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s[s.length - 1].toByteArray());
            return hex(d).substring(0, 32) + "…";
        } catch (Exception e) { return "?"; }
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        return hex(md.digest()).substring(0, 32) + "…";
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    static void install(Context c, File f) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setSize(f.length());
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(f); OutputStream o = s.openWrite("base.apk", 0, f.length())) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
                s.fsync(o);
            }
            s.commit(result(c, "install", f.getName()));
        }
    }

    static void uninstall(Context c, String pkg) {
        c.getPackageManager().getPackageInstaller().uninstall(pkg, result(c, "uninstall", pkg));
    }

    private static android.content.IntentSender result(Context c, String op, String arg) {
        Intent i = new Intent(c, InstallReceiver.class).putExtra("op", op).putExtra("arg", arg);
        return PendingIntent.getBroadcast(c, (op + arg).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE).getIntentSender();
    }
}
