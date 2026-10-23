# Whole-hour Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** First next local whole hour, stable selected cadence, native precise scheduling when authorized.
**Architecture:** Keep one pending AlarmManager broadcast and Room settings. Compute calendar slots in WaterRules; advance from saved target, reset only for schedule/clock changes. Native special-access UI exposes precise versus approximate delivery.
**Tech Stack:** Existing Kotlin, AndroidX Wear Compose, Room, AlarmManager; no new dependency.
**Spec:** docs/superpowers/specs/2026-10-23-whole-hour-reminders-design.md

## Global Constraints
- Preserve offline/privacy, silent intake, one pending alarm, quiet hours, minSdk30/target36 and schema v1.
- Preserve signing pin and agent/mvp delivery cache; no data reset or main merge.
- No permanent service, polling, USE_EXACT_ALARM or unapproved dependency.

## Review Focus
- Delayed delivery must skip elapsed slots without drift or a burst.
- Quiet windows ending at half hours must retain whole-hour targets; impossible windows must not loop.
- DST gaps/overlaps and non-integer-offset zones must produce valid future local whole hours.
- Permission changes must change alarm mode without moving a valid deadline.
- Migration from non-hour deadlines and reboot/foreground refresh must preserve user data and valid scheduling.

### Task 1: Whole-hour scheduling with native precision

**Files:** WaterRules.kt, ReminderScheduler.kt, WaterActionReceiver.kt, SystemChangeReceiver.kt, ReminderDiagnostics.kt, AquaTicksScreen.kt, AndroidManifest.xml, app/build.gradle.kts; domain/scheduler/receiver tests and docs/TESTING.md.
**Interfaces:** Keep Settings.nextReminderAt. Extend nextReminder with optional prior deadline, nullable result for impossible quiet windows; reconcile distinguishes consumed deadline from reset after clock changes. Native precision state is exposed by scheduler and last requested mode by diagnostics.

- [x] Write RED tests using current interfaces:12:43→13:00 for all intervals; interval2 after first13:00→15:00; late14:58 with hourly saved13:00→15:00 (including foreground reconciliation); permission grant expects exact window0 and one stable deadline. Test half-hour silence, no eligible hour, DST and Kathmandu.
- [x] Run CI on tests-only reviewed commit; expected runtime assertion failures, not compilation failure. Record failure names/output before any production change.
- [x] Implement calendar slot calculation and scheduler advancement/reset. Quiet resumes at first allowed full hour; impossible window cancels schedule. Exact when permitted, otherwise approximate; preserve schema, data and one PendingIntent.
- [x] Add manifest special access and permission-grant receiver; reset phase only on timezone/time changes. Add explanatory settings action and actual scheduling mode. Bump0.2.5/code7.
- [x] Update existing tests for the intentionally changed cadence, add recurrence/permission/cancellation coverage; run full44+ regression suite, lint and APK in CI. Mutation-check the first-slot and no-drift assertions by confirming they failed against old production.
- [ ] Independent whole-branch exact-head review, signing verification and artifact link. Record limitations and stop at HUMAN_TESTING; no merge.

Review correction: delayed recovery must replay quiet/DST calendar anchors before skipping old slots. Two additional regression tests failed in Actions40015573011 (61 total,2 expected failures); fixed by walking virtual slots without posting them. Final GREEN and exact-head review pending.
