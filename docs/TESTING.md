# Test AquaTicks on a Galaxy Watch

## Download the cloud-built APK
1. Open https://github.com/luisfelipealves/aquaticks/actions/workflows/android.yml.
2. Choose a successful **Wear OS APK** run for the desired commit/branch (currently `agent/mvp`).
3. Download `aquaticks-debug-N` under **Artifacts** while signed in to GitHub.
4. Extract the ZIP; the installable file is `app-debug.apk`.
5. Reports are in `aquaticks-reports-N`. An artifact from a failed build is not a validated release.

The workflow triggers on pushes to main and on pull requests. After the workflow reaches main,
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
The debug keystore is at the ignored repository-root `debug.keystore`, matching the
Actions cache path exactly. CI checks the APK signer against that key and the public
fingerprint in `ci/debug-certificate.sha256` before uploading the APK. A missing or
changed cached key fails verification; it must not silently publish a new identity.
The private key is never committed or uploaded as an artifact. Cache retention is
finite and caches are scoped to workflow refs: moving from PR to main may require
restoring the same private key through a secure signing setup. These are test builds,
not a production key-management solution.

If an old test install already has a different signature, uninstalling erases all
its local records/settings. Do this only when loss is acceptable (as explicitly
authorized for the current test data), then install fresh and allow notifications
and reminders again. Otherwise preserve the data and stop before uninstalling.

## Watch acceptance checklist
- Launch AquaTicks and confirm a black screen, blue ring, readable numbers and a +250 ml action inside the ring.
- Tap the large progress ring (+250 ml) once: one record, progress increase, brief "Boa!" confirmation.
- Exit/reopen: the amount remains. Reboot: amount and settings remain.
- Change the daily goal with +/−. Yesterday's goal must remain unchanged.
  Dates with no saved goal show "Meta não registrada"; they must not borrow today's goal.
- Enable notifications/reminders; choose 1/2/3/4 hours and quiet hours.
- Test an interval while awake. Delivery is inexact and may be delayed by Android/Doze.
- With reminders allowed and enabled, wait outside quiet hours with the screen off.
  Reopening the app after a missed deadline must not start another full interval.
  Version 0.2.2 uses an inexact idle-capable alarm; Android can still batch delivery.
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

If no reminder arrives after the selected interval outside quiet hours, note the
interval, quiet hours and watch notification/Do Not Disturb settings. Preserve app
data. With ADB connected, collect only AquaTicks lines from `dumpsys alarm` and
relevant errors from `logcat -d -v brief AndroidRuntime:E AquaTicks:E '*:S'`.
The device/Doze delivery timing still requires a real-watch test; JVM tests exercise
scheduling and the notification receiver processing, not Samsung's power manager.

## History, doses and complication (second milestone)
- Use Outra quantidade to add 100/200/250/300/500 ml without changing the default.
- In Configurações > Dose padrão, choose the single-tap amount for home and reminders.
- Open Histórico, use Dia controls and confirm totals, goals, chart and record times.
- On the watch face, long-press > Customize > Complications and choose AquaTicks
  in a compatible short-text or ranged-value slot.
- Record water and confirm the complication refreshes. Tap it to open AquaTicks.
- Check its new-day behavior. Appearance and update timing depend on the watch face.
  Older watch faces that ignore timelines rely on a system/app refresh for the new day.

### If the complication slot stays blank
Note the watch face name and slot position. With ADB connected, select AquaTicks again,
then capture its relevant errors without clearing any app data:

```sh
./adb -s CONNECTED_DEVICE logcat -d -v brief AndroidRuntime:E AquaTicks:E '*:S'
```

Use the device whose status is `device` in `./adb devices`, including an mDNS name
if the direct IP entry is offline. Share only the relevant AquaTicks exception.
