# 胖小胖 (Pangxiaopang) — Eta Background Edition

[简体中文](README.md) · [English](README_EN.md) · [Version history and source map](etax/README.md) · [Build instructions](.github/RELEASING.md)

胖小胖 (formerly "Eta 后台版") is a derivative branch of **[Eta](https://github.com/Mangi-11/Eta), created by [蛮吉 / Mangi-11](https://github.com/Mangi-11)**, based on upstream v3.3.5. From 2026-11-06 the app is shown as 胖小胖 with a golden-shaded cat icon. This repository maintains background virtual-display automation and related task, notification and lifecycle extensions. It is an independent distribution. The internal project name remains `Etax`; its Android package is `io.github.mangi.etax`.

A development candidate adds built-in Root configuration for the system assistant, with a durable backup, read-back checks, user-confirmed power-key wake and restoration. It does not depend on Vector or inject into system_server. Physical power-key routing on ColorOS remains unverified; this is not a fix claim for the earlier soft reboot. [Implementation and acceptance](etax/NATIVE_ASSISTANT.md).

## Latest test build

**2026-11-09 · `3.3.5` 胖小胖 · versionCode `2026110901`**, with engine `20261105-e49-parallel-observe` unchanged.

Desktop sessions now carry the desktop's details: pick the model and reasoning effort from the top bar, and see total tokens, cache hit rate, running time and per-turn stats. Subagents live behind a subagent button inside their parent session instead of crowding the session list. Fixed a steered message showing twice on the phone, and the background-task notification's "Open conversation" opening the virtual screen. **Update the `dsh-bridge` plugin on the computer and restart DSH.**

The built-in relay `8.163.249.159` and `pxp://` pairing codes are unchanged. APK: **`Pangxiaopang-3.3.5-2026110901.apk`**.

**[Download the signed test APK](https://github.com/Lmin2419/eta-background/releases)** · [Changelog](etax/README.md#release-2026110901)

> Development baseline: `main` is the latest version. New work starts from `main`.

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

See the [version history and source map](etax/README.md) and [build/signing instructions](.github/RELEASING.md) (Chinese). Download APKs from this repository's [Releases](https://github.com/Lmin2419/eta-background/releases), published automatically after each signed build on `main`. Branch test builds are available from [Etax Build runs](https://github.com/Lmin2419/eta-background/actions/workflows/etax-build.yml); those artifacts require a GitHub login and expire after 30 days. Upstream Eta APKs do not contain this branch's additions.

Application source lives in `app/`, background bridges in `app/src/main/java/com/eta/shadow/`, the Root engine in `etax/engine/`, and shared geometry and action evidence in `etax/shared/`. Regression tests in `app/src/test/` and deployment script tests in `etax/engine/tests/` remain part of the build.

## Attribution and license

Credit to **蛮吉 (Mangi-11)** and the [Eta contributors](https://github.com/Mangi-11/Eta/graphs/contributors). Upstream: [Mangi-11/Eta](https://github.com/Mangi-11/Eta). Background branch: [Lmin2419/eta-background](https://github.com/Lmin2419/eta-background).

This project retains **[PolyForm Noncommercial 1.0.0](LICENSE)** and the upstream copyright notice. It is source-available under a noncommercial license, not MIT/Apache or OSI-defined open source. Third-party components retain their own licenses.

Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
