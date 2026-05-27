# LANboard

> LANboard is built on top of [HeliBoard](https://github.com/Helium314/HeliBoard), a privacy-conscious open-source Android keyboard by Helium314 and contributors. HeliBoard is itself a fork of [OpenBoard](https://github.com/openboard-team/openboard), based on AOSP's Latin IME. LANboard adds self-hosted voice transcription via Whisper and a terminal-friendly modifier row. We are grateful to the HeliBoard maintainers for the excellent foundation. Issues with the underlying typing engine should generally be reported upstream; LANboard-specific issues (voice, server config, terminal row) belong in this repo.

## Current State

**v1 complete.** All milestones M1-M12 built, tested on Pixel 10 Fold hardware, and reviewed against the full project specification. See [BUILD_SUMMARY.md](BUILD_SUMMARY.md) for what was built and deferred, and [TESTING.md](TESTING.md) for the full testing guide.

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
