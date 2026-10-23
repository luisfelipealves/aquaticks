# Whole-hour reminders

User intent: activate at12:43, first reminder13:00, then the selected1–4hour interval. Delayed delivery must not shift all later deadlines. Existing quiet hours and offline/battery/privacy requirements continue.

Approaches: keep inexact alarms only (low power, no new access, timing still flexible); use native exact idle-capable alarms when authorized (fits wall-clock reminders, requires user-granted special access); permanent service (rejected: unnecessary battery cost). Implementation choice: exact when allowed, clearly labelled approximate fallback otherwise. Existing autonomy covers this reversible native choice; do not imply the user has already granted device permission.

One pending alarm. First target is the strictly next local whole hour, including activation exactly on an hour. Subsequent target is prior scheduled target plus the selected interval, skipping elapsed slots rather than starting a new interval at delivery. Settings changes and timezone/manual-clock changes reset to the next local whole hour. Existing non-hour0.2.4 deadlines realign without deleting data.

Builder choices: after quiet hours resume on the first allowed whole hour, then retain the selected interval. If no whole hour exists outside a daily quiet window, schedule nothing and explain that the quiet window must change. At DST gaps choose a valid whole hour; do not duplicate a repeated local hour. Normal reopening, intake and reboot preserve a valid future deadline. Elapsed slots are skipped on foreground/reboot reconciliation as well, preserving phase; reopening must not replay a stale reminder or postpone by a new interval from now. An actual delivered receiver arms the next future slot.

Precise scheduling uses SCHEDULE_EXACT_ALARM and setExactAndAllowWhileIdle only when canScheduleExactAlarms permits; never USE_EXACT_ALARM. Settings explains approximate mode and opens the supported special-access screen. Return-to-app and permission-granted broadcast reconcile without resetting phase. Android can stop the app and cancel exact alarms after revocation; recovery occurs on reopening/reboot, not by a permanent watchdog.

No database migration, dependency or collection. Main action stays silent. Complication restart defect is outside this timing change. Verify domain/calendar/DST rules, late delivery, precise/fallback modes, permission grant, silence and existing privacy/persistence/Tile/complication tests. Human watch validation remains necessary.
