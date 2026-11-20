package com.andyhub.dns_switch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

final class DnsSettings {
    static final String MODE_KEY = "private_dns_mode";
    static final String SPECIFIER_KEY = "private_dns_specifier";
    static final String MODE_AUTOMATIC = "opportunistic";
    static final String MODE_CUSTOM = "hostname";
    static final String ACTION_EXTEND_DNS = "com.andyhub.dns_switch.EXTEND_DNS";
    static final String PREFS_NAME = "dns_restore";
    static final String PREF_HOSTNAME = "hostname";
    static final String PREF_DEADLINE = "deadline";

    private static final String TAG = "DnsSettings";
    private static final int RESTORE_REQUEST_CODE = 1;
    private static final int END_REQUEST_CODE = 2;

    private DnsSettings() {
    }

    static String getMode(Context context) {
        return Settings.Global.getString(context.getContentResolver(), MODE_KEY);
    }

    static String getHostname(Context context) {
        return Settings.Global.getString(context.getContentResolver(), SPECIFIER_KEY);
    }

    static boolean setMode(Context context, String mode) {
        return Settings.Global.putString(context.getContentResolver(), MODE_KEY, mode);
    }

    static boolean setHostname(Context context, String hostname) {
        return Settings.Global.putString(context.getContentResolver(), SPECIFIER_KEY, hostname);
    }

    static PendingIntent restorePendingIntent(Context context) {
        Intent intent = new Intent(context, RestoreDnsReceiver.class)
                .setAction("com.andyhub.dns_switch.RESTORE_DNS");
        return receiverPendingIntent(context, RESTORE_REQUEST_CODE, intent);
    }

    static PendingIntent endPendingIntent(Context context) {
        Intent intent = new Intent(context, RestoreDnsReceiver.class)
                .setAction("com.andyhub.dns_switch.END_DNS");
        return receiverPendingIntent(context, END_REQUEST_CODE, intent);
    }

    static PendingIntent extendPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(ACTION_EXTEND_DNS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                3,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent receiverPendingIntent(Context context, int requestCode, Intent intent) {
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static void scheduleRestore(Context context, long deadline) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        alarmManager.cancel(restorePendingIntent(context));
        alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadline,
                restorePendingIntent(context)
        );
    }

    static void cancelRestore(Context context) {
        context.getSystemService(AlarmManager.class).cancel(restorePendingIntent(context));
    }

    static void logRestoreFailure(String message, Exception exception) {
        Log.e(TAG, message, exception);
    }
}
