# Complication boot compatibility

Keep AquaTicks available to Wear OS complication discovery before first unlock; return native NoData without reading Room while locked, then normal data after unlock. Preserve provider component identity, schema, signer and working reminders. Existing BOOT_COMPLETED recovery requests fresh surface data.

Alternatives: directBootAware alone risks locked credential access; device-protected hydration cache duplicates private data; periodic refresh adds battery cost. Developer chooses guarded direct boot plus minimal device-protected diagnostic, under the user autonomy instruction. This is not a claimed confirmed fix for Samsung selection loss.

Diagnostics: fixed latest activation/deactivation/request/response/failure times and response status, no consumption or history, no network. Settings page describes events as system requests/data supplied, not proof of rendering or watch-face selection. No active-instance count or persisted IDs interpreted as current selection. Stop at human reboot testing after tests/build/review.
