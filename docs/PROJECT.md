# AquaTicks project brief and decisions

## Product
Native standalone Wear OS app for Samsung Galaxy Watch (Wear OS 3+, API 30+).
Kotlin, Jetpack Compose for Wear OS, black background, blue accents, white/gray text, circular-screen layout.
The user's supplied eight-watch concept is the official visual reference. Do not add gradients or decorative UI.
The principal interaction is one tap to record 250 ml, with immediate progress update and a daily progress ring.
Portuguese is the initial UI language; project name and repository documentation use English.

## Incremental delivery
1. MVP: home, daily progress, quick record, editable daily goal, persistent local data, configurable reminders and quiet hours, Tile, tests and a cloud-built debug APK.
2. Only after the MVP build/tests succeed: basic history, watch-face complication, further visual/accessibility refinements.
No completion claim until build and tests pass and Actions publishes the APK.
Physical notification/haptic, battery and circular-display behavior require a watch test.

## Architecture
One Android application module; packages ui, data, domain, reminders, tile, then complication.
Room alone stores settings, intake events and per-day goal snapshots. One store avoids cross-store transactions and keeps settings/records atomic; no DataStore or dependency-injection framework needed.
Room/KSP are AndroidX tooling dependencies. Coroutines support Room and lifecycle-scoped work.
No backend, INTERNET permission, accounts, tracking, telemetry, analytics, advertisements or remote history.
Android backup/data extraction is disabled. The OS can still remove data when the user uninstalls.

## Time and records
Each record has a unique operation ID, instant in UTC, local date and zone at creation.
Local dates remain stable when the user travels; yesterday's records never migrate to another date.
The displayed day is computed from the current local zone, not a mutable running total.
Goals are captured per local day; edits affect today's snapshot and the default for future days, leaving previous goals unchanged.
Timezone/date/time changes trigger refresh and reminder reconciliation. Reboot re-registers alarms.
An active UI waits once until midnight; it does not poll. A Tile timeline supplies an empty future-day layout at midnight.

## Reminders and battery
Disabled until user enables them; default interval 2 hours; choices 1/2/3/4 hours.
Default quiet period 22:00–08:00, editable with watch-sized hour/minute controls.
First deadline is the next local whole hour: enabling at12:43 schedules13:00, even with a2-hour interval; subsequent slots are15:00,17:00. Delayed delivery advances from the requested slot, skipping elapsed slots without drifting or replaying missed alerts. Quiet periods resume at the first eligible whole hour; if none exists, no alarm is scheduled.
One idle-capable AlarmManager alarm: exact with native SCHEDULE_EXACT_ALARM special access, otherwise approximate. Notification permission is separate. No periodic service, polling, network or USE_EXACT_ALARM permission.
Receiver re-checks quiet hours and notification permission at delivery. Foreground/reboot reconciliation preserves valid future slots and skips expired slots; clock/timezone or schedule changes reset the phase. Exact delivery still does not guarantee a visible alert or vibration; channel settings and Do Not Disturb apply.
Notification includes a direct add-water action, deduplicated by notification instance ID.
Notifications/haptics respect Android notification channel settings and Do Not Disturb.
If notifications are denied, UI explains that reminders need permission; core tracking remains usable.

## Build
Pinned JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin/Compose compiler 2.2.21, compile/target SDK 36.
Official stable Wear Compose Material3 1.5.0, Tiles 1.6.2, ProtoLayout 1.4.2, Room 2.8.5.
Do not assume newest major versions are automatically compatible.
GitHub Actions runs tests, lint and assembleDebug and uploads APK + test reports.
No secrets needed. Debug signatures are for testing, not production distribution.
Use agent/* branches; do not rewrite history. Human review/testing before merge to main.

## Core verification
Progress at zero/goal/over-goal; invalid amounts; deduplicated and concurrent records;
local midnight, timezone travel and DST; persistence across database reopening;
settings/goal persistence; quiet hours crossing midnight and same-day periods;
quiet-period edges; disabled reminders; interval changes; schedule is always future.
After MVP: history and complication data/timeline tests.

## Complication startup
The existing provider component remains stable and direct-boot aware. Before first unlock,
it responds with native NoData and does not access Room or copy hydration into device
storage. BOOT_COMPLETED recovery already requests fresh surface data after unlock.
A fixed-size device-protected diagnostic exposes latest lifecycle/request/response status
without consumption, instance IDs, active-slot inference or network. Samsung reboot/selection
behavior remains a human test; source correctness does not prove a host defect is resolved.
