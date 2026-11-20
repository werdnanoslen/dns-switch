package com.andyhub.dns_switch;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

final class DnsNotification {
    private static final String CHANNEL_ID = "temporary_dns";
    private static final int NOTIFICATION_ID = 1;

    private DnsNotification() {
    }

    static void show(Context context, boolean restoreFailed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        createChannel(context);
        SharedPreferences prefs = context.getSharedPreferences(
                DnsSettings.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        long deadline = prefs.getLong(DnsSettings.PREF_DEADLINE, 0L);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                        0,
                        context.getString(R.string.notification_extend_action),
                        DnsSettings.extendPendingIntent(context)
                )
                .addAction(
                        0,
                        context.getString(R.string.notification_end_action),
                        DnsSettings.endPendingIntent(context)
                );
        if (restoreFailed) {
            notification.setContentText(context.getString(R.string.notification_restore_failed));
        } else if (deadline > 0L) {
            // The restore alarm can be late; only the receiver should dismiss this notification.
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (!DateUtils.isToday(deadline)) {
                flags |= DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH;
            }
            String restoreTime = DateUtils.formatDateTime(context, deadline, flags);
            notification.setContentText(context.getString(R.string.notification_restore_scheduled, restoreTime));
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build());
    }

    static void cancel(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(context.getString(R.string.notification_channel_description));
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
}
