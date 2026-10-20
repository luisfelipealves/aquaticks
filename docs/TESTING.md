# Test AquaTicks on a Galaxy Watch

## Download the cloud-built APK
1. Open https://github.com/luisfelipealves/aquaticks/actions/workflows/android.yml.
2. Choose a successful **Wear OS APK** run for the desired commit/branch (currently `agent/mvp`).
3. Download `aquaticks-debug-N` under **Artifacts** while signed in to GitHub.
4. Extract the ZIP; the installable file is `app-debug.apk`.
5. Reports are in `aquaticks-reports-N`. An artifact from a failed build is not a validated release.

The workflow triggers on branch pushes and pull requests. After the workflow reaches main,
**Run workflow** can also launch it manually and select a branch.
No development secrets are required.

## Install without Android Studio or local compilation
You only need Android SDK Platform Tools (ADB) to transfer the prebuilt APK.
Official download: https://developer.android.com/tools/releases/platform-tools.
Keep your watch and installation computer on the same Wi-Fi network.

1. On the watch, open Settings > About watch > Software information.
   Tap Software version repeatedly until developer options are enabled.
   Names vary with One UI Watch versions.
2. Open Developer options, enable ADB debugging, then Wireless debugging.
3. Select Pair new device. Note the watch IP, pairing port and displayed pairing code.
4. In the directory containing platform-tools, run (replace capitalized placeholders):

```sh
adb pair WATCH_IP:PAIRING_PORT
# Enter the pairing code when prompted.
adb connect WATCH_IP:CONNECTION_PORT
adb devices
adb -s WATCH_IP:CONNECTION_PORT install -r app-debug.apk
```

Use the **connection port from the main Wireless debugging screen**, which differs
from the temporary pairing port. Prefix adb with `./` on macOS/Linux if it is not on PATH.
On older Wear OS versions that expose only legacy Wi-Fi ADB, use the address/port
shown by that version and approve the connection on the watch; pairing may not be present.
Official instructions: https://developer.android.com/training/wearables/get-started/debug-wifi.

This builds entirely in the cloud. ADB here is only for installation.
The debug keystore is cached by Actions to support test upgrades. A cache reset can
change the signature; do not uninstall an existing installation merely to solve a
signature mismatch if you want to preserve its local history.

## Watch acceptance checklist
- Launch AquaTicks and confirm a black screen, blue ring, readable numbers and a +250 ml action.
- Tap +250 ml once: one record, progress increase, brief "Boa!" confirmation.
- Exit/reopen: the amount remains. Reboot: amount and settings remain.
- Change the daily goal with +/−. Yesterday's goal must remain unchanged.
- Enable notifications/reminders; choose 1/2/3/4 hours and quiet hours.
- Test an interval while awake. Delivery is inexact and may be delayed by Android/Doze.
- Verify no reminder appears during quiet hours, including a period crossing midnight.
- Reject notification permission: tracking still works; UI shows how to allow notifications.
- Add the Água Tile through the watch's Tile picker. It shows total and goal;
  tapping opens the app at the quick-register action. A refresh never records water.
- At local midnight the home/Tile show a new day; older records remain stored.
- Test a timezone change and a daylight-saving transition where practical.
- Turn off connectivity: tracking, settings, reminders and Tile still work.
- Try large font settings, rotary scrolling and TalkBack on the actual watch.
- Check physical comfort/haptics and battery over at least one normal day.

Watch-only battery, OS scheduling, haptic strength and exact circular clipping have
not been validated by a JVM build. Debug builds are for testing, not Play distribution.
Turn off wireless debugging after installation when you no longer need it.
