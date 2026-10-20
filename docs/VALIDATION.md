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

## Full feature checkpoint
Commit: `f721ab41a1b5d043da1239d35814aef9a72f08ca`
Run: https://github.com/luisfelipealves/aquaticks/actions/runs/39479832595

- 24 JVM tests passed; zero failures/errors/skips.
- Includes history/dose UI, complication data tests and effective-manifest privacy tests.
- Android lint and debug APK assembly passed; `aquaticks-debug-9` published.
- Final follow-up keeps a committed intake confirmed even if a system surface refresh fails.
  Check the latest PR status and Actions artifact for that follow-up's build.
