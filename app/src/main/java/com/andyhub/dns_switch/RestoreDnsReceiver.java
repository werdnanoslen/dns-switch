package com.andyhub.dns_switch;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.Manifest;

public class RestoreDnsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(DnsSettings.PREFS_NAME, Context.MODE_PRIVATE);
        String hostname = prefs.getString(DnsSettings.PREF_HOSTNAME, null);
        long deadline = prefs.getLong(DnsSettings.PREF_DEADLINE, 0L);

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (hostname != null && deadline > 0L) {
                if (deadline > System.currentTimeMillis()
                        && DnsSettings.MODE_AUTOMATIC.equals(DnsSettings.getMode(context))) {
                    DnsNotification.show(context, false);
                }
                DnsSettings.scheduleRestore(context, deadline);
            }
            return;
        }
        if (hostname == null || deadline == 0L) {
            return;
        }
        boolean endNow = "com.andyhub.dns_switch.END_DNS".equals(intent.getAction());
        if (!endNow && deadline > System.currentTimeMillis()) {
            DnsSettings.scheduleRestore(context, deadline);
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            DnsSettings.logRestoreFailure("WRITE_SECURE_SETTINGS permission is no longer granted.", null);
            DnsNotification.show(context, true);
            return;
        }

        try {
            if (DnsSettings.MODE_AUTOMATIC.equals(DnsSettings.getMode(context))) {
                boolean hostnameRestored = DnsSettings.setHostname(context, hostname);
                boolean modeRestored = hostnameRestored
                        && DnsSettings.setMode(context, DnsSettings.MODE_CUSTOM);
                if (!modeRestored) {
                    DnsSettings.logRestoreFailure("Could not restore the saved Private DNS hostname.", null);
                    DnsNotification.show(context, true);
                    return;
                }
            }
            DnsSettings.cancelRestore(context);
            prefs.edit().clear().apply();
            DnsNotification.cancel(context);
            closeAppTask(context);
        } catch (SecurityException exception) {
            DnsSettings.logRestoreFailure("Could not restore the saved Private DNS hostname.", exception);
            DnsNotification.show(context, true);
        }
    }

    private void closeAppTask(Context context) {
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
            task.finishAndRemoveTask();
        }
    }
}
