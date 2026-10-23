# AquaTicks 💧

A minimalist, offline water reminder and hydration tracker for Samsung Galaxy Watch / Wear OS 3+.

**Development branch:** [agent/mvp](https://github.com/luisfelipealves/aquaticks/tree/agent/mvp)  
**Review:** [PR #1](https://github.com/luisfelipealves/aquaticks/pull/1)  
**Cloud builds:** [Wear OS APK](https://github.com/luisfelipealves/aquaticks/actions/workflows/android.yml)

The MVP checkpoint passed 18 tests, lint and debug APK assembly in [run #7](https://github.com/luisfelipealves/aquaticks/actions/runs/39478249519).
The full-feature checkpoint passed 24 tests, lint and APK assembly in [run #9](https://github.com/luisfelipealves/aquaticks/actions/runs/39479832595), including history, alternative amounts and a watch-face complication. Use the latest successful run on the PR for the final follow-up.
Physical watch acceptance is still pending; main is intentionally not merged yet.

## Features
- Black background, blue progress ring, white/gray text and a water-drop icon.
- Tap the large progress ring once to record the default 250 ml; other common amounts: 100/200/250/300/500 ml.
- Adjustable daily goal, initially 2,000 ml; no medical recommendation or automatic goal.
- Reminders every 1/2/3/4 hours, configurable quiet hours and a direct notification action.
- Daily history, per-day goals, a simple time-of-day chart and individual records.
- A progress Tile that immediately opens the quick-register screen.
- SHORT_TEXT and RANGED_VALUE complication data with a drop icon.
- Offline Room storage; no network permission, accounts, analytics, ads or backend.

## Architecture and source layout
One Kotlin Android application module, with Wear Compose Material 3 and Room.
Room stores settings as well as consumption events and daily-goal snapshots so updates stay atomic.
No separate DataStore, dependency-injection framework or architecture boilerplate.
Five local timestamps in SharedPreferences provide a fixed-size reminder diagnostic
snapshot (last received/posted/finished/failed and next requested time). They contain
no intake records, stay on the watch and are available under reminder settings.

| Package | Responsibility |
| --- | --- |
| `ui/` | Wear Compose screens, ViewModel and state |
| `domain/` | Progress, local-day and quiet-hour rules |
| `data/` | Room entities/DAO/database and transactional repository |
| `reminders/` | One calendar alarm, notification channel/actions and system-change recovery |
| `tile/` | Wear Tile provider and midnight timeline |
| `complication/` | Short-text/ranged-value data source and midnight timeline |
| `app/src/test/` | Rules, persistence and complication tests |
| `.github/workflows/android.yml` | Java/Android setup, tests, lint, APK and report artifacts |

Each record keeps its UTC instant, original local date and timezone. Travel never reassigns
old records to another day. Goals are captured per date. Reboots preserve data and re-register
the reminder. There are no permanent services or background polling loops.

## Obtain and install the APK
Read [docs/TESTING.md](docs/TESTING.md) for Actions downloads and Wi-Fi ADB installation.
No Android Studio or local compilation is required. The build uses JDK 17, Gradle 8.13,
AGP 8.13.2, Kotlin 2.2.21 and Android SDK 36. Versions are pinned in Gradle files.

## Limitations
- Reminders start at the next local whole hour, then follow the selected interval.
  Native precise alarm access is separate from notification permission; without it,
  scheduling is approximate and Android/Doze may delay delivery. Even precise alarms
  do not guarantee a visible alert or vibration. Quiet hours and notification settings apply.
- The Tile opens quick logging; it does not record solely because it is displayed/refreshed.
- A complication's final appearance depends on the chosen watch face and slot.
  Modern hosts can apply the supplied midnight timeline; older hosts may briefly show no
  value until the next refresh rather than displaying yesterday's consumption.
- No cloud backup/sync/export or phone companion. Uninstalling removes local data.
- Debug builds are for testing; signing-key cache resets can prevent in-place updates.
- Actual battery, haptics, accessibility and round-screen behavior still need watch testing.

See [project decisions](docs/PROJECT.md), [validation](docs/VALIDATION.md) and
[installation/testing](docs/TESTING.md).
