package io.nulkast.wall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

/**
 * Automatically resumes NulKast Wall VpnService after device reboot if previously active.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (WallStateStore.wasVpnEnabled(context) && VpnService.prepare(context) == null) {
            Intent svc = new Intent(context, NulKastVpnService.class);
            svc.setAction(NulKastVpnService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
    }
}
