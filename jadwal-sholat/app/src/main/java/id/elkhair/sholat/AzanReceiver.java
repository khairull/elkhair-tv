package id.elkhair.sholat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarm waktu sholat, boot, dan perubahan jam/zona: putar azan bila perlu, lalu jadwalkan berikutnya. */
public class AzanReceiver extends BroadcastReceiver {
    static final String ACTION_AZAN = "id.elkhair.sholat.AZAN";
    static final String ACTION_NOTICE = "id.elkhair.sholat.NOTICE";

    @Override
    public void onReceive(Context c, Intent intent) {
        String act = intent.getAction();
        if (ACTION_AZAN.equals(act) || ACTION_NOTICE.equals(act)) {
            long fireAt = intent.getLongExtra("fire_at", 0);
            // Alarm basi: jam TV melompat (mati lampu / sinkron waktu) sehingga alarm untuk waktu lain langsung berbunyi.
            if (!Azan.clockValid() || (fireAt > 0 && Math.abs(System.currentTimeMillis() - fireAt) > 3 * 60_000L)) {
                android.util.Log.w("Sholat", "alarm basi diabaikan: " + act + " idx=" + intent.getIntExtra("idx", -1));
                Azan.scheduleNext(c);
                return;
            }
        }
        if (ACTION_NOTICE.equals(intent.getAction()) && Azan.notices(c)) {
            try {
                c.startForegroundService(new Intent(c, NextWidgetService.class).setAction(NextWidgetService.ACTION_NOTICE)
                        .putExtra("idx", intent.getIntExtra("idx", 0)));
            } catch (Exception ignored) {}
        }
        if (ACTION_AZAN.equals(intent.getAction()) && Azan.enabled(c)) {
            Intent s = new Intent(c, AzanService.class).putExtra("idx", intent.getIntExtra("idx", 4));
            c.startForegroundService(s);
        }
        Azan.scheduleNext(c);
        try { c.startForegroundService(new Intent(c, NextWidgetService.class)); } catch (Exception ignored) {}
    }
}
