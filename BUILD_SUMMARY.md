# Build Summary

**Project**: LANboard v1
**Date**: 2026-05-26
**Platform**: Android (minSdk 21 / targetSdk 36)
**Package**: `dev.lanboard.keyboard`
**Repository**: https://github.com/wingman1487/LANboard

---

## What Was Built

LANboard is a GPL-3.0 fork of HeliBoard that adds self-hosted voice transcription via Whisper and a terminal-friendly modifier row to a privacy-first Android keyboard.

### Core features (all validated on Pixel 10 Fold hardware)

- **Voice transcription pipeline** — Tap mic to listen, tap again to commit, long-press to discard. Audio captured at 16 kHz mono PCM, WAV-encoded, sent to any OpenAI-compatible Whisper endpoint. Silence gating (RMS threshold) and hallucination blocklist prevent false transcriptions.
- **Dual-path commit** — On commit, audio is simultaneously written to local app-private storage AND sent to the server. Audio is never lost to a transient network failure.
- **Pending recordings queue** — Failed transcriptions are cached locally, retried on next keyboard session when the server is healthy, and auto-expire after 7 days. UI for manual retry/discard.
- **Five-state mic ring** — Canvas-based custom view: idle-ok (green breathing), idle-unreachable (red static), idle-checking (amber pulse), listening (cyan spikes driven by real-time audio RMS), transcribing (sweeping arc). Hero visual element.
- **Audio meter** — 14-18 bar EQ visualization replacing the suggestion strip during listening, sharing the same audio source as the mic ring (no sample skew).
- **Terminal modifier row** — Double-row layout: top row (Esc, Ctrl, Alt, PgUp, PgDn), bottom row (Tab, arrows, /). Yellow underline indicators for active Ctrl/Alt. Toggleable from toolbar tray.
- **Voice context presets** — 5 default presets (Coding, Email, Personal, Terminal, General) that bias Whisper's prompt parameter. Editable with word counter warning at 150 words. Switchable from toolbar tray with scale-pulse animation.
- **Post-processing substitutions** — Literal find/replace rules applied after transcription (longest-match-first, case-insensitive). Default rules for common tech terms (TrueNAS, kubectl, PyQt, etc.). Editable list in settings.
- **Optional auth** — None, Bearer, or Basic auth modes. Credentials stored in Android Keystore with AES-GCM encryption. Never logged or displayed in plaintext.
- **Settings screen** — Grouped sections: Server (URL, model dropdown from `/v1/models`, language, auth, test connection), Voice Accuracy (presets, substitutions), Keyboard (terminal row default, sensitive-field behavior, HeliBoard settings), Storage (pending queue), About (credits, version).
- **First-run wizard** — 5 steps: Welcome, Mic Permission, Server Setup, Enable IME, Try It Out. Auto-launches on first keyboard use. Re-triggerable from settings. Skippable.
- **App icon** — Static ring-with-spikes design at all Android density buckets (mdpi through xxxhdpi) plus adaptive icon layers for Android 8+.
- **Full GPL-3.0 compliance** — LICENSE files preserved, README attribution, CHANGES.md, in-app Credits & Licenses screen with tappable links to HeliBoard and OpenBoard repos.
- **Sensitive field detection** — Warns before voice input in password fields. Session-scoped override. Dual-path local cache bypassed for sensitive fields.
- **Server health checking** — Only runs while keyboard is on screen (`onStartInputView`/`onFinishInputView`). No background services. First-tap latency check if stale >30s.
- **Toolbar tray** — Modal overlay with Clipboard, Terminal toggle (cyan when active), Preset switcher, Emoji. Voice preset pills with horizontal scroll.

### Code metrics

| Metric | Value |
|---|---|
| LANboard-specific Kotlin | ~4,500 lines across 19 files |
| Layout XML | 1 custom layout (terminal row) + modifications to strip_container |
| Total project (inherited + new) | ~5,400 lines of LANboard additions |
| Unit tests | 160 passing, 3 pre-existing HeliBoard failures |
| Feature branches merged | 7 |

## What Was Deferred

Per spec section 12.2 (explicitly v2):

- **Pixel 10 Fold split-keyboard mode** — priority v2 item
- Per-app voice preset auto-selection
- Per-preset substitution rules
- Voice activity detection / hands-free mode
- Custom terminal key combos beyond default set
- Function keys (F1-F12), Home/End keys
- Multi-language Whisper beyond configured language
- Custom keyboard themes beyond HeliBoard's
- Model auto-update notifications
- Regex in substitutions

Additionally deferred during build:

- **HeliBoard setup pages integration into LANboard wizard** — wizard works standalone; integrating HeliBoard's own setup screens was deferred to avoid scope creep
- **Health check ring state not updating after async check completes** — cosmetic; ring updates on next frame cycle

## How to Run It

```bash
# Clone
git clone https://github.com/wingman1487/LANboard.git
cd LANboard

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/LANboard_3.9-debug.apk
```

**Prerequisites:**
- Android SDK with build-tools and platform for API 36
- A Whisper-compatible transcription server (e.g., `faster-whisper-server`) reachable from the device

**First run:** The setup wizard launches automatically. It walks through mic permission, server URL, enabling the IME, and a live test.

## How to Test It

Quick reference — full guide is in [TESTING.md](TESTING.md).

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build and install
./gradlew assembleDebug && adb install app/build/outputs/apk/debug/LANboard_3.9-debug.apk
```

Manual validation on device:
1. Complete the setup wizard
2. Open any text field, verify keyboard appears with top strip (toolbar, suggestions, mic ring)
3. Tap mic → speak → tap to commit → verify transcription inserts
4. Long-press mic during listening → verify discard
5. Toggle terminal row from tray → verify Esc/Ctrl/Alt/arrows work in Termux
6. Switch voice presets from tray → verify pill animation
7. Check Settings → About → Credits & Licenses

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin + Java (HeliBoard base) |
| UI framework | Android Views + Jetpack Compose (settings screens) |
| Audio capture | Android AudioRecord (16 kHz mono PCM) |
| Networking | OkHttp (multipart form upload) |
| Credential storage | Android Keystore (AES-GCM) |
| Canvas rendering | Android Canvas API (mic ring, audio meter) |
| Build system | Gradle (Kotlin DSL) |
| Min SDK | 21 (Android 5.0 Lollipop) |
| Target SDK | 36 |
| License | GPL-3.0 |

## Known Issues

- 3 pre-existing HeliBoard test failures (autocorrect capitalization, Hangul input, emoji detection) — inherited from upstream, not LANboard regressions
- Health check ring state may not visually update immediately after an async health check completes; updates on next user interaction
- HeliBoard's own setup/onboarding screens are not integrated into the LANboard wizard — users who want to configure HeliBoard-specific settings (themes, glide typing, etc.) navigate to them from Settings → Keyboard → HeliBoard settings

## Suggested Next Steps

1. **Pixel 10 Fold split-keyboard mode** — user's primary device uses the inner display frequently; split keyboard layout would improve the unfolded experience
2. **Integrate HeliBoard setup pages into LANboard wizard** — seamless first-run experience covering both LANboard and HeliBoard configuration
3. **Quick preset switcher** — a faster way to change presets than the toolbar tray (e.g., swipe on mic ring, tappable label on top strip, or mini-picker overlay) for users who hop between apps frequently
4. **Per-app voice preset auto-selection** — automatically switch to Terminal preset in Termux, Coding in Android Studio, etc.
