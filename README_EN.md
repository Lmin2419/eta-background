# Eta 后台版 — Eta Background Edition

[简体中文](README.md) · [English](README_EN.md) · [Version history and source map](etax/README.md) · [Build instructions](.github/RELEASING.md)

A derivative branch of **[Eta](https://github.com/Mangi-11/Eta), created by [蛮吉 / Mangi-11](https://github.com/Mangi-11)**, based on upstream v3.3.5. This repository maintains background virtual-display automation and related task, notification and lifecycle extensions. It is an independent distribution. The internal project name remains `Etax`; its Android package is `io.github.mangi.etax`.

## Latest test build

Current source adds virtual-display cleanup, handoff confirmation and timeout isolation in `2026103002` / `20261030-e39-lifecycle`; validation is in progress. See the [new changelog](etax/README.md#release-2026103002). The previous verified APK is listed below.

**2026-10-30 · `3.3.5+etax.3b1794d` · versionCode `2026103001`**, with engine `20261029-e38-input`.

Built-in web search now uses Bing first and tries DuckDuckGo on failure, with a 10-second network timeout per source and explicit failure diagnostics. Overlay permission settings remain clickable after authorization and provide alternative system entry points when the dedicated page is unavailable.

Native task completion banners, all three notification actions and the pop-up preference remain available. See the [completion notification guide](etax/README.md#completion-notice-guide) (Chinese).

**[Download the signed test APK](https://github.com/Lmin2419/eta-background/actions/runs/41024159901/artifacts/12963208802)** · [Exact source 3b1794d](https://github.com/Lmin2419/eta-background/tree/3b1794d70881c3c849b53d7778dd2bfdf0a46cc5) · [Changelog and checksums](etax/README.md#release-2026103001)

CI reported 2,086 regression tests: 2,085 passed, 1 skipped, no failures or errors. All eight engine deployment tests passed; lint reported no errors. The APK retains the previous signing certificate for installation over the existing background edition, and its embedded engine was checked against source. A live Bing response was parsed in the development environment; phone-network behavior and ColorOS 17 settings navigation still need device testing. Downloading requires a GitHub login; this artifact expires on **2026-11-29 (UTC)**.

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
- Capture suspension after completion, retained app pages and explicit foreground handoff of an existing task before releasing its empty display.
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
