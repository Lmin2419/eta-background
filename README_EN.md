# Eta 后台版 — Eta Background Edition

[简体中文](README.md) · [English](README_EN.md) · [Version history and source map](etax/README.md) · [Build instructions](.github/RELEASING.md)

A derivative branch of **[Eta](https://github.com/Mangi-11/Eta), created by [蛮吉 / Mangi-11](https://github.com/Mangi-11)**, based on upstream v3.3.5. This repository maintains background virtual-display automation and related task, notification and lifecycle extensions. It is an independent distribution. The internal project name remains `Etax`; its Android package is `io.github.mangi.etax`.

## Latest test build

**2026-10-29 · `3.3.5+etax.f8bd297` · versionCode `2026102901`**, with engine `20261029-e38-input`.

This update integrates background tools into the native capability catalog, applies the same window readiness checks to newly launched and adopted apps, and separates accepted input from observed changes. Repeated actions with no observed progress are blocked so the agent can inspect the page again.

**[Download the signed test APK](https://github.com/Lmin2419/eta-background/actions/runs/40819261662/artifacts/12873514442)** · [Exact source f8bd297](https://github.com/Lmin2419/eta-background/tree/f8bd2975ce9d98970aac2e8ad04511a467e0a59b) · [Changelog and checksums](etax/README.md#release-2026102901)

CI reported 2,058 regression tests: 2,057 passed, one skipped, no failures or errors. All eight engine deployment tests passed; lint reported no errors. The APK retains the previous background edition's signing certificate for installation over the existing app, and its embedded engine was checked against the source build. Scrolling and long-press copying in Feishu still need ColorOS 17 device testing for both launch paths. Downloading requires a GitHub login; this artifact expires on **2026-11-28 (UTC)**.

## What this branch adds

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
