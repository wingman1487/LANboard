# Changes from HeliBoard

LANboard is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard). This file documents the major modifications.

## Added
- Self-hosted voice transcription via OpenAI-compatible Whisper API (faster-whisper-server or equivalent)
- Mic ring: custom Canvas-based voice interaction widget with 5 visual states
- Audio meter: real-time amplitude visualization replacing suggestion strip during recording
- Dual-path audio commit: simultaneous local cache + server upload for zero audio loss
- Pending recordings queue with retry/discard for offline resilience
- Voice context presets: named prompt libraries biasing Whisper toward domain vocabulary
- Post-transcription substitution rules for correcting common mishearings
- Terminal modifier row: Esc, Tab, Ctrl, Alt, arrow keys, PgUp, PgDn as a toggleable keyboard layer
- Toolbar tray: modal overlay for clipboard, terminal toggle, preset switching, emoji
- Optional Bearer/Basic authentication for reverse-proxy-protected servers
- Server health checking tied to IME lifecycle (no background polling)
- Sensitive field detection with per-session voice override
- First-run setup wizard (server config, mic permission, IME enable, live test)
- Settings screen: server config, voice accuracy, keyboard options, storage, credits
- LANboard app icon generated from ring renderer snapshot
- Dark theme with cyan/electric-blue accent palette

## Changed
- Application ID: `helium314.keyboard` → `dev.lanboard.keyboard`
- App name and branding: HeliBoard → LANboard
- Top strip layout: added toolbar button (left) and mic ring (right) flanking suggestions

## Preserved
- All HeliBoard keyboard functionality, layouts, themes, and settings
- Full commit history from upstream
- GPL-3.0 and Apache 2.0 (AOSP) license files
- All upstream copyright notices
