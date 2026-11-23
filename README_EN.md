# 胖小胖 (Pangxiaopang) — Eta Background Edition

[简体中文](README.md) · [English](README_EN.md) · [Version history and source map](etax/README.md) · [Build instructions](.github/RELEASING.md)

胖小胖 (formerly "Eta 后台版") is a derivative branch of **[Eta](https://github.com/Mangi-11/Eta), created by [蛮吉 / Mangi-11](https://github.com/Mangi-11)**, based on upstream v3.3.5. From 2026-11-06 the app is shown as 胖小胖 with a coral paw-print icon. This repository maintains background virtual-display automation and related task, notification and lifecycle extensions. It is an independent distribution. The internal project name remains `Etax`; its Android package is `io.github.mangi.etax`.

A development candidate adds built-in Root configuration for the system assistant, with a durable backup, read-back checks, user-confirmed power-key wake and restoration. It does not depend on Vector or inject into system_server. Physical power-key routing on ColorOS remains unverified; this is not a fix claim for the earlier soft reboot. [Implementation and acceptance](etax/NATIVE_ASSISTANT.md).

## Latest test build

**2026-11-23 · `3.3.5` 胖小胖 · versionCode `2026111627`**, engine `20261120-e50-handoff-giveup`.

New: scheduled tasks (schedules, notify-only-on-change, per-task model and tools, UI steps can run on the background virtual screen even while the phone is locked) and Gemini on Vertex AI; first batch of upstream changes after 3.4.4 (real LaTeX typesetting, hold-to-talk with release-to-send, default reasoning depth, keep-screen-on switch, default digital assistant status, 512K context preset, subagent privilege and UI fixes, compaction summaries kept on edit/branch; Kimi, Breeno/XiaoAi takeover and the Gemini voice option removed); faster background execution (automatic look after each background action, checks only the targeted element, the app decides between UI tree and screenshot, faster file edits, failure reasons in diagnostics); fixes task notifications stuck on "running" after the task finished, and computer tasks can show the glow too; smart edge glow (a faint thin edge line only while the AI operates your screen, a small ring around the camera while it works in the background (position and size adjustable), and a brief cue at start and finish) with a selectable brightness, Back on the workbench returns to the conversation you came from (Back in a conversation goes to the workbench first), and a fix for top glass buttons occasionally rendering solid; read-only lookups run in parallel (reading several files or searching is faster); tool errors now say what to check next and warn on repeated identical failures; self-check before answering (when a failed step is unresolved, the agent re-checks once before concluding), multi-select messages to copy or share several at once, the new-conversation page is a workbench (in progress, pick up where you left off), long-press an answer to regenerate it with another model (the default model stays), the task capsule details open centred under the capsule with a smooth spring; power-key long-press works again after minor system updates; model presets (switch model and thinking level in one tap from the title capsule), rename and export from the title capsule menu; thinking and steps fold into one line after a reply finishes (can be turned off in Appearance), toasts that follow the page colour; unread dots for finished work, desktop sessions on the task page, one-tap copy in text selection and a terminal tile in the side pane; provider detail pages scroll again and the desktop session page shows phone tasks; the desktop session page matches the phone chat (glass controls floating over the messages), text selection that spans thinking and answer paragraphs (phone and desktop sessions), a glass top bar on the terminal page, top controls that stay legible at large font sizes, a smooth memory page for long documents, and edit_file cards that show the changed lines; desktop sessions in the home task capsule and done notice, capsule items open their conversation, and the model queued during a reply shows as "next turn" on the title capsule; a render diagnostics switch (frames, late frames, sampling sources) and suggestion chips above the composer on the new-chat page; a one-tap "Recheck error and retry" when an answer gives up, model switches during a reply take effect next turn, a home-screen notice when another conversation finishes, and edit_file reports the changed lines; COUI search bar in the side pane and glass controls on the desktop session page; device-mode screenshots are checked before each release. Fixes the blank phone chat page (root cause: the popup menu took the chat page's slot in the side-pane layout) and the oval composer; settings, side pane and chat header rebuilt on upstream COUI (COUI page skeleton, switches and dropdown menus in settings; upstream push-style side pane with a liquid-glass bottom dock; "title + model" chat header); the agent now fixes small coding errors (escaping, whitespace, CRLF) before giving up; upstream COUI menus, bottom sheets and capsule buttons, COUI fluid liquid glass on the chat top controls and composer, per-reply speed stats and background context compaction with an optional dedicated model. Earlier: upstream context-compression cache reuse and usage stats, MiMo/DeepSeek voice, a separate model for the assistant overlay and cleaner read-aloud. Earlier upstream merges: auto-generated conversation titles, branching a new conversation from a reply, full-screen chat image viewer, share-to-chat from other apps, per-tool switches, automatic root re-check after reboot, plus Grok/Gemini fixes. The license follows upstream to GPL-3.0.

APK: **`Pangxiaopang-3.3.5-2026111627.apk`**.

**[Download the signed test APK](https://github.com/Lmin2419/eta-background/releases)** · [Changelog](etax/README.md#release-2026111627)

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

Upstream Eta moved to **[GPL-3.0](LICENSE)** in 3.4.1. This project now includes upstream 3.4.x code and follows GPL-3.0 as well, keeping the upstream copyright and attribution. You may use, modify and distribute it; distributed modified versions must publish their source under GPL-3.0. Third-party components retain their own licenses.

Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
