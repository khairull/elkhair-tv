package id.elkhair.kontroltv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) Store.p(c).edit().putBoolean("just_booted", true).apply();
        Doctor.heal(c);
        try { c.startForegroundService(new Intent(c, BotService.class)); } catch (Exception ignored) {}
    }
}
