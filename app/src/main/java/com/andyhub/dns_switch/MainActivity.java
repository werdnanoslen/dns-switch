package com.andyhub.dns_switch;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final long MINUTE = 60_000L;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1;

    private final String[] durationLabels = {"1 minute", "10 minutes", "1 hour", "8 hours"};
    private final long[] durationMillis = {MINUTE, 10 * MINUTE, 60 * MINUTE, 8 * 60 * MINUTE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionInstructions();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        } else {
            handleLaunch();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            handleLaunch();
        } else {
            showError(getString(R.string.notification_permission_required));
        }
    }

    private void handleLaunch() {
        if (DnsSettings.ACTION_EXTEND_DNS.equals(getIntent().getAction())) {
            SharedPreferences prefs = getSharedPreferences(DnsSettings.PREFS_NAME, MODE_PRIVATE);
            String hostname = prefs.getString(DnsSettings.PREF_HOSTNAME, null);
            long deadline = prefs.getLong(DnsSettings.PREF_DEADLINE, 0L);
            if (hostname == null || deadline <= System.currentTimeMillis()) {
                showError(getString(R.string.extend_expired));
                return;
            }
            showDurationPicker(hostname, deadline);
            return;
        }
        switchToAutomatic();
    }

    private void showPermissionInstructions() {
        String command = "adb shell pm grant " + getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS";
        new AlertDialog.Builder(this)
                .setTitle(R.string.permission_title)
                .setMessage(getString(R.string.permission_message, command))
                .setPositiveButton(R.string.copy_command, (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", command));
                    Toast.makeText(this, R.string.command_copied, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void switchToAutomatic() {
        SharedPreferences prefs = getSharedPreferences(DnsSettings.PREFS_NAME, MODE_PRIVATE);
        String hostname = prefs.getString(DnsSettings.PREF_HOSTNAME, null);
        long deadline = prefs.getLong(DnsSettings.PREF_DEADLINE, 0L);
        boolean hasActiveTemporaryDns = hostname != null
                && deadline > System.currentTimeMillis();
        if (!hasActiveTemporaryDns) {
            hostname = DnsSettings.getHostname(this);
        }
        if (hostname == null || hostname.trim().isEmpty()) {
            showCustomDnsBlank();
            return;
        }

        if (!hasActiveTemporaryDns) {
            String mode = DnsSettings.getMode(this);
            if (DnsSettings.MODE_AUTOMATIC.equals(mode)) {
                showAlreadyAutomaticConfirmation(hostname);
                return;
            }
            if (!DnsSettings.MODE_CUSTOM.equals(mode)) {
                showSetCustomConfirmation(hostname);
                return;
            }
        }
        showDurationPicker(hostname, hasActiveTemporaryDns ? deadline : 0L);
    }

    private void showDurationPicker(String hostname, long existingDeadline) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.duration_title)
                .setItems(durationLabels, (dialog, which) -> setTimer(hostname, existingDeadline, which))
                .setOnCancelListener(dialog -> finishAndRemoveTask())
                .show();
    }

    private void setTimer(String hostname, long existingDeadline, int which) {
        SharedPreferences prefs = getSharedPreferences(DnsSettings.PREFS_NAME, MODE_PRIVATE);
        String previousHostname = prefs.getString(DnsSettings.PREF_HOSTNAME, null);
        long previousDeadline = prefs.getLong(DnsSettings.PREF_DEADLINE, 0L);
        long now = System.currentTimeMillis();
        boolean extending = existingDeadline > 0L;
        if (extending && (existingDeadline != previousDeadline || existingDeadline <= now
                || !hostname.equals(previousHostname)
                || !DnsSettings.MODE_AUTOMATIC.equals(DnsSettings.getMode(this)))) {
            showError(getString(R.string.extend_expired));
            return;
        }

        long deadline = now + durationMillis[which];
        if (!saveTimer(prefs, hostname, deadline).commit()) {
            saveTimer(prefs, previousHostname, previousDeadline).apply();
            showError(getString(R.string.save_failed_unchanged));
            return;
        }
        if (!extending) {
            try {
                if (!DnsSettings.setMode(this, DnsSettings.MODE_AUTOMATIC)) {
                    saveTimer(prefs, previousHostname, previousDeadline).apply();
                    showError(getString(R.string.change_failed));
                    return;
                }
            } catch (SecurityException exception) {
                saveTimer(prefs, previousHostname, previousDeadline).apply();
                showError(getString(R.string.change_failed));
                return;
            }
        }
        DnsSettings.scheduleRestore(this, deadline);
        DnsNotification.show(this, false);
        Toast.makeText(this, getString(R.string.automatic_duration_set, durationLabels[which]),
                Toast.LENGTH_SHORT).show();
        finishAndRemoveTask();
    }

    private SharedPreferences.Editor saveTimer(SharedPreferences prefs, String hostname, long deadline) {
        return prefs.edit().putString(DnsSettings.PREF_HOSTNAME, hostname)
                .putLong(DnsSettings.PREF_DEADLINE, deadline);
    }

    private void clearRestoreState() {
        DnsSettings.cancelRestore(this);
        getSharedPreferences(DnsSettings.PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
        DnsNotification.cancel(this);
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showSetCustomConfirmation(String hostname) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.set_custom_confirmation)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    try {
                        boolean hostnameRestored = DnsSettings.setHostname(this, hostname);
                        boolean modeRestored = hostnameRestored
                                && DnsSettings.setMode(this, DnsSettings.MODE_CUSTOM);
                        if (!modeRestored) {
                            showError(getString(R.string.change_failed));
                            return;
                        }
                        clearRestoreState();
                        finish();
                    } catch (SecurityException exception) {
                        showError(getString(R.string.change_failed));
                    }
                })
                .setNegativeButton(android.R.string.no, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showAlreadyAutomaticConfirmation(String hostname) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.already_automatic_title)
                .setMessage(R.string.already_automatic_confirmation)
                .setPositiveButton(R.string.button_yes, (dialog, which) -> showDurationPicker(hostname, 0L))
                .setNegativeButton(R.string.button_no, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void showCustomDnsBlank() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.custom_dns_blank)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> openDnsSettings())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void openDnsSettings() {
        try {
            startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS"));
        } catch (ActivityNotFoundException exception) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
        finish();
    }
}
