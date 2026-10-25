package id.elkhair.kontroltv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;

/** Hasil pemasangan/penghapusan: tampilkan dialog konfirmasi di TV, lalu laporkan ke pemilik. */
public class InstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String op = i.getStringExtra("op"), arg = i.getStringExtra("arg");
        String pkg = i.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        boolean install = "install".equals(op);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Exception e) {
                    BotService.notifyOwner(c, "⚠️ Dialog konfirmasi tidak bisa ditampilkan di TV: " + Bot.md(e.getMessage()));
                }
            }
            return;
        }
        String name = pkg != null ? Apps.label(c, pkg) : (install ? "aplikasi" : arg);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            BotService.notifyOwner(c, install ? "✅ *" + Bot.md(name) + "* berhasil dipasang." : "🗑 *" + Bot.md(arg) + "* berhasil dihapus.");
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            BotService.notifyOwner(c, install ? "✖️ Pemasangan dibatalkan di TV." : "✖️ Penghapusan dibatalkan di TV.");
        } else {
            String m = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            BotService.notifyOwner(c, (install ? "❌ Pemasangan gagal: " : "❌ Penghapusan gagal: ") + Bot.md(m == null ? "status " + status : m));
        }
        if (install && arg != null) new File(Installer.dir(c), arg).delete();
    }
}
