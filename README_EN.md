# Eta 后台版 — Eta Background Edition

[简体中文](README.md) · [English](README_EN.md) · [Version history and source map](etax/README.md) · [Build instructions](.github/RELEASING.md)

A derivative branch of **[Eta](https://github.com/Mangi-11/Eta), created by [蛮吉 / Mangi-11](https://github.com/Mangi-11)**, based on upstream v3.3.5. This repository maintains background virtual-display automation and related task, notification and lifecycle extensions. It is an independent distribution. The internal project name remains `Etax`; its Android package is `io.github.mangi.etax`.

A development candidate adds built-in Root configuration for the system assistant, with a durable backup, read-back checks, user-confirmed power-key wake and restoration. It does not depend on Vector or inject into system_server. Physical power-key routing on ColorOS remains unverified; this is not a fix claim for the earlier soft reboot. [Implementation and acceptance](etax/NATIVE_ASSISTANT.md).

## Latest test build

**2026-10-30 · `3.3.5+etax.4637961` · versionCode `2026103003`**, with engine `20261030-e40-takeover`.

When an active background app actually moves to the foreground, ETA pauses only its owning run, blocks old-screen input and prevents that session from pulling the app back. Merely viewing its Recents thumbnail does not trigger takeover. Resume manually and observe again before further input.

This build also includes capture-allocation rollback, foreground-window handoff confirmation and timeout isolation. Completion banners, all three notification actions, inline continuation and search fixes remain available. Screen-off development remains paused.

**[Download the signed test APK](https://github.com/Lmin2419/eta-background/actions/runs/41096466829/artifacts/12998081108)** · [Exact source 4637961](https://github.com/Lmin2419/eta-background/tree/4637961bc55fe546da17fda33f2dc274fdc65e20) · [Changelog and checksums](etax/README.md#release-2026103003)

Local and CI checks report 2,108 tests: 2,107 passed, 1 skipped, no failures or errors. Eight deployment tests and twelve integration checks passed; lint reported no errors. The APK retains the previous signing certificate and CI verified its embedded engine assets. **The device-wide cold-start blank-screen cause remains unproven and requires device testing; this release does not claim to have eliminated it.** Downloading requires a GitHub login; the artifact expires on **2026-11-29 (UTC)**.

## Integration candidate: task controls, efficiency and saved tasks

The e47 candidate (`20261103-e47-recovery`) closes and exits a resident engine after system-service connection loss. Deployment recognizes owned engines despite thread-name changes and IPv6 sockets, rechecks process identity before signalling, and leaves unknown port owners untouched. Recovery diagnostics distinguish port conflicts and connection loss from Root failure. See [recovery and validation](etax/ENGINE_RECOVERY.md). The reported ColorOS native power-key replacement failure remains unresolved.

The unpublished `etax-340-step1-foundation` candidate retains the e45 preview/control fixes and adds bounded data-reading guidance, fixed-window notification paging, a compact measured execution summary, and local **Saved tasks**. Save a name and instruction from the home menu, run it in a fresh ordinary conversation using the current model, or request a desktop shortcut. Notification replies still continue their original conversation.

Measurements reuse runtime events and existing conversation storage; they add no model request or background service. Notification counts describe retained notification records, not complete chat history. Speedups, ColorOS transitions and launcher shortcuts still need device validation. The official test download above is unchanged. See [implementation and validation notes](etax/EFFICIENCY_AND_COMMON_TASKS.md) (Chinese).

The 2026-11-02 action-feedback candidate listens before input, returns transient text and bounded historical image evidence, checks settled input values, and limits repeated submissions to the same identifiable target across node/coordinate/long-press APIs. It applies to foreground and background tools without app-specific code. See [implementation and device-test limits](etax/ACTION_FEEDBACK.md); device validation is still pending.

## What this branch adds

- Built-in Bing search with a bounded DuckDuckGo fallback, safe network diagnostics, and overlay settings navigation that remains available after permission is granted.
- Foreground tools for the current page; virtual displays for explicitly requested background work or continuation.
- Root-backed virtual displays with per-task screen ownership, observations and input state.
- Shared capability registration, window readiness checks, explicit scroll targets, and separate input acceptance/change evidence with bounded no-progress retries.
- Two executing tasks and at most two active or retained displays; bounded queuing and serialized access to the same app.
- Live conversation recovery after leaving the power-button assistant sheet.
- Separate native task notifications, streaming progress, pause/stop controls and in-place result updates. Compatible systems can show them as Live Updates / Fluid Cloud.
- Optional native completion banners on the same task card, with separate conversation, app handoff and inline continuation actions. The Appearance switch disables pop-ups while retaining task notifications.
- Inline notification replies to continue the original conversation, plus a separate action to open the ETA conversation.
- Capture suspension after completion, retained app pages and verified foreground handoff before releasing an empty display.
- Pause only the affected run when its app leaves the hidden display; do not pull it back or replay input without manual resume.
- Capture-allocation rollback and command admission control while timed-out work drains, with fresh observation required before subsequent input.
- Shared display geometry validation, landscape capture, screenshot-coordinate mapping and structured rejection diagnostics.
- Assistant sheet transitions, optional edge lighting in Appearance settings and conversation reading-position restoration.

Upstream v3.3.5 Subagents, Secondary Model selection, progress details, tool summaries and Debian recovery are included. Subagents cannot borrow their parent's background display tools. Screen-off pauses execution; resume manually after unlocking. Earlier three-display and screen-off execution experiments have been superseded.

## Requirements and limitations

Android 13+, ARM64. Background virtual displays require Root and the accessibility service. Configure your own model provider. Device feedback primarily comes from OPPO Find X9 Ultra / ColorOS 17; notification presentation and cross-display behavior depend on the ROM.

Retained and active displays share the two-display capacity. Retained pages can be reclaimed for new work. Android memory pressure, cross-display configuration changes or the target app itself may still recreate an activity; seamless game handoff is not guaranteed. ETA does not force an app restart merely to remove letterboxing.

## Source, builds and history

The complete current source is on [`main`](https://github.com/Lmin2419/eta-background/tree/main). Earlier sources remain on [`etax-3.3.3`](https://github.com/Lmin2419/eta-background/tree/etax-3.3.3), [`etax-3.3.4`](https://github.com/Lmin2419/eta-background/tree/etax-3.3.4), and in the [background development history](https://github.com/Lmin2419/eta-background/commits/etax-3.3.4-background-multitask), which now also includes the v3.3.5 integration.

See the [version history and source map](etax/README.md) and [build/signing instructions](.github/RELEASING.md) (Chinese). Download APK artifacts from successful [Etax Build runs](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml). Artifacts require a GitHub login and expire; maintainers can publish permanent downloads through this repository's Releases. Upstream Eta APKs do not contain this branch's additions.

Application source lives in `app/`, background bridges in `app/src/main/java/com/eta/shadow/`, the Root engine in `etax/engine/`, and shared geometry and action evidence in `etax/shared/`. Regression tests in `app/src/test/` and deployment script tests in `etax/engine/tests/` remain part of the build.

## Attribution and license

Credit to **蛮吉 (Mangi-11)** and the [Eta contributors](https://github.com/Mangi-11/Eta/graphs/contributors). Upstream: [Mangi-11/Eta](https://github.com/Mangi-11/Eta). Background branch: [Lmin2419/eta-background](https://github.com/Lmin2419/eta-background).

This project retains **[PolyForm Noncommercial 1.0.0](LICENSE)** and the upstream copyright notice. It is source-available under a noncommercial license, not MIT/Apache or OSI-defined open source. Third-party components retain their own licenses.

Required Notice: Copyright © 2026 蛮吉 (Mangi-11).

## Background navigation candidate

The 2026-11-03 candidate adds task-bound `screen_back`, input admission after an unready launch, structured recovery guidance, and a four-second cap on whole-page idle waits. Text waits retain their own timeout. Engine e46 is unchanged; ColorOS device acceptance is pending. See [behavior and acceptance steps](etax/BACKGROUND_NAVIGATION.md).
