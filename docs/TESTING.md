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
- Tap the large progress ring (+250 ml) once: one record and immediate progress increase,
  without a success message or overlay. A notification action also records silently
  and dismisses its reminder without creating a confirmation notification.
- Exit/reopen: the amount remains. Reboot: amount and settings remain.
- Change the daily goal with +/−. Yesterday's goal must remain unchanged.
  Dates with no saved goal show "Meta não registrada"; they must not borrow today's goal.
- Enable notifications/reminders; choose 1/2/3/4 hours and quiet hours.
- On 0.2.5+, enable at12:43: first deadline13:00. With interval2 hours, subsequent
  slots are15:00 and17:00. Enabling exactly on an hour starts at the next hour.
- In settings, use **Permitir horário preciso** and grant the native special access
  if available; this is separate from notification permission. Return to AquaTicks
  and verify the precise status. Without access, the UI says scheduling is approximate
  and Android/Doze may delay delivery. If the watch cannot open the access screen,
  check its system settings; the approximate fallback remains usable.
- With reminders allowed and enabled, wait outside quiet hours with the screen off.
  Reopening after a missed deadline skips expired slots and keeps the future calendar
  cadence. A delayed13:00 delivery at14:58 schedules15:00 with an hourly interval.
  Repeat for at least two intervals without opening AquaTicks between them,
  including leaving the first notification unacknowledged. Each new reminder may alert.
  In Configurações > Estado dos lembretes, note Próximo previsto, Última execução,
  Notificação criada and Última falha. They are a fixed-size local diagnostic snapshot;
  creation does not prove the watch displayed or vibrated the notification.
- Verify no reminder appears during quiet hours, including a period crossing midnight.
  A quiet period ending08:30 resumes09:00. A period00:00–23:30 leaves no eligible
  whole hour: no alarm and a visible explanation. Changing the period restores scheduling.
- Grant precise access with a valid future deadline: its time stays fixed. Revoke access:
  Android stops the app and cancels exact alarms; reopen AquaTicks to resume approximate
  scheduling. Reboot and verify the future deadline is restored without losing records.
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
The device/Doze delivery timing still requires a real-watch test. JVM tests exercise
scheduling, cancellation/failure after rearming and the actual alarm PendingIntent to
receiver/goAsync path (twice without starting an activity). Robolectric requires explicit
registration of this receiver in the test harness; tests verify its manifest declaration
separately. They do not verify Samsung's power manager, manifest cold startup or a real
process restart. If the next deadline is overdue but Última execução did not
advance, capture the alarm dump before reopening the app; if Notificação criada advanced,
check watch notification/channel and Do Not Disturb settings instead of resetting data.

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

## Complication reboot test (0.2.6+)
- Update in place, keep the current watch face and AquaTicks selection. Record some water.
- Reboot, note the time, unlock if a PIN is configured and observe without opening AquaTicks.
  Before first unlock a native NoData result is expected; the provider must not read Room.
- Observe for one minute, then let the screen sleep and wake it. Note whether/when the
  complication appears. Test the Samsung Clássico tradicional face/slot originally reported.
- If blank, inspect the editor to distinguish an empty display from the provider changing
  to None/another app. Record the selection before changing it.
- Then open Configurações > Estado da complication and capture the latest activation,
  deactivation, request, prepared response, failure and status. These are historical system
  events, not evidence that a slot is currently selected or that a response was rendered.
  A request may occur without an activation callback after reboot. Use Atualizar estado
  to reread local evidence; this button does not force a watch-face update.
- No new request after reboot points to an availability/host-selection question; a locked
  response calls for testing first unlock; prepared data with a blank slot needs host/Binder
  evidence. No single case proves the Samsung root cause. Preserve data and the original
  face/slot while reporting it.

JVM tests cover manifest directBootAware registration, both locked request types without
creating the Room file, data after unlock and device-protected lifecycle evidence. They do
not emulate Samsung's selection persistence or guarantee actual boot/Binder/render timing.
No app can restore a user's watch-face slot selection through the data-source API alone.
