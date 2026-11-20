# Complication Boot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline. Steps use checkbox syntax.

**Goal:** Startup-safe complication with diagnostic evidence.
**Architecture:** Existing AndroidX service and Room; UserManager guard, native NoData before unlock; tiny device-protected diagnostic, settings display. Existing boot refresh retained.
**Tech Stack:** Existing Kotlin/Wear Compose/Room; no dependency.
**Spec:** docs/superpowers/specs/2026-10-27-complication-boot-design.md

## Constraints
Keep application/provider IDs, supported types, Room v1, signer and reminder code unchanged. No network, data reset, polling or main merge.

## Review focus
Locked cold start cannot access Room; unlocked process can serve real data without reselecting; error/cancellation handling must preserve coroutine semantics; diagnostics cannot claim selected state or expose intakes; old face rendering and actual reboot remain hardware tests.

### Task 1: boot-safe service and evidence
- [ ] Add tests for direct-boot discovery, both supported locked request types without DB creation, unlocked recovery, and persisted lifecycle evidence. Observe expected assertions fail in Actions.
- [ ] Mark existing service directBootAware; guard isUserUnlocked before repository access. Return native NoData timeline while locked. On read failure record status and return NoData; rethrow cancellation.
- [ ] Record latest lifecycle/request/response/error in device-protected preferences; expose settings screen with honest labels, no selection inference.
- [ ] Update version0.2.6/code8 and testing docs. Full tests/lint/APK in Actions and signer verification.
- [ ] Independent exact-head review, record HUMAN_TESTING and deliver artifact; no merge.
