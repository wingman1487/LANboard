# LANboard

> LANboard is built on top of [HeliBoard](https://github.com/Helium314/HeliBoard), a privacy-conscious open-source Android keyboard by Helium314 and contributors. HeliBoard is itself a fork of [OpenBoard](https://github.com/openboard-team/openboard), based on AOSP's Latin IME. LANboard adds self-hosted voice transcription via Whisper and a terminal-friendly modifier row. We are grateful to the HeliBoard maintainers for the excellent foundation. Issues with the underlying typing engine should generally be reported upstream; LANboard-specific issues (voice, server config, terminal row) belong in this repo.

## Current State

**v1 complete; v2.4 build underway.** v1 milestones M1-M12 shipped to `main` (tested on Pixel 10 Fold). The design is locked at **v2.4** (the Stage-2 build-review round resolved five open questions; the spec lives in Forge). **Stage 0 done:** Q7 clipboard fallback and Q8 pause-media-during-recording are merged. **Stage 1 done:** the Q6 LANboard Dark glass keyboard (procedural glass key renderer matching `Q6-glass-keyboard.html`, device-tuned and owner-approved on the Pixel 10 Fold) and the Q2 per-preset color data model + `LBPresetPalette` are merged to `develop`. **Stage 2 underway:** the §6.6 settings frosted-glass reskin (single shared §6.1 token source `LANboardTheme.kt`, fixed LANboard Dark matching `Q6-settings-reskin-screen.html`) and the §6.2 per-preset mic-ring color are merged. Newly merged: **§9.3 Manage Presets UI** — the list (identity dot · name · subtitle · drag-handle reorder; General permanent) and the edit screens (curated 6×2 swatch grid with a live cyan→preset-tip mic preview; General's fixed cyan chip), built to the approved `Q2-manage-presets-screen.html`; one global preset order drives the quick-picker fan + per-app cold-start. Newly merged: the **§7.4 delayed-transcription banner** — **Phase 1** is the expanded glass banner (amber dot + preview + Insert/Copy/×) that replaces the v1 Toast when a queued recording transcribes late; **Phase 2** (the "always dot first" model) makes a late transcription *arrive* as a collapsed amber dot on the ▸ toolbar key (pulse-once-then-static) that expands into the banner on a ▸ tap, with §9.2 tap-priority so the first tap goes to the pending transcription. Built to `delayed-transcription-banner.html` and **device-verified on the Pixel 10 Pro Fold** (Phase 1's on-device check also caught + fixed an IME touchable-region passthrough bug). Newly merged: **§7.3 network-resilience fixes** — pending recordings now auto-retry the moment the server returns (no manual Settings retry), recording is allowed while the server is unreachable (§7.1 dual-path; the WAV queues and transcribes on reconnect), and a session-long health monitor keeps the mic ring tracking server reachability in **both directions** on its own (down *or* up) without a keyboard reopen; all device-verified on the Pixel 10 Pro Fold. Next: quick picker, split keyboard, terminal row, font bundling. See [BUILD_SUMMARY.md](BUILD_SUMMARY.md) and [TESTING.md](TESTING.md). See [BUILD_SUMMARY.md](BUILD_SUMMARY.md) and [TESTING.md](TESTING.md).

## What is LANboard

LANboard is a privacy-first Android keyboard that extends HeliBoard with self-hosted voice transcription and terminal-friendly input. All voice processing happens on your own server -- nothing is sent to third-party cloud services. The result is a keyboard that handles both everyday typing and power-user workflows without compromising privacy.

## Features

- **Voice transcription** -- Speak and have your words transcribed by a Whisper-compatible server running on your own hardware or LAN. Supports offline queuing, retry, and dual-path audio commit so recordings are never lost.
- **Terminal modifier row** -- A toggleable row providing Esc, Tab, Ctrl, Alt, arrow keys, PgUp, and PgDn for terminal and IDE use.
- **Dual-path audio** -- Audio is simultaneously cached locally and streamed to the server, ensuring zero audio loss even if the network drops mid-recording.
- **Voice context presets** -- Named prompt libraries that bias Whisper toward domain-specific vocabulary (medical, legal, code, etc.).
- **Toolbar tray** -- Quick access to clipboard, terminal toggle, preset switching, and emoji via a modal overlay.
- **All of HeliBoard** -- Every HeliBoard feature (themes, glide typing, multilingual support, clipboard history, split keyboard, number pad) is preserved.

## Requirements

- Android 5.0 (Lollipop) or later
- A self-hosted Whisper-compatible transcription server (e.g., [faster-whisper-server](https://github.com/fedirz/faster-whisper-server)) reachable from the device

## Building from Source

```bash
git clone https://github.com/user/LANboard.git
cd LANboard
# Ensure Android SDK is installed and ANDROID_HOME is set
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/`.

## License

LANboard is licensed under the [GNU General Public License v3.0](LICENSE), the same license as HeliBoard and OpenBoard.

Since the app is based on Apache 2.0 licensed AOSP Keyboard, an [Apache 2.0](LICENSE-Apache-2.0) license file is also provided. The HeliBoard icon is licensed under [Creative Commons BY-SA 4.0](LICENSE-CC-BY-SA-4.0).

See [CHANGES.md](CHANGES.md) for a summary of what LANboard adds relative to upstream HeliBoard.
