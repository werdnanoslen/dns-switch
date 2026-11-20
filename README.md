# DNS Switch

Temporarily switch Android's system Private DNS to **Automatic** for 1 minute, 10 minutes, 1 hour, or 8 hours, then return to **Private DNS provider hostname** using your saved hostname.

## Setup

1. Use Android 9 or later and enter a provider hostname in your phone's Private DNS settings.
2. Install DNS Switch, connect your phone to ADB over USB or [Wireless debugging](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi), and grant permission once:

   ```sh
   adb shell pm grant com.andyhub.dns_switch android.permission.WRITE_SECURE_SETTINGS
   ```

3. Open DNS Switch, allow notifications if prompted, and pick a duration. The notification shows the scheduled restore time and offers **Extend** and **Stop**. Android may delay the restore alarm; tap **Stop** to restore the provider without waiting for it.

## Build

With Android SDK 37 installed, build from Android Studio or run `./gradlew :app:assembleDebug` from the project root. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## License

[MIT](LICENSE)
