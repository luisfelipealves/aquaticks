# Validation record

## MVP checkpoint
Commit: `e6b088b67061c259570d3c2716513dcebc002afd`
Run: https://github.com/luisfelipealves/aquaticks/actions/runs/39478249519

- 18 JVM tests passed; zero failures/errors/skips.
- Android lint passed.
- `:app:assembleDebug` passed.
- `aquaticks-debug-7` APK artifact published.
- No physical watch test has been performed.
- History, configurable doses and complication are added after this checkpoint.

## Physical checks still required
Notification permission/channel behavior, actual reminder delivery, quiet periods across
Doze/reboot/time changes, Tile refresh, watch-face complication rendering, font scaling,
circular-screen clipping, touch/rotary behavior, haptics and battery consumption.
See TESTING.md.
